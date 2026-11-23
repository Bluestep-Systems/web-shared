package dev.bluestep.secretfiles.spring;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.PropertyResolver;

import dev.bluestep.secretfiles.ExceptionTypes;

/**
 * Hands out {@link RotatingSecret}s and {@link OptionalRotatingSecret}s that follow the mounted secret
 * directory: every secret it created is reloaded on each {@link SecretsReloadedEvent}. Auto-configured
 * by {@link SecretFilesAutoConfiguration} over the application's Environment.
 *
 * <p>A consumer injects this and asks it for the secret it holds; the secret need not be a bean, and
 * nothing else needs registering:</p>
 *
 * <pre>{@code
 * @Component
 * class AgentClient {
 *     private final RotatingSecret token;
 *
 *     AgentClient(RotatingSecrets secrets) {
 *         this.token = secrets.required("oauth2.agent.token", "OAUTH2_AGENT_TOKEN");
 *     }
 *
 *     void call() {
 *         String bearer = token.current();   // per use, so a rotation applies from the next call
 *     }
 * }
 * }</pre>
 *
 * <p>Create each secret once, at construction, as above: this holds every secret it created for the
 * life of the context.</p>
 *
 * <p>A secret whose property goes blank, absent or unresolvable is withdrawn
 * ({@link SecretWithdrawnException}); see {@link RotatingSecret}. One whose reload throws anyway — a
 * broken property resolver — is logged by property name and does not stop the others.</p>
 *
 * <p>Each secret is registered before its first read, so a reload that lands while it is being created
 * is not missed.</p>
 */
public final class RotatingSecrets {

	private static final Log LOG = LogFactory.getLog(RotatingSecrets.class);

	private final PropertyResolver properties;
	private final List<RotatingSecret> required = new CopyOnWriteArrayList<>();
	private final List<OptionalRotatingSecret> optional = new CopyOnWriteArrayList<>();

	/**
	 * @param properties where every secret is resolved, normally the application's Environment
	 */
	public RotatingSecrets(final PropertyResolver properties) {
		this.properties = Objects.requireNonNull(properties, "properties");
	}

	/**
	 * A secret that must have a value, followed from now on.
	 *
	 * @param property the Spring property holding it
	 * @return the secret
	 * @throws IllegalStateException    if the property is absent or blank now; the message names it
	 * @throws IllegalArgumentException if its value holds a placeholder that does not resolve
	 * @see RotatingSecret#required(PropertyResolver, String)
	 */
	public RotatingSecret required(final String property) {
		return register(RotatingSecret.unread(properties, property, Optional.empty()));
	}

	/**
	 * A secret that must have a value, followed from now on.
	 *
	 * @param property the Spring property holding it
	 * @param setting  what an operator sets to provide it (the environment variable and file name)
	 * @return the secret
	 * @throws IllegalStateException    if the property is absent or blank now; the message names the
	 *                                  property and {@code setting}
	 * @throws IllegalArgumentException if its value holds a placeholder that does not resolve
	 * @see RotatingSecret#required(PropertyResolver, String, String)
	 */
	public RotatingSecret required(final String property, final String setting) {
		return register(RotatingSecret.unread(properties, property, Optional.of(setting)));
	}

	/**
	 * A secret whose absence is legitimate, followed from now on.
	 *
	 * @param property the Spring property holding it
	 * @return the secret, empty now if the property is absent or blank
	 * @throws IllegalArgumentException if its value holds a placeholder that does not resolve
	 * @see OptionalRotatingSecret
	 */
	public OptionalRotatingSecret optional(final String property) {
		final OptionalRotatingSecret secret = new OptionalRotatingSecret(properties, property);
		// Registered before its first read, so a reload landing in between reloads it.
		optional.add(secret);
		try {
			return secret.initialize();
		} catch (RuntimeException e) {
			optional.remove(secret);
			throw e;
		}
	}

	/**
	 * Reloads every secret this created.
	 *
	 * @param event the change that prompted it
	 */
	@EventListener
	public void onSecretsReloaded(final SecretsReloadedEvent event) {
		for (RotatingSecret secret : required) {
			try {
				secret.reload();
			} catch (RuntimeException e) {
				failed(secret.property(), e);
			}
		}
		for (OptionalRotatingSecret secret : optional) {
			try {
				secret.reload();
			} catch (RuntimeException e) {
				failed(secret.property(), e);
			}
		}
	}

	/**
	 * Registers {@code secret} and only then reads it. A reload landing between the two either finds it
	 * registered and reloads it after the read (both are synchronized on the secret), or ran entirely
	 * before the read, which then sees the reloaded Environment: either way none is missed.
	 */
	private RotatingSecret register(final RotatingSecret secret) {
		required.add(secret);
		try {
			return secret.initialize();
		} catch (RuntimeException e) {
			required.remove(secret);
			throw e;
		}
	}

	private static void failed(final String property, final RuntimeException e) {
		// The type only: a resolver's message can quote the text it was resolving.
		LOG.error("Reloading " + property + " failed (" + ExceptionTypes.of(e) + "); it keeps the state it had "
				+ "and the other secrets were still reloaded");
	}
}
