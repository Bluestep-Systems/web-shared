package dev.bluestep.global.dto.domain;

/**
 * The kinds of sign-in a domain can carry: the monolith's {@code LoginTypeRecord.Type}, by name. The
 * constant's name is its wire spelling and the stored {@code logintype.type} value; what each kind's
 * {@link LoginTypeSetting#data()} holds is the monolith's to interpret.
 *
 * <p>An unknown name fails binding, so a new kind arrives with a release of this library.</p>
 */
public enum LoginTypeName {
	/** OAuth 2 configured by the tenant. */
	TENANT_OAUTH2,
	/** OAuth 2 against the fleet's shared providers. */
	GENERAL_OAUTH2,
	/** Password sign-in, in one step. */
	ONE_STEP,
	/** Password sign-in, in two steps. */
	TWO_STEP,
	/** A custom sign-in. */
	CUSTOM
}
