package com.fincity.saas.entity.processor.service.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.entity.processor.dao.base.BaseProcessorDAO;
import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.functions.annotations.IgnoreGeneration;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import org.jooq.UpdatableRecord;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class BaseProcessorService<
                R extends UpdatableRecord<R>, D extends BaseProcessorDto<D>, O extends BaseProcessorDAO<R, D>>
        extends BaseUpdatableService<R, D, O> {

    @Override
    protected Mono<D> updatableEntity(D entity) {
        return FlatMapUtil.flatMapMono(() -> super.updatableEntity(entity), existing -> {
            if (existing.getVersion() != entity.getVersion())
                return this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                        ProcessorMessageResourceService.VERSION_MISMATCH);

            existing.setVersion(existing.getVersion() + 1);
            return Mono.just(existing);
        });
    }

    @Override
    @IgnoreGeneration
    public Mono<D> create(ProcessorAccess access, D entity) {
        return FlatMapUtil.flatMapMono(
                () -> access.isOutsideUser()
                        ? Mono.just(entity.setClientId(
                                ULongUtil.valueOf(access.getUser().getClientId())))
                        : Mono.just(entity),
                uEntity -> super.create(access, uEntity));
    }

    protected <T> Mono<T> throwDuplicateError(ProcessorAccess access, D existing) {

        if (access.isOutsideUser())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.DUPLICATE_ENTITY_OUTSIDE_USER,
                    this.getEntityPrefix(access.getAppCode()));

        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                ProcessorMessageResourceService.DUPLICATE_ENTITY,
                this.getEntityPrefix(access.getAppCode()),
                existing.getId(),
                this.getEntityPrefix(access.getAppCode()));
    }

    protected Flux<D> updateAll(ProcessorAccess access, Flux<D> entities) {
        return entities.flatMap(entity -> super.update(access, entity));
    }
}
