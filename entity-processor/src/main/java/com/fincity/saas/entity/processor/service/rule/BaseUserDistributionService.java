package com.fincity.saas.entity.processor.service.rule;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.functions.annotations.IgnoreGeneration;
import com.fincity.saas.commons.security.model.EntityProcessorUser;
import com.fincity.saas.commons.security.model.UsersListRequest;
import com.fincity.saas.commons.util.HashUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.rule.BaseUserDistributionDAO;
import com.fincity.saas.entity.processor.dto.rule.BaseUserDistributionDto;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@IgnoreGeneration
public abstract class BaseUserDistributionService<
                R extends UpdatableRecord<R>,
                D extends BaseUserDistributionDto<D>,
                O extends BaseUserDistributionDAO<R, D>>
        extends BaseUpdatableService<R, D, O> {

    private static final String USER_DISTRIBUTION = "userDistribution";
    private static final String USER_DISTRIBUTION_MAPS = "userDistributionMaps";

    @Override
    protected boolean canOutsideCreate() {
        return Boolean.FALSE;
    }

    @Override
    protected Mono<Boolean> evictCache(D entity) {
        return Mono.zip(
                super.evictCache(entity),
                this.evictRuleCache(entity),
                (baseEvicted, ruleEvicted) -> baseEvicted && ruleEvicted);
    }

    private String getUserDistributionCacheName(String appCode, String clientCode) {
        return super.getCacheName(USER_DISTRIBUTION, appCode, clientCode);
    }

    private String getUserDistributionCacheKey(ProcessorAccess access) {
        return super.getCacheKey(access.getAppCode(), access.getClientCode());
    }

    private String getUserDistributionMapCacheKey(ProcessorAccess access) {
        return super.getCacheKey(access.getAppCode(), access.getClientCode(), USER_DISTRIBUTION_MAPS);
    }

    public String getUserCacheKey(ProcessorAccess access, Object... keys) {
        return super.getCacheKey(
                access.getAppCode(),
                access.getClientCode(),
                access.getUserId(),
                access.getUser().getDesignationId(),
                HashUtil.sha256Hash(access.getUser().getAuthorities()),
                keys);
    }

    private Mono<Boolean> evictRuleCache(D entity) {
        return Mono.zip(
                        this.cacheService.evict(
                                this.getCacheName(),
                                super.getCacheKey(entity.getAppCode(), entity.getClientCode(), entity.getRuleId())),
                        this.cacheService.evictAll(
                                this.getUserDistributionCacheName(entity.getAppCode(), entity.getClientCode())),
                        (baseEvicted, ruleEvicted) -> baseEvicted && ruleEvicted)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BaseUserDistributionService.evictRuleCache"));
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

    private <T> Flux<T> throwFluxMissingParam(String paramName) {
        return this.msgService.throwFluxMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                ProcessorMessageResourceService.MISSING_PARAMETERS,
                paramName,
                this.getEntityName());
    }

    public Flux<D> updateDistributions(ProcessorAccess access, ULong ruleId, List<D> userDistributions) {

        return FlatMapUtil.flatMapFlux(
                        () -> this.deleteByRuleId(access, ruleId).flux(),
                        deleted -> this.createDistributions(access, ruleId, userDistributions))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BaseUserDistributionService.updateDistributions"));
    }

    public Flux<D> createDistributions(ProcessorAccess access, ULong ruleId, List<D> userDistributions) {

        if (ruleId == null) return this.throwFluxMissingParam(BaseUserDistributionDto.Fields.ruleId);

        for (D userDistribution : userDistributions) userDistribution.setRuleId(ruleId);

        return Flux.fromIterable(userDistributions)
                .flatMap(userDistribution -> super.create(access, userDistribution))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BaseUserDistributionService.createDistributions"));
    }

    public Mono<Integer> deleteByRuleId(ProcessorAccess access, ULong ruleId) {

        if (ruleId == null) return super.throwMissingParam(BaseUserDistributionDto.Fields.ruleId);

        return FlatMapUtil.flatMapMono(() -> this.dao.getUserDistributions(access, ruleId), userDistributions -> {
                    if (userDistributions == null || userDistributions.isEmpty()) return Mono.just(0);

                    return Flux.fromIterable(userDistributions)
                            .flatMap(entity -> super.deleteInternal(access, entity))
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
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BaseUserDistributionService.getUsersByRuleId"));
    }

    private Mono<List<EntityProcessorUser>> getAllUserForClient(ProcessorAccess access) {
        return super.cacheService.cacheValueOrGet(
                this.getUserDistributionCacheName(access.getAppCode(), access.getClientCode()),
                () -> super.securityService.getUsersForEntityProcessor(new UsersListRequest()
                        .setClientCode(access.getClientCode())
                        .setAppCode(access.getAppCode())),
                this.getUserDistributionCacheKey(access));
    }

    public Mono<EntityProcessorUser> getUserForClient(ProcessorAccess access) {
        return super.cacheService.cacheValueOrGet(
                this.getUserDistributionCacheName(access.getAppCode(), access.getClientCode()),
                () -> super.securityService.getUserForEntityProcessor(
                        access.getUserId().toBigInteger(),
                        new UsersListRequest()
                                .setClientCode(access.getClientCode())
                                .setAppCode(access.getAppCode())),
                this.getUserCacheKey(access));
    }

    private Mono<UserMaps> getAllUserMappings(ProcessorAccess access) {
        return super.cacheService.cacheValueOrGet(
                this.getUserDistributionCacheName(access.getAppCode(), access.getClientCode()),
                () -> this.getAllUserForClient(access).map(this::buildMaps),
                this.getUserDistributionMapCacheKey(access));
    }

    private UserMaps buildMaps(List<EntityProcessorUser> users) {

        Map<ULong, Set<ULong>> roles = new HashMap<>();
        Map<ULong, Set<ULong>> profiles = new HashMap<>();
        Map<ULong, Set<ULong>> designations = new HashMap<>();
        Map<ULong, Set<ULong>> departments = new HashMap<>();

        if (users == null || users.isEmpty()) return new UserMaps(roles, profiles, designations, departments);

        for (EntityProcessorUser u : users) {
            ULong userId = ULong.valueOf(u.getId());
            this.addToMap(roles, u.getRoleId(), userId);
            this.addToMap(designations, u.getDesignationId(), userId);
            this.addToMap(departments, u.getDepartmentId(), userId);
            this.addListToMap(profiles, u.getProfileIds(), userId);
        }

        return new UserMaps(roles, profiles, designations, departments);
    }

    private void addToMap(Map<ULong, Set<ULong>> map, Long key, ULong userId) {
        if (key != null)
            map.computeIfAbsent(ULong.valueOf(key), k -> new HashSet<>()).add(userId);
    }

    private void addListToMap(Map<ULong, Set<ULong>> map, Set<Long> keys, ULong userId) {
        if (keys == null) return;
        keys.stream().filter(Objects::nonNull).forEach(key -> this.addToMap(map, key, userId));
    }

    public Mono<List<D>> getUserDistributions(ProcessorAccess access, ULong ruleId) {
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
}
