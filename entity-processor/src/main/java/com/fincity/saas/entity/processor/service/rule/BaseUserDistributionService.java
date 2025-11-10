package com.fincity.saas.entity.processor.service.rule;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.model.EntityProcessorUser;
import com.fincity.saas.commons.security.model.UsersListRequest;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.rule.BaseUserDistributionDAO;
import com.fincity.saas.entity.processor.dto.rule.BaseUserDistributionDto;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import com.fincity.saas.entity.processor.util.ReflectionUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    private static final String USER_DISTRIBUTION = "userDistribution";

    @Override
    protected boolean canOutsideCreate() {
        return Boolean.FALSE;
    }

    @Override
    protected Mono<Boolean> evictCache(D entity) {
        return Mono.zip(
                super.evictCache(entity),
                this.cacheService.evict(
                        this.getCacheName(),
                        super.getCacheKey(entity.getAppCode(), entity.getClientCode(), entity.getRuleId())),
                (baseEvicted, ruleEvicted) -> baseEvicted && ruleEvicted);
    }

    private String getUserDistributionCacheName(String appCode, String clientCode) {
        return super.getCacheKey(appCode, clientCode, USER_DISTRIBUTION);
    }

    @Override
    protected Mono<D> updatableEntity(D entity) {
        return super.updatableEntity(entity).flatMap(existing -> {
            if (existing.getUserId() != null) existing.setUserId(entity.getUserId());
            else if (existing.getRoleId() != null) existing.setRoleId(entity.getRoleId());
            else if (existing.getProfileId() != null) existing.setProfileId(entity.getProfileId());
            else if (existing.getDesignationId() != null) existing.setDesignationId(entity.getDesignationId());
            else if (existing.getDepartmentId() != null) existing.setDepartmentId(entity.getDepartmentId());

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
            List<ULong> designationIds,
            List<ULong> departmentIds) {

        if (ruleId == null) return this.throwFluxMissingParam(BaseUserDistributionDto.Fields.ruleId);

        List<DistributionItem<D>> items = new ArrayList<>();

        this.addDistributionItems(items, userIds, BaseUserDistributionDto::setUserId);
        this.addDistributionItems(items, roleIds, BaseUserDistributionDto::setRoleId);
        this.addDistributionItems(items, profileIds, BaseUserDistributionDto::setProfileId);
        this.addDistributionItems(items, designationIds, BaseUserDistributionDto::setDesignationId);
        this.addDistributionItems(items, departmentIds, BaseUserDistributionDto::setDepartmentId);

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

    public Mono<Set<ULong>> getUsersByRuleId(ProcessorAccess access, ULong ruleId) {
        if (ruleId == null) return super.throwMissingParam(BaseUserDistributionDto.Fields.ruleId);

        return Mono.zip(this.getAllUserMappings(access), this.getUserDistributions(access, ruleId))
                .map(tuple -> {
                    UserMaps maps = tuple.getT1();
                    List<D> userDistributions = tuple.getT2();

                    Set<ULong> userIds = new HashSet<>();

                    for (BaseUserDistributionDto<D> dto : userDistributions) {

                        userIds.add(dto.getUserId());

                        userIds.addAll(Objects.requireNonNullElse(maps.roleMap().get(dto.getRoleId()), Set.of()));
                        userIds.addAll(
                                Objects.requireNonNullElse(maps.profileMap().get(dto.getProfileId()), Set.of()));
                        userIds.addAll(
                                Objects.requireNonNullElse(maps.desigMap().get(dto.getDesignationId()), Set.of()));
                        userIds.addAll(Objects.requireNonNullElse(maps.deptMap().get(dto.getDepartmentId()), Set.of()));
                    }

                    userIds.remove(null);
                    return userIds;
                });
    }

    public Mono<List<EntityProcessorUser>> getAllUserForClient(ProcessorAccess access) {
        return super.cacheService.cacheValueOrGet(
                this.getUserDistributionCacheName(access.getAppCode(), access.getClientCode()),
                () -> super.securityService.getUsersForEntityProcessor(new UsersListRequest()
                        .setClientCode(access.getClientCode())
                        .setAppCode(access.getAppCode())),
                super.getCacheKey(access.getAppCode(), access.getClientCode()));
    }

    private Mono<UserMaps> getAllUserMappings(ProcessorAccess access) {
        return super.cacheService.cacheValueOrGet(
                this.getUserDistributionCacheName(access.getAppCode(), access.getClientCode()),
                () -> this.getAllUserForClient(access).map(this::buildMaps),
                super.getCacheKey(access.getAppCode(), access.getClientCode(), "USER_MAPS"));
    }

    private UserMaps buildMaps(List<EntityProcessorUser> users) {

        Map<ULong, Set<ULong>> role = new HashMap<>();
        Map<ULong, Set<ULong>> profile = new HashMap<>();
        Map<ULong, Set<ULong>> desig = new HashMap<>();
        Map<ULong, Set<ULong>> dept = new HashMap<>();

        if (users == null || users.isEmpty()) return new UserMaps(role, profile, desig, dept);

        for (EntityProcessorUser u : users) {
            ULong userId = ULong.valueOf(u.getId());
            this.addToMap(role, u.getRoleId(), userId);
	        this.addToMap(desig, u.getDesignationId(), userId);
	        this.addToMap(dept, u.getDepartmentId(), userId);
	        this.addListToMap(profile, u.getProfileIds(), userId);
        }

        return new UserMaps(role, profile, desig, dept);
    }

    private void addToMap(Map<ULong, Set<ULong>> map, Long key, ULong userId) {
        if (key != null)
            map.computeIfAbsent(ULong.valueOf(key), k -> new HashSet<>()).add(userId);
    }

    private void addListToMap(Map<ULong, Set<ULong>> map, Set<Long> keys, ULong userId) {
        if (keys == null) return;
        for (Long key : keys) {
            if (key != null)
                map.computeIfAbsent(ULong.valueOf(key), k -> new HashSet<>()).add(userId);
        }
    }

    private Mono<List<D>> getUserDistributions(ProcessorAccess access, ULong ruleId) {
        return super.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao.getUserDistributions(access, ruleId),
                super.getCacheKey(access.getAppCode(), access.getClientCode(), ruleId));
    }

    private record UserMaps(
            Map<ULong, Set<ULong>> roleMap,
            Map<ULong, Set<ULong>> profileMap,
            Map<ULong, Set<ULong>> desigMap,
            Map<ULong, Set<ULong>> deptMap) {}

    private record DistributionItem<D>(ULong id, BiConsumer<D, ULong> setter) {}
}
