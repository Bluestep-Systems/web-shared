package dev.bluestep.secretfiles.spring;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.ConfigTreePropertySource;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.env.PropertySource;

import dev.bluestep.secretfiles.SecretFiles;

/**
 * Makes the mounted secret directory's config tree serve each file's exact contents.
 *
 * <p>Spring Boot's {@code configtree:} import always builds its source with
 * {@code AUTO_TRIM_TRAILING_NEW_LINE}, dropping a lone trailing newline. Secrets used to arrive as
 * environment variables, which carry the Secret's bytes exactly, so a password or a crypt seed stored
 * with a trailing newline would silently change on the first rollout that mounts it instead. Running
 * just after {@link ConfigDataEnvironmentPostProcessor}, this replaces every imported
 * {@link ConfigTreePropertySource} whose directory is the mount directory — {@value SecretFiles#DIRECTORY_PROPERTY}
 * (environment variable {@code BLUESTEP_SECRETS_DIRECTORY}), default {@link SecretFiles#DEFAULT_DIRECTORY}
 * — with an untrimmed one of the same name, in the same place. {@link ConfigTreeSecretsReloader}
 * rebuilds that tree untrimmed too. Any other config tree keeps Boot's trimming.</p>
 *
 * <p>Registered in {@code META-INF/spring.factories}, which only Spring Boot reads.</p>
 */
public final class ExactSecretTreePostProcessor implements EnvironmentPostProcessor, Ordered {

	/** Just after Boot's config data import has added the config trees. */
	public static final int ORDER = ConfigDataEnvironmentPostProcessor.ORDER + 1;

	@Override
	public void postProcessEnvironment(final ConfigurableEnvironment environment, final SpringApplication application) {
		final Path directory = secretsDirectory(environment);
		final List<ConfigTreePropertySource> mounted = new ArrayList<>();
		for (PropertySource<?> source : environment.getPropertySources()) {
			if (source instanceof ConfigTreePropertySource tree && isSecretsDirectory(tree.getSource(), directory)) {
				mounted.add(tree);
			}
		}
		for (ConfigTreePropertySource tree : mounted) {
			environment.getPropertySources().replace(tree.getName(),
					new ConfigTreePropertySource(tree.getName(), tree.getSource()));
		}
	}

	@Override
	public int getOrder() {
		return ORDER;
	}

	/**
	 * The mount directory configured in {@code properties}: {@value SecretFiles#DIRECTORY_PROPERTY}, or
	 * {@link SecretFiles#DEFAULT_DIRECTORY} when that is unset or blank.
	 *
	 * @param properties normally the application's Environment
	 * @return the directory, absolute and normalized
	 */
	static Path secretsDirectory(final PropertyResolver properties) {
		final String directory = properties.getProperty(SecretFiles.DIRECTORY_PROPERTY, "");
		return (directory.isBlank() ? SecretFiles.DEFAULT_DIRECTORY : Path.of(directory)).toAbsolutePath().normalize();
	}

	/**
	 * Whether a config tree read from {@code treeDirectory} is the mount directory.
	 *
	 * @param treeDirectory   the tree's source directory
	 * @param secretsDirectory the mount directory, absolute and normalized
	 * @return true when they are the same directory
	 */
	static boolean isSecretsDirectory(final Path treeDirectory, final Path secretsDirectory) {
		return treeDirectory.toAbsolutePath().normalize().equals(secretsDirectory);
	}
}
