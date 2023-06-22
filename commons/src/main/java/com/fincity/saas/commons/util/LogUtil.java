package com.fincity.saas.commons.util;

import java.util.Optional;
import java.util.function.BiConsumer;

import org.slf4j.MDC;

import reactor.core.publisher.Signal;

public class LogUtil {

	public static final String DEBUG_KEY = "x-debug";

	public static final String METHOD_NAME = "x-method-name";

	public static <T> void logIfDebugKey(Signal<T> signal, BiConsumer<String, T> logStatement) {

		if (!signal.isOnNext())
			return;

		Optional<String> toPutInMdc = signal.getContextView()
		        .getOrEmpty(DEBUG_KEY);

		String mName = signal.getContextView()
		        .getOrDefault(METHOD_NAME, "");

		toPutInMdc.ifPresent(tpim -> {
			try (MDC.MDCCloseable cMdc = MDC.putCloseable(DEBUG_KEY, tpim)) {
				
				logStatement.accept(mName, signal.get());
			}
		});

	}

	private LogUtil() {
	}
}
