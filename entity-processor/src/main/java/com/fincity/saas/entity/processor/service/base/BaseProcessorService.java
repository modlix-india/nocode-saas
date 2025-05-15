package com.fincity.saas.entity.processor.service.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.entity.processor.dao.base.BaseProcessorDAO;
import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import java.util.Map;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;

@Service
public abstract class BaseProcessorService<
                R extends UpdatableRecord<R>, D extends BaseProcessorDto<D>, O extends BaseProcessorDAO<R, D>>
        extends BaseService<R, D, O> {

    protected abstract Mono<D> checkEntity(D entity, Tuple3<String, String, ULong> accessInfo);

    @Override
    protected Mono<D> updatableEntity(D entity) {
        return FlatMapUtil.flatMapMono(() -> super.updatableEntity(entity), existing -> {
            if (existing.getVersion() != entity.getVersion())
                return this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                        ProcessorMessageResourceService.VERSION_MISMATCH);

            existing.setCurrentUserId(entity.getCurrentUserId());

            existing.setVersion(existing.getVersion() + 1);
            return Mono.just(existing);
        });
    }

    @Override
    public Mono<D> create(D entity) {
        return super.hasAccess().flatMap(hasAccess -> this.createInternal(entity, hasAccess));
    }

    public Mono<D> createPublic(D entity) {
        return super.hasPublicAccess().flatMap(hasAccess -> this.createInternal(entity, hasAccess));
    }

    protected Mono<D> createInternal(D entity, Tuple2<Tuple3<String, String, ULong>, Boolean> hasAccess) {
        return FlatMapUtil.flatMapMono(() -> this.checkEntity(entity, hasAccess.getT1()), cEntity -> {
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
                super::hasAccess,
                hasAccess -> this.checkEntity(entity, hasAccess.getT1()),
                (hasAccess, cEntity) -> this.updateInternal(cEntity));
    }

    public Mono<D> updateInternal(D entity) {
        return super.update(entity).flatMap(updated -> this.evictCache(entity).map(evicted -> updated));
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
