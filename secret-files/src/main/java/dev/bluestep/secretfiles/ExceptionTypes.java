package dev.bluestep.secretfiles;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.StringJoiner;

import org.jspecify.annotations.Nullable;

/**
 * Describes an exception for a log line by its type and its causes' types, and by nothing else.
 *
 * <p>This library logs a failure by nothing else, and so should a consumer's code that handles
 * secrets. Neither a message nor a stack trace may be logged there: an exception thrown while handling a
 * secret can quote it — Spring Boot's binder, for one, puts the offending property value in its
 * message, and a stack trace prints every cause's message too.</p>
 */
public final class ExceptionTypes {

	/** Causes followed before the chain is cut short. */
	private static final int MAX_DEPTH = 8;

	private ExceptionTypes() {
	}

	/**
	 * The exception's class name followed by each cause's, outermost first.
	 *
	 * @param failure the exception
	 * @return for example {@code java.lang.IllegalStateException <- org.springframework.boot.context.properties.bind.BindException}
	 */
	public static String of(final Throwable failure) {
		final StringJoiner chain = new StringJoiner(" <- ");
		final Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		@Nullable Throwable next = failure;
		while (next != null && seen.add(next)) {
			if (seen.size() > MAX_DEPTH) {
				chain.add("...");
				break;
			}
			chain.add(next.getClass().getName());
			next = next.getCause();
		}
		return chain.toString();
	}
}
