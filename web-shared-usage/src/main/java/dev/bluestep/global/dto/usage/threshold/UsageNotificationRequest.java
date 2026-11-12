package dev.bluestep.global.dto.usage.threshold;

import java.util.Objects;
import java.util.Set;

import dev.bluestep.global.dto.constraints.CodePointSize;
import dev.bluestep.global.dto.usage.StorageSample;

import jakarta.validation.constraints.NotBlank;

/**
 * Which pending {@link UsageNotification}s web's dispatcher asks for: those of the schemas its
 * namespace serves. web-global answers a {@link UsageNotificationBatch} holding only those
 * schemas' notifications, so a dispatcher is never handed a notice for a tenant whose users it
 * cannot resolve.
 *
 * <p>The dispatcher runs on each namespace's task pod, one per namespace, so the namespaces' requests
 * never overlap and no two dispatchers are offered the same notification.</p>
 *
 * @param schemaNames the canonical {@code U<seqnum>} of every schema the namespace serves, each at
 *                    most {@value StorageSample#MAX_SCHEMA_NAME_LENGTH} code points; at least one
 */
public record UsageNotificationRequest(
		Set<@NotBlank @CodePointSize(max = StorageSample.MAX_SCHEMA_NAME_LENGTH) String> schemaNames) {

	/**
	 * Refuses a missing or empty scope and copies it.
	 *
	 * @throws NullPointerException     when {@code schemaNames} is missing or holds a null
	 * @throws IllegalArgumentException when {@code schemaNames} is empty
	 */
	public UsageNotificationRequest {
		Objects.requireNonNull(schemaNames, "schemaNames");
		schemaNames = Set.copyOf(schemaNames);
		if (schemaNames.isEmpty()) {
			throw new IllegalArgumentException("a notification request names at least one schema");
		}
	}
}
