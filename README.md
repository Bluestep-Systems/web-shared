# web-shared

The DTOs web-global serves and its consumers bind (web-global, web, the gateway), plus a few small libraries.

## Artifacts

| Artifact (`dev.bluestep:`) | Content | Tag |
|---|---|---|
| `web-shared` | Umbrella from 6.6.0: **no classes**, `api` dependencies on the four below | bare (`6.6.0`) |
| `web-shared-identity` | tenant, fleet, domain, registry, knowledgebase, tenantknowledgebase, accesstoken, selftoken, ... and the `Argon2idVectors` test fixtures | bare, with the umbrella |
| `web-shared-ai` | ai, aitenantblock, aitenantconfig, aiunitrate, aiusage | bare, with the umbrella |
| `web-shared-usage` | usage | bare, with the umbrella |
| `web-shared-lookup` | lookup, icd10updater, dsm5updater | bare, with the umbrella |
| `secret-files`, `deadline-http`, `contract-negotiation`, `harness-contract` | separate libraries, own version lines | `<artifact>-<version>` |

One bare tag publishes the umbrella and its four groups, the groups first and the umbrella last. `./gradlew checkClassSet`
(part of `check`) proves the four groups hold exactly 6.5.0's classes plus those in `config/class-set/new-in-6.6.0.txt`.

## Gradle module metadata is required

Each group declares a constraint `dev.bluestep:web-shared >= 6.6.0`, so a dependency graph that still holds the
class-carrying `web-shared` 6.5.0 beside a group is raised to the empty umbrella instead of loading every DTO class twice.
**That constraint is in the Gradle module metadata (`.module`).** A POM puts it in `dependencyManagement`, which does not
propagate to consumers, so a POM-only build (Maven, or any resolver that ignores `.module` files) can resolve 6.5.0 and a
group together. Every consumer builds with Gradle, which reads the metadata; keep it that way, or depend on the umbrella
or one group at 6.6.0 and nothing that pins 6.5.0.

## Releasing

See `.github/workflows/publish.yml`. Rerunning a release that stopped part-way finishes it: an upload of a version that
GitHub Packages already holds is skipped.
