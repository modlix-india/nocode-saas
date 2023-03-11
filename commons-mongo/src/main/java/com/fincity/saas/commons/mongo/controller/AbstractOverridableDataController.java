package com.fincity.saas.commons.mongo.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;
import com.fincity.saas.commons.mongo.model.ListResultObject;
import com.fincity.saas.commons.mongo.repository.IOverridableDataRepository;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;

import reactor.core.publisher.Mono;

public class AbstractOverridableDataController<D extends AbstractOverridableDTO<D>, R extends IOverridableDataRepository<D>, S extends AbstractOverridableDataService<D, R>>
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

	@GetMapping("/createForClient/{id}/{clientCode}")
	public Mono<ResponseEntity<D>> createForClient(@PathVariable String id, @PathVariable String clientCode) {

		return this.service.createForClient(id, clientCode)
				.map(ResponseEntity::ok);
	}
}
