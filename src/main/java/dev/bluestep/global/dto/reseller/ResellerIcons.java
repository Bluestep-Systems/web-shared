package dev.bluestep.global.dto.reseller;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * A reseller's icon set, as {@code global.reseller} holds it.
 *
 * <p>Grouped rather than spread across {@link ResellerResponse} because eight of the twelve columns
 * on that table are icons, and a response whose shape is dominated by them reads as though that is
 * what a reseller mostly is. Every one is optional: the monolith renders a default where a reseller
 * has not supplied one.</p>
 *
 * <p>The wire shape is unchanged since 4.0.0 and must stay that way — every released contract serves
 * it. An absent icon is a {@code @Nullable} component, read through its {@code optX()} accessor.</p>
 */
public record ResellerIcons(
		@Nullable String favicon,
		@Nullable String favicon16,
		@Nullable String favicon32,
		@Nullable String appleTouchIcon,
		@Nullable String appleTouchIcon72,
		@Nullable String appleTouchIcon114,
		@Nullable String appleTouchIcon144,
		@Nullable String safariPinnedTab
) {

	/** {@link #favicon}, as an {@code Optional}. */
	public Optional<String> optFavicon() {
		return Optional.ofNullable(favicon);
	}

	/** {@link #favicon16}, as an {@code Optional}. */
	public Optional<String> optFavicon16() {
		return Optional.ofNullable(favicon16);
	}

	/** {@link #favicon32}, as an {@code Optional}. */
	public Optional<String> optFavicon32() {
		return Optional.ofNullable(favicon32);
	}

	/** {@link #appleTouchIcon}, as an {@code Optional}. */
	public Optional<String> optAppleTouchIcon() {
		return Optional.ofNullable(appleTouchIcon);
	}

	/** {@link #appleTouchIcon72}, as an {@code Optional}. */
	public Optional<String> optAppleTouchIcon72() {
		return Optional.ofNullable(appleTouchIcon72);
	}

	/** {@link #appleTouchIcon114}, as an {@code Optional}. */
	public Optional<String> optAppleTouchIcon114() {
		return Optional.ofNullable(appleTouchIcon114);
	}

	/** {@link #appleTouchIcon144}, as an {@code Optional}. */
	public Optional<String> optAppleTouchIcon144() {
		return Optional.ofNullable(appleTouchIcon144);
	}

	/** {@link #safariPinnedTab}, as an {@code Optional}. */
	public Optional<String> optSafariPinnedTab() {
		return Optional.ofNullable(safariPinnedTab);
	}
}
