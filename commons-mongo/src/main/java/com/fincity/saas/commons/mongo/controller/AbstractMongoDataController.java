package com.fincity.saas.commons.mongo.controller;

import java.io.Serializable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.commons.mongo.service.AbstractMongoDataService;
import com.fincity.saas.commons.util.ConditionUtil;

import reactor.core.publisher.Mono;

public class AbstractMongoDataController<I extends Serializable, D extends AbstractDTO<I, I>, R extends ReactiveCrudRepository<D, I>, S extends AbstractMongoDataService<I, D, R>> {

	public static final String PATH_VARIABLE_ID = "id";
	public static final String PATH_ID = "/{" + PATH_VARIABLE_ID + "}";
	public static final String PATH_QUERY = "query";

	@Autowired
	protected S service;

	@PostMapping
	public Mono<ResponseEntity<D>> create(@RequestBody D entity) {
		return this.service.create(entity)
		        .map(ResponseEntity::ok);
	}

	@GetMapping(PATH_ID)
	public Mono<ResponseEntity<D>> read(@PathVariable(PATH_VARIABLE_ID) final I id, ServerHttpRequest request) {
		return this.service.read(id)
		        .map(ResponseEntity::ok);
	}

	@GetMapping()
	public Mono<ResponseEntity<Page<D>>> readPageFilter(Pageable pageable, ServerHttpRequest request) {
		pageable = (pageable == null ? PageRequest.of(0, 10, Direction.ASC, PATH_VARIABLE_ID) : pageable);
		return this.service.readPageFilter(pageable, ConditionUtil.parameterMapToMap(request.getQueryParams()))
		        .map(ResponseEntity::ok);
	}

	@PostMapping(PATH_QUERY)
	public Mono<ResponseEntity<Page<D>>> readPageFilter(@RequestBody Query query) {

		Pageable pageable = PageRequest.of(query.getPage(), query.getSize(), query.getSort());

		return this.service.readPageFilter(pageable, query.getCondition())
		        .map(ResponseEntity::ok);
	}

	@DeleteMapping(PATH_ID)
	public Mono<ResponseEntity<Boolean>> delete(@PathVariable(PATH_VARIABLE_ID) final I id) {
		return this.service.delete(id)
		        .map(ResponseEntity::ok);
	}
}
