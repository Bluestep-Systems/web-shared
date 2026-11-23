package dev.bluestep.secretfiles.spring.hikari;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.ConfigurableEnvironment;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Gives Spring Boot's own {@code spring.datasource} pool a {@link HikariCredentialsRotator} reading
 * {@value #USERNAME_PROPERTY} and {@value #PASSWORD_PROPERTY}.
 *
 * <p>Conditions, all of which must hold:</p>
 * <ul>
 *   <li>HikariCP is on the classpath ({@code @ConditionalOnClass}; the whole class is skipped
 *       otherwise, so nothing here loads a Hikari type without it);</li>
 *   <li>{@value #ENABLED_PROPERTY} is not {@code false};</li>
 *   <li>the application defines no {@link HikariCredentialsRotator} of its own — one that does wires
 *       every pool itself;</li>
 *   <li>exactly one {@link HikariDataSource} exists, it is the pool Boot's
 *       {@code DataSourceAutoConfiguration} built, and {@value #PASSWORD_PROPERTY} is set
 *       ({@link OnSpringDataSourcePoolCondition}).</li>
 * </ul>
 *
 * <p>Ordered after {@code DataSourceAutoConfiguration}, named rather than referenced so this class
 * does not need {@code spring-boot-jdbc} to load.</p>
 */
@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@ConditionalOnClass(HikariDataSource.class)
@ConditionalOnProperty(name = HikariSecretsAutoConfiguration.ENABLED_PROPERTY, havingValue = "true",
		matchIfMissing = true)
public class HikariSecretsAutoConfiguration {

	/** Set to {@code false} to leave Boot's pool without an automatic rotator. */
	public static final String ENABLED_PROPERTY = "bluestep.secrets.datasource-rotation.enabled";

	/** Where Boot's pool takes its username. */
	public static final String USERNAME_PROPERTY = "spring.datasource.username";

	/** Where Boot's pool takes its password. */
	public static final String PASSWORD_PROPERTY = "spring.datasource.password";

	/**
	 * The rotator for Boot's pool. The bean is injected as a plain {@code DataSource} and unwrapped, so
	 * a pool Boot has wrapped (a lazy-connection proxy, say) is still found.
	 *
	 * @param dataSource  Boot's {@code spring.datasource} pool
	 * @param environment where the rotated credentials are read back
	 * @return the rotator, listening for every reload
	 * @throws IllegalStateException if the bean cannot be unwrapped to its Hikari pool
	 */
	@Bean
	@ConditionalOnMissingBean(HikariCredentialsRotator.class)
	@Conditional(OnSpringDataSourcePoolCondition.class)
	public HikariCredentialsRotator springDataSourceCredentialsRotator(final DataSource dataSource,
			final ConfigurableEnvironment environment) {
		final HikariDataSource pool;
		try {
			pool = dataSource.unwrap(HikariDataSource.class);
		} catch (SQLException e) {
			throw new IllegalStateException("The spring.datasource bean is not a Hikari pool", e);
		}
		return new HikariCredentialsRotator(pool, environment, USERNAME_PROPERTY, PASSWORD_PROPERTY);
	}
}
