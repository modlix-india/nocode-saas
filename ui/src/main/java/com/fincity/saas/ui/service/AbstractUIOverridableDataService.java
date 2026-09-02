package com.fincity.saas.ui.service;

import org.springframework.beans.factory.annotation.Autowired;

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

    @Autowired
    protected SSRCacheEvictionService ssrCacheEvictionService;

    protected AbstractUIOverridableDataService(Class<D> pojoClass) {
        super(pojoClass);
    }

    /**
     * Page, Application, Style and StyleTheme all support draft and publish. The
     * commons-core objects inherit the code but stay off until they are wanted.
     */
    @Override
    protected boolean isDraftable() {
        return true;
    }

    /**
     * The draft surface reads through the same Engine caches and the same SSR cache
     * as live, so a draft save has to clear them as well as the object's own
     * `_DRAFT` definition cache. Their surface dimension is in the cache KEY (the
     * `d-` marked uniqueId), not the name, so they can only be cleared whole. This
     * is the same set update() clears, and for the same reason: missing one leaves
     * the draft host answering with content from before the save.
     */
    @Override
    protected Mono<Boolean> evictDraft(String appCode, String clientCode, String name) {
        return super.evictDraft(appCode, clientCode, name)
                .flatMap(this.cacheService
                        .evictAllFunction(EngineService.CACHE_NAME_APPLICATION + "-" + appCode))
                .flatMap(this.cacheService
                        .evictAllFunction(EngineService.CACHE_NAME_PAGE + "-" + appCode))
                .flatMap(this.ssrCacheEvictionService.evictByAppCodeFunction(appCode));
    }

    @Override
    public Mono<D> update(D entity) {
        return super.update(entity)
                .flatMap(this.cacheService
                        .evictAllFunction(EngineService.CACHE_NAME_APPLICATION + "-" + entity.getAppCode()))
                .flatMap(this.cacheService
                        .evictAllFunction(
                                EngineService.CACHE_NAME_PAGE + "-" + entity.getAppCode()))
                .flatMap(this.ssrCacheEvictionService.evictByAppCodeFunction(entity.getAppCode()));
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

                (e, deleted, pageCache, appCache) -> this.ssrCacheEvictionService.evictByAppCode(e.getAppCode()),

                (e, deleted, pageCache, appCache, ssrEvicted) -> Mono.just(deleted))
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
