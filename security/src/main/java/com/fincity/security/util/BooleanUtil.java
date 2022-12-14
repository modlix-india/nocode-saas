package com.fincity.security.util;

import reactor.core.publisher.Mono;

public class BooleanUtil {

	private BooleanUtil() {

	}

	public static Mono<Boolean> getTruthOrEmpty(Boolean b) {
		return b.booleanValue() ? Mono.just(b) : Mono.empty();
	}
}
