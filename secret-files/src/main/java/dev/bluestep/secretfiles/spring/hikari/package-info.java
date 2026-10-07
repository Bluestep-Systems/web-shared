/**
 * Validate-then-swap rotation of HikariCP credentials: {@link
 * dev.bluestep.secretfiles.spring.hikari.HikariCredentialsRotator}, auto-configured for Spring Boot's
 * own {@code spring.datasource} pool by {@link
 * dev.bluestep.secretfiles.spring.hikari.HikariSecretsAutoConfiguration}.
 *
 * <p>Active only when HikariCP is on the consumer's classpath; this artifact does not bring it.</p>
 *
 * <p>Null-marked: every type here is non-null unless explicitly annotated
 * {@link org.jspecify.annotations.Nullable}.</p>
 */
@NullMarked
package dev.bluestep.secretfiles.spring.hikari;

import org.jspecify.annotations.NullMarked;
