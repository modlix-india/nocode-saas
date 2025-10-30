package com.fincity.saas.entity.processor.service.form;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.form.BaseWalkInFromDAO;
import com.fincity.saas.entity.processor.dto.form.BaseWalkInFormDto;
import com.fincity.saas.entity.processor.enums.AssignmentType;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.form.WalkInFormRequest;
import com.fincity.saas.entity.processor.model.response.WalkInFormResponse;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.StageService;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;

public abstract class BaseWalkInFormService<
                R extends UpdatableRecord<R>, D extends BaseWalkInFormDto<D>, O extends BaseWalkInFromDAO<R, D>>
        extends BaseUpdatableService<R, D, O> {

    protected StageService stageService;

    @Autowired
    private void setStageService(StageService stageService) {
        this.stageService = stageService;
    }

    @Override
    protected boolean canOutsideCreate() {
        return Boolean.FALSE;
    }

    @Override
    protected Mono<Boolean> evictCache(D entity) {
        return Mono.zip(
                super.evictCache(entity),
                super.cacheService.evict(
                        this.getCacheName(),
                        super.getCacheKey(entity.getAppCode(), entity.getClientCode(), entity.getProductId())),
                (baseEvicted, pEvicted) -> baseEvicted && pEvicted);
    }

    @Override
    protected Mono<D> updatableEntity(D entity) {
        return super.updatableEntity(entity)
                .flatMap(existing -> {
                    existing.setAssignmentType(entity.getAssignmentType());
                    existing.setStageId(entity.getStageId());
                    existing.setStatusId(entity.getStatusId());
                    return Mono.just(existing);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, this.getClass().getSimpleName() + ".updatableEntity"));
    }

    protected abstract String getProductEntityName();

    protected abstract Mono<Tuple2<ULong, ULong>> resolveProduct(ProcessorAccess access, Identity productId);

    protected abstract D create(
            String name, ULong entityId, ULong stageId, ULong statusId, AssignmentType assignmentType);

    protected abstract Mono<D> attachEntity(ProcessorAccess access, ULong productId, D entity);

    protected Mono<D> createOrUpdate(
            ProcessorAccess access,
            String name,
            ULong productId,
            ULong stageId,
            ULong statusId,
            AssignmentType assignmentType) {
        return this.dao
                .getByProductId(access, productId)
                .flatMap(existing -> super.update(access, existing.update(name, stageId, statusId, assignmentType)))
                .switchIfEmpty(super.create(access, this.create(name, productId, stageId, statusId, assignmentType)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, this.getClass().getSimpleName() + ".createOrUpdate"));
    }

    public Mono<D> create(WalkInFormRequest walkInFormRequest) {
        if (walkInFormRequest.getProductId() == null)
            return msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING,
                    this.getProductEntityName());

        if (walkInFormRequest.getStageId() == null)
            return msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING,
                    stageService.getEntityDisplayName());

        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> this.resolveProduct(access, walkInFormRequest.getProductId()),
                        (access, product) -> stageService
                                .getParentChild(
                                        access,
                                        product.getT2(),
                                        walkInFormRequest.getStageId(),
                                        walkInFormRequest.getStatusId())
                                .switchIfEmpty(this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        ProcessorMessageResourceService.STAGE_MISSING)),
                        (access, product, stageStatusEntity) -> this.createOrUpdate(
                                access,
                                walkInFormRequest.getName(),
                                product.getT1(),
                                stageStatusEntity.getKey().getId(),
                                stageStatusEntity.getValue().isEmpty()
                                        ? null
                                        : stageStatusEntity
                                                .getValue()
                                                .getFirst()
                                                .getId(),
                                walkInFormRequest.getAssignmentType()),
                        (access, product, stageStatusEntity, created) ->
                                this.attachEntity(access, product.getT1(), created))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, this.getClass().getSimpleName() + ".create"));
    }

    public Mono<D> getWalkInForm(Identity productId) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.resolveProduct(access, productId),
                        (access, product) -> this.getWalkInFormInternal(access, product.getT1()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, this.getClass().getSimpleName() + ".getWalkInForm"));
    }

    public Mono<WalkInFormResponse> getWalkInFormResponse(ProcessorAccess access, ULong productId) {
        return this.getWalkInFormInternal(access, productId)
                .map(walkInForm -> new WalkInFormResponse()
                        .setProductId(walkInForm.getProductId())
                        .setStageId(walkInForm.getStageId())
                        .setStatusId(walkInForm.getStatusId())
                        .setAssignmentType(walkInForm.getAssignmentType())
                        .setActive(walkInForm.isActive()))
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, this.getClass().getSimpleName() + ".getWalkInFormResponse"));
    }

    private Mono<D> getWalkInFormInternal(ProcessorAccess access, ULong productId) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao.getByProductId(access, productId),
                super.getCacheKey(access.getAppCode(), access.getClientCode(), productId));
    }
}
