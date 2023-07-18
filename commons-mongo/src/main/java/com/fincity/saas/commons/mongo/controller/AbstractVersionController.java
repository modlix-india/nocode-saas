package com.fincity.saas.commons.mongo.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.mongo.document.Version;
import com.fincity.saas.commons.mongo.service.AbstractVersionService;

import reactor.core.publisher.Mono;

public class AbstractVersionController {

	public static final String PATH_VARIABLE_ID = "id";
	public static final String PATH_ID = "/{" + PATH_VARIABLE_ID + "}";
	public static final String PATH_QUERY = "/{" + PATH_VARIABLE_ID + "}/query";

	@Autowired
	private AbstractVersionService versionService;

	@GetMapping(PATH_ID)
	public Mono<ResponseEntity<Version>> read(@PathVariable(PATH_VARIABLE_ID) final String id,
	        ServerHttpRequest request) {

		return this.versionService.read(id)
		        .map(ResponseEntity::ok);
	}

	@PostMapping(PATH_QUERY)
	public Mono<ResponseEntity<Page<Version>>> readPageFilter(@PathVariable(PATH_VARIABLE_ID) final String id,
	        @RequestBody Query query) {

		return this.versionService.readPagePerObjectId(id, query)
		        .map(ResponseEntity::ok);
	}
}
