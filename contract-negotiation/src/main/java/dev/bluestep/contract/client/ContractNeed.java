package dev.bluestep.contract.client;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import dev.bluestep.contract.ContractTag;

/**
 * What one call can speak: the tags its client is built for, and the floor, the oldest it accepts. The gateway's token
 * check speaks {@code 6.6.0, 6.5.0, 6.4.0} with floor {@code 6.4.0}; web-global's harness client speaks {@code 1.0.0}
 * with floor {@code 1.0.0}.
 *
 * @param speaks every tag the call can speak, newest first, each at or above the floor, the floor among them
 * @param floor  the oldest tag the call accepts; also the tag a path-style fallback addresses
 */
public record ContractNeed(List<ContractTag> speaks, ContractTag floor) {

	/** Sorts the tags newest first, and holds them to the floor. */
	public ContractNeed {
		Objects.requireNonNull(floor, "floor");
		speaks = speaks.stream().distinct().sorted(Comparator.reverseOrder()).toList();
		if (!speaks.contains(floor)) {
			throw new IllegalArgumentException("A need speaks its floor " + floor + ": " + speaks);
		}
		if (!speaks.getLast().equals(floor)) {
			throw new IllegalArgumentException("A need speaks nothing below its floor " + floor + ": " + speaks);
		}
	}

	/**
	 * A need written in code.
	 *
	 * @param floor  the oldest tag accepted
	 * @param speaks every tag spoken, the floor included, in any order
	 * @return the need
	 * @throws IllegalArgumentException when a tag is malformed, or the floor is missing or not the oldest
	 */
	public static ContractNeed of(String floor, String... speaks) {
		return new ContractNeed(Arrays.stream(speaks).map(ContractTag::of).toList(), ContractTag.of(floor));
	}

	/** The newest tag spoken. */
	public ContractTag newest() {
		return speaks.getFirst();
	}
}
