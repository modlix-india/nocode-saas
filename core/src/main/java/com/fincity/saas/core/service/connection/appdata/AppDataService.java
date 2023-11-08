package com.fincity.saas.core.service.connection.appdata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.expression.ParseException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ZeroCopyHttpOutputMessage;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Service;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.array.ArraySchemaType;
import com.fincity.nocode.kirun.engine.json.schema.reactive.ReactiveSchemaUtil;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.reactive.ReactiveHybridRepository;
import com.fincity.nocode.kirun.engine.repository.reactive.KIRunReactiveSchemaRepository;
import com.fincity.nocode.kirun.engine.runtime.expression.tokenextractor.ObjectValueSetterExtractor;
import com.fincity.nocode.kirun.engine.util.primitive.PrimitiveUtil;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.file.DataFileReader;
import com.fincity.saas.commons.file.DataFileWriter;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.DataFileType;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.MapUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.core.document.Connection;
import com.fincity.saas.core.document.Storage;
import com.fincity.saas.core.enums.ConnectionSubType;
import com.fincity.saas.core.enums.ConnectionType;
import com.fincity.saas.core.enums.StorageTriggerType;
import com.fincity.saas.core.kirun.repository.CoreSchemaRepository;
import com.fincity.saas.core.model.DataObject;
import com.fincity.saas.core.service.ConnectionService;
import com.fincity.saas.core.service.CoreFunctionService;
import com.fincity.saas.core.service.CoreMessageResourceService;
import com.fincity.saas.core.service.CoreSchemaService;
import com.fincity.saas.core.service.EventDefinitionService;
import com.fincity.saas.core.service.StorageService;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.netflix.discovery.converters.Auto;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class AppDataService {

	private static final ConnectionSubType DEFAULT_APP_DATA_SERVICE = ConnectionSubType.MONGO;

	@Autowired
	private ConnectionService connectionService;

	@Autowired
	private StorageService storageService;

	@Autowired
	private MongoAppDataService mongoAppDataService;

	@Autowired
	private CoreSchemaService schemaService;

	@Autowired
	private CoreMessageResourceService msgService;

	@Autowired
	@Lazy
	private CoreFunctionService functionService;

	@Autowired
	@Lazy
	private EventDefinitionService eventDefinitionService;

	@Autowired
	@Lazy
	private EventCreationService ecService;

	private EnumMap<ConnectionSubType, IAppDataService> services = new EnumMap<>(ConnectionSubType.class);

	private static final Logger logger = LoggerFactory.getLogger(AppDataService.class);

	private static final String DATA_OBJECT_KEY = "dataObject";
	private static final String EXISTING_DATA_OBJECT_KEY = "existingDataObject";

	@PostConstruct
	public void init() {

		this.services.putAll(Map.of(ConnectionSubType.MONGO, (IAppDataService) mongoAppDataService));
	}

	public Mono<Map<String, Object>> create(String appCode, String clientCode, String storageName,
			DataObject dataObject) {

		Mono<Map<String, Object>> mono = FlatMapUtil.flatMapMonoWithNull(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),

				(ca, ac) -> Mono.just(clientCode == null ? ca.getUrlClientCode() : clientCode),

				(ca, ac, cc) -> connectionService.find(ac, cc, ConnectionType.APP_DATA),

				(ca, ac, cc, conn) -> Mono
						.just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

				(ca, ac, cc, conn, dataService) -> storageService.read(storageName, ac, cc)
						.map(ObjectWithUniqueID::getObject),

				(ca, ac, cc, conn, dataService, storage) -> this.genericOperation(storage,
						(cona, hasAccess) -> this.createWithTriggers(dataService, conn, storage, dataObject)
								.flatMap(e -> this.generateEvent(ca, ac, cc, storage, "Create", e, null)),
						Storage::getCreateAuth,
						CoreMessageResourceService.FORBIDDEN_CREATE_STORAGE));

		return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.create"));
	}

	private Mono<Map<String, Object>> generateEvent(ContextAuthentication ca, String appCode, String clientCode,
			Storage storage,
			String operation, Map<String, Object> data, Map<String, Object> existing) {

		if (storage.getGenerateEvents() == null || !storage.getGenerateEvents().booleanValue())
			return Mono.just(data);

		String eventName = "Storage." + storage.getName() + "." + operation;

		return FlatMapUtil.flatMapMono(
				() -> this.eventDefinitionService.read(eventName, appCode, clientCode).map(Optional::of)
						.defaultIfEmpty(Optional.empty()),
				op -> {

					if (op.isEmpty())
						return Mono.just(data);

					HashMap<String, Object> eventData = new HashMap<>();

					eventData.put(DATA_OBJECT_KEY, data);
					if (BooleanUtil.safeValueOf(op.get().getObject().getIncludeContextAuthentication()))
						eventData.put("authentication", ca);

					if (existing != null)
						eventData.put(EXISTING_DATA_OBJECT_KEY, existing);

					return this.ecService
							.createEvent(new EventQueObject().setAppCode(appCode)
									.setClientCode(clientCode)
									.setEventName(eventName)
									.setData(eventData))
							.map(e -> data);
				});
	}

	private Mono<Boolean> executeTriggers(Storage storage, StorageTriggerType triggerType,
			Map<String, JsonElement> args) {

		return Flux.fromIterable(storage.getTriggers()
				.get(triggerType))
				.flatMap(trigger -> this.functionService.execute(trigger.substring(0, trigger.lastIndexOf('.')),
						trigger.substring(trigger.lastIndexOf('.') + 1), storage.getAppCode(),
						storage.getClientCode(), args, null))
				.collectList()
				.map(e -> true);
	}

	private Mono<Map<String, Object>> createWithTriggers(IAppDataService dataService, Connection conn, Storage storage,
			DataObject dataObject) {

		boolean noBeforeCreate = storage.getTriggers() == null
				|| storage.getTriggers().get(StorageTriggerType.BEFORE_CREATE) == null
				|| storage.getTriggers().get(StorageTriggerType.BEFORE_CREATE)
						.isEmpty();

		boolean noAfterCreate = storage.getTriggers() == null
				|| storage.getTriggers().get(StorageTriggerType.AFTER_CREATE) == null
				|| storage.getTriggers().get(StorageTriggerType.AFTER_CREATE)
						.isEmpty();

		if (noBeforeCreate && noAfterCreate)
			return dataService.create(conn, storage, dataObject);

		return FlatMapUtil.flatMapMono(
				() -> {

					if (noBeforeCreate)
						return Mono.just(true);

					return this.executeTriggers(storage, StorageTriggerType.BEFORE_CREATE, Map.of(DATA_OBJECT_KEY,
							new Gson().toJsonTree(dataObject.getData())));
				},

				beforeCreate -> dataService.create(conn, storage, dataObject),

				(beforeCreate, created) -> {

					if (noAfterCreate)
						return Mono.just(created);

					return this.executeTriggers(storage, StorageTriggerType.AFTER_CREATE, Map.of(DATA_OBJECT_KEY,
							new Gson().toJsonTree(created)))
							.map(e -> created);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.genericCreate"));
	}

	public Mono<Map<String, Object>> update(String appCode, String clientCode, String storageName,
			DataObject dataObject, Boolean override) {

		Mono<Map<String, Object>> mono = FlatMapUtil.flatMapMonoWithNull(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),

				(ca, ac) -> Mono.just(clientCode == null ? ca.getUrlClientCode() : clientCode),

				(ca, ac, cc) -> connectionService.find(ac, cc, ConnectionType.APP_DATA),

				(ca, ac, cc, conn) -> Mono
						.just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

				(ca, ac, cc, conn, dataService) -> storageService.read(storageName, ac, cc)
						.map(ObjectWithUniqueID::getObject),

				(ca, ac, cc, conn, dataService, storage) -> this.genericOperation(storage,
						(cona, hasAccess) -> this.updateWithTriggers(ac, cc, dataService, conn, storage, dataObject,
								override)
								.flatMap(e -> this.generateEvent(ca, appCode, clientCode, storage, "Update",
										e.getT1(), e.getT2().orElse(null))),
						Storage::getUpdateAuth, CoreMessageResourceService.FORBIDDEN_UPDATE_STORAGE));

		return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.update"));
	}

	private Mono<Tuple2<Map<String, Object>, Optional<Map<String, Object>>>> updateWithTriggers(String appCode,
			String clientCode, IAppDataService dataService,
			Connection conn, Storage storage,
			DataObject dataObject, Boolean override) {

		boolean noBeforeUpdate = storage.getTriggers() == null
				|| storage.getTriggers().get(StorageTriggerType.BEFORE_UPDATE) == null
				|| storage.getTriggers().get(StorageTriggerType.BEFORE_UPDATE)
						.isEmpty();

		boolean noAfterUpdate = storage.getTriggers() == null
				|| storage.getTriggers().get(StorageTriggerType.AFTER_UPDATE) == null
				|| storage.getTriggers().get(StorageTriggerType.AFTER_UPDATE)
						.isEmpty();

		if (noBeforeUpdate && noAfterUpdate && !BooleanUtil.safeValueOf(storage.getGenerateEvents()))
			return dataService.update(conn, storage, dataObject, override).map(e -> Tuples.of(e, Optional.empty()));

		String id = StringUtil.safeValueOf(dataObject.getData().get("_id"));

		if (noBeforeUpdate && noAfterUpdate) {

			return this.read(appCode, clientCode, storage.getName(), id)
					.flatMap(existing -> dataService
							.update(conn, storage, dataObject, override).map(e -> Tuples.of(e, Optional.of(existing))));
		}

		return FlatMapUtil.flatMapMono(

				() -> this.read(appCode, clientCode, storage.getName(), id),

				existing -> {

					if (existing == null)
						return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
								AbstractMongoMessageResourceService.OBJECT_NOT_FOUND, storage.getName(), id);

					if (noBeforeUpdate)
						return Mono.just(true);

					Map<String, JsonElement> args = Map.of(DATA_OBJECT_KEY, new Gson().toJsonTree(dataObject.getData()),
							EXISTING_DATA_OBJECT_KEY,
							new Gson().toJsonTree(existing));

					return this.executeTriggers(storage, StorageTriggerType.BEFORE_UPDATE, args);
				},

				(existing, beforeUpdate) -> dataService.update(conn, storage, dataObject, override),

				(existing, beforeUpdate, created) -> {

					if (noAfterUpdate)
						return Mono.just(Tuples.of(created, Optional.of(existing)));

					Map<String, JsonElement> args = Map.of(DATA_OBJECT_KEY, new Gson().toJsonTree(created),
							EXISTING_DATA_OBJECT_KEY,
							new Gson().toJsonTree(existing));

					return this.executeTriggers(storage, StorageTriggerType.AFTER_UPDATE, args)
							.map(e -> Tuples.of(created, Optional.of(existing)));
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.updateWithTriggers"));
	}

	public Mono<Map<String, Object>> read(String appCode, String clientCode, String storageName, String id) {

		Mono<Map<String, Object>> mono = FlatMapUtil.flatMapMonoWithNull(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),

				(ca, ac) -> Mono.just(clientCode == null ? ca.getUrlClientCode() : clientCode),

				(ca, ac, cc) -> connectionService.find(ac, cc, ConnectionType.APP_DATA),

				(ca, ac, cc, conn) -> Mono
						.just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

				(ca, ac, cc, conn, dataService) -> storageService.read(storageName, ac, cc)
						.map(ObjectWithUniqueID::getObject),

				(ca, ac, cc, conn, dataService, storage) -> this.genericOperation(storage,
						(cona, hasAccess) -> dataService.read(conn, storage, id), Storage::getReadAuth,
						CoreMessageResourceService.FORBIDDEN_READ_STORAGE));

		return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.read"));
	}

	public Mono<Page<Map<String, Object>>> readPage(String appCode, String clientCode, String storageName,
			Query query) {

		Mono<Page<Map<String, Object>>> mono = FlatMapUtil.flatMapMonoWithNull(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),

				(ca, ac) -> Mono.just(clientCode == null ? ca.getUrlClientCode() : clientCode),

				(ca, ac, cc) -> connectionService.find(ac, cc, ConnectionType.APP_DATA),

				(ca, ac, cc, conn) -> Mono
						.just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

				(ca, ac, cc, conn, dataService) -> storageService.read(storageName, ac, cc)
						.map(ObjectWithUniqueID::getObject),

				(ca, ac, cc, conn, dataService, storage) -> this.genericOperation(storage,
						(cona, hasAccess) -> dataService.readPage(conn, storage, query), Storage::getReadAuth,
						CoreMessageResourceService.FORBIDDEN_READ_STORAGE));

		return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.readPage"));
	}

	public Mono<Boolean> delete(String appCode, String clientCode, String storageName, String id) {
		Mono<Boolean> mono = FlatMapUtil.flatMapMonoWithNull(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),

				(ca, ac) -> Mono.just(clientCode == null ? ca.getUrlClientCode() : clientCode),

				(ca, ac, cc) -> connectionService.find(ac, cc, ConnectionType.APP_DATA),

				(ca, ac, cc, conn) -> Mono
						.just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

				(ca, ac, cc, conn, dataService) -> storageService.read(storageName, ac, cc)
						.map(ObjectWithUniqueID::getObject),

				(ca, ac, cc, conn, dataService, storage) -> this.genericOperation(storage,
						(cona, hasAccess) -> this.deleteWithTriggers(appCode, clientCode, dataService, conn, storage,
								id).flatMap(
										e -> this.generateEvent(ca, appCode, clientCode, storage, "Delete",
												e.getT2().orElse(null), null).map(x -> e.getT1())),
						Storage::getDeleteAuth,
						CoreMessageResourceService.FORBIDDEN_DELETE_STORAGE));

		return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.delete"));
	}

	private Mono<Tuple2<Boolean, Optional<Map<String, Object>>>> deleteWithTriggers(String appCode, String clientCode,
			IAppDataService dataService,
			Connection conn, Storage storage, String id) {

		boolean noBeforeDelete = storage.getTriggers() == null
				|| storage.getTriggers().get(StorageTriggerType.BEFORE_DELETE) == null
				|| storage.getTriggers().get(StorageTriggerType.BEFORE_DELETE)
						.isEmpty();

		boolean noAfterDelete = storage.getTriggers() == null
				|| storage.getTriggers().get(StorageTriggerType.AFTER_DELETE) == null
				|| storage.getTriggers().get(StorageTriggerType.AFTER_DELETE)
						.isEmpty();

		if (noBeforeDelete && noAfterDelete && !BooleanUtil.safeValueOf(storage.getGenerateEvents()))
			return dataService.delete(conn, storage, id).map(e -> Tuples.of(e, Optional.empty()));

		if (noBeforeDelete && noAfterDelete)
			return this.read(appCode, clientCode, storage.getName(), id).flatMap(
					existing -> dataService.delete(conn, storage, id).map(e -> Tuples.of(e, Optional.of(existing))));

		return FlatMapUtil.flatMapMono(

				() -> this.read(appCode, clientCode, storage.getName(), id),

				existing -> {

					if (existing == null)
						return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
								AbstractMongoMessageResourceService.OBJECT_NOT_FOUND, storage.getName(), id);

					if (noBeforeDelete)
						return Mono.just(true);

					Map<String, JsonElement> args = Map.of(DATA_OBJECT_KEY,
							new Gson().toJsonTree(existing));

					return this.executeTriggers(storage, StorageTriggerType.BEFORE_DELETE, args);
				},

				(existing, beforeDelete) -> dataService.delete(conn, storage, id),

				(existing, beforeDelete, deleted) -> {

					if (noAfterDelete)
						return Mono.just(Tuples.of(deleted, Optional.of(existing)));

					Map<String, JsonElement> args = Map.of(DATA_OBJECT_KEY,
							new Gson().toJsonTree(existing));

					return this.executeTriggers(storage, StorageTriggerType.AFTER_DELETE, args)
							.map(e -> Tuples.of(deleted, Optional.of(existing)));
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.genericDelete"));
	}

	public Mono<Void> downloadData(String appCode, String clientCode, String storageName, Query query,
			DataFileType fileType, ServerHttpResponse response) {

		Mono<Void> mono = FlatMapUtil.flatMapMonoWithNull(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),

				(ca, ac) -> Mono.just(clientCode == null ? ca.getUrlClientCode() : clientCode),

				(ca, ac, cc) -> connectionService.find(ac, cc, ConnectionType.APP_DATA),

				(ca, ac, cc, conn) -> Mono
						.just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

				(ca, ac, cc, conn, dataService) -> storageService.read(storageName, ac, cc)
						.map(ObjectWithUniqueID::getObject),

				(ca, ac, cc, conn, dataService, storage) -> this.genericOperation(storage,
						(cas, hasAccess) -> this.writeDataToResponse(storage,
								dataService.readPageAsFlux(conn, storage, query), fileType, response),
						Storage::getReadAuth, CoreMessageResourceService.FORBIDDEN_READ_STORAGE));

		return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.downloadData"));
	}

	private Mono<Void> writeDataToResponse(Storage storage, Flux<Map<String, Object>> dataFlux, DataFileType fileType,
			ServerHttpResponse response) {

		return FlatMapUtil.flatMapMonoWithNull(

				() -> fileType.isNestedStructure() ? Mono.<Schema>empty() : storageService.getSchema(storage),

				schema -> schema != null ? this.getHeaders(null, storage, schema) : Mono.just(List.of()),

				(schema, dataHeaders) -> {
					String file = storage.getName() + "_data." + fileType.toString()
							.toLowerCase();
					try {
						Path fPath = Files.createTempFile(file, "");
						DataFileWriter dfw = new DataFileWriter(dataHeaders, fileType, Files.newOutputStream(fPath,
								StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING));

						ObjectValueSetterExtractor ovs = new ObjectValueSetterExtractor(new JsonObject(), "Data.");
						Gson gson = new Gson();

						return fluxToFile(dataFlux, fileType, dataHeaders, dfw, ovs, gson)
								.flatMap(e -> flieToResponse(fileType, response, file, fPath, dfw));
					} catch (Exception ex) {
						return this.msgService.throwMessage(
								msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg, ex),
								CoreMessageResourceService.NOT_ABLE_TO_DOWNLOAD_DATA, file);
					}
				});
	}

	private Mono<Void> flieToResponse(DataFileType fileType, ServerHttpResponse response, String file, Path fPath,
			DataFileWriter dfw) {

		try {

			dfw.flush();
			dfw.close();
			ZeroCopyHttpOutputMessage zeroCopyResponse = (ZeroCopyHttpOutputMessage) response;
			long length = Files.size(fPath);
			HttpHeaders headers = response.getHeaders();
			headers.setContentLength(length);
			headers.add("content-type", fileType.getMimeType());
			headers.setContentDisposition(ContentDisposition.attachment()
					.filename(file)
					.build());

			return zeroCopyResponse.writeWith(fPath, 0, length);
		} catch (Exception ex) {
			return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg, ex),
					CoreMessageResourceService.NOT_ABLE_TO_DOWNLOAD_DATA, file);
		}
	}

	private Mono<Boolean> fluxToFile(Flux<Map<String, Object>> dataFlux, DataFileType fileType,
			List<String> dataHeaders, DataFileWriter dfw, ObjectValueSetterExtractor ovs, Gson gson) {
		return dataFlux.reduce(Boolean.TRUE, (db, e) -> {

			Map<String, Object> newMap = e;
			if (!fileType.isNestedStructure()) {

				JsonElement job = gson.toJsonTree(e);
				ovs.setStore(job);
				newMap = dataHeaders.stream()
						.map(head -> {
							JsonElement ele = ovs.getValue("Data." + head);
							return Tuples.of(head, ele == null ? "" : ele.getAsString());
						})
						.collect(Collectors.toMap(Tuple2::getT1, Tuple2::getT2));
			}

			try {
				dfw.write(newMap);
			} catch (IOException e1) {
				throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to create file", e1);
			}
			return true;
		});
	}

	public Mono<byte[]> downloadTemplate(String appCode, String clientCode, String storageName, DataFileType fileType) {

		return FlatMapUtil.flatMapMonoWithNull(

				() -> connectionService.find(appCode, clientCode, ConnectionType.APP_DATA),

				conn -> Mono
						.just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

				(conn, dataService) -> storageService.read(storageName, appCode, clientCode)
						.map(ObjectWithUniqueID::getObject),

				(conn, dataService, storage) -> this
						.genericOperation(storage, (ca, hasAccess) -> downloadTemplate(storage, fileType, "notghjin"),
								Storage::getCreateAuth, CoreMessageResourceService.FORBIDDEN_CREATE_STORAGE)

						.switchIfEmpty(
								this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
										CoreMessageResourceService.NOT_ABLE_TO_OPEN_FILE_ERROR)))

				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.downloadTemplate"));
	}

	public Mono<Boolean> uploadData(String appCode, String clientCode, String storageName, DataFileType fileType,
			FilePart file) {

		Mono<Boolean> mono = FlatMapUtil.flatMapMonoWithNull(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),

				(ca, ac) -> Mono.just(clientCode == null ? ca.getUrlClientCode() : clientCode),

				(ca, ac, cc) -> connectionService.find(ac, cc, ConnectionType.APP_DATA),

				(ca, ac, cc, conn) -> Mono
						.just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

				(ca, ac, cc, conn, dataService) -> storageService.read(storageName, ac, cc)
						.map(ObjectWithUniqueID::getObject),

				(ca, ac, cc, conn, dataService, storage) -> this.genericOperation(storage,
						(cona, hasAccess) -> uploadDataInternal(conn, storage, fileType, file, dataService),
						Storage::getCreateAuth, CoreMessageResourceService.FORBIDDEN_CREATE_STORAGE));

		return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.uploadData"));
	}

	private <T> Mono<T> genericOperation(Storage storage, BiFunction<ContextAuthentication, Boolean, Mono<T>> bifun,
			Function<Storage, String> authFun, String msgString) {

		if (storage == null)
			return msgService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
					CoreMessageResourceService.STORAGE_NOT_FOUND);

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> Mono.justOrEmpty(SecurityContextUtil.hasAuthority(authFun.apply(storage), ca.getUser()
						.getAuthorities()) ? true : null),

				bifun)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.genericOperation"))
				.switchIfEmpty(this.msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						msgString, storage.getName()));
	}

	private Mono<byte[]> downloadTemplate(Storage storage, DataFileType type, String temp) { // NOSONAR

		if (type.isNestedStructure())
			return Mono.just(new byte[0]);

		return FlatMapUtil.flatMapMonoWithNull(() -> storageService.getSchema(storage),

				storageSchema -> (storageSchema.getRef() != null)
						|| (storageSchema.getType() != null && storageSchema.getType()
								.getAllowedSchemaTypes()
								.size() == 1 && storageSchema.getType()
										.getAllowedSchemaTypes()
										.contains(SchemaType.OBJECT)) ? this.getHeaders(null, storage, storageSchema)
												: Mono.empty()

				,

				(storageSchema, acutalHeaders) -> {
					try {
						ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
						DataFileWriter writer = new DataFileWriter(acutalHeaders, type, byteStream);
						writer.write(Map.of());
						writer.flush();
						writer.close();
						byteStream.flush();
						byteStream.close();
						byte[] bytes = byteStream.toByteArray();
						return Mono.just(bytes);
					} catch (Exception e) {
						return this.msgService.throwMessage(
								msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
								CoreMessageResourceService.TEMPLATE_GENERATION_ERROR, type.toString());
					}

				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.downloadTemplate"));

	}

	private Mono<Map<String, Set<SchemaType>>> getHeadersSchemaType(String prefix, Storage storage, Schema schema,
			int level) {

		return FlatMapUtil.flatMapMono(

				() -> {

					if (schema.getRef() == null)
						return Mono.just(schema);

					return ReactiveSchemaUtil.getSchemaFromRef(schema,
							new ReactiveHybridRepository<>(new KIRunReactiveSchemaRepository(),
									new CoreSchemaRepository(), this.schemaService
											.getSchemaRepository(storage.getAppCode(), storage.getClientCode())),
							schema.getRef());

				},

				rSchema -> {

					if (rSchema.getType()
							.contains(SchemaType.OBJECT)) {

						return getSchemaHeadersIfObject(prefix, storage, level, rSchema);

					} else if (rSchema.getType()
							.contains(SchemaType.ARRAY)) {

						return getSchemaHeadersIfArray(prefix, storage, level, rSchema);
					}

					return Mono.just(Map.of(prefix, rSchema.getType()
							.getAllowedSchemaTypes()));
				});
	}

	private Mono<Map<String, Set<SchemaType>>> getSchemaHeadersIfArray(String prefix, Storage storage, int level,
			Schema rSchema) {

		if (level > 2 || rSchema.getItems() == null)
			return Mono.just(Map.of());

		ArraySchemaType aType = rSchema.getItems();

		if (aType.getSingleSchema() != null) {

			return Flux.range(0, 2)
					.map(e -> getPrefixArrayName(prefix, e))
					.flatMap(e -> this.getHeadersSchemaType(e, storage, aType.getSingleSchema(), level + 1)
							.map(Map::entrySet)
							.flatMapMany(Flux::fromIterable))
					.collectMap(Map.Entry::getKey, Map.Entry::getValue);

		} else if (aType.getTupleSchema() != null) {

			return Flux.<Tuple2<Integer, Schema>>create(sink -> {
				for (int i = 0; i < aType.getTupleSchema()
						.size(); i++)
					sink.next(Tuples.of(Integer.valueOf(i), aType.getTupleSchema()
							.get(i)));

				sink.complete();
			})
					.flatMap(tup -> this
							.getHeadersSchemaType(getPrefixArrayName(prefix, tup.getT1()), storage, tup.getT2(),
									level + 1)
							.map(Map::entrySet)
							.flatMapMany(Flux::fromIterable))
					.collectMap(Map.Entry::getKey, Map.Entry::getValue);
		}

		return Mono.just(Map.of());
	}

	private Mono<Map<String, Set<SchemaType>>> getSchemaHeadersIfObject(String prefix, Storage storage, int level,
			Schema rSchema) {

		if (level >= 2 || rSchema.getProperties() == null)
			return Mono.just(Map.of());

		return Flux.fromIterable(rSchema.getProperties()
				.entrySet())
				.flatMap(e -> this
						.getHeadersSchemaType(getFlattenedObjectName(prefix, e), storage, e.getValue(), level + 1)
						.map(Map<String, Set<SchemaType>>::entrySet)
						.flatMapMany(Flux::fromIterable))
				.collectMap(Map.Entry::getKey, Map.Entry::getValue);
	}

	private Mono<List<String>> getHeaders(String prefix, Storage storage, Schema sch) { // NOSONAR

		return this.getHeadersSchemaType(prefix, storage, sch, 0)
				.flatMapMany(e -> Flux.fromIterable(e.keySet()))
				.sort((a, b) -> {
					int aCount = StringUtils.countMatches(a, '.');
					int bCount = StringUtils.countMatches(b, '.');
					if (aCount == bCount)
						return a.compareToIgnoreCase(b);

					return aCount - bCount;
				})
				.collectList();
	}

	private String getPrefixArrayName(String prefix, int e) {
		return prefix == null ? "[" + e + "]" : prefix + "[" + e + "]";
	}

	private String getFlattenedObjectName(String prefix, Entry<String, Schema> e) {
		return prefix == null ? e.getKey() : prefix + "." + e.getKey();
	}

	// add a check for storage schema is only object
	private Mono<Boolean> uploadDataInternal(Connection conn, Storage storage, DataFileType fileType, FilePart filePart,
			IAppDataService dataService) {

		return FlatMapUtil.flatMapMono(

				() -> storageService.getSchema(storage),

				storageSchema -> fileType.isNestedStructure() ? Mono.just(Map.of())
						: this.getHeadersSchemaType(null, storage, storageSchema, 0),

				(storageSchema, headers) -> {

					List<Mono<Boolean>> monoList = (fileType == DataFileType.JSON || fileType == DataFileType.JSONL)
							? nestedFileToDB(conn, storage, fileType, filePart, dataService)
							: flatFileToDB(conn, storage, fileType, filePart, dataService, headers);

					return Flux.concat(monoList)
							.collectList()
							.map(e -> true);
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.uploadDataInternal"))
				.switchIfEmpty(msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
						CoreMessageResourceService.NOT_ABLE_TO_READ_FILE_FORMAT, fileType));
	}

	private List<Mono<Boolean>> nestedFileToDB(Connection conn, Storage storage, DataFileType fileType,
			FilePart filePart, IAppDataService dataService) {

		List<Mono<Boolean>> monoList = new ArrayList<>();

		Map<String, Object> job;
		try (DataFileReader reader = new DataFileReader(filePart, fileType)) {
			while ((job = reader.readObject()) != null)
				monoList.add(dataService.create(conn, storage, new DataObject().setData(job))
						.map(v -> true));
		} catch (Exception ex) {
			logger.debug("Error while reading upload file. ", ex);
		}

		return monoList;
	}

	private List<Mono<Boolean>> flatFileToDB(Connection conn, Storage storage, DataFileType fileType, FilePart filePart,
			IAppDataService dataService, Map<String, Set<SchemaType>> headers) {

		List<Mono<Boolean>> monoList = new ArrayList<>();

		try (DataFileReader reader = new DataFileReader(filePart, fileType)) {
			List<String> row;

			do {
				row = reader.readRow();
				if (row != null && !row.isEmpty()) {
					Map<String, Object> rowMap = new HashMap<>();
					for (int i = 0; i < reader.getHeaders()
							.size() && i < row.size(); i++) {
						if (StringUtil.safeIsBlank(row.get(i)))
							continue;
						MapUtil.setValueInMap(rowMap, reader.getHeaders()
								.get(i),
								getElementBySchemaType(headers.get(reader.getHeaders()
										.get(i)), row.get(i)));
					}
					monoList.add(dataService.create(conn, storage, new DataObject().setData(rowMap))
							.map(v -> true));
				}
			} while (row != null && !row.isEmpty());
		} catch (Exception ex) {
			logger.debug("Error while reading upload file. ", ex);
		}

		return monoList;
	}

	private static Object getElementBySchemaType(Set<SchemaType> schemaTypes, String value) {

		if (StringUtil.safeIsBlank(value) || "null".equalsIgnoreCase(value))
			return null;

		if (schemaTypes == null)
			return value;

		if (schemaTypes.contains(SchemaType.STRING))
			return value;

		if (schemaTypes.contains(SchemaType.BOOLEAN)) {

			try {
				return BooleanUtil.parse(value);
			} catch (ParseException pe) {
				return PrimitiveUtil.findPrimitiveNumberType(new JsonPrimitive(value))
						.getT2();
			}

		}

		return PrimitiveUtil.findPrimitiveNumberType(new JsonPrimitive(value))
				.getT2();
	}

	public Mono<Map<String, Object>> readVersion(String appCode, String clientCode, String storageName,
			String versionId) {

		Mono<Map<String, Object>> mono = FlatMapUtil.flatMapMonoWithNull(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),

				(ca, ac) -> Mono.just(clientCode == null ? ca.getUrlClientCode() : clientCode),

				(ca, ac, cc) -> connectionService.find(ac, cc, ConnectionType.APP_DATA),

				(ca, ac, cc, conn) -> Mono
						.just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

				(ca, ac, cc, conn, dataService) -> storageService.read(storageName, ac, cc)
						.map(ObjectWithUniqueID::getObject),

				(ca, ac, cc, conn, dataService, storage) -> this.genericOperation(storage,
						(cona, hasAccess) -> dataService.readVersion(conn, storage, versionId), Storage::getReadAuth,
						CoreMessageResourceService.FORBIDDEN_READ_STORAGE));

		return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.readVersion"));
	}

	public Mono<Page<Map<String, Object>>> readPageVersion(String appCode, String clientCode, String storageName,
			String versionId, Query query) {

		Mono<Page<Map<String, Object>>> mono = FlatMapUtil.flatMapMonoWithNull(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),

				(ca, ac) -> Mono.just(clientCode == null ? ca.getUrlClientCode() : clientCode),

				(ca, ac, cc) -> connectionService.find(ac, cc, ConnectionType.APP_DATA),

				(ca, ac, cc, conn) -> Mono
						.just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

				(ca, ac, cc, conn, dataService) -> storageService.read(storageName, ac, cc)
						.map(ObjectWithUniqueID::getObject),

				(ca, ac, cc, conn, dataService, storage) -> this.genericOperation(storage,
						(cona, hasAccess) -> dataService.readPageVersion(conn, storage, versionId, query),
						Storage::getReadAuth, CoreMessageResourceService.FORBIDDEN_READ_STORAGE));

		return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.readPageVersion"));
	}
}
