package com.fincity.saas.core.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.array.ArraySchemaType;
import com.fincity.nocode.kirun.engine.json.schema.array.ArraySchemaType.ArraySchemaTypeAdapter;
import com.fincity.nocode.kirun.engine.json.schema.object.AdditionalType;
import com.fincity.nocode.kirun.engine.json.schema.object.AdditionalType.AdditionalTypeAdapter;
import com.fincity.nocode.kirun.engine.json.schema.reactive.ReactiveSchemaUtil;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.nocode.kirun.engine.json.schema.type.Type.SchemaTypeAdapter;
import com.fincity.nocode.kirun.engine.reactive.ReactiveHybridRepository;
import com.fincity.nocode.kirun.engine.repository.reactive.KIRunReactiveSchemaRepository;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.gson.LocalDateTimeAdapter;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.saas.core.document.Storage;
import com.fincity.saas.core.enums.StorageTriggerType;
import com.fincity.saas.core.kirun.repository.CoreSchemaRepository;
import com.fincity.saas.core.model.StorageRelation;
import com.fincity.saas.core.repository.StorageRepository;
import com.fincity.saas.core.service.connection.appdata.AppDataService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.netflix.discovery.converters.Auto;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class StorageService extends AbstractOverridableDataService<Storage, StorageRepository> {

	public static final String CACHE_NAME_STORAGE_SCHEMA = "storageSchema";

	@Autowired
	private CoreMessageResourceService coreMsgService;

	@Autowired
	private CoreSchemaService coreSchemaService;

	@Autowired
	private CoreFunctionService coreFunctionService;

	private Gson gson;

	protected StorageService() {
		super(Storage.class);

		AdditionalTypeAdapter at = new AdditionalTypeAdapter();
		ArraySchemaTypeAdapter ast = new ArraySchemaTypeAdapter();

		this.gson = new GsonBuilder().registerTypeAdapter(Type.class, new SchemaTypeAdapter())
				.registerTypeAdapter(AdditionalType.class, at)
				.registerTypeAdapter(ArraySchemaType.class, ast)
				.registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
				.create();

		at.setGson(this.gson);
		ast.setGson(this.gson);
	}

	@Override
	public Mono<Storage> create(Storage entity) {

		entity.setUniqueName(UniqueUtil.uniqueName(32, entity.getAppCode(), entity.getClientCode(), entity.getName()));

		if (entity.getBaseClientCode() != null) {

			return FlatMapUtil.flatMapMono(

					SecurityContextUtil::getUsersContextAuthentication,

					ca -> this.getMergedSources(entity),

					(ca, merged) -> {

						if (BooleanUtil.safeValueOf(merged.getIsAppLevel())) {

							return this.securityService.hasWriteAccess(entity.getAppCode(), entity.getClientCode())
									.flatMap(access -> {

										if (!access.booleanValue())
											return coreMsgService.throwMessage(
													msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
													CoreMessageResourceService.STORAGE_IS_APP_LEVEL);

										return this.localCreate(entity);
									});
						}

						return this.localCreate(entity);
					}

			)
					.contextWrite(Context.of(LogUtil.METHOD_NAME, "StorageService.create"));
		}

		return this.localCreate(entity);
	}

	private Mono<Storage> localCreate(Storage entity) {

		return FlatMapUtil.flatMapMono(

				() -> this.validate(entity),

				super::create)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "StorageService.localCreate"));
	}

	public Mono<Storage> validate(Storage storage) {

		return FlatMapUtil.flatMapMono(
				() -> {

					Schema schema = gson.fromJson(gson.toJsonTree(storage.getSchema()), Schema.class);

					if (schema.getRef() == null)
						return Mono.just(schema);

					return ReactiveSchemaUtil.getSchemaFromRef(schema,
							new ReactiveHybridRepository<>(new KIRunReactiveSchemaRepository(),
									new CoreSchemaRepository(), this.coreSchemaService
											.getSchemaRepository(storage.getAppCode(), storage.getClientCode())),
							schema.getRef()).defaultIfEmpty(schema);
				},

				schema -> {

					if (schema.getType().getAllowedSchemaTypes().size() != 1
							|| !schema.getType().getAllowedSchemaTypes().contains(SchemaType.OBJECT)) {

						return this.messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
								CoreMessageResourceService.STORAGE_SCHEMA_ALWAYS_OBJECT);
					}

					if (storage.getRelations() == null || storage.getRelations().isEmpty())
						return Mono.just(storage);

					for (StorageRelation relation : storage.getRelations().values()) {

						if (schema.getProperties().containsKey(relation.getFieldName())) {

							return this.messageResourceService.throwMessage(
									msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
									CoreMessageResourceService.STORAGE_SCHEMA_FIELD_ALREADY_EXISTS,
									relation.getFieldName());
						}

						if (relation.getUniqueRelationId() != null)
							continue;

						relation.setUniqueRelationId(
								UniqueUtil.uniqueName(32, storage.getAppCode(), storage.getClientCode(),
										storage.getName(), relation.getFieldName()));
					}

					return Flux.fromIterable(storage.getRelations().values())
							.flatMap(relation -> this.read(relation.getStorageName(),
									storage.getAppCode(), storage.getClientCode()).switchIfEmpty(
											this.messageResourceService.throwMessage(
													msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
													CoreMessageResourceService.NO_STORAGE_FOUND_WITH_NAME,
													relation.getStorageName())))
							.collectList().map(e -> storage);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "StorageService.validate"));
	}

	@Override
	public Mono<Storage> update(Storage entity) {

		return FlatMapUtil.flatMapMono(

				() -> this.validate(entity),

				storage -> this.read(entity.getId()),

				(storage, existing) -> {

					if (existing.getTriggers() == null
							|| existing.getTriggers().get(StorageTriggerType.BEFORE_UPDATE_STORAGE) == null
							|| existing.getTriggers().get(StorageTriggerType.BEFORE_UPDATE_STORAGE).isEmpty())
						return Mono.just(true);

					Map<String, JsonElement> args = Map.of("existing", gson.toJsonTree(existing), "creating",
							gson.toJsonTree(storage));

					return Flux.fromIterable(existing.getTriggers().get((StorageTriggerType.BEFORE_UPDATE_STORAGE)))
							.flatMap(trigger -> this.coreFunctionService.execute(
									trigger.substring(0, trigger.lastIndexOf('.')),
									trigger.substring(trigger.lastIndexOf('.') + 1), entity.getAppCode(),
									entity.getClientCode(),
									args, null))
							.collectList().map(e -> true);

				},

				(storage, existing, beforeExecuted) -> super.update(storage),

				(storage, existing, beforeExecuted, created) -> {

					if (existing.getTriggers() == null
							|| existing.getTriggers().get(StorageTriggerType.AFTER_UPDATE_STORAGE) == null
							|| existing.getTriggers().get(StorageTriggerType.AFTER_UPDATE_STORAGE).isEmpty())
						return Mono.just(true);

					Map<String, JsonElement> args = Map.of("existing", gson.toJsonTree(existing), "created",
							gson.toJsonTree(created));

					return Flux.fromIterable(existing.getTriggers().get((StorageTriggerType.AFTER_UPDATE_STORAGE)))
							.flatMap(trigger -> this.coreFunctionService.execute(
									trigger.substring(0, trigger.lastIndexOf('.')),
									trigger.substring(trigger.lastIndexOf('.') + 1), entity.getAppCode(),
									entity.getClientCode(),
									args, null))
							.collectList().map(e -> true);
				},

				(storage, existing, beforeExecuted, created, afterExecuted) -> this.cacheService
						.evict(CACHE_NAME_STORAGE_SCHEMA, created.getId())
						.map(e -> created).map(Storage.class::cast))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "StorageService.update"));
	}

	@Override
	public Mono<Boolean> delete(String id) {

		return FlatMapUtil.flatMapMono(

				() -> this.read(id),

				storage -> {

					if (storage.getTriggers() == null
							|| storage.getTriggers().get(StorageTriggerType.BEFORE_DELETE_STORAGE) == null
							|| storage.getTriggers().get(StorageTriggerType.BEFORE_DELETE_STORAGE).isEmpty())
						return Mono.just(true);

					Map<String, JsonElement> args = Map.of("storage", gson.toJsonTree(storage));

					return Flux.fromIterable(storage.getTriggers().get((StorageTriggerType.BEFORE_DELETE_STORAGE)))
							.flatMap(trigger -> this.coreFunctionService.execute(
									trigger.substring(0, trigger.lastIndexOf('.')),
									trigger.substring(trigger.lastIndexOf('.') + 1), storage.getAppCode(),
									storage.getClientCode(),
									args, null))
							.collectList().map(e -> true);

				},

				(storage, beforeExecuted) -> super.delete(id),

				(storage, beforeExecuted, deleted) -> {

					if (storage.getTriggers() == null
							|| storage.getTriggers().get(StorageTriggerType.AFTER_DELETE_STORAGE) == null
							|| storage.getTriggers().get(StorageTriggerType.AFTER_DELETE_STORAGE).isEmpty())
						return Mono.just(true);

					Map<String, JsonElement> args = Map.of("storage", gson.toJsonTree(storage));

					return Flux.fromIterable(storage.getTriggers().get((StorageTriggerType.AFTER_DELETE_STORAGE)))
							.flatMap(trigger -> this.coreFunctionService.execute(
									trigger.substring(0, trigger.lastIndexOf('.')),
									trigger.substring(trigger.lastIndexOf('.') + 1), storage.getAppCode(),
									storage.getClientCode(),
									args, null))
							.collectList().map(e -> true);
				},

				(storage, beforeExecuted, deleted, afterExecuted) -> this.cacheService
						.evict(CACHE_NAME_STORAGE_SCHEMA, id)
						.map(e -> deleted))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "StorageService.delete"));
	}

	@Override
	protected Mono<Storage> updatableEntity(Storage entity) {
		return flatMapMono(

				() -> this.read(entity.getId()),

				existing -> {
					if (existing.getVersion() != entity.getVersion())
						return this.messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
								AbstractMongoMessageResourceService.VERSION_MISMATCH);

					existing.setSchema(entity.getSchema())
							.setIsAudited(entity.getIsAudited())
							.setIsVersioned(entity.getIsVersioned())
							.setIsAppLevel(entity.getIsAppLevel())
							.setCreateAuth(entity.getCreateAuth())
							.setReadAuth(entity.getReadAuth())
							.setUpdateAuth(entity.getUpdateAuth())
							.setDeleteAuth(entity.getDeleteAuth())
							.setGenerateEvents(entity.getGenerateEvents())
							.setTriggers(entity.getTriggers())
							.setRelations(entity.getRelations());

					existing.setVersion(existing.getVersion() + 1);

					return this.validate(existing);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "StorageService.updatableEntity"));
	}

	public Mono<Schema> getSchema(Storage storage) {

		return cacheService.cacheEmptyValueOrGet(CACHE_NAME_STORAGE_SCHEMA,
				() -> Mono.just(gson.fromJson(gson.toJsonTree(storage.getSchema()), Schema.class)), storage.getId());
	}
}
