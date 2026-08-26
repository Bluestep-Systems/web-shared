package dev.bluestep.global.dto.globaluser;

/**
 * How wide a global user's tenant catalog is, on the wire.
 *
 * <p>Three values rather than a boolean, because the boolean is what got this wrong.
 * {@code global.globaluser.super} was read as "sees everything", and a reseller super has it set
 * legitimately while needing one reseller's tenants only — an answer two-valued logic has no room
 * for, so it silently became the wrong one of the two.</p>
 */
public enum CatalogScopeKind {

	/** Every AI-tools-enabled tenant. */
	FLEET,

	/** Only the tenants owned by the user's reseller. */
	RESELLER,

	/** Only the tenants that have named this user in their own access report. */
	VOUCHED
}
