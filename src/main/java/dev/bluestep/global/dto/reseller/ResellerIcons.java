package dev.bluestep.global.dto.reseller;

import java.util.Optional;

/**
 * A reseller's icon set, as {@code global.reseller} holds it.
 *
 * <p>Grouped rather than spread across {@link ResellerResponse} because eight of the twelve columns
 * on that table are icons, and a response whose shape is dominated by them reads as though that is
 * what a reseller mostly is. Every one is optional: the monolith renders a default where a reseller
 * has not supplied one.</p>
 *
 * <p>The shape is unchanged since 4.0.0 and must stay that way — both released contracts serve it.
 * What was added is the compact constructor below, which is not a reshape: it binds nothing new and
 * serializes identically, and it exists because this record became something a caller <em>sends</em>
 * as well as receives.</p>
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

	/**
	 * Jackson binds an omitted field to {@code null} whatever this package's null-marking says, and an
	 * icon set is exactly the payload a caller sends half of — eight optional fields, most of which a
	 * given reseller has no value for.
	 *
	 * <p>Without this, an omitted icon puts a literal {@code null} into a component the type system
	 * says can never hold one, and the failure surfaces at whatever later dereferences it rather than
	 * at the boundary that produced it.</p>
	 */
	public ResellerIcons {
		favicon = favicon == null ? Optional.empty() : favicon;
		favicon16 = favicon16 == null ? Optional.empty() : favicon16;
		favicon32 = favicon32 == null ? Optional.empty() : favicon32;
		appleTouchIcon = appleTouchIcon == null ? Optional.empty() : appleTouchIcon;
		appleTouchIcon72 = appleTouchIcon72 == null ? Optional.empty() : appleTouchIcon72;
		appleTouchIcon114 = appleTouchIcon114 == null ? Optional.empty() : appleTouchIcon114;
		appleTouchIcon144 = appleTouchIcon144 == null ? Optional.empty() : appleTouchIcon144;
		safariPinnedTab = safariPinnedTab == null ? Optional.empty() : safariPinnedTab;
	}
}
