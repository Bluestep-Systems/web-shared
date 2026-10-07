# secret-files

`dev.bluestep:secret-files` reads secrets from a directory of files, one file per key, and follows
that directory live. It exists so services can take their secrets from a Kubernetes Secret volume
(mounted at `/var/lib/bluestep/secrets/`, tmpfs) instead of environment variables, and pick up a
rotated Secret without a restart.

One artifact, three layers:

| Package | What | Needs |
|---|---|---|
| `dev.bluestep.secretfiles` | The core: `WatchedSecretDirectory`, `SecretSnapshot`, `PinnedGeneration`, `SecretFiles` | Java 25, nothing else |
| `dev.bluestep.secretfiles.spring` | Config-tree reloader, `RotatingSecret`, auto-configuration | Spring Boot 4.1 (the consumer's) |
| `dev.bluestep.secretfiles.spring.hikari` | `HikariCredentialsRotator`, auto-configured for Boot's pool | HikariCP 7 (the consumer's) |

Plus a **test fixture**, `dev.bluestep.secretfiles.testing.KubeletSecretVolume`, published as this
artifact's test-fixtures variant.

**The artifact brings no runtime dependencies.** Spring Boot, Spring Framework and HikariCP are
compile-only here: the published POM and Gradle module list none of them. The Spring layer exists for a
consumer only when that consumer has Spring Boot on its classpath (Boot reads the auto-configuration
imports file; nothing else does), and the Hikari layer only when it also has HikariCP
(`@ConditionalOnClass`). A plain-Java consumer uses the core and never loads a Spring class.

## Consumer setup (Spring Boot 4.1 service)

1. **Dependencies** — from the same GitHub Packages repository as `web-shared`:

   ```groovy
   implementation 'dev.bluestep:secret-files:1.0.0'
   testImplementation(testFixtures('dev.bluestep:secret-files:1.0.0'))
   ```

