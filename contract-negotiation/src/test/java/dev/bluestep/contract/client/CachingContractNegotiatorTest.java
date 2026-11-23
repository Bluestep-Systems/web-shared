package dev.bluestep.contract.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import dev.bluestep.contract.ContractHandshake;
import dev.bluestep.contract.ContractRefusalToken;
import dev.bluestep.contract.ContractTag;

/** {@link CachingContractNegotiator} over a scripted {@link HandshakeSource} and a clock the test moves. */
class CachingContractNegotiatorTest {

	static final URI WEB_GLOBAL = URI.create("http://web-global.b6p-system.svc.cluster.local:8080");
	static final URI B6P_07 = URI.create("http://web.b6p-07.svc.cluster.local:8082");
	static final ContractNeed TOKEN_CHECK = ContractNeed.of("6.4.0", "6.6.0", "6.5.0", "6.4.0");

	final MovableClock clock = new MovableClock();
	final ScriptedSource source = new ScriptedSource();

	CachingContractNegotiator negotiator(NegotiatorSettings settings) {
		return new CachingContractNegotiator(source, settings, clock);
	}

	CachingContractNegotiator withFallback() {
		return negotiator(NegotiatorSettings.withPathStyleFallback());
	}

	static HandshakeResult answered(String... tags) {
		return new HandshakeResult.Answered(new ContractHandshake(1, "web-global", List.of(tags)));
	}

	static Negotiation.Tagged tagged(Negotiation negotiation) {
		assertThat(negotiation).isInstanceOf(Negotiation.Tagged.class);
		return (Negotiation.Tagged) negotiation;
	}

	@Test
	void picksTheHighestCommonTag() throws Exception {
		source.always(WEB_GLOBAL, answered("6.7.0", "6.5.0", "6.4.0", "6.3.0"));
		assertThat(tagged(withFallback().negotiate(WEB_GLOBAL, TOKEN_CHECK)).tag()).isEqualTo(ContractTag.of("6.5.0"));
	}

	@Test
	void readsTheHandshakeInAnyOrderAndSkipsWhatItCannotParse() throws Exception {
		source.always(WEB_GLOBAL, answered("6.4.0", "garbage", "6.6.0-rc1", "6.5.0"));
		assertThat(tagged(withFallback().negotiate(WEB_GLOBAL, TOKEN_CHECK)).tag()).isEqualTo(ContractTag.of("6.5.0"));
	}

	@Test
	void nothingAtOrAboveTheFloorIsNoCommonTag() throws Exception {
		source.always(WEB_GLOBAL, answered("6.3.0", "1.2.2"));
		Negotiation negotiation = withFallback().negotiate(WEB_GLOBAL, TOKEN_CHECK);
		assertThat(negotiation).isEqualTo(new Negotiation.NoCommonTag(
			List.of(ContractTag.of("6.3.0"), ContractTag.of("1.2.2"))));
	}

	@Test
	void anAnsweredHandshakeIsTrustedFiveMinutesThenAskedAgain() throws Exception {
		source.always(WEB_GLOBAL, answered("6.5.0"));
		CachingContractNegotiator negotiator = withFallback();
		negotiator.negotiate(WEB_GLOBAL, TOKEN_CHECK);
		clock.advance(Duration.ofMinutes(5).minusMillis(1));
		negotiator.negotiate(WEB_GLOBAL, TOKEN_CHECK);
		assertThat(source.fetches(WEB_GLOBAL)).isEqualTo(1);
		clock.advance(Duration.ofMillis(1));
		source.always(WEB_GLOBAL, answered("6.6.0", "6.5.0"));
		assertThat(tagged(negotiator.negotiate(WEB_GLOBAL, TOKEN_CHECK)).tag()).isEqualTo(ContractTag.of("6.6.0"));
		assertThat(source.fetches(WEB_GLOBAL)).isEqualTo(2);
	}

	@Test
	void aFailedHandshakeIsUnavailableAndAskedAgainAfterThirtySeconds() throws Exception {
		source.always(WEB_GLOBAL, new HandshakeResult.Failed("connect timed out"));
		CachingContractNegotiator negotiator = withFallback();
		assertThat(negotiator.negotiate(WEB_GLOBAL, TOKEN_CHECK))
			.isEqualTo(new Negotiation.Unavailable(Negotiation.Unavailable.Reason.HANDSHAKE_FAILED));
		clock.advance(Duration.ofSeconds(29));
		negotiator.negotiate(WEB_GLOBAL, TOKEN_CHECK);
		assertThat(source.fetches(WEB_GLOBAL)).isEqualTo(1);
		clock.advance(Duration.ofSeconds(1));
		source.always(WEB_GLOBAL, answered("6.4.0"));
		assertThat(tagged(negotiator.negotiate(WEB_GLOBAL, TOKEN_CHECK)).tag()).isEqualTo(ContractTag.of("6.4.0"));
		assertThat(source.fetches(WEB_GLOBAL)).isEqualTo(2);
	}

