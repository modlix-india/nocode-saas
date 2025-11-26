package com.fincity.saas.ui.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.dto.AbstractOverridableDTO;
import com.fincity.saas.commons.mongo.repository.IOverridableDataRepository;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.util.LogUtil;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

// Currently this override is only used for those object which require eviction of the html cache
public abstract class AbstractUIOverridableDataService<D extends AbstractOverridableDTO<D>, R extends IOverridableDataRepository<D>>
        extends AbstractOverridableDataService<D, R> {

    private static final String ABSTRACT_UI_OVERRIDABLE_DATA_SERVICE = "AbstractUIOverridableDataService (";

    protected AbstractUIOverridableDataService(Class<D> pojoClass) {
        super(pojoClass);
    }

    @Override
    public Mono<D> update(D entity) {
        return super.update(entity)
                .flatMap(this.cacheService
                        .evictAllFunction(EngineService.CACHE_NAME_APPLICATION + "-" + entity.getAppCode()))
                .flatMap(this.cacheService
                        .evictAllFunction(
                                EngineService.CACHE_NAME_PAGE + "-" + entity.getAppCode()));
    }

    @Override

    public Mono<Boolean> delete(String id) {
        return FlatMapUtil.flatMapMono(
                () -> this.repo.findById(id),

                e -> super.delete(id),

                (e, deleted) -> this.cacheService
                        .evictAll(EngineService.CACHE_NAME_APPLICATION + "-" + e.getAppCode()),

                (e, deleted, pageCache) -> this.cacheService
                        .evictAll(EngineService.CACHE_NAME_PAGE + "-" + e.getAppCode()),

                (e, deleted, pageCache, appCache) -> Mono.just(deleted))
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        ABSTRACT_UI_OVERRIDABLE_DATA_SERVICE + this.getObjectName() + "Service).delete(" + id + ")"));
    }

    // public Mono<ObjectWithUniqueID<D>> read(String objectUniqueId, String name,
    // String appCode, String clientCode) {

    //
    //
    // }
    //
}
