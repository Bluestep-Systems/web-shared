package dev.bluestep.secretfiles.spring;

import java.nio.file.Path;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;

import dev.bluestep.secretfiles.SecretFiles;

/**
 * Wires the mounted-secret glue into any Spring Boot application that has this library on its
 * classpath: the {@link ConfigTreeSecretsReloader}, the {@link RotatingSecrets} registry, and the
 * {@link SecretsReloadedListenerFactory} that isolates {@code @EventListener}s for
 * {@link SecretsReloadedEvent}.
 *
 * <p>Registered in {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports},
 * which only Spring Boot reads, so a non-Boot consumer of the plain-Java core never meets it. It needs
 * nothing beyond Spring Boot itself and has no condition of its own: with no config tree imported the
 * reloader does nothing. Each bean backs off if the application defines its own.</p>
 *
 * <p>Reads one property: {@value SecretFiles#DIRECTORY_PROPERTY} (environment variable
 * {@code BLUESTEP_SECRETS_DIRECTORY}), the directory the chart mounts, default
 * {@code /var/lib/bluestep/secrets}. If it exists, a config tree must import it.</p>
 */
@AutoConfiguration
public class SecretFilesAutoConfiguration {

	/**
	 * Follows every imported config tree.
	 *
	 * @param environment the application's Environment
	 * @param events      where {@link SecretsReloadedEvent} goes
	 * @return the reloader, started with the context
	 */
	@Bean
	@ConditionalOnMissingBean
	public ConfigTreeSecretsReloader configTreeSecretsReloader(final ConfigurableEnvironment environment,
			final ApplicationEventPublisher events) {
		final String directory = environment.getProperty(SecretFiles.DIRECTORY_PROPERTY, "");
		return new ConfigTreeSecretsReloader(environment, events,
				directory.isBlank() ? SecretFiles.DEFAULT_DIRECTORY : Path.of(directory));
	}

	/**
	 * The registry consumers ask for their secrets.
	 *
	 * @param environment the application's Environment
	 * @return the registry, reloading its secrets on every {@link SecretsReloadedEvent}
	 */
	@Bean
	@ConditionalOnMissingBean
	public RotatingSecrets rotatingSecrets(final ConfigurableEnvironment environment) {
		return new RotatingSecrets(environment);
	}

	/**
	 * Static, so the event-listener processor can have it without instantiating this configuration
	 * during bean-factory post-processing.
	 *
	 * @return the factory isolating {@link SecretsReloadedEvent} listeners
	 */
	@Bean
	@ConditionalOnMissingBean
	public static SecretsReloadedListenerFactory secretsReloadedListenerFactory() {
		return new SecretsReloadedListenerFactory();
	}
}