	@Test
	void aSourceThatThrowsIsAFailedHandshake() throws Exception {
		source.throwing(WEB_GLOBAL, new IllegalStateException("bug"));
		assertThat(withFallback().negotiate(WEB_GLOBAL, TOKEN_CHECK))
			.isEqualTo(new Negotiation.Unavailable(Negotiation.Unavailable.Reason.HANDSHAKE_FAILED));
	}

	@Test
	void aHandshakeBelowProtocolOneIsAFailedHandshake() throws Exception {
		source.always(WEB_GLOBAL, new HandshakeResult.Answered(new ContractHandshake(0, "web-global", List.of("6.5.0"))));
		assertThat(withFallback().negotiate(WEB_GLOBAL, TOKEN_CHECK))
			.isEqualTo(new Negotiation.Unavailable(Negotiation.Unavailable.Reason.HANDSHAKE_FAILED));
	}

	@Test
	void aLaterProtocolIsStillRead() throws Exception {
		source.always(WEB_GLOBAL, new HandshakeResult.Answered(new ContractHandshake(2, "web-global", List.of("6.5.0"))));
		assertThat(tagged(withFallback().negotiate(WEB_GLOBAL, TOKEN_CHECK)).tag()).isEqualTo(ContractTag.of("6.5.0"));
	}

	@Test
	void aPeerThatPredatesNegotiationGetsThePathStyleAtTheFloorAndIsAskedAgainAfterThirtySeconds() throws Exception {
		source.always(WEB_GLOBAL, new HandshakeResult.PredatesNegotiation());
		CachingContractNegotiator negotiator = withFallback();
		assertThat(negotiator.negotiate(WEB_GLOBAL, TOKEN_CHECK))
			.isEqualTo(new Negotiation.PathStyle(ContractTag.of("6.4.0")));
		assertThat(negotiator.negotiate(WEB_GLOBAL, ContractNeed.of("6.5.0", "6.5.0")))
			.as("each call's own floor").isEqualTo(new Negotiation.PathStyle(ContractTag.of("6.5.0")));
		clock.advance(Duration.ofSeconds(29));
		negotiator.negotiate(WEB_GLOBAL, TOKEN_CHECK);
		assertThat(source.fetches(WEB_GLOBAL)).isEqualTo(1);
		clock.advance(Duration.ofSeconds(1));
		source.always(WEB_GLOBAL, answered("6.6.0"));
		assertThat(tagged(negotiator.negotiate(WEB_GLOBAL, TOKEN_CHECK)).tag()).isEqualTo(ContractTag.of("6.6.0"));
	}

	@Test
	void withoutTheFallbackAPeerThatPredatesNegotiationIsUnavailable() throws Exception {
		source.always(B6P_07, new HandshakeResult.PredatesNegotiation());
		assertThat(negotiator(NegotiatorSettings.withoutPathStyleFallback())
			.negotiate(B6P_07, ContractNeed.of("1.0.0", "1.0.0")))
			.isEqualTo(new Negotiation.Unavailable(Negotiation.Unavailable.Reason.PREDATES_NEGOTIATION));
	}

	@Test
	void aMismatchRefusalExcludesTheTagReHandshakesOnceAndRetriesOnce() throws Exception {
		source.always(WEB_GLOBAL, answered("6.6.0", "6.5.0"));
		CachingContractNegotiator negotiator = withFallback();
		List<Negotiation> attempts = new ArrayList<>();
		String answer = negotiator.exchange(WEB_GLOBAL, TOKEN_CHECK, negotiation -> {
			attempts.add(negotiation);
			return tagged(negotiation).tag().equals(ContractTag.of("6.6.0"))
				? Attempt.refused(ContractRefusalToken.UNKNOWN_VERSION, "refused at 6.6.0")
				: Attempt.done("answered at " + tagged(negotiation).tag());
		});
		assertThat(answer).isEqualTo("answered at 6.5.0");
		assertThat(attempts).hasSize(2);
		assertThat(source.fetches(WEB_GLOBAL)).as("re-handshaken once").isEqualTo(2);
	}

