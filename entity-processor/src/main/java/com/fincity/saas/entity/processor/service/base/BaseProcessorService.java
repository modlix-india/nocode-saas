package com.fincity.saas.entity.processor.service.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.entity.processor.dao.base.BaseProcessorDAO;
import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import java.util.Map;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public abstract class BaseProcessorService<
                R extends UpdatableRecord<R>, D extends BaseProcessorDto<D>, O extends BaseProcessorDAO<R, D>>
        extends BaseService<R, D, O> {

    protected abstract Mono<D> checkEntity(D entity);

    @Override
    protected Mono<Boolean> evictCache(D entity) {
        return super.evictCache(entity);
    }

    @Override
    protected Mono<D> updatableEntity(D entity) {
        return FlatMapUtil.flatMapMono(() -> super.updatableEntity(entity), e -> {
            if (e.getVersion() != entity.getVersion())
                return this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                        AbstractMongoMessageResourceService.VERSION_MISMATCH);

            e.setCurrentUserId(entity.getCurrentUserId());

            e.setVersion(e.getVersion() + 1);

            return Mono.just(e);
        });
    }

    @Override
    public Mono<D> create(D entity) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess, hasAccess -> this.checkEntity(entity), (hasAccess, cEntity) -> {
                    cEntity.setAppCode(hasAccess.getT1().getT1());
                    cEntity.setClientCode(hasAccess.getT1().getT2());

                    if (cEntity.getAddedByUserId() == null)
                        cEntity.setAddedByUserId(hasAccess.getT1().getT3());

                    if (cEntity.getCurrentUserId() == null)
                        cEntity.setCurrentUserId(hasAccess.getT1().getT3());

                    return super.create(cEntity);
                });
    }

    @Override
    public Mono<D> update(ULong key, Map<String, Object> fields) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                hasAccess -> key != null ? this.read(key) : Mono.empty(),
                (hasAccess, entity) -> super.update(key, fields),
                (hasAccess, entity, updated) ->
                        this.evictCache(entity).map(evicted -> updated).switchIfEmpty(Mono.just(updated)));
    }

    @Override
    public Mono<D> update(D entity) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess, hasAccess -> super.update(entity), (hasAccess, updated) -> this.evictCache(entity)
                        .map(evicted -> updated));
    }

    @Override
    public Mono<Integer> delete(ULong id) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                hasAccess -> this.read(id),
                (hasAccess, entity) -> super.delete(entity.getId()),
                (ca, entity, deleted) -> this.evictCache(entity).map(evicted -> deleted));
    }
}
