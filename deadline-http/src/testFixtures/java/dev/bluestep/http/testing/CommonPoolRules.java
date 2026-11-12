package dev.bluestep.http.testing;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.net.http.HttpClient;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The ArchUnit rules that keep a consumer's main code off {@code ForkJoinPool.commonPool()} (RG1flake2, web-global's
 * gateway, 2026-11-11): with one worker on a two-CPU host, any task blocking the pool stalls everything else queued on
 * it, and the JDK delivers {@code HttpClient.sendAsync}'s answer through it, which turned forge API reads into 502s.
 * So main code
 * <ul>
 *   <li>never uses {@code HttpClient.sendAsync} ({@link #NO_SEND_ASYNC}; {@code dev.bluestep.http.DeadlineHttp} is
 *       the bounded send),</li>
 *   <li>never an async {@code CompletableFuture}/{@code CompletionStage} method without an {@code Executor}
 *       ({@link #NO_ASYNC_WITHOUT_EXECUTOR}; they run on the common pool), and</li>
 *   <li>never asks for the common pool itself ({@link #NO_COMMON_POOL}).</li>
 * </ul>
 *
 * <p>Each rule matches any access to the method, so a call and a method reference ({@code client::sendAsync}) alike,
 * and matches the method's owner by assignability, so a subclass's inherited async method counts too. A consumer checks
 * them over its own main classes, imported without tests:</p>
 * <pre>{@code
 * JavaClasses main = new ClassFileImporter()
 *     .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
 *     .importPackages("dev.bluestep.gateway");
 * CommonPoolRules.checkAll(main);
 * }</pre>
 *
 * <p>An import that found nothing fails the check (ArchUnit's {@code failOnEmptyShould}, on by default), so a broken
 * scan cannot pass vacuously. The library's own tests run every rule over fixtures that each commit one violation.</p>
 *
 * <p>A holder of constant rules, not a service: nothing here has state.</p>
 */
public final class CommonPoolRules {

	/** {@code HttpClient.sendAsync}, called or referenced. */
	public static final DescribedPredicate<JavaAccess<?>> SEND_ASYNC = new DescribedPredicate<>(
		"HttpClient.sendAsync, called or referenced") {
		@Override
		public boolean test(JavaAccess<?> access) {
			AccessTarget target = access.getTarget();
			return target.getName().equals("sendAsync") && target.getOwner().isAssignableTo(HttpClient.class);
		}
	};

	/** An async {@code CompletableFuture}/{@code CompletionStage} method whose last parameter is not an executor. */
	public static final DescribedPredicate<JavaAccess<?>> ASYNC_WITHOUT_EXECUTOR = new DescribedPredicate<>(
		"an async CompletableFuture/CompletionStage method without an Executor, called or referenced") {
		@Override
		public boolean test(JavaAccess<?> access) {
			if (!(access.getTarget() instanceof AccessTarget.CodeUnitAccessTarget target)
				|| !isFuture(target.getOwner()) || !target.getName().endsWith("Async")) {
				return false;
			}
			List<JavaClass> parameters = target.getRawParameterTypes();
			return parameters.isEmpty() || !parameters.getLast().isAssignableTo(Executor.class);
		}
	};

	/** {@code ForkJoinPool.commonPool} or {@code CompletableFuture.defaultExecutor}, called or referenced. */
	public static final DescribedPredicate<JavaAccess<?>> COMMON_POOL = new DescribedPredicate<>(
		"ForkJoinPool.commonPool or CompletableFuture.defaultExecutor, called or referenced") {
		@Override
		public boolean test(JavaAccess<?> access) {
			AccessTarget target = access.getTarget();
			return target.getName().equals("commonPool") && target.getOwner().isAssignableTo(ForkJoinPool.class)
				|| target.getName().equals("defaultExecutor") && isFuture(target.getOwner());
		}
	};

	/** No class uses {@code HttpClient.sendAsync}. */
	public static final ArchRule NO_SEND_ASYNC = forbid(SEND_ASYNC,
		"the JDK completes sendAsync's future on the common pool; use DeadlineHttp");

	/** No class runs async {@code CompletableFuture} work without naming an executor. */
	public static final ArchRule NO_ASYNC_WITHOUT_EXECUTOR = forbid(ASYNC_WITHOUT_EXECUTOR,
		"an executor-less async CompletableFuture method runs on the common pool");

	/** No class asks for the common pool. */
	public static final ArchRule NO_COMMON_POOL = forbid(COMMON_POOL,
		"work runs on executors of its own, never on the common pool");

	/** All three rules. */
	public static final List<ArchRule> ALL = List.of(NO_SEND_ASYNC, NO_ASYNC_WITHOUT_EXECUTOR, NO_COMMON_POOL);

	private CommonPoolRules() {
	}

	/**
	 * Checks every rule over {@code classes}, failing with the first rule that is broken.
	 *
	 * @param classes the consumer's main classes
	 * @throws AssertionError naming each violation of the first rule broken
	 */
	public static void checkAll(JavaClasses classes) {
		for (ArchRule rule : ALL) {
			rule.check(classes);
		}
	}

	private static boolean isFuture(JavaClass owner) {
		return owner.isAssignableTo(CompletableFuture.class) || owner.isAssignableTo(CompletionStage.class);
	}

	private static ArchRule forbid(DescribedPredicate<JavaAccess<?>> access, String because) {
		return noClasses().should().accessTargetWhere(access).because(because);
	}
}
