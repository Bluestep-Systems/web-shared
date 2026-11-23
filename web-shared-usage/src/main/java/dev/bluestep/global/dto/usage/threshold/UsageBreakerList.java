package dev.bluestep.global.dto.usage.threshold;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Every circuit currently broken, fleet-wide: the list web's pods poll and hold in memory to refuse
 * a tripped tenant's usage.
 *
 * <p>The whole list every time, not a change feed: a pod replaces what it holds with each answer, so
 * a breaker absent from it has cleared. A tenant with no entry is not refused anything.</p>
 *
 * @param generatedAt when web-global read the breakers
 * @param breakers    every uncleared break, in no particular order
 */
public record UsageBreakerList(Instant generatedAt, List<UsageBreaker> breakers) {

	/**
	 * Refuses a missing component by name and copies the list. An absent {@code breakers} is refused
	 * rather than read as empty: a pod replaces its whole map with each answer, so an empty list clears
	 * every circuit, and only an explicit {@code []} may say that.
	 *
	 * @throws NullPointerException when {@code generatedAt} or {@code breakers} is missing, or a
	 *                              breaker is null
	 */
	public UsageBreakerList {
		Objects.requireNonNull(generatedAt, "generatedAt");
		Objects.requireNonNull(breakers, "breakers");
		breakers = List.copyOf(breakers);
	}
}
