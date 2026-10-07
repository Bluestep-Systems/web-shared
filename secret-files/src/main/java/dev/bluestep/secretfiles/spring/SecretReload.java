package dev.bluestep.secretfiles.spring;

/**
 * What re-reading a {@link RotatingSecret} or an {@link OptionalRotatingSecret} did.
 */
public enum SecretReload {

	/** The property still holds the value in force. */
	UNCHANGED,

	/** The property holds a different value, which is now in force; the previous one no longer is. */
	ROTATED,

	/**
	 * {@link RotatingSecret} only: the property is now blank or absent, so the value in force was kept
	 * and the refusal logged as an error.
	 */
	REFUSED_BLANK,

	/**
	 * {@link OptionalRotatingSecret} only: the property is now blank or absent, so the secret is now
	 * absent too. Absence is a legitimate state for an optional secret, and a credential the operator
	 * withdrew must stop working.
	 */
	WITHDRAWN
}
