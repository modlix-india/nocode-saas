package com.fincity.saas.commons.mongo.model;

import reactor.core.publisher.Mono;

public class ListResultObject extends AbstractOverridableDTO<ListResultObject> {

	private static final long serialVersionUID = 4425643888630525907L;

	@Override
	public Mono<ListResultObject> applyOverride(ListResultObject base) {

		return Mono.just(base);
	}

	@Override
	public Mono<ListResultObject> makeOverride(ListResultObject base) {
		
		return Mono.just(base);
	}

}
