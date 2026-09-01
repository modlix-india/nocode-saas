package com.fincity.saas.commons.util;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.MDC;

import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;
import reactor.core.scheduler.Schedulers;

public class LogUtil {

	public static final String DEBUG_KEY = "x-debug";

	/**
	 * Marks a request as being served from the draft surface.
	 *
	 * The header name is the Reactor Context key, matching DEBUG_KEY. It is set
	 * ONLY by the gateway, from the resolved hostname, and the gateway strips any
	 * inbound value first, so it can never be forged by a caller.
	 *
	 * What it governs differs by object, and the difference is deliberate:
	 *
	 * - Definitions (Page, Style, ...) use it for READS only. Writes name their
	 *   target with an explicit ?draft=true, so a page running on the draft surface
	 *   doing an ordinary SendData PUT is not diverted into a draft it never asked
	 *   for.
	 * - App DATA uses it for reads AND writes. AppDataController has no draft
	 *   parameter on any route; a write on the draft surface goes to the draft
	 *   database because of this flag alone. That is what makes the draft surface a
	 *   usable sandbox rather than a read-only preview.
	 *
	 * It also crosses the message queue, carried as a field on EventQueObject. The
	 * event listener has no inbound request to read it from, and without it a
	 * draft-surface write that raised an event ended up writing to the LIVE
	 * database.
	 */
	public static final String DRAFT_KEY = "x-draft";

	public static final String METHOD_NAME = "x-method-name";

	/**
	 * Whether the current request is on the draft surface. Defaults to false, so
	 * every existing call path is unaffected.
	 */
	public static Mono<Boolean> isDraft() {
		return Mono.deferContextual(ctx -> Mono.just(ctx.getOrDefault(DRAFT_KEY, Boolean.FALSE)));
	}

	public static <T> void logIfDebugKey(Signal<T> signal, BiConsumer<String, String> logStatement) {

		if (!signal.isOnNext())
			return;

		Optional<String> toPutInMdc = signal.getContextView()
		        .getOrEmpty(DEBUG_KEY);

		String mName = signal.getContextView()
		        .getOrDefault(METHOD_NAME, "");

		toPutInMdc.ifPresent(tpim -> {
			try (MDC.MDCCloseable cMdc = MDC.putCloseable(DEBUG_KEY, tpim)) {

				logStatement.accept(mName, signal.isOnNext() ? signal.get()
				        .toString()
				        : signal.getThrowable()
				                .getMessage());
			}
		});
	}

	public static <T> Disposable logIfDebugKey(Logger logger, T object) {

		if (object == null)
			return null;

		return Mono.deferContextual(ctx -> {

			Optional<String> toPutInMdc = ctx.getOrEmpty(DEBUG_KEY);

			if (toPutInMdc.isEmpty())
				return Mono.just(false);

			try (MDC.MDCCloseable cMdc = MDC.putCloseable(DEBUG_KEY, toPutInMdc.get())) {
				logger.debug(object.toString());
			}

			return Mono.just(true);
		})
		        .subscribeOn(Schedulers.parallel())
		        .subscribe();
	}
	
	public static <V> Function<V, Mono<V>> logIfDebugKey(Logger logger) {
		return value -> Mono.deferContextual(ctx -> {

			Optional<String> toPutInMdc = ctx.getOrEmpty(DEBUG_KEY);

			if (toPutInMdc.isEmpty())
				return Mono.just(value);

			try (MDC.MDCCloseable cMdc = MDC.putCloseable(DEBUG_KEY, toPutInMdc.get())) {
				logger.debug("{}", value);
			}

			return Mono.just(value);
		}); 
	}

	public static <V> Function<V, Mono<V>> logIfDebugKey(Logger logger, String format, Object... objects) {

		return value -> Mono.deferContextual(ctx -> {

			Optional<String> toPutInMdc = ctx.getOrEmpty(DEBUG_KEY);

			if (toPutInMdc.isEmpty())
				return Mono.just(value);

			try (MDC.MDCCloseable cMdc = MDC.putCloseable(DEBUG_KEY, toPutInMdc.get())) {
				logger.debug(format, objects);
			}

			return Mono.just(value);
		});
	}

	private LogUtil() {
	}
}
