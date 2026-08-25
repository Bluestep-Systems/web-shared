package dev.bluestep.global.dto.ai;

/**
 * A modifier over a whole model's pricing, which is the axis {@code (category, measure)} never had.
 *
 * <p>Batch, fast mode and regional or residency endpoints do not change <em>what</em> is metered or the measure it
 * is counted in — they multiply the price of everything a model meters. Collapsed onto the unit axis that can only
 * be expressed by inventing a model id ({@code claude-opus-5:fast:batch}), which turns a pricing table into a
 * string-munging exercise. Here it is its own column, so one model's rates fan out across variants without any of
 * them pretending to be a different model.</p>
 *
 * <p>Closed, unlike {@code unit_key}, and for the opposite reason: a provider decides what it meters, but a
 * variant is something BlueStep has to <em>choose to sell</em> before a rate for it means anything. Whether any of
 * these are ever exposed to tenants is a product question; representing them is not.</p>
 */
public enum RateVariant {
	/** Ordinary interactive pricing — what every rate row means unless it says otherwise. */
	DEFAULT,
	/** Asynchronous batch processing, discounted by both major providers. */
	BATCH,
	/** Priority or fast-mode serving, charged above the default rate. */
	FAST,
	/** Serving pinned to US data residency, charged above the default rate. */
	RESIDENCY_US;
}
