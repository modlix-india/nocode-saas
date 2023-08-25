package com.fincity.saas.core.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.array.ArraySchemaType;
import com.fincity.nocode.kirun.engine.json.schema.array.ArraySchemaType.ArraySchemaTypeAdapter;
import com.fincity.nocode.kirun.engine.json.schema.object.AdditionalType;
import com.fincity.nocode.kirun.engine.json.schema.object.AdditionalType.AdditionalTypeAdapter;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.nocode.kirun.engine.json.schema.type.Type.SchemaTypeAdapter;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.saas.core.document.Storage;
import com.fincity.saas.core.repository.StorageRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class StorageService extends AbstractOverridableDataService<Storage, StorageRepository> {

	public static final String CACHE_NAME_STORAGE_SCHEMA = "storageSchema";

	@Autowired
	private CoreMessageResourceService coreMsgService;

	protected StorageService() {
		super(Storage.class);
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

										return super.create(entity);
									});
						}

						return super.create(entity);
					}

			)
					.contextWrite(Context.of(LogUtil.METHOD_NAME, "StorageService.create"));
		}

		return super.create(entity);
	}

	@Override
	public Mono<Storage> update(Storage entity) {

		return super.update(entity).flatMap(uped -> this.cacheService.evict(CACHE_NAME_STORAGE_SCHEMA, uped.getId())
				.map(e -> uped));
	}

	@Override
	public Mono<Boolean> delete(String id) {

		return super.delete(id).flatMap(del -> this.cacheService.evict(CACHE_NAME_STORAGE_SCHEMA, id)
				.map(e -> del));
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
							.setDeleteAuth(entity.getDeleteAuth());

					existing.setVersion(existing.getVersion() + 1);

					return Mono.just(existing);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "StorageService.updatableEntity"));
	}

	public Mono<Schema> getSchema(Storage storage) {

		return cacheService.cacheEmptyValueOrGet(CACHE_NAME_STORAGE_SCHEMA, () -> {

			AdditionalTypeAdapter at = new AdditionalTypeAdapter();
			ArraySchemaTypeAdapter ast = new ArraySchemaTypeAdapter();
			Gson gson = new GsonBuilder().registerTypeAdapter(Type.class, new SchemaTypeAdapter())
					.registerTypeAdapter(AdditionalType.class, at)
					.registerTypeAdapter(ArraySchemaType.class, ast)
					.create();

			at.setGson(gson);
			ast.setGson(gson);

			return Mono.just(gson.fromJson(gson.toJsonTree(storage.getSchema()), Schema.class));
		}, storage.getId());
	}
}
