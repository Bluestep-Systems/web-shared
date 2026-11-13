package dev.bluestep.http;

import java.net.ProtocolException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import org.jspecify.annotations.Nullable;

/**
 * The body as bytes, failing with {@link BodyTooLargeException} as soon as more than {@code maxBytes} arrive. It asks
 * for one chunk at a time and checks each chunk's size before copying any of it, so what it holds never passes the
 * cap; the breaching chunk (the JDK client's, already in memory) is dropped uncopied, the subscription cancelled and
 * everything held released.
 *
 * <p>Where the bytes go does not depend on how the peer splits them:</p>
 * <ul>
 * <li>With a declared length (at most the cap, or the handler would have refused it), one array of exactly that
 * length, allocated at the first chunk and returned as the body: the body is held once. A body that runs past its
 * declared length fails with a {@link ProtocolException}.</li>
 * <li>Without one (chunked, unknown length), fixed-size blocks of {@value #BLOCK} bytes (or the cap, if smaller),
 * filled in order, so many tiny chunks cost no more than one large one. On completion they are joined into one array
 * of the body's length, so for that moment the body is held twice: a {@code byte[]} of unknown length cannot be built
 * any other way.</li>
 * </ul>
 *
 * <p>The signals arrive one at a time (the {@link Flow} contract), so the fields need no locking. Once the body has
 * completed, either way, every later signal is ignored: a cancelled JDK exchange may still deliver what it had in
 * hand, or an error.</p>
 */
final class CappedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {

	/** The block size for a body of unknown length. */
	static final int BLOCK = 16 * 1024;

	private final long maxBytes;
	/** The declared length, at most the cap, or -1 when there is none. */
	private final long declared;
	private final CompletableFuture<byte[]> body = new CompletableFuture<>();
	/** The declared-length body, allocated at the first chunk. */
	private byte @Nullable [] whole;
	/** The unknown-length body's blocks, every one full but the last. */
	private final List<byte[]> blocks = new ArrayList<>();
	/** The bytes written into the last block. */
	private int filled;
	private long received;
	private Flow.@Nullable Subscription subscription;

	/**
	 * Reads up to {@code maxBytes}.
	 *
	 * @param maxBytes the cap, already checked by {@link CappedBodyHandler}
	 * @param declared the response's {@code Content-Length}, at most {@code maxBytes}, or -1 when it declares none
	 */
	CappedBodySubscriber(long maxBytes, long declared) {
		this.maxBytes = maxBytes;
		this.declared = declared;
	}

	/**
	 * Refuses the body before reading any of it: completed with {@code refusal} now, and cancels its subscription the
	 * moment it gets one, without requesting anything.
	 *
	 * @param refusal what the body fails with
	 * @return the subscriber
	 */
	static CappedBodySubscriber refusing(BodyTooLargeException refusal) {
		CappedBodySubscriber subscriber = new CappedBodySubscriber(refusal.maxBytes(), -1);
		subscriber.body.completeExceptionally(refusal);
		return subscriber;
	}

	@Override
	public void onSubscribe(Flow.Subscription subscription) {
		Objects.requireNonNull(subscription, "subscription");
		if (this.subscription != null || body.isDone()) {
			subscription.cancel();
			return;
		}
		this.subscription = subscription;
		subscription.request(1);
	}

	@Override
	public void onNext(List<ByteBuffer> items) {
		Flow.Subscription current = this.subscription;
		if (body.isDone() || current == null) {
			return;
		}
		long incoming = 0;
		for (ByteBuffer item : items) {
			incoming += item.remaining();
		}
		if (incoming > maxBytes - received) {
			fail(new BodyTooLargeException(maxBytes, received + incoming, false), current);
			return;
		}
		if (declared >= 0 && incoming > declared - received) {
			fail(new ProtocolException("The response body ran past its Content-Length of " + declared), current);
			return;
		}
		for (ByteBuffer item : items) {
			copy(item);
		}
		current.request(1);
	}

	/** Copies all of {@code item} after what is already held, which the caller has checked it fits. */
	private void copy(ByteBuffer item) {
		int length = item.remaining();
		if (declared >= 0) {
			byte[] target = whole;
			if (target == null) {
				// declared is at most the cap, which CappedBodyHandler keeps within an array's length.
				target = new byte[Math.toIntExact(declared)];
				whole = target;
			}
			item.get(target, Math.toIntExact(received), length);
			received += length;
			return;
		}
		int blockSize = (int) Math.max(1, Math.min(BLOCK, maxBytes));
		while (item.hasRemaining()) {
			if (blocks.isEmpty() || filled == blocks.getLast().length) {
				blocks.add(new byte[blockSize]);
				filled = 0;
			}
			byte[] block = blocks.getLast();
			int part = Math.min(item.remaining(), block.length - filled);
			item.get(block, filled, part);
			filled += part;
		}
		received += length;
	}

	private void fail(Exception failure, Flow.Subscription current) {
		release();
		// Completed before cancelling: a cancel the JDK answers with onError, on this thread, finds it done.
		body.completeExceptionally(failure);
		current.cancel();
	}

	private void release() {
		whole = null;
		blocks.clear();
	}

	@Override
	public void onError(Throwable throwable) {
		release();
		body.completeExceptionally(Objects.requireNonNull(throwable, "throwable"));
	}

	@Override
	public void onComplete() {
		if (body.isDone()) {
			return;
		}
		// received is at most maxBytes, which CappedBodyHandler keeps within an array's length.
		int length = Math.toIntExact(received);
		byte[] joined;
		byte[] target = whole;
		if (declared >= 0) {
			// Shorter than declared only if the JDK client let it end early; it refuses that on HTTP/1.1 itself.
			joined = target == null ? new byte[0] : target.length == length ? target : Arrays.copyOf(target, length);
		}
		else {
			joined = new byte[length];
			int at = 0;
			for (byte[] block : blocks) {
				int part = Math.min(block.length, length - at);
				System.arraycopy(block, 0, joined, at, part);
				at += part;
			}
		}
		release();
		body.complete(joined);
	}

	/** The blocks an unknown-length body holds, for the tests. */
	int blocksHeld() {
		return blocks.size();
	}

	@Override
	public CompletionStage<byte[]> getBody() {
		return body;
	}
}
