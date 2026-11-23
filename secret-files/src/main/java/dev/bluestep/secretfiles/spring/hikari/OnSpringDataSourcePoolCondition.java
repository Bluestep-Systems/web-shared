package dev.bluestep.secretfiles.spring.hikari;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.MethodMetadata;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Matches when the context holds exactly one {@link HikariDataSource}, it is the pool Spring Boot's own
 * {@code DataSourceAutoConfiguration} built from {@code spring.datasource.*}, and
 * {@code spring.datasource.password} is a property to rotate from.
 *
 * <p>"Boot built it" is read from the bean definition: its factory method is declared in Boot's
 * {@code org.springframework.boot.jdbc.autoconfigure} package. A pool the application builds itself —
 * even from the same properties — belongs to the application, which constructs its own rotator.</p>
 */
final class OnSpringDataSourcePoolCondition extends SpringBootCondition implements ConfigurationCondition {

	/** Where Boot 4 declares the {@code spring.datasource} pool's bean method. */
	static final String BOOT_JDBC_AUTOCONFIGURE_PACKAGE = "org.springframework.boot.jdbc.autoconfigure.";

	@Override
	public ConfigurationPhase getConfigurationPhase() {
		return ConfigurationPhase.REGISTER_BEAN;
	}

	@Override
	public ConditionOutcome getMatchOutcome(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
		final ConfigurableListableBeanFactory beans = context.getBeanFactory();
		if (beans == null) {
			return ConditionOutcome.noMatch("no bean factory");
		}
		if (!context.getEnvironment().containsProperty(HikariSecretsAutoConfiguration.PASSWORD_PROPERTY)) {
			return ConditionOutcome.noMatch(HikariSecretsAutoConfiguration.PASSWORD_PROPERTY + " is not set");
		}
		final String[] names = beans.getBeanNamesForType(HikariDataSource.class, true, false);
		if (names.length != 1) {
			return ConditionOutcome.noMatch("found " + names.length + " HikariDataSource beans, not exactly one");
		}
		final String name = names[0];
		if (!beans.containsBeanDefinition(name)) {
			return ConditionOutcome.noMatch("HikariDataSource '" + name + "' has no bean definition");
		}
		final BeanDefinition definition = beans.getBeanDefinition(name);
		if (definition instanceof AnnotatedBeanDefinition annotated) {
			final MethodMetadata factory = annotated.getFactoryMethodMetadata();
			if (factory != null && factory.getDeclaringClassName().startsWith(BOOT_JDBC_AUTOCONFIGURE_PACKAGE)) {
				return ConditionOutcome.match("HikariDataSource '" + name + "' is Spring Boot's spring.datasource pool");
			}
		}
		return ConditionOutcome.noMatch("HikariDataSource '" + name + "' is the application's own pool");
	}
}
