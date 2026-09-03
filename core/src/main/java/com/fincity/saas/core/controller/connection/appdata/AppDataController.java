package com.fincity.saas.core.controller.connection.appdata;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.model.DataObject;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import com.fincity.saas.commons.core.service.connection.appdata.AppDataService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.util.ConditionUtil;
import com.fincity.saas.commons.util.DataFileType;
import com.fincity.saas.commons.util.LogUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
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
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("api/core/data/")
public class AppDataController {

	public static final String PATH_VARIABLE_ID = "id";
	public static final String PATH_VARIABLE_STORAGE = "storage";
	public static final String PATH_ID = "{storage}/{" + PATH_VARIABLE_ID + "}";
	public static final String PATH_QUERY = "{storage}/query";

	/**
	 * Empties a storage and keeps it.
	 * <p>
	 * A literal segment, not an id in disguise, and that is the point. {@code DELETE
	 * {storage}} means "drop the collection", and it is reachable by any caller that
	 * concatenates a row id that turns out to be absent -- which has destroyed a
	 * collection before. A separate path can only be arrived at deliberately.
	 */
	public static final String PATH_ROWS = "{storage}/rows";

	public static final String PATH_COPY_TO_DRAFT = "{storage}/copyToDraft";

	// Here id is version's ID
	public static final String PATH_VERSION_ID = "{storage}/version/{" + PATH_VARIABLE_ID + "}";

	// Here id is the object's ID for which versions are queried
	public static final String PATH_VERSION_ID_QUERY = "{storage}/version/{" + PATH_VARIABLE_ID + "}/query";

	// Everything NOT in here is copied into the filter condition by
	// ConditionUtil.parameterMapToMap, so a declared @RequestParam that is missing from
	// this set also becomes a condition on a field of that name, and the request
	// silently matches no row. `draft` has to be here for that reason.
	private static final Set<String> IGNORE_PARAMS = Set.of("page", "size", "sort", "eager", "eagerFields", "draft");

	@Autowired
	private AppDataService service;

	@Autowired
	private CoreMessageResourceService messageResourceService;

	/*
	 * Every route below takes an optional `draft`.
	 *
	 * Which surface a data call hits is normally ambient, from the hostname the
	 * gateway resolved, and that stays the default: omit the parameter and nothing
	 * about the call changes. Naming it explicitly is for the builder, which runs on
	 * the live host and still has to reach an app's draft sandbox. AppDataService
	 * .onSurface holds the authorisation for that, not this controller.
	 */

	@PostMapping("{storage}")
	public Mono<ResponseEntity<Map<String, Object>>> create(
			@PathVariable(PATH_VARIABLE_STORAGE) final String storageName,
			@RequestHeader String appCode,
			@RequestHeader String clientCode,
			@RequestParam(required = false, defaultValue = "false") Boolean eager,
			@RequestParam(required = false) List<String> eagerFields,
			@RequestParam(required = false) Boolean draft,
			@RequestBody DataObject entity) {

		return this.service.onSurface(appCode, draft,
						this.service.create(appCode, clientCode, storageName, entity, eager, eagerFields))
				.map(ResponseEntity::ok);
	}

	@PutMapping(value = { PATH_ID, "{storage}" })
	public Mono<ResponseEntity<Map<String, Object>>> update(
			@PathVariable(PATH_VARIABLE_STORAGE) final String storageName,
			@RequestHeader String appCode,
			@RequestHeader String clientCode,
			@PathVariable(name = PATH_VARIABLE_ID, required = false) final String id,
			@RequestParam(required = false, defaultValue = "false") Boolean eager,
			@RequestParam(required = false) List<String> eagerFields,
			@RequestParam(required = false) Boolean draft,
			@RequestBody DataObject entity) {

		if (id != null)
			entity.getData()
					.put("_id", id);

		return this.service.onSurface(appCode, draft,
						this.service.update(appCode, clientCode, storageName, entity, true, eager, eagerFields))
				.map(ResponseEntity::ok);
	}

	@PatchMapping(value = { PATH_ID, "{storage}" })
	public Mono<ResponseEntity<Map<String, Object>>> updatePatch(
			@PathVariable(PATH_VARIABLE_STORAGE) final String storageName,
			@RequestHeader String appCode,
			@RequestHeader String clientCode,
			@PathVariable(name = PATH_VARIABLE_ID, required = false) final String id,
			@RequestParam(required = false, defaultValue = "false") Boolean eager,
			@RequestParam(required = false) List<String> eagerFields,
			@RequestParam(required = false) Boolean draft,
			@RequestBody DataObject entity) {

		if (id != null)
			entity.getData()
					.put("_id", id);

		return this.service.onSurface(appCode, draft,
						this.service.update(appCode, clientCode, storageName, entity, false, eager, eagerFields))
				.map(ResponseEntity::ok);

	}