	@Test
	void surfaceNotServedIsAMismatchToo() throws Exception {
		source.always(WEB_GLOBAL, answered("6.6.0", "6.5.0"));
		String answer = withFallback().exchange(WEB_GLOBAL, TOKEN_CHECK, negotiation ->
			tagged(negotiation).tag().equals(ContractTag.of("6.6.0"))
				? Attempt.refused(ContractRefusalToken.SURFACE_NOT_SERVED, "refused")
				: Attempt.done("answered"));
		assertThat(answer).isEqualTo("answered");
	}

	@Test
	void aSecondRefusalGoesBackToTheCaller() throws Exception {
		source.always(WEB_GLOBAL, answered("6.6.0", "6.5.0"));
		AtomicInteger calls = new AtomicInteger();
		String answer = withFallback().exchange(WEB_GLOBAL, TOKEN_CHECK, negotiation ->
			Attempt.refused(ContractRefusalToken.UNKNOWN_VERSION, "refused #" + calls.incrementAndGet()));
		assertThat(answer).isEqualTo("refused #2");
		assertThat(calls).hasValue(2);
	}

	@Test
	void bothRefusedTagsAreExcludedSoTheNextCallPicksTheThird() throws Exception {
		source.always(WEB_GLOBAL, answered("3.0.0", "2.0.0", "1.0.0"));
		CachingContractNegotiator negotiator = withFallback();
		ContractNeed need = ContractNeed.of("1.0.0", "3.0.0", "2.0.0", "1.0.0");
		List<ContractTag> tried = new ArrayList<>();
		String answer = negotiator.exchange(WEB_GLOBAL, need, negotiation -> {
			ContractTag tag = tagged(negotiation).tag();
			tried.add(tag);
			return tag.equals(ContractTag.of("1.0.0")) ? Attempt.done("answered at 1.0.0")
				: Attempt.refused(ContractRefusalToken.UNKNOWN_VERSION, "refused at " + tag);
		});
		assertThat(answer).as("no third attempt").isEqualTo("refused at 2.0.0");
		assertThat(tried).containsExactly(ContractTag.of("3.0.0"), ContractTag.of("2.0.0"));
		assertThat(tagged(negotiator.negotiate(WEB_GLOBAL, need)).tag()).isEqualTo(ContractTag.of("1.0.0"));
		clock.advance(Duration.ofMinutes(5));
		assertThat(tagged(negotiator.negotiate(WEB_GLOBAL, need)).tag()).as("both exclusions expire")
			.isEqualTo(ContractTag.of("3.0.0"));
	}

	@Test
	void anyOtherRefusalIsNotRetried() throws Exception {
		source.always(WEB_GLOBAL, answered("6.6.0", "6.5.0"));
		for (ContractRefusalToken token : List.of(ContractRefusalToken.OPERATION_ARRIVED_LATER,
				ContractRefusalToken.CONTRACT_HEADER_INVALID)) {
			AtomicInteger calls = new AtomicInteger();
			withFallback().exchange(WEB_GLOBAL, TOKEN_CHECK, negotiation -> {
				calls.incrementAndGet();
				return Attempt.refused(token, "refused");
			});
			assertThat(calls).as(token.token()).hasValue(1);
		}
	}

	@Test
	void aRefusedPathStyleCallIsNotRetried() throws Exception {
		source.always(WEB_GLOBAL, new HandshakeResult.PredatesNegotiation());
		AtomicInteger calls = new AtomicInteger();
		withFallback().exchange(WEB_GLOBAL, TOKEN_CHECK, negotiation -> {
			calls.incrementAndGet();
			return Attempt.refused(ContractRefusalToken.UNKNOWN_VERSION, "refused");
		});
		assertThat(calls).hasValue(1);
		assertThat(source.fetches(WEB_GLOBAL)).isEqualTo(1);
	}

	@Test
	void theExchangeIsHandedAFailureToAnswerWithoutCalling() throws Exception {
		source.always(WEB_GLOBAL, new HandshakeResult.Failed("refused"));
		String answer = withFallback().exchange(WEB_GLOBAL, TOKEN_CHECK, negotiation ->
			Attempt.done(negotiation instanceof Negotiation.Unavailable ? "unreachable" : "called"));
		assertThat(answer).isEqualTo("unreachable");
	}

