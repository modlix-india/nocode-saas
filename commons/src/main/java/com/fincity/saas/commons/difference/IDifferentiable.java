package com.fincity.saas.commons.difference;

import reactor.core.publisher.Mono;

public interface IDifferentiable<T extends IDifferentiable<T>> {

	public Mono<T> extractDifference(T inc);

	public Mono<T> applyOverride(T override);

}