	@GetMapping(PATH_ID)
	public Mono<ResponseEntity<Map<String, Object>>> read(
			@PathVariable(PATH_VARIABLE_STORAGE) final String storageName,
			@RequestHeader String appCode,
			@RequestHeader String clientCode,
			@RequestParam(required = false, defaultValue = "false") Boolean eager,
			@RequestParam(required = false) List<String> eagerFields,
			@PathVariable(PATH_VARIABLE_ID) final String id,
			@RequestParam(required = false) Boolean draft,
			ServerHttpRequest request) {

		return this.service.onSurface(appCode, draft,
						this.service.read(appCode, clientCode, storageName, id, eager, eagerFields))
				.map(ResponseEntity::ok);
	}

	@GetMapping("{storage}")
	public Mono<ResponseEntity<Page<Map<String, Object>>>> readPageFilter(
			@PathVariable(PATH_VARIABLE_STORAGE) final String storageName,
			@RequestHeader String appCode,
			@RequestHeader String clientCode,
			@RequestParam(value = "count", required = false, defaultValue = "true") Boolean count,
			@RequestParam(required = false, defaultValue = "false") Boolean eager,
			@RequestParam(required = false) List<String> eagerFields,
			@RequestParam(required = false) Boolean draft,
			Pageable pageable,
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
				.setSort(pageable.getSort())
				.setEager(eager)
				.setEagerFields(eagerFields);

		return this.service.onSurface(appCode, draft,
						this.service.readPage(appCode, clientCode, storageName, query))
				.map(ResponseEntity::ok);
	}

	@PostMapping(PATH_QUERY)
	public Mono<ResponseEntity<Page<Map<String, Object>>>> readPageFilter(
			@PathVariable(PATH_VARIABLE_STORAGE) final String storageName,
			@RequestHeader String appCode,
			@RequestHeader String clientCode,
			@RequestParam(required = false) Boolean draft,
			@RequestBody Query query) {

		return this.service.onSurface(appCode, draft,
						this.service.readPage(appCode, clientCode, storageName, query))
				.map(ResponseEntity::ok);
	}

	@DeleteMapping(PATH_ID)
	public Mono<ResponseEntity<Boolean>> delete(@PathVariable(PATH_VARIABLE_STORAGE) final String storageName,
			@RequestHeader String appCode, @RequestHeader String clientCode,
			@PathVariable(PATH_VARIABLE_ID) final String id,
			@RequestParam(required = false) Boolean draft) {

		return this.service.onSurface(appCode, draft,
						this.service.delete(appCode, clientCode, storageName, id))
				.map(ResponseEntity::ok);
	}

	/**
	 * Drops every row in the storage, and its version history with them.
	 * <p>
	 * The opt-in is not ceremony. This path also matches
	 * {@code DELETE {storage}/{id}} when the id resolves to nothing, so any caller that builds a
	 * delete URL by concatenating a row id silently wipes the collection the first time that id is
	 * absent. That has already happened. Requiring {@code deleteAll=true} means an accidentally
	 * empty id can only ever produce a 400.
	 */
	@DeleteMapping("{storage}")
	public Mono<ResponseEntity<Boolean>> deleteStorage(@PathVariable(PATH_VARIABLE_STORAGE) final String storageName,
			@RequestHeader String appCode, @RequestHeader String clientCode,
			@RequestParam(required = false, defaultValue = "false") Boolean deleteAll,
			@RequestParam(required = false) Boolean draft) {

		if (!Boolean.TRUE.equals(deleteAll))
			return this.messageResourceService.throwMessage(
					msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					CoreMessageResourceService.STORAGE_DELETE_ALL_NOT_CONFIRMED, storageName);

		return this.service.onSurface(appCode, draft,
						this.service.deleteStorage(appCode, clientCode, storageName))
				.map(ResponseEntity::ok);
	}

	/**
	 * Deletes every row and keeps the storage.
	 * <p>
	 * Distinct from {@link #deleteStorage} in what survives: the collection stays, and
	 * so does its {@code _version} history. What it does NOT do is run the single-row
	 * delete path -- no {@code BEFORE_DELETE}/{@code AFTER_DELETE} triggers, no
	 * {@code Delete} events, and no relation {@code deleteConstraint} checks, so a
	 * RESTRICT does not stop it and a CASCADE does not follow it. Anything offering
	 * this to a person has to say so.
	 * <p>
	 * Authorised for the storage's own {@code deleteAuth} OR write access to the
	 * application, because the caller here is usually a builder and a builder holds
	 * none of an app's runtime authorities. See
	 * {@code AppDataService.builderOrAuthorised}.
	 *
	 * @param dryRun count the rows instead of deleting them
	 */
	@DeleteMapping(PATH_ROWS)
	public Mono<ResponseEntity<Long>> deleteAllRows(@PathVariable(PATH_VARIABLE_STORAGE) final String storageName,
			@RequestHeader String appCode, @RequestHeader String clientCode,
			@RequestParam(required = false, defaultValue = "false") Boolean deleteAll,
			@RequestParam(required = false, defaultValue = "false") Boolean dryRun,
			@RequestParam(required = false) Boolean draft) {

		if (!Boolean.TRUE.equals(deleteAll) && !Boolean.TRUE.equals(dryRun))
			return this.messageResourceService.throwMessage(
					msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					CoreMessageResourceService.STORAGE_DELETE_ALL_NOT_CONFIRMED, storageName);

		return this.service.onSurface(appCode, draft,
						this.service.clearAllRows(appCode, clientCode, storageName, dryRun))
				.map(ResponseEntity::ok);
	}

