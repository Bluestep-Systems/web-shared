package dev.bluestep.secretfiles.spring.hikari;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;

import com.zaxxer.hikari.HikariDataSource;

import dev.bluestep.secretfiles.SecretFiles;
import dev.bluestep.secretfiles.testing.KubeletSecretVolume;

/**
 * When Boot's own {@code spring.datasource} pool gets a rotator automatically, and that it then rotates
 * end to end: a kubelet swap of the password file, through the config tree, the reloader and the event,
 * to the pool — against an H2 database whose user's password really changed.
 */
@DisplayName("HikariSecretsAutoConfiguration")
class HikariSecretsAutoConfigurationTest {

	private static final String USER = "APP";
	private static final String FIRST = "first-pw-c41d";
	private static final String SECOND = "second-pw-e86f";
	private static final Duration TIMEOUT = Duration.ofSeconds(15);

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
					HikariSecretsAutoConfiguration.class));

	@TempDir
	Path tmp;

	private String url;
	private Connection admin;
	private ConfigurableApplicationContext context;

	@BeforeEach
	void createDatabase() throws SQLException {
		// No DB_CLOSE_DELAY: only an administrator may set it, and the admin connection held open below
		// keeps the in-memory database alive for the test anyway.
		url = "jdbc:h2:mem:" + UUID.randomUUID();
		admin = DriverManager.getConnection(url, "SA", "admin-pw");
		execute("CREATE USER " + USER + " PASSWORD '" + FIRST + "'");
	}

	@AfterEach
	void dropDatabase() throws SQLException {
		if (context != null) {
			context.close();
		}
		execute("SHUTDOWN");
		admin.close();
	}

	private void execute(final String sql) throws SQLException {
		try (Statement statement = admin.createStatement()) {
			statement.execute(sql);
		}
	}

	@Test
	@DisplayName("Boot's spring.datasource pool gets a rotator")
	void bootPoolGetsARotator() {
		runner.withPropertyValues("spring.datasource.url=" + url, "spring.datasource.username=" + USER,
				"spring.datasource.password=" + FIRST)
				.run(context -> assertThat(context).hasSingleBean(HikariCredentialsRotator.class));
	}

	@Test
	@DisplayName("an application's own pool does not, even built from the same properties")
	void ownPoolGetsNone() {
		runner.withPropertyValues("spring.datasource.url=" + url, "spring.datasource.username=" + USER,
				"spring.datasource.password=" + FIRST)
				.withBean(HikariDataSource.class, () -> {
					final HikariDataSource own = new HikariDataSource();
					own.setJdbcUrl(url);
					return own;
				})
				.run(context -> {
					assertThat(context).hasSingleBean(HikariDataSource.class);
					assertThat(context).doesNotHaveBean(HikariCredentialsRotator.class);
				});
	}

	@Test
	@DisplayName("nor does a pool with no spring.datasource.password to rotate from")
	void noPasswordPropertyGetsNone() {
		runner.withPropertyValues("spring.datasource.url=" + url)
				.run(context -> {
					assertThat(context).hasSingleBean(HikariDataSource.class);
					assertThat(context).doesNotHaveBean(HikariCredentialsRotator.class);
				});
	}

	@Test
	@DisplayName("and it can be switched off")
	void canBeDisabled() {
		runner.withPropertyValues("spring.datasource.url=" + url, "spring.datasource.username=" + USER,
				"spring.datasource.password=" + FIRST, HikariSecretsAutoConfiguration.ENABLED_PROPERTY + "=false")
				.run(context -> assertThat(context).doesNotHaveBean(HikariCredentialsRotator.class));
	}

	@Test
	@DisplayName("an application that defines its own rotator wires its pools itself")
	void ownRotatorWins() {
		runner.withPropertyValues("spring.datasource.url=" + url, "spring.datasource.username=" + USER,
				"spring.datasource.password=" + FIRST)
				.withBean("ownRotator", HikariCredentialsRotator.class,
						() -> new HikariCredentialsRotator(unstartedPool(), new MockEnvironment(), "a", "b"))
				.run(context -> assertThat(context.getBeansOfType(HikariCredentialsRotator.class)).containsOnlyKeys(
						"ownRotator"));
	}

	private HikariDataSource unstartedPool() {
		final HikariDataSource pool = new HikariDataSource();
		pool.setJdbcUrl(url);
		return pool;
	}

	@Test
	@DisplayName("end to end: a kubelet swap of the password file moves Boot's pool onto the new password")
	void endToEnd() throws Exception {
		final Path mount = Files.createDirectories(tmp.resolve("secrets"));
		final KubeletSecretVolume volume = KubeletSecretVolume.create(mount, Map.of("DB_PASSWORD", FIRST + "\n"));
		context = new SpringApplicationBuilder(TestApplication.class)
				.web(WebApplicationType.NONE)
				.run("--spring.config.import=optional:configtree:" + mount + "/",
						"--" + SecretFiles.DIRECTORY_PROPERTY + "=" + mount,
						"--spring.datasource.url=" + url,
						"--spring.datasource.username=" + USER,
						"--spring.datasource.password=${DB_PASSWORD}");
		final HikariDataSource pool = context.getBean(HikariDataSource.class);
		try (Connection connection = pool.getConnection()) {
			assertThat(connection.isValid(1)).isTrue();
		}

		execute("ALTER USER " + USER + " SET PASSWORD '" + SECOND + "'");
		volume.swap(Map.of("DB_PASSWORD", SECOND + "\n"));

		final long deadline = System.nanoTime() + TIMEOUT.toNanos();
		while (!SECOND.equals(pool.getCredentials().getPassword())) {
			if (System.nanoTime() - deadline > 0) {
				throw new AssertionError("the pool was not rotated within " + TIMEOUT);
			}
			Thread.sleep(10);
		}
		pool.getHikariPoolMXBean().softEvictConnections();
		try (Connection connection = pool.getConnection()) {
			assertThat(connection.isValid(1)).as("a new connection authenticates with the new password").isTrue();
		}
	}

	/** A Boot application with everything auto-configured, this library included. */
	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	static class TestApplication {
	}
}