	@Test
	void anExcludedTagStaysExcludedForFiveMinutesAcrossReHandshakes() throws Exception {
		source.always(WEB_GLOBAL, answered("6.6.0", "6.5.0"));
		CachingContractNegotiator negotiator = withFallback();
		negotiator.refused(WEB_GLOBAL, tagged(negotiator.negotiate(WEB_GLOBAL, TOKEN_CHECK)));
		assertThat(tagged(negotiator.negotiate(WEB_GLOBAL, TOKEN_CHECK)).tag()).isEqualTo(ContractTag.of("6.5.0"));
		clock.advance(Duration.ofMinutes(5).minusMillis(1));
		assertThat(tagged(negotiator.negotiate(WEB_GLOBAL, TOKEN_CHECK)).tag()).isEqualTo(ContractTag.of("6.5.0"));
		clock.advance(Duration.ofMillis(1));
		assertThat(tagged(negotiator.negotiate(WEB_GLOBAL, TOKEN_CHECK)).tag()).isEqualTo(ContractTag.of("6.6.0"));
	}

	@Test
	void excludingTheOnlyCommonTagIsNoCommonTag() throws Exception {
		source.always(B6P_07, answered("1.0.0"));
		CachingContractNegotiator negotiator = negotiator(NegotiatorSettings.withoutPathStyleFallback());
		ContractNeed harness = ContractNeed.of("1.0.0", "1.0.0");
		negotiator.refused(B6P_07, tagged(negotiator.negotiate(B6P_07, harness)));
		assertThat(negotiator.negotiate(B6P_07, harness)).isInstanceOf(Negotiation.NoCommonTag.class);
	}

	@Test
	void aRefusalOfAnOlderHandshakesPickDropsNothing() throws Exception {
		source.always(WEB_GLOBAL, answered("6.6.0", "6.5.0"));
		CachingContractNegotiator negotiator = withFallback();
		Negotiation.Tagged stale = tagged(negotiator.negotiate(WEB_GLOBAL, TOKEN_CHECK));
		negotiator.refused(WEB_GLOBAL, stale);
		Negotiation.Tagged fresh = tagged(negotiator.negotiate(WEB_GLOBAL, TOKEN_CHECK));
		assertThat(source.fetches(WEB_GLOBAL)).isEqualTo(2);
		// A second caller's refusal of the same stale pick: the tag is already excluded, and the fresh handshake stays.
		negotiator.refused(WEB_GLOBAL, stale);
		assertThat(negotiator.negotiate(WEB_GLOBAL, TOKEN_CHECK)).isEqualTo(fresh);
		assertThat(source.fetches(WEB_GLOBAL)).isEqualTo(2);
	}

	@Test
	void peersAreIndependent() throws Exception {
		source.always(WEB_GLOBAL, answered("6.6.0", "6.5.0"));
		source.always(B6P_07, answered("6.6.0", "6.5.0"));
		CachingContractNegotiator negotiator = withFallback();
		negotiator.refused(WEB_GLOBAL, tagged(negotiator.negotiate(WEB_GLOBAL, TOKEN_CHECK)));
		assertThat(tagged(negotiator.negotiate(B6P_07, TOKEN_CHECK)).tag()).isEqualTo(ContractTag.of("6.6.0"));
		assertThat(tagged(negotiator.negotiate(WEB_GLOBAL, TOKEN_CHECK)).tag()).isEqualTo(ContractTag.of("6.5.0"));
	}

