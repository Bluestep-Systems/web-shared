package dev.bluestep.global.dto.reseller;

import java.util.Optional;

/**
 * A reseller's icon set, as {@code global.reseller} holds it.
 *
 * <p>Grouped rather than spread across {@link ResellerResponse} because eight of the twelve columns
 * on that table are icons, and a response whose shape is dominated by them reads as though that is
 * what a reseller mostly is. Every one is optional: the monolith renders a default where a reseller
 * has not supplied one.</p>
 */
public record ResellerIcons(
		Optional<String> favicon,
		Optional<String> favicon16,
		Optional<String> favicon32,
		Optional<String> appleTouchIcon,
		Optional<String> appleTouchIcon72,
		Optional<String> appleTouchIcon114,
		Optional<String> appleTouchIcon144,
		Optional<String> safariPinnedTab
) {
}
