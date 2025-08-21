package com.fincity.saas.ui.service;

import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.dto.AbstractOverridableDTO;
import com.fincity.saas.commons.mongo.repository.IOverridableDataRepository;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;

import reactor.core.publisher.Mono;

// Currently this override is only used for those object which require eviction of the html cache
public abstract class AbstractUIOverridableDataService<D extends AbstractOverridableDTO<D>, R extends IOverridableDataRepository<D>>
        extends AbstractOverridableDataService<D, R> {

    @Autowired // NOSONAR
    private IndexHTMLCacheService indexHTMLCacheService;

    protected AbstractUIOverridableDataService(Class<D> pojoClass) {
        super(pojoClass);
    }

    @Override
    public Mono<D> update(D entity) {
        return super.update(entity)
                .flatMap(e -> this.indexHTMLCacheService.evict(e.getAppCode()).map(x -> e));
    }

    @Override
    public Mono<Boolean> delete(String id) {
        return FlatMapUtil.flatMapMono(
                () -> this.repo.findById(id),

                e -> super.delete(id),

                (e, deleted) -> this.indexHTMLCacheService.evict(e.getAppCode()).map(x -> deleted));
    }
}