	@Test
	void concurrentCallersShareOneFetchPerPeerAndASlowPeerHoldsUpOnlyItsOwn() throws Exception {
		CountDownLatch release = new CountDownLatch(1);
		source.blocking(WEB_GLOBAL, release, answered("6.5.0"));
		source.always(B6P_07, answered("6.5.0"));
		CachingContractNegotiator negotiator = withFallback();
		int callers = 8;
		CountDownLatch done = new CountDownLatch(callers);
		List<Negotiation> results = Collections.synchronizedList(new ArrayList<>());
		for (int i = 0; i < callers; i++) {
			Thread.ofVirtual().start(() -> {
				try {
					results.add(negotiator.negotiate(WEB_GLOBAL, TOKEN_CHECK));
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				finally {
					done.countDown();
				}
			});
		}
		assertThat(source.awaitFetching(WEB_GLOBAL)).isTrue();
		assertThat(tagged(negotiator.negotiate(B6P_07, TOKEN_CHECK)).tag()).as("another peer is not held up")
			.isEqualTo(ContractTag.of("6.5.0"));
		assertThat(done.getCount()).isEqualTo(callers);
		release.countDown();
		assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
		assertThat(results).hasSize(callers).allSatisfy(result -> assertThat(tagged(result).tag())
			.isEqualTo(ContractTag.of("6.5.0")));
		assertThat(source.fetches(WEB_GLOBAL)).isEqualTo(1);
	}

	@Test
	void aCallerWaitingForAPeersHandshakeCanBeInterrupted() throws Exception {
		CountDownLatch release = new CountDownLatch(1);
		source.blocking(WEB_GLOBAL, release, answered("6.5.0"));
		CachingContractNegotiator negotiator = withFallback();
		Thread fetcher = Thread.ofVirtual().start(() -> {
			try {
				negotiator.negotiate(WEB_GLOBAL, TOKEN_CHECK);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		assertThat(source.awaitFetching(WEB_GLOBAL)).isTrue();
		AtomicReference<Throwable> thrown = new AtomicReference<>();
		Thread waiter = Thread.ofVirtual().start(() -> {
			try {
				negotiator.negotiate(WEB_GLOBAL, TOKEN_CHECK);
			}
			catch (Throwable t) {
				thrown.set(t);
			}
		});
		Thread.sleep(100);
		waiter.interrupt();
		waiter.join(5_000);
		assertThat(thrown.get()).isInstanceOf(InterruptedException.class);
		release.countDown();
		fetcher.join(5_000);
	}

	@Test
	void needsAreSortedAndHeldToTheirFloor() {
		assertThat(ContractNeed.of("6.4.0", "6.4.0", "6.6.0", "6.5.0").speaks()).extracting(ContractTag::toString)
			.containsExactly("6.6.0", "6.5.0", "6.4.0");
		assertThat(ContractNeed.of("6.4.0", "6.6.0", "6.4.0").newest()).isEqualTo(ContractTag.of("6.6.0"));
		assertThatThrownBy(() -> ContractNeed.of("6.4.0", "6.5.0")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ContractNeed.of("6.4.0", "6.5.0", "6.4.0", "6.3.0"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ContractNeed.of("6.4", "6.4")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void settingsDefaultToTheRuledDurations() {
		NegotiatorSettings settings = NegotiatorSettings.withPathStyleFallback();
		assertThat(settings.answeredFor()).isEqualTo(Duration.ofMinutes(5));
		assertThat(settings.failedFor()).isEqualTo(Duration.ofSeconds(30));
		assertThat(settings.predatesFor()).isEqualTo(Duration.ofSeconds(30));
		assertThat(settings.excludedFor()).isEqualTo(Duration.ofMinutes(5));
		assertThat(settings.pathStyleFallback()).isTrue();
		assertThat(NegotiatorSettings.withoutPathStyleFallback().pathStyleFallback()).isFalse();
		assertThatThrownBy(() -> new NegotiatorSettings(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(1),
			Duration.ofSeconds(1), true)).isInstanceOf(IllegalArgumentException.class);
	}

	/** A clock that moves only when told. */
	static final class MovableClock implements InstantSource {
		private volatile Instant now = Instant.parse("2026-11-12T12:00:00Z");

		@Override
		public Instant instant() {
			return now;
		}

		void advance(Duration by) {
			now = now.plus(by);
		}
	}

	/** Answers per peer as scripted, counting fetches. */
	static final class ScriptedSource implements HandshakeSource {
		private final Map<URI, Script> scripts = new ConcurrentHashMap<>();
		private final Map<URI, AtomicInteger> fetches = new ConcurrentHashMap<>();
		private final Map<URI, CountDownLatch> fetching = new ConcurrentHashMap<>();

		private interface Script {
			HandshakeResult answer() throws InterruptedException;
		}

		void always(URI peer, HandshakeResult result) {
			scripts.put(peer, () -> result);
		}

		void throwing(URI peer, RuntimeException failure) {
			scripts.put(peer, () -> {
				throw failure;
			});
		}

		void blocking(URI peer, CountDownLatch release, HandshakeResult result) {
			scripts.put(peer, () -> {
				release.await();
				return result;
			});
		}

		boolean awaitFetching(URI peer) throws InterruptedException {
			return latch(peer).await(10, TimeUnit.SECONDS);
		}

		int fetches(URI peer) {
			return fetches.computeIfAbsent(peer, key -> new AtomicInteger()).get();
		}

		private CountDownLatch latch(URI peer) {
			return fetching.computeIfAbsent(peer, key -> new CountDownLatch(1));
		}

		@Override
		public HandshakeResult fetch(URI peer) throws InterruptedException {
			fetches.computeIfAbsent(peer, key -> new AtomicInteger()).incrementAndGet();
			latch(peer).countDown();
			return scripts.get(peer).answer();
		}
	}
}
