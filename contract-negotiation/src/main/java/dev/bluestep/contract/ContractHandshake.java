package dev.bluestep.contract;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The handshake's answer: {@code {"protocol":1,"service":"web-global","tags":["6.6.0","6.5.0",…]}}.
 *
 * <p>The JSON keys are the component names, so a consumer's Jackson binds this record as it is. Tags travel as
 * strings, so a reader skips a tag it cannot parse rather than failing the whole handshake.</p>
 *
 * @param protocol the protocol version, {@value #PROTOCOL}; a later protocol stays readable by this one's clients
 * @param service  the answering service ({@code web-global}, {@code web-harness}), for logs
 * @param tags     every live tag, newest first; no per-tag surface list, since a missing surface shows up as the
 *                 {@code surface-not-served} refusal
 */
public record ContractHandshake(int protocol, String service, List<String> tags) {

	/** This library's protocol version. */
	public static final int PROTOCOL = 1;

	/** Copies the tags, so a handshake cannot change once built. */
	public ContractHandshake {
		Objects.requireNonNull(service, "service");
		tags = List.copyOf(tags);
	}

	/**
	 * The handshake a server answers: this protocol, {@code service}, and {@code liveTags} newest first.
	 *
	 * @param service  the answering service
	 * @param liveTags every live tag, in any order
	 * @return the handshake
	 */
	public static ContractHandshake of(String service, List<ContractTag> liveTags) {
		return new ContractHandshake(PROTOCOL, service, liveTags.stream().distinct()
			.sorted(Comparator.reverseOrder()).map(ContractTag::toString).toList());
	}
}
