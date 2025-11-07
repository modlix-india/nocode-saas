package com.fincity.saas.entity.processor.service.rule;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.rule.BaseUserDistributionDAO;
import com.fincity.saas.entity.processor.dto.rule.BaseUserDistributionDto;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import com.fincity.saas.entity.processor.util.ReflectionUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public abstract class BaseUserDistributionService<
                R extends UpdatableRecord<R>,
                D extends BaseUserDistributionDto<D>,
                O extends BaseUserDistributionDAO<R, D>>
        extends BaseUpdatableService<R, D, O> {

    @Override
    protected boolean canOutsideCreate() {
        return Boolean.FALSE;
    }

    @Override
    protected Mono<D> updatableEntity(D entity) {
        return super.updatableEntity(entity).flatMap(existing -> {
            if (existing.getUserId() != null) existing.setUserId(entity.getUserId());
            else if (existing.getRoleId() != null) existing.setRoleId(entity.getRoleId());
            else if (existing.getProfileId() != null) existing.setProfileId(entity.getProfileId());
            else if (existing.getDesignationId() != null) existing.setDesignationId(entity.getDesignationId());

            return Mono.just(existing);
        });
    }

    public Mono<Class<D>> getPojoClass() {
        return this.dao.getPojoClass();
    }

    protected <T> Flux<T> throwFluxMissingParam(String paramName) {
        return this.msgService.throwFluxMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                ProcessorMessageResourceService.MISSING_PARAMETERS,
                paramName,
                this.getEntityName());
    }

    public Flux<D> createDistributions(
            ProcessorAccess access,
            ULong ruleId,
            List<ULong> userIds,
            List<ULong> roleIds,
            List<ULong> profileIds,
            List<ULong> designationIds) {

        if (ruleId == null) return this.throwFluxMissingParam(BaseUserDistributionDto.Fields.ruleId);

        List<DistributionItem<D>> items = new ArrayList<>();

        this.addDistributionItems(items, userIds, BaseUserDistributionDto::setUserId);
        this.addDistributionItems(items, roleIds, BaseUserDistributionDto::setRoleId);
        this.addDistributionItems(items, profileIds, BaseUserDistributionDto::setProfileId);
        this.addDistributionItems(items, designationIds, BaseUserDistributionDto::setDesignationId);

        if (items.isEmpty()) return this.throwFluxMissingParam("distributionIds");

        return this.getPojoClass()
                .flatMapMany(clazz -> this.createDistributionsInternal(access, ruleId, items, clazz))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BaseUserDistributionService.createDistributions"));
    }

    private Flux<D> createDistributionsInternal(
            ProcessorAccess access, ULong ruleId, List<DistributionItem<D>> items, Class<D> pojoClass) {

        List<D> dtos = new ArrayList<>(items.size());
        for (DistributionItem<D> item : items) {
            D dto = ReflectionUtil.getInstance(pojoClass);
            dto.setRuleId(ruleId);
            item.setter.accept(dto, item.id);
            dtos.add(dto);
        }

        return Flux.fromIterable(dtos).flatMap(dto -> super.create(access, dto));
    }

    private void addDistributionItems(List<DistributionItem<D>> items, List<ULong> ids, BiConsumer<D, ULong> setter) {
        if (ids != null && !ids.isEmpty())
            ids.stream().filter(Objects::nonNull).forEach(id -> items.add(new DistributionItem<>(id, setter)));
    }

    public Flux<D> createUserDistributions(ULong ruleId, List<ULong> userIds) {
        return this.createDistributionsByType(
                        ruleId, userIds, BaseUserDistributionDto.Fields.userId, BaseUserDistributionDto::setUserId)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BaseUserDistributionService.createUserDistributions"));
    }

    public Flux<D> createRoleDistributions(ULong ruleId, List<ULong> roleIds) {
        return this.createDistributionsByType(
                        ruleId, roleIds, BaseUserDistributionDto.Fields.roleId, BaseUserDistributionDto::setRoleId)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BaseUserDistributionService.createRoleDistributions"));
    }

    public Flux<D> createProfileDistributions(ULong ruleId, List<ULong> profileIds) {
        return this.createDistributionsByType(
                        ruleId,
                        profileIds,
                        BaseUserDistributionDto.Fields.profileId,
                        BaseUserDistributionDto::setProfileId)
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "BaseUserDistributionService.createProfileDistributions"));
    }

    public Flux<D> createDesignationDistributions(ULong ruleId, List<ULong> designationIds) {
        return this.createDistributionsByType(
                        ruleId,
                        designationIds,
                        BaseUserDistributionDto.Fields.designationId,
                        BaseUserDistributionDto::setDesignationId)
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "BaseUserDistributionService.createDesignationDistributions"));
    }

    private Flux<D> createDistributionsByType(
            ULong ruleId, List<ULong> ids, String fieldName, BiConsumer<D, ULong> setter) {

        if (ruleId == null) return this.throwFluxMissingParam(BaseUserDistributionDto.Fields.ruleId);

        if (ids == null || ids.isEmpty()) return this.throwFluxMissingParam(fieldName);

        List<DistributionItem<D>> items = new ArrayList<>();
        this.addDistributionItems(items, ids, setter);

        return Mono.zip(this.hasAccess(), this.getPojoClass())
                .flatMapMany(tuple -> this.createDistributionsInternal(tuple.getT1(), ruleId, items, tuple.getT2()));
    }

    public Mono<Integer> deleteByRuleId(ProcessorAccess access, ULong ruleId) {

        if (ruleId == null) return super.throwMissingParam(BaseUserDistributionDto.Fields.ruleId);

        return FlatMapUtil.flatMapMono(() -> this.dao.getUserDistributions(access, ruleId), userDistributions -> {
                    if (userDistributions == null || userDistributions.isEmpty()) return Mono.just(0);

                    return Flux.fromIterable(userDistributions)
                            .flatMap(dto -> super.deleteInternal(access, dto))
                            .count()
                            .map(Long::intValue);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BaseUserDistributionService.deleteByRuleId"));
    }

    private record DistributionItem<D>(ULong id, BiConsumer<D, ULong> setter) {}
}
