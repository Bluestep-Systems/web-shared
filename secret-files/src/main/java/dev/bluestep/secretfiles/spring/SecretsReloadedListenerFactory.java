package dev.bluestep.secretfiles.spring;

import java.lang.reflect.Method;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationListenerMethodAdapter;
import org.springframework.context.event.EventListener;
import org.springframework.context.event.EventListenerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;

import dev.bluestep.secretfiles.ExceptionTypes;

/**
 * Isolates each {@code @EventListener} for {@link SecretsReloadedEvent}, so one consumer that throws
 * cannot starve the others.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Spring's default multicaster delivers an event to its listeners in a loop and lets the first
 * exception escape it: every listener after the one that threw never hears about the event. For most
 * events that is the right failure. For this one it is not — the listeners are independent holders of
 * independent secrets (an API key, a pool's database credentials, a client registration), and a bug in
 * one would leave every later one serving a credential the operator has just withdrawn, with no further
 * event coming to fix it. The alternatives were worse: an {@code ErrorHandler} on the context's
 * multicaster changes delivery for every event in the application, and a documented "never throw"
 * pattern is a rule the first careless listener breaks.</p>
 *
 * <h2>How</h2>
 *
 * <p>{@code EventListenerMethodProcessor} asks each {@link EventListenerFactory} bean, in order,
 * whether it handles an {@code @EventListener} method. This one claims the methods whose declared
 * event is exactly {@link SecretsReloadedEvent} — the method's single parameter, or its annotation's
 * {@code classes} when that names this type alone — and adapts them as Spring would, except that an
 * exception from the method is logged and swallowed. It is ordered after Spring's
 * {@code TransactionalEventListenerFactory}, so a {@code @TransactionalEventListener} keeps its own
 * semantics, and before the {@code DefaultEventListenerFactory}, which handles everything else
 * unchanged.</p>
 *
 * <p>The failure is logged by the listener's identity (its bean class and method) and the exception's
 * type and its causes' types — never its message or stack trace. A listener here handles secrets, and
 * the exceptions it meets can quote one: Spring Boot's binder, for one, puts the offending property
 * value in its message.</p>
 */
public final class SecretsReloadedListenerFactory implements EventListenerFactory, Ordered {

	/**
	 * After {@code TransactionalEventListenerFactory} (50), before {@code DefaultEventListenerFactory}
	 * (lowest precedence).
	 */
	public static final int ORDER = Ordered.LOWEST_PRECEDENCE - 1000;

	private static final Log LOG = LogFactory.getLog(SecretsReloadedListenerFactory.class);

	@Override
	public int getOrder() {
		return ORDER;
	}

	/**
	 * Claims a method whose declared event is exactly {@link SecretsReloadedEvent}.
	 *
	 * @param method an {@code @EventListener} method
	 * @return whether this factory adapts it
	 */
	@Override
	public boolean supportsMethod(final Method method) {
		final EventListener annotation = AnnotatedElementUtils.findMergedAnnotation(method, EventListener.class);
		final Class<?>[] declared = annotation == null ? new Class<?>[0] : annotation.classes();
		if (declared.length == 0) {
			return method.getParameterCount() == 1 && method.getParameterTypes()[0] == SecretsReloadedEvent.class;
		}
		return declared.length == 1 && declared[0] == SecretsReloadedEvent.class;
	}

	/**
	 * Adapts {@code method} as Spring's default factory would, with its exceptions contained.
	 *
	 * @param beanName the bean declaring the method
	 * @param type     the bean's target class
	 * @param method   the listener method
	 * @return the listener Spring registers
	 */
	@Override
	public ApplicationListener<?> createApplicationListener(final String beanName, final Class<?> type,
			final Method method) {
		return new IsolatedListener(beanName, type, method);
	}

	/** One listener method whose failure stays its own. */
	private static final class IsolatedListener extends ApplicationListenerMethodAdapter {

		/** The bean class and method, for the log line; neither is secret. */
		private final String identity;

		IsolatedListener(final String beanName, final Class<?> type, final Method method) {
			super(beanName, type, method);
			this.identity = ClassUtils.getUserClass(type).getName() + "#" + method.getName();
		}

		@Override
		public void processEvent(final ApplicationEvent event) {
			try {
				super.processEvent(event);
			} catch (RuntimeException e) {
				// Types only: the message, or any cause's, can quote a secret value.
				LOG.error("Listener " + identity + " failed handling a SecretsReloadedEvent ("
						+ ExceptionTypes.of(e) + "); the other listeners are still told, and this one keeps "
						+ "whatever it held before");
			}
		}
	}
}
