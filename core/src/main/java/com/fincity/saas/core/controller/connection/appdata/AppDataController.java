package com.fincity.saas.core.controller.connection.appdata;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.util.ConditionUtil;
import com.fincity.saas.commons.util.FlatFileType;
import com.fincity.saas.core.model.DataObject;
import com.fincity.saas.core.service.connection.appdata.AppDataService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/core/data/")
public class AppDataController {

	public static final String PATH_VARIABLE_ID = "id";
	public static final String PATH_VARIABLE_STORAGE = "storage";
	public static final String PATH_ID = "{storage}/{" + PATH_VARIABLE_ID + "}";
	public static final String PATH_QUERY = "{storage}/query";

	private static final Set<String> IGNORE_PARAMS = Set.of("page", "size", "sort");

	@Autowired
	private AppDataService service;

	@PostMapping("{storage}")
	public Mono<ResponseEntity<Map<String, Object>>> create(
	        @PathVariable(PATH_VARIABLE_STORAGE) final String storageName, @RequestHeader String appCode,
	        @RequestHeader String clientCode, @RequestBody DataObject entity) {

		return this.service.create(appCode, clientCode, storageName, entity)
		        .map(ResponseEntity::ok);
	}

	@PutMapping(value = { PATH_ID, "{storage}" })
	public Mono<ResponseEntity<Map<String, Object>>> update(
	        @PathVariable(PATH_VARIABLE_STORAGE) final String storageName, @RequestHeader String appCode,
	        @RequestHeader String clientCode, @PathVariable(name = PATH_VARIABLE_ID, required = false) final String id,
	        @RequestBody DataObject entity) {

		if (id != null)
			entity.getData()
			        .put("_id", id);

		return this.service.update(appCode, clientCode, storageName, entity, true)
		        .map(ResponseEntity::ok);
	}

	@PatchMapping(value = { PATH_ID, "{storage}" })
	public Mono<ResponseEntity<Map<String, Object>>> updatePatch(
	        @PathVariable(PATH_VARIABLE_STORAGE) final String storageName, @RequestHeader String appCode,
	        @RequestHeader String clientCode, @PathVariable(name = PATH_VARIABLE_ID, required = false) final String id,
	        @RequestBody DataObject entity) {

		if (id != null)
			entity.getData()
			        .put("_id", id);

		return this.service.update(appCode, clientCode, storageName, entity, false)
		        .map(ResponseEntity::ok);

	}

	@GetMapping(PATH_ID)
	public Mono<ResponseEntity<Map<String, Object>>> read(@PathVariable(PATH_VARIABLE_STORAGE) final String storageName,
	        @RequestHeader String appCode, @RequestHeader String clientCode,
	        @PathVariable(PATH_VARIABLE_ID) final String id, ServerHttpRequest request) {

		return this.service.read(appCode, clientCode, storageName, id)
		        .map(ResponseEntity::ok);
	}

	@GetMapping("{storage}")
	public Mono<ResponseEntity<Page<Map<String, Object>>>> readPageFilter(
	        @PathVariable(PATH_VARIABLE_STORAGE) final String storageName, @RequestHeader String appCode,
	        @RequestHeader String clientCode,
	        @RequestParam(value = "count", required = false, defaultValue = "true") Boolean count, Pageable pageable,
	        ServerHttpRequest request) {

		pageable = (pageable == null ? PageRequest.of(0, 10, Direction.ASC, PATH_VARIABLE_ID) : pageable);

		MultiValueMap<String, String> params = request.getQueryParams();
		MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
		for (var param : params.entrySet()) {
			if (IGNORE_PARAMS.contains(param.getKey()))
				continue;
			map.addAll(param.getKey(), param.getValue());
		}

		Query query = new Query().setExcludeFields(false)
		        .setFields(List.of())
		        .setCondition(ConditionUtil.parameterMapToMap(map))
		        .setCount(count)
		        .setPage(pageable.getPageNumber())
		        .setSize(pageable.getPageSize())
		        .setSort(pageable.getSort());

		return this.service.readPage(appCode, clientCode, storageName, query)
		        .map(ResponseEntity::ok);
	}

	@PostMapping(PATH_QUERY)
	public Mono<ResponseEntity<Page<Map<String, Object>>>> readPageFilter(
	        @PathVariable(PATH_VARIABLE_STORAGE) final String storageName, @RequestHeader String appCode,
	        @RequestHeader String clientCode, @RequestBody Query query) {

		return this.service.readPage(appCode, clientCode, storageName, query)
		        .map(ResponseEntity::ok);
	}

	@DeleteMapping(PATH_ID)
	public Mono<ResponseEntity<Boolean>> delete(@PathVariable(PATH_VARIABLE_STORAGE) final String storageName,
	        @RequestHeader String appCode, @RequestHeader String clientCode,
	        @PathVariable(PATH_VARIABLE_ID) final String id) {

		return this.service.delete(appCode, clientCode, storageName, id)
		        .map(ResponseEntity::ok);
	}

	@GetMapping("template/{storage}")
	public Mono<ResponseEntity<byte[]>> downloadTemplate(@PathVariable(PATH_VARIABLE_STORAGE) final String storageName,
	        @RequestHeader String appCode, @RequestHeader String clientCode,
	        @RequestParam(value = "type", defaultValue = "CSV") FlatFileType fileType) {

		return this.service.downloadTemplate(appCode, clientCode, storageName, fileType)
		        .map(bytes -> ResponseEntity.ok()
		                .header(HttpHeaders.CONTENT_TYPE, fileType.getMimeType())
		                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
		                        .filename(storageName + "." + fileType.toString()
		                                .toLowerCase())
		                        .build()
		                        .toString())
		                .body(bytes));
	}

	@PostMapping(value = "template/{storage}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public Mono<ResponseEntity<Boolean>> uploadTemplateData(
	        @PathVariable(PATH_VARIABLE_STORAGE) final String storageName, @RequestHeader String appCode,
	        @RequestHeader String clientCode, @RequestParam(value = "type") FlatFileType fileType,
	        @RequestPart(value = "file") Mono<FilePart> file) {

		return file.flatMap(f -> this.service.uploadTemplate(appCode, clientCode, storageName, fileType, f))
		        .map(ResponseEntity::ok);
	}
}
