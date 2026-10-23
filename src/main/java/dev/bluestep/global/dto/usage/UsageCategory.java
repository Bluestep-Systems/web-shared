package dev.bluestep.global.dto.usage;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A tenant-visible usage lane: which kind of foreground traffic a figure counts.
 *
 * <p>Only the two lanes a tenant is shown. The background lanes web's producers also report
 * ({@code formula.scheduled}, {@code history.clean} and the rest) are internal and have no constant
 * here, which is why {@link UsageWindow#category()} stays an open string while this enum is
 * closed.</p>
 *
 * <p><b>The wire value is the stored key, not the constant name.</b> {@link #wireValue()} is exactly
 * what producers send in {@link UsageWindow#category()} and what web-global keeps in
 * {@code tenant_usage.category}, so a category read off a response, a query parameter and a stored
 * row are one spelling. {@code UsageWireContractTest} pins each value.</p>
 *
 * <p><b>{@link #name()} and {@link #toString()} are not the wire value.</b> {@code @JsonValue} reaches
 * Jackson only: Spring MVC binds an enum request parameter through {@code Enum.valueOf}, so
 * {@code @RequestParam UsageCategory} would refuse {@code ?category=http}. Bind the parameter as a
 * string and resolve it with {@link #fromWireValue(String)}, and build a URL from
 * {@link #wireValue()}.</p>
 */
public enum UsageCategory {

	/** Page and API requests: everything the per-request filter records that is not file traffic. */
	HTTP {
		@Override
		public String wireValue() {
			return "http";
		}
	},

	/** Stored-file traffic — downloads, previews, WebDAV — counted apart from page traffic. */
	FILE {
		@Override
		public String wireValue() {
			return "file";
		}
	};

	/**
	 * The category's spelling on the wire and in {@code tenant_usage.category}.
	 *
	 * @return the lowercase category key
	 */
	@JsonValue
	public abstract String wireValue();

	/**
	 * Resolves a stored or requested category key to its constant.
	 *
	 * @param wireValue a category key as {@link #wireValue()} spells it; matched exactly, so a
	 *                  differently cased key is not a category
	 * @return the matching category, or empty for a key with no tenant-visible lane
	 */
	public static Optional<UsageCategory> fromWireValue(final String wireValue) {
		for (final UsageCategory category : values()) {
			if (category.wireValue().equals(wireValue)) {
				return Optional.of(category);
			}
		}
		return Optional.empty();
	}
}
