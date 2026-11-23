package dev.bluestep.contract.client;

import java.lang.System.Logger.Level;
import java.net.URI;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import org.jspecify.annotations.Nullable;

import dev.bluestep.contract.ContractHandshake;
import dev.bluestep.contract.ContractTag;

/**
 * A {@link ContractNegotiator} that keeps what it learns about each peer in memory, on this pod only.
 *
 * <h2>Per peer</h2>
 * <ul>
 *   <li>An answered handshake is trusted for {@link NegotiatorSettings#answeredFor()} (5 min), a failed one for
 *       {@link NegotiatorSettings#failedFor()} (30 s), a peer that predates negotiation for
 *       {@link NegotiatorSettings#predatesFor()} (30 s); then it is asked again.</li>
 *   <li><b>Single-flight</b>: one lock per peer, held while its handshake is fetched, so concurrent callers share one
 *       fetch and a slow peer holds up only its own callers. The {@link HandshakeSource} bounds each fetch.</li>
 *   <li>A tag refused as a mismatch is excluded for {@link NegotiatorSettings#excludedFor()} (5 min), across
 *       re-handshakes, and the handshake it was picked from is dropped. A refusal of an older handshake's pick drops
 *       nothing: another caller has re-handshaken already.</li>
 *   <li>Entering the path-style fallback logs one WARNING per transition (not per call, not per re-ask); a failed
 *       handshake logs one WARNING per fetch, so at most one per peer per {@code failedFor}.</li>
 * </ul>
 *
 * <p>Peers are never evicted: there is one per service a pod calls (web-global), or one per namespace (web's harness,
 * a few dozen). Nothing here must agree across replicas: each pod negotiates for itself, and two pods disagreeing for a
 * few minutes about a peer mid-rollout costs at most one refused call each.</p>
 */
public final class CachingContractNegotiator implements ContractNegotiator {

	private static final System.Logger LOG = System.getLogger(CachingContractNegotiator.class.getName());

	private final HandshakeSource source;
	private final NegotiatorSettings settings;
	private final InstantSource clock;
	private final ConcurrentMap<URI, Peer> peers = new ConcurrentHashMap<>();

