package dev.bluestep.harness.testing;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * The harness's wire, as JSON both sides' tests read: web's controller test asserts that it answers these, and
 * web-global's harness client test has a stub server answer them. One file per sample, beside this class.
 */
public enum HarnessWireSamples {

	/** U137803 found, two rows in name order. */
	TENANT_KNOWLEDGE_BASE_ROWS_FOUND("tenant-knowledge-base-rows-found.json"),

	/** U137803 found, no rows (the schema has no knowledge_base table yet, or no knowledge bases). */
	TENANT_KNOWLEDGE_BASE_ROWS_FOUND_EMPTY("tenant-knowledge-base-rows-found-empty.json"),

	/** U142133 absent from this namespace. */
	TENANT_KNOWLEDGE_BASE_ROWS_ABSENT("tenant-knowledge-base-rows-absent.json"),

	/** What a newer web might answer: a state and a field 1.0.0 does not know. Binds as UNKNOWN. */
	TENANT_KNOWLEDGE_BASE_ROWS_NEWER_STATE("tenant-knowledge-base-rows-newer-state.json");

	private final String resource;

	HarnessWireSamples(String resource) {
		this.resource = resource;
	}

	/** The resource's file name, beside this class. */
	public String resource() {
		return resource;
	}

	/**
	 * The sample's JSON.
	 *
	 * @return the JSON, as UTF-8 text
	 */
	public String json() {
		try (InputStream in = Objects.requireNonNull(HarnessWireSamples.class.getResourceAsStream(resource),
				resource)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
