package com.fincity.saas.core.controller.connection.appdata;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.util.ConditionUtil;
import com.fincity.saas.core.model.DataObject;
import com.fincity.saas.core.service.connection.appdata.AppDataService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/core/data/{storage}/")
public class AppDataController {

	public static final String PATH_VARIABLE_ID = "id";
	public static final String PATH_VARIABLE_STORAGE = "storage";
	public static final String PATH_ID = "/{" + PATH_VARIABLE_ID + "}";
	public static final String PATH_QUERY = "query";

	@Autowired
	private AppDataService service;

	@PostMapping
	public Mono<ResponseEntity<Map<String, Object>>> create(
	        @PathVariable(PATH_VARIABLE_STORAGE) final String storageName, @RequestHeader String appCode,
	        @RequestHeader String clientCode, @RequestBody DataObject entity) {

		return this.service.create(appCode, clientCode, storageName, entity)
		        .map(ResponseEntity::ok);
	}

	@GetMapping(PATH_ID)
	public Mono<ResponseEntity<Map<String, Object>>> read(@PathVariable(PATH_VARIABLE_STORAGE) final String storageName,
	        @RequestHeader String appCode, @RequestHeader String clientCode,
	        @PathVariable(PATH_VARIABLE_ID) final String id, ServerHttpRequest request) {

		return this.service.read(appCode, clientCode, storageName, id)
		        .map(ResponseEntity::ok);
	}

	@GetMapping()
	public Mono<ResponseEntity<Page<Map<String, Object>>>> readPageFilter(
	        @PathVariable(PATH_VARIABLE_STORAGE) final String storageName, @RequestHeader String appCode,
	        @RequestHeader String clientCode, Pageable pageable, ServerHttpRequest request) {

		pageable = (pageable == null ? PageRequest.of(0, 10, Direction.ASC, PATH_VARIABLE_ID) : pageable);

		return this.service
		        .readPage(appCode, clientCode, storageName, pageable,
		                ConditionUtil.parameterMapToMap(request.getQueryParams()))
		        .map(ResponseEntity::ok);
	}

	@PostMapping(PATH_QUERY)
	public Mono<ResponseEntity<Page<Map<String, Object>>>> readPageFilter(
	        @PathVariable(PATH_VARIABLE_STORAGE) final String storageName, @RequestHeader String appCode,
	        @RequestHeader String clientCode, @RequestBody Query query) {

		Pageable pageable = PageRequest.of(query.getPage(), query.getSize(), query.getSort());

		return this.service.readPage(appCode, clientCode, storageName, pageable, query.getCondition())
		        .map(ResponseEntity::ok);
	}

	@DeleteMapping(PATH_ID)
	public Mono<ResponseEntity<Boolean>> delete(@PathVariable(PATH_VARIABLE_STORAGE) final String storageName,
	        @RequestHeader String appCode, @RequestHeader String clientCode,
	        @PathVariable(PATH_VARIABLE_ID) final String id) {

		return this.service.delete(appCode, clientCode, storageName, id)
		        .map(ResponseEntity::ok);
	}
}
