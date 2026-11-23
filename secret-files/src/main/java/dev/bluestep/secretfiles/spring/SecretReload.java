package dev.bluestep.secretfiles.spring;

/**
 * What re-reading a {@link RotatingSecret} or an {@link OptionalRotatingSecret} did.
 */
public enum SecretReload {

	/** Nothing changed: the same value is in force, or the secret was already withdrawn and still is. */
	UNCHANGED,

	/**
	 * The property holds a different value, which is now in force; the previous one no longer is. Also a
	 * withdrawn secret getting a value again, and an optional one getting its first.
	 */
	ROTATED,

	/**
	 * The property is now blank or absent, or holds a placeholder that no longer resolves, so the value
	 * that was in force no longer is. A {@link RotatingSecret}'s {@code current()} now throws
	 * {@link SecretWithdrawnException} (logged as an error); an {@link OptionalRotatingSecret}'s is empty
	 * (logged as a warning). Either way whatever the secret guarded now refuses everyone: a credential the
	 * operator withdrew must stop working at once.
	 */
	WITHDRAWN
}
