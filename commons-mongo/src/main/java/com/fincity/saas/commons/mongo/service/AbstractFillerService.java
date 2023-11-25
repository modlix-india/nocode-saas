package com.fincity.saas.commons.mongo.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.mongo.document.AbstractFiller;
import com.fincity.saas.commons.mongo.repository.IOverridableDataRepository;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.LogUtil;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public abstract class AbstractFillerService<D extends AbstractFiller<D>, R extends IOverridableDataRepository<D>>
		extends AbstractOverridableDataService<D, R> {

	@Autowired
	private CacheService cacheService;

	protected AbstractFillerService(Class<D> pojoClass) {
		super(pojoClass);
	}

	@Override
	public Mono<D> create(D entity) {

		entity.setName(entity.getAppCode());
		return super.create(entity).flatMap(e -> this.cacheService
				.evict(this.getCacheName(e.getAppCode(), e.getAppCode()), e.getClientCode()).map(x -> e));
	}

	@Override
	public Mono<ObjectWithUniqueID<D>> read(String name, String appCode, String clientCode) {

		return this.cacheService.cacheEmptyValueOrGet(this.getCacheName(appCode, appCode) + "_READ",
				() -> super.read(name, appCode, clientCode), clientCode);
	}

	@Override
	public Mono<D> update(D entity) {

		return super.update(entity).flatMap(e -> this.cacheService
				.evict(this.getCacheName(e.getAppCode(), e.getAppCode()), e.getClientCode()).map(x -> e));
	}

	@Override
	protected Mono<D> updatableEntity(D entity) {

		return flatMapMono(

				() -> this.read(entity.getId()),

				existing -> {
					if (existing.getVersion() != entity.getVersion())
						return this.messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
								AbstractMongoMessageResourceService.VERSION_MISMATCH);

					existing.setDefinition(entity.getDefinition());
					existing.setValues(entity.getValues());

					existing.setVersion(existing.getVersion() + 1)
							.setPermission(entity.getPermission());

					return Mono.just(existing);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFillerService.updatableEntity"));
	}

	@Override
	public Mono<Boolean> delete(String id) {

		return this.read(id).flatMap(e -> super.delete(id).flatMap(x -> this.cacheService
				.evict(this.getCacheName(e.getAppCode(), e.getAppCode()), e.getClientCode()).thenReturn(x)));
	}
}