	/**
	 * A negotiator over {@code source}.
	 *
	 * @param source   the transport the handshakes are asked through
	 * @param settings how long each answer is trusted, and whether to fall back to the path style
	 * @param clock    the time; {@code InstantSource.system()} outside tests
	 */
	public CachingContractNegotiator(HandshakeSource source, NegotiatorSettings settings, InstantSource clock) {
		this.source = Objects.requireNonNull(source, "source");
		this.settings = Objects.requireNonNull(settings, "settings");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public Negotiation negotiate(URI peer, ContractNeed need) throws InterruptedException {
		Objects.requireNonNull(need, "need");
		Peer state = peer(peer);
		state.lock.lockInterruptibly();
		try {
			Known known = state.known;
			if (known == null || !clock.instant().isBefore(known.until())) {
				known = fetch(peer, state);
			}
			return decide(known, state, need);
		}
		finally {
			state.lock.unlock();
		}
	}

	@Override
	public void refused(URI peer, Negotiation.Tagged refused) {
		Objects.requireNonNull(refused, "refused");
		Peer state = peer(peer);
		state.lock.lock();
		try {
			state.excluded.put(refused.tag(), clock.instant().plus(settings.excludedFor()));
			if (state.known instanceof Answered answered && answered.generation() == refused.generation()) {
				state.known = null;
			}
		}
		finally {
			state.lock.unlock();
		}
		LOG.log(Level.INFO, "Contract peer {0} refused tag {1}; excluded for {2}", peer, refused.tag(),
			settings.excludedFor());
	}

	@Override
	public <T> T exchange(URI peer, ContractNeed need, ContractExchange<T> exchange) throws InterruptedException {
		Objects.requireNonNull(exchange, "exchange");
		Negotiation first = negotiate(peer, need);
		Attempt<T> attempt = exchange.attempt(first);
		if (first instanceof Negotiation.Tagged tagged && attempt instanceof Attempt.Refused<T> refusal
				&& refusal.token().isMismatch()) {
			refused(peer, tagged);
			Negotiation second = negotiate(peer, need);
			Attempt<T> retried = exchange.attempt(second);
			// No third attempt, but the second refusal is recorded too, so the next call does not pick that tag again.
			if (second instanceof Negotiation.Tagged retriedTag && retried instanceof Attempt.Refused<T> again
					&& again.token().isMismatch()) {
				refused(peer, retriedTag);
			}
			return retried.value();
		}
		return attempt.value();
	}

	private Peer peer(URI peer) {
		Objects.requireNonNull(peer, "peer");
		return peers.computeIfAbsent(peer, key -> new Peer());
	}

	/** Asks the peer, under its lock, and records what came of it. */
	private Known fetch(URI peer, Peer state) throws InterruptedException {
		HandshakeResult result;
		try {
			result = source.fetch(peer);
		}
		catch (RuntimeException e) {
			result = new HandshakeResult.Failed("the handshake source threw " + e.getClass().getName());
		}
		Instant now = clock.instant();
		state.generation++;
		Known known = switch (result) {
			case HandshakeResult.Answered answered -> answered(peer, answered.handshake(), now, state.generation);
			case HandshakeResult.PredatesNegotiation predates -> new Predates(now.plus(settings.predatesFor()));
			case HandshakeResult.Failed failed -> {
				LOG.log(Level.WARNING, "Contract handshake with {0} failed ({1}); asking again in {2}", peer,
					failed.reason(), settings.failedFor());
				yield new Failed(now.plus(settings.failedFor()));
			}
		};
		if (known instanceof Predates && !state.warnedPredates) {
			LOG.log(Level.WARNING, "Contract peer {0} predates negotiation; {1}, asking again every {2}", peer,
				settings.pathStyleFallback() ? "using the path style at each call's floor tag" : "it cannot be used",
				settings.predatesFor());
			state.warnedPredates = true;
		}
		else if (!(known instanceof Predates)) {
			state.warnedPredates = false;
		}
		state.known = known;
		return known;
	}

	private Known answered(URI peer, ContractHandshake handshake, Instant now, long generation) {
		if (handshake.protocol() < ContractHandshake.PROTOCOL) {
			LOG.log(Level.WARNING, "Contract handshake with {0} named protocol {1}; asking again in {2}", peer,
				handshake.protocol(), settings.failedFor());
			return new Failed(now.plus(settings.failedFor()));
		}
		// A tag this version cannot parse is skipped, not fatal: the rest of the handshake is still good.
		List<ContractTag> tags = handshake.tags().stream().map(ContractTag::parse).flatMap(Optional::stream).distinct()
			.sorted(Comparator.reverseOrder()).toList();
		return new Answered(tags, now.plus(settings.answeredFor()), generation);
	}

	private Negotiation decide(Known known, Peer state, ContractNeed need) {
		return switch (known) {
			case Answered answered -> pick(answered, state, need);
			case Predates predates -> settings.pathStyleFallback() ? new Negotiation.PathStyle(need.floor())
				: new Negotiation.Unavailable(Negotiation.Unavailable.Reason.PREDATES_NEGOTIATION);
			case Failed failed -> new Negotiation.Unavailable(Negotiation.Unavailable.Reason.HANDSHAKE_FAILED);
		};
	}

	private Negotiation pick(Answered answered, Peer state, ContractNeed need) {
		Instant now = clock.instant();
		state.excluded.values().removeIf(until -> !now.isBefore(until));
		for (ContractTag tag : need.speaks()) {
			if (answered.tags().contains(tag) && !state.excluded.containsKey(tag)) {
				return new Negotiation.Tagged(tag, answered.generation());
			}
		}
		return new Negotiation.NoCommonTag(answered.tags());
	}

	/** One peer's state; every field guarded by {@link #lock}. */
	private static final class Peer {
		final ReentrantLock lock = new ReentrantLock();
		@Nullable Known known;
		final Map<ContractTag, Instant> excluded = new HashMap<>();
		long generation;
		boolean warnedPredates;
	}

	/** What is known about a peer, and until when. */
	private sealed interface Known {
		Instant until();
	}

	private record Answered(List<ContractTag> tags, Instant until, long generation) implements Known {
	}

	private record Predates(Instant until) implements Known {
	}

	private record Failed(Instant until) implements Known {
	}
}
