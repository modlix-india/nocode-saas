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

	public static final String METHOD_NAME = "x-method-name";

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