2. **One line of configuration** — import the mount as a config tree, in `application.yml`:

   ```yaml
   spring:
     config:
       import: "optional:configtree:/var/lib/bluestep/secrets/"
   ```

   Each file becomes a property named after it, so existing placeholders keep working:
   `my.api-key: ${MY_API_KEY:}` resolves from the file `MY_API_KEY`. `optional:` lets the service start
   where there is no mount (local development, tests). This is the only place the path is written; the
   reloader finds the import itself. Each value is the file's **exact bytes**, a trailing newline
   included, as the environment variable it replaces carried them — see
   [Exact bytes](#exact-bytes-no-trailing-newline-trim).

3. **Chart** — mount the Secret as a volume at `/var/lib/bluestep/secrets`:

   ```yaml
   volumes:
     - name: secrets
       secret:
         secretName: my-service-secrets
   containers:
     - volumeMounts:
         - name: secrets
           mountPath: /var/lib/bluestep/secrets
           readOnly: true
   ```

   - Mount the **whole** Secret. A `subPath` mount is never updated by the kubelet, so it never rotates.
   - **Stop injecting the same keys as environment variables.** An environment variable outranks the
     file of the same name, by design (it is how local development pins a value), so a key still set
     through `env`/`envFrom` would never show a rotation.
   - File names are the Secret's keys; keep them identical to the environment variable names they
     replace. Keys are flat: no `items[].path` with a `/`, since a subdirectory refuses startup.

That is all the wiring. Everything below is what you then use.

## Exact bytes (no trailing-newline trim)

Spring Boot's `configtree:` import always trims a lone trailing newline
(`ConfigTreePropertySource.Option.AUTO_TRIM_TRAILING_NEW_LINE`). Secrets used to arrive through
`envFrom`, which passes the Secret's bytes exactly, so a password or crypt seed stored with a trailing
newline would silently change on the first rollout that mounts it instead.

This library therefore serves **the mount directory** untrimmed:

- `ExactSecretTreePostProcessor` (an `EnvironmentPostProcessor` registered in `META-INF/spring.factories`,
  ordered just after Boot's `ConfigDataEnvironmentPostProcessor`) replaces the imported config tree for
  the mount directory — `bluestep.secrets.directory`, default `/var/lib/bluestep/secrets` — with an
  untrimmed one of the same name, in the same place.
- The reloader rebuilds that tree untrimmed too, so a value reads the same before and after a reload.
- `SecretFiles.get`/`require` return the file's exact contents.

A file `seed\n` is served as `seed\n`. Any **other** config tree a service imports keeps Boot's trimming.
If you created a Secret with `echo` and relied on Boot dropping the newline, recreate it without one
(`kubectl create secret generic ... --from-literal`, or `echo -n`).

## The pieces

### Config-tree reloader (automatic)

`ConfigTreeSecretsReloader`, a `SmartLifecycle` bean auto-configured by
`SecretFilesAutoConfiguration`:

- **At start** it finds every `ConfigTreePropertySource` the Environment holds, follows each one's
  directory with a `WatchedSecretDirectory`, and catches up once (a swap between Boot reading the tree
  and the bean starting is applied, not missed). With no config tree imported it does nothing.
- **It refuses to start** if the mount directory exists but no config tree imports it — the chart
  mounted secrets the service would never read. The directory is `/var/lib/bluestep/secrets` unless
  `bluestep.secrets.directory` (env `BLUESTEP_SECRETS_DIRECTORY`) says otherwise.
- **On a change** it builds a fresh `ConfigTreePropertySource` with the same name and options as the
  source it replaces (the mount directory untrimmed, any other tree with Boot's
  `AUTO_TRIM_TRAILING_NEW_LINE`), reads and caches every value, compares with what the Environment
  serves, and only on a real difference replaces the source in place (`replace`: same position, so
  precedence does not move) and publishes **`SecretsReloadedEvent(Path directory)`** — directory only,
  no values. Compare, replace and publish happen under one lock.
- **Each re-read is pinned to one kubelet generation**, like the core's (`PinnedGeneration`): it is kept
  only if `..data` named the same timestamped directory before and after every value was read, and if
  Boot listed exactly that generation's keys. A swap landing mid-read discards it, so the Environment
  never holds values from two versions of the Secret. The values are captured, so the source never
  reopens a file the kubelet has since deleted.
- **Keys match Boot's**: a single-dot file such as `.token` is a key (rotating it refreshes Spring);
  the kubelet's `..`-prefixed entries are not. A subdirectory, which Boot would read as dotted keys,
  refuses startup with an error naming it — a Secret volume cannot produce one.
- **A re-read that fails is retried until it succeeds**, backing off from 200 ms to a 5 s ceiling. (A
  link the kubelet is about to delete dangles mid-swap; Boot's reader fails on it while the core
  skips it, so the core has nothing more to report. Without retrying, the Environment would stay stale
  until the Secret next changed.)
- **On stop** nothing more is published, pending retries included.

Listen for changes with `@EventListener`:

```java
@EventListener
void onSecretsReloaded(SecretsReloadedEvent event) {
    // re-read what you own from the Environment; never throw a value in an exception message
}
```

**Each such listener is isolated**: one that throws is logged (its bean class and method, and the
exception's types — see [Logging](#logging-never-a-value-never-an-exceptions-message)), and the
listeners after it are still told (Spring's default delivery would stop at the first exception). This applies to `@EventListener`
methods whose declared event is exactly `SecretsReloadedEvent`; see `SecretsReloadedListenerFactory`.

### `RotatingSecret` — one secret-valued property

Ask the auto-configured `RotatingSecrets` registry; it reloads every secret it hands out on each
`SecretsReloadedEvent`. The secret itself need not be a bean.

```java
@Component
class AgentClient {
    private final RotatingSecret token;
    private final OptionalRotatingSecret reader;

    AgentClient(RotatingSecrets secrets) {
        this.token = secrets.required("oauth2.agent.token", "OAUTH2_AGENT_TOKEN");
        this.reader = secrets.optional("registry.reader-key");
    }

    void call() {
        String bearer = token.current();   // read per use: a rotation applies from the next call
    }

    boolean readerAllowed(String presented) {
        return reader.current().map(presented::equals).orElse(false);   // absent refuses everyone
    }
}
```

- `required(property[, setting])` — blank or absent at startup throws `IllegalStateException` naming
  the property and the setting to provide. Blank, absent, or a placeholder that no longer resolves at
  runtime **withdraws** it: `current()` throws `SecretWithdrawnException`, logged once as an ERROR
  (property name only). A later non-blank value restores it. A changed value replaces the old one at
  once, no grace window.
- `optional(property)` — absence is a legitimate state (`current()` is an `Optional`). Going blank,
  absent or unresolvable at runtime is a withdrawal too: `current()` becomes empty, logged as a WARN.
- `reload()` returns `SecretReload.UNCHANGED`, `ROTATED` (a new value, or one restored after a
  withdrawal) or `WITHDRAWN`.
- `RotatingSecret.required(env, property)` / `RotatingSecret.optional(env, property)` build one outside
  the registry; it then reloads only when you call `reload()`.
- The registry registers each secret before its first read, so a reload landing while it is being
  created is not missed.
- Nothing logs a value; `toString()` names the property.

#### Withdrawal

Deleting a key from the Secret (or blanking it) is how an operator revokes a compromised credential,
and it takes effect in running pods at the next reload: the old value is **not** kept. Catch
`SecretWithdrawnException` (an `IllegalStateException`) at your auth boundary and **deny** — reject the
request, skip the outbound call:

```java
try {
    return MessageDigest.isEqual(presented, token.current().getBytes(UTF_8));
} catch (SecretWithdrawnException e) {
    return false;   // withdrawn: nobody is let in
}
```

Its message names the property and setting, never a value. Uncaught, it still fails closed.

### `HikariCredentialsRotator` — validated database credential rotation

On `SecretsReloadedEvent` it reads the username and password properties back from the Environment. If
they are what the pool holds, nothing happens. Otherwise it opens **one** connection with them exactly
as the pool does, through the DataSource Hikari itself uses, in Hikari's order:

- a supplied `DataSource` (such as a `PGSimpleDataSource`), that same instance;
- else a fresh `dataSourceClassName` instance given the pool's `dataSourceProperties` (Hikari ignores
  `jdbcUrl` then, and so does the probe);
- else a `DriverDataSource` over the pool's `jdbcUrl`/driver/properties.

The credentials are passed to `getConnection(username, password)`, and the pool's `connectionInitSql`,
if any, is run on the connection. Only if all of that works are they applied with
`HikariDataSource.setCredentials`. A failure, an init-SQL failure included, is logged (SQLState and
exception types only) and the pool keeps its credentials. Open connections retire at `maxLifetime`
unless you ask for `ExistingConnections.SOFT_EVICT`.

**Password-only configurations rotate**: with the username property blank or absent, the password is
offered with the pool's own username (`HikariConfig.getUsername()`). With no username there either, the
offer is refused untried.

**A new rotator catches up at once.** Its constructor runs the same validate-then-swap (retries
included) instead of waiting for the next reload, so a pool created from old properties after a rotation
it never heard of — the password published before `ALTER ROLE`, say — still converges.

**A refused offer is retried.** A refusal is usually order or timing — the Secret was updated before
`ALTER ROLE` ran, or the database blinked — and the Secret will not change again to say it is now fine.
So the rotator tries the offer again on a background virtual thread: after 5 s, doubling, to a ceiling
of 5 min, and on at the ceiling until one of these happens:

- the offer validates — it is applied, exactly as an event would have applied it;
- the offer changes — the new offer supersedes it and the old retry is cancelled (a new offer refused
  in turn starts its own retry from 5 s; one that reverts to what the pool holds, or goes blank, ends
  retrying);
- the offer can no longer be read (a placeholder that stopped resolving) — the next reload tries again;
- the rotator is closed. It is `AutoCloseable`, so a rotator **bean** is closed with the context.

Each retry reads the offer back from the Environment afresh. The refusal is logged once at ERROR,
failed retries at DEBUG, the eventual success at INFO.

**Observing an apply.** Since an apply can now happen later, on the retry thread, a consumer that keeps
state derived from the pool's credentials registers a callback rather than inspecting `rotate()`'s
outcome:

```java
new HikariCredentialsRotator(pool, environment, "tenant.datasource.username", "tenant.datasource.password")
        .onApplied(credentials -> tenantProperties.setPassword(credentials.getPassword()));
```

`onApplied(Consumer<? super Credentials>)` is called after every successful `setCredentials` — from an
event, a direct `rotate()`, a retry or the construction catch-up — with exactly the credentials the pool
now holds, under the rotator's lock (so applies arrive in order). A listener registered after the
rotator has applied anything (the catch-up, in the pattern above) is told of the latest apply at once. Refusals and unchanged offers never reach it. A callback
that throws is logged by type and neither undoes the rotation nor silences the next callback.

**Boot's own pool is automatic**: when the only `HikariDataSource` is the one Boot's
`DataSourceAutoConfiguration` built and `spring.datasource.password` is set, a rotator over
`spring.datasource.username`/`spring.datasource.password` is auto-configured. Point those properties at
the files: `spring.datasource.password: ${DB_PASSWORD}`. Switch it off with
`bluestep.secrets.datasource-rotation.enabled=false`.

**Your own pools** get a rotator bean each (defining any rotator bean turns the automatic one off):

```java
@Bean
HikariCredentialsRotator tenantPoolRotator(HikariDataSource tenantPool, Environment environment) {
    return new HikariCredentialsRotator(tenantPool, environment,
            "tenant.datasource.username", "tenant.datasource.password");
}
```

A pool that takes its credentials from a `HikariCredentialsProvider` is refused at construction:
Hikari consults the provider instead of `setCredentials`, so the rotation would silently not happen.

A rotator **only listens as a bean**. One you hold privately — to rotate several pools from one
listener of your own, say — hears nothing by itself: call `rotate()` on each reload and `close()` at
shutdown. Its retries and `onApplied` callbacks work exactly as a bean's do.

### `@WebMvcTest` slices (automatic)

A slice runs only the auto-configurations listed under its own annotations, so a consumer whose slice
includes a `Filter` or `@Component` that injects `RotatingSecrets` used to fail every slice. This
artifact lists `SecretFilesAutoConfiguration` in
`META-INF/spring/org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc.imports` (the
file Boot 4.1's `@WebMvcTest` reads), so every `@WebMvcTest` slice has the reloader, the registry and
the listener factory with no help from the consumer. Under test no config tree is imported, so the
reloader does nothing. Delete any copy of that line a consumer added to its own test resources.

### `SecretFiles` — no Spring, one shot

For CLI mains, `runclass` utilities and anything else that runs without a Spring context:

```java
String password = SecretFiles.require("B6P_DB_PASSWORD");        // throws naming the key if missing/blank
Optional<String> token = SecretFiles.get("OPTIONAL_TOKEN");
```

It reads the file `KEY` from the mount directory (`/var/lib/bluestep/secrets`, or the system property
`bluestep.secrets.directory`, or env `BLUESTEP_SECRETS_DIRECTORY`) if it exists, returning its exact
contents (nothing trimmed, as the Spring side serves the mount), and otherwise falls back to the
environment variable `KEY`. It reads once and does not watch.

### `WatchedSecretDirectory` — the core, for anything else

```java
SecretDirectory secrets = WatchedSecretDirectory.open(Path.of("/var/lib/bluestep/secrets"));
secrets.subscribe(snapshot -> rebind(snapshot.get("DB_PASSWORD").orElseThrow()));
String password = secrets.current().get("DB_PASSWORD").orElseThrow();
// ... on shutdown
secrets.close();
```

See [How the core works](#how-the-core-works) below.

### `KubeletSecretVolume` — test fixture

Lays a temp directory out exactly as the kubelet does (`..<timestamp>/KEY`, `..data -> ..<timestamp>`,
`KEY -> ..data/KEY`) and replays an update in the kubelet's order, including the atomic `..data` rename.

```java
@TempDir Path mount;

@Test
void followsARotation() throws Exception {
    KubeletSecretVolume volume = KubeletSecretVolume.create(mount, Map.of("API_KEY", "first\n"));
    // boot the app with --spring.config.import=optional:configtree:<mount>/
    volume.swap(Map.of("API_KEY", "second\n"));
    // await the SecretsReloadedEvent, then assert
}
```

`beginSwap(next)` stops an update just after the rename, leaving links for dropped keys dangling, and
`complete()` finishes it — for testing what happens inside the kubelet's update window.

## Logging: never a value, never an exception's message

The library logs secrets by **name** only — a key, a property, a directory, a pool — and logs a failure
by **exception type** only: the exception's class name and its causes' class names, never
`getMessage()` and never a stack trace (which prints every cause's message). The reason is that
messages quote what they were given. Spring Boot's binder, for one, puts the offending property value
in its exception — bind a secret into an `Integer` and the secret is in the message, and in the
message of every exception wrapping it. A listener failure, a placeholder that stops resolving, a
driver refusing a login: all are logged as, for example,
`(java.lang.IllegalStateException <- org.springframework.boot.context.properties.bind.BindException)`.
A JDBC failure adds its SQLState.

Hold your own code that handles secrets to the same rule. `dev.bluestep.secretfiles.ExceptionTypes.of(e)`
renders exactly that chain.

**Beware `'` in parameterised `java.util.logging` / `System.Logger` messages.** With parameters,
JUL formats the message with `java.text.MessageFormat`, where a single quote starts a quoted literal:
`LOG.log(Level.WARNING, "the chart's mount {0} is missing", dir)` logs `the charts mount {0} is
missing` — the apostrophe vanishes and so does the value it was meant to show. Write `''` for a
literal quote, rephrase, or concatenate without parameters (a message logged with no parameters is not
run through `MessageFormat`). Commons Logging / SLF4J messages are not affected.

## Properties

| Property | Default | Meaning |
|---|---|---|
| `bluestep.secrets.directory` (env `BLUESTEP_SECRETS_DIRECTORY`) | `/var/lib/bluestep/secrets` | The chart's mount. If it exists, a config tree must import it. Also read by `SecretFiles` (as a system property). |
| `bluestep.secrets.datasource-rotation.enabled` | `true` | Auto-configure a rotator for Boot's `spring.datasource` pool. |

## How the core works

The kubelet never rewrites a secret file in place. The mount looks like this:

```
DB_PASSWORD -> ..data/DB_PASSWORD
API_TOKEN   -> ..data/API_TOKEN
..data      -> ..2026_10_07_16_00_00.123456789
..2026_10_07_16_00_00.123456789/DB_PASSWORD   (the real files)
..2026_10_07_16_00_00.123456789/API_TOKEN
```

An update writes a complete new timestamped directory, points a temporary symlink at it, and renames
that symlink over `..data`. The rename is atomic, so every key switches at once and a reader sees
either all-old or all-new values. The kubelet then adds links for new keys, removes links for deleted
keys, and deletes the old timestamped directory.

`WatchedSecretDirectory`:

- **Reads one generation at a time.** `..data` is resolved once to its timestamped directory, every key
  is read from that directory, and the read is kept only if `..data` still names it afterwards;
  otherwise it is discarded and done again, up to `PinnedGeneration.MAX_ATTEMPTS` (5) times. A swap
  landing between two key reads therefore never yields a snapshot mixing two versions. A directory
  with no `..data` (a plain directory, as in local development) is read directly.
- **Keys as Spring Boot's config tree sees them.** A key is an entry whose name does not start with
  `..` and which resolves, following symlinks, to a regular file: `.token` is a key; `..data`,
  `..data_tmp`, the timestamped directories and dangling links are not. A subdirectory is refused:
  `open` throws `IllegalArgumentException` naming it, and a later read that meets one fails (logged,
  snapshot kept).
- **Watches and polls.** A `java.nio` `WatchService` on the directory sees the swap as events on
  `..data`. A poll (every 30 s by default) re-reads anyway, so a missed event delays an update rather
  than losing it. Both run on one virtual thread.
- **Coalesces and compares.** The events from one swap produce one re-read. Listeners are notified
  only if the values actually changed.
- **Never publishes a failed read.** A read that fails (`..data` kept moving, or a plain directory's
  file vanished between listing and reading) is thrown away and retried once shortly afterwards; the
  poll covers anything later.
- **Isolates listeners.** A listener that throws is logged (by exception type only) and the others still run.
- **Never logs a value.** Log lines name the directory and keys only. `SecretSnapshot.toString()`
  prints key names, not values.

Values are the file contents decoded as UTF-8, **exactly**: nothing is trimmed (nor does the Spring
layer trim the mount directory, nor `SecretFiles`). A file that is not valid UTF-8 is left out of the
snapshot, with a warning naming the key.

`open` throws `IllegalArgumentException` when the path is missing, is not a directory, or holds a
subdirectory. Subscribe first and then read `current()`, so a change that lands between the two calls
is seen rather than missed. Listeners run in subscription order on the watcher thread, so keep them quick.

## Versioning and releases

This artifact has its **own version line** (it started at 1.0.0), separate from the root
`dev.bluestep:web-shared` library. The root library's version is also the API path web-global serves,
so the two must not move together.

- The version lives in `secret-files/build.gradle`.
- Release it with a GitHub release tagged **`secret-files-<version>`**, e.g. `secret-files-1.0.0`.
  The publish workflow sees the prefix, checks that the number matches the build file, and publishes
  this artifact only (main jar, test-fixtures jar, POM and Gradle module).
- Any other tag (e.g. `4.1.7`) publishes the root library only, as before.
- Before a release exists, `./gradlew :secret-files:publishToMavenLocal` puts the current version in
  `~/.m2` for the consuming services to build against; consumers resolve it through `mavenLocal()`.
  Gradle consumers need the Gradle module metadata (published alongside the POM) to see the
  test-fixtures variant.
