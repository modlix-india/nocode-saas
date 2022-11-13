package com.fincity.saas.ui.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.mongo.controller.AbstractMongoUpdatableDataController;
import com.fincity.saas.ui.document.AbstractUIDTO;
import com.fincity.saas.ui.document.ListResultObject;
import com.fincity.saas.ui.repository.IUIRepository;
import com.fincity.saas.ui.service.AbstractUIServcie;

import reactor.core.publisher.Mono;

public class AbstractUIController<D extends AbstractUIDTO<D>, R extends IUIRepository<D>, S extends AbstractUIServcie<D, R>>
        extends AbstractMongoUpdatableDataController<String, D, R, S> {

	@Override
	@GetMapping("/nomap2")
	public Mono<ResponseEntity<Page<D>>> readPageFilter(Pageable pageable, ServerHttpRequest request) {
		return Mono.just(ResponseEntity.badRequest()
		        .build());
	}

	@Override
	@PostMapping("/nomap2")
	public Mono<ResponseEntity<Page<D>>> readPageFilter(Query query) {
		return Mono.just(ResponseEntity.badRequest()
		        .build());
	}

	@GetMapping()
	public Mono<ResponseEntity<Page<ListResultObject>>> readPageFilterLRO(Pageable pageable,
	        ServerHttpRequest request) {
		final Pageable finPageable = (pageable == null ? PageRequest.of(0, 10, Direction.ASC, PATH_VARIABLE_ID)
		        : pageable);
		return this.service.readPageFilterLRO(finPageable, request.getQueryParams())
		        .map(ResponseEntity::ok);
	}
}
