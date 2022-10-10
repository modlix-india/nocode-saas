package com.fincity.saas.ui.document;

import reactor.core.publisher.Mono;

public class ListResultObject extends AbstractUIDTO<ListResultObject> {

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
