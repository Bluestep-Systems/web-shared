/**
 * Spring Boot glue for {@code dev.bluestep:secret-files}: keeps the config tree a service imports from
 * its mounted Kubernetes Secret volume in step with that volume, and tells the beans holding a secret
 * when it changed.
 *
 * <p>Compiled against Spring Boot 4.1; Spring is not a dependency of this artifact, so these classes
 * exist for a consumer only when it brings Spring Boot itself. Auto-configured by
 * {@link dev.bluestep.secretfiles.spring.SecretFilesAutoConfiguration}.</p>
 *
 * <p>The arrangement, end to end:</p>
 * <ol>
 *   <li>{@code application.yml} imports the mount with
 *       {@code spring.config.import: optional:configtree:/var/lib/bluestep/secrets/}, so each file
 *       becomes a property named after it and placeholders such as {@code ${API_KEY:}} resolve from it.
 *       An environment variable of the same name still wins, which is how local development and tests
 *       pin a value. {@link dev.bluestep.secretfiles.spring.ExactSecretTreePostProcessor} makes that
 *       tree serve each file's exact bytes, undoing Boot's trailing-newline trim.</li>
 *   <li>{@link dev.bluestep.secretfiles.spring.ConfigTreeSecretsReloader} watches every config tree the
 *       Environment holds. When the kubelet swaps the volume it replaces that property source with a
 *       freshly read one and publishes {@link dev.bluestep.secretfiles.spring.SecretsReloadedEvent}.</li>
 *   <li>Each holder of a secret re-reads its property from the Environment on that event — through a
 *       {@link dev.bluestep.secretfiles.spring.RotatingSecret} from
 *       {@link dev.bluestep.secretfiles.spring.RotatingSecrets}, a
 *       {@code dev.bluestep.secretfiles.spring.hikari.HikariCredentialsRotator}, or its own
 *       {@code @EventListener}. A secret whose property is withdrawn throws
 *       {@link dev.bluestep.secretfiles.spring.SecretWithdrawnException} from {@code current()}; catch
 *       it where the secret is checked and deny.</li>
 * </ol>
 *
 * <p>Null-marked: every type here is non-null unless explicitly annotated
 * {@link org.jspecify.annotations.Nullable}.</p>
 */
@NullMarked
package dev.bluestep.secretfiles.spring;

import org.jspecify.annotations.NullMarked;