	/**
	 * Copies this storage's LIVE rows into its DRAFT rows.
	 * <p>
	 * Publish promotes definitions and never promotes data, so a draft surface starts
	 * empty. This is how a sandbox gets realistic rows to work against. It takes no
	 * {@code draft} parameter because it names both surfaces itself, and it only goes
	 * one way: draft rows are never copied live.
	 *
	 * @param replace empty the draft rows first, rather than overlaying onto them
	 * @return how many rows were written
	 */
	@PostMapping(PATH_COPY_TO_DRAFT)
	public Mono<ResponseEntity<Long>> copyToDraft(@PathVariable(PATH_VARIABLE_STORAGE) final String storageName,
			@RequestHeader String appCode, @RequestHeader String clientCode,
			@RequestParam(required = false, defaultValue = "true") Boolean replace) {

		return this.service.copyLiveDataToDraft(appCode, clientCode, storageName, replace)
				.map(ResponseEntity::ok);
	}

	@GetMapping("download/{fileType}/{storage}")
	public Mono<Void> downloadContent(@PathVariable(PATH_VARIABLE_STORAGE) final String storageName,
			@RequestHeader String appCode, @RequestHeader String clientCode,
			@PathVariable(name = "fileType") DataFileType fileType,
			@RequestParam(required = false) Boolean draft, ServerHttpRequest request,
			ServerHttpResponse response) {

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
				.setSize(1000);

		return this.service.onSurface(appCode, draft,
				this.service.downloadData(appCode, clientCode, storageName, query, fileType, response));
	}

	@PostMapping("download/{fileType}/{storage}")
	public Mono<Void> downloadContent(@PathVariable(PATH_VARIABLE_STORAGE) final String storageName,
			@RequestHeader String appCode, @RequestHeader String clientCode,
			@PathVariable(name = "fileType", required = false) DataFileType fileType,
			@RequestParam(required = false) Boolean draft, @RequestBody Query query,
			ServerHttpResponse response) {

		return this.service.onSurface(appCode, draft,
				this.service.downloadData(appCode, clientCode, storageName, query, fileType, response));
	}

	@GetMapping("template/{storage}")
	public Mono<ResponseEntity<byte[]>> downloadTemplate(@PathVariable(PATH_VARIABLE_STORAGE) final String storageName,
			@RequestHeader String appCode, @RequestHeader String clientCode,
			@RequestParam(value = "type", defaultValue = "CSV") DataFileType fileType) {

		return this.service.downloadTemplate(appCode, clientCode, storageName, fileType)
				.map(bytes -> ResponseEntity.ok()
						.header(HttpHeaders.CONTENT_TYPE, fileType.getMimeType())
						.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
								.filename(storageName + "_template." + fileType.toString()
										.toLowerCase())
								.build()
								.toString())
						.body(bytes));
	}

	@PostMapping(value = "upload/{storage}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public Mono<ResponseEntity<Boolean>> uploadData(@PathVariable(PATH_VARIABLE_STORAGE) final String storageName,
			@RequestHeader String appCode, @RequestHeader String clientCode,
			@RequestParam(value = "type", required = false) DataFileType fileType,
			@RequestParam(required = false) Boolean draft,
			@RequestPart(value = "file") Mono<FilePart> filePartMono) {

		return this.service.onSurface(appCode, draft, FlatMapUtil.flatMapMono(

						() -> filePartMono,

						filePart -> Mono.just(fileType == null
								? DataFileType.getFileTypeFromExtension(filePart.filename())
								: fileType),

						(filePart, type) -> this.service.uploadData(appCode, clientCode, storageName, type, filePart)
								.map(ResponseEntity::ok)))

				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataController.uploadData"));
	}

	@GetMapping(PATH_VERSION_ID)
	public Mono<ResponseEntity<Map<String, Object>>> getVersion(
			@PathVariable(PATH_VARIABLE_STORAGE) final String storageName, @RequestHeader String appCode,
			@RequestHeader String clientCode, @PathVariable(PATH_VARIABLE_ID) final String versionId,
			@RequestParam(required = false) Boolean draft) {

		return this.service.onSurface(appCode, draft,
						this.service.readVersion(appCode, clientCode, storageName, versionId))
				.map(ResponseEntity::ok);
	}

	@PostMapping(PATH_VERSION_ID_QUERY)
	public Mono<ResponseEntity<Page<Map<String, Object>>>> findVersions(
			@PathVariable(PATH_VARIABLE_STORAGE) final String storageName, @RequestHeader String appCode,
			@RequestHeader String clientCode, @PathVariable(PATH_VARIABLE_ID) final String versionId,
			@RequestParam(required = false) Boolean draft,
			@RequestBody Query query) {

		return this.service.onSurface(appCode, draft,
						this.service.readPageVersion(appCode, clientCode, storageName, versionId, query))
				.map(ResponseEntity::ok);
	}
}
