package com.fincity.saas.entity.processor.service.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.entity.processor.dao.base.BaseValueDAO;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.dto.base.BaseValueDto;
import com.fincity.saas.entity.processor.enums.Platform;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.response.BaseValueResponse;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.ProductTemplateService;
import com.fincity.saas.entity.processor.util.NameUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

@Service
public abstract class BaseValueService<
                R extends UpdatableRecord<R>, D extends BaseValueDto<D>, O extends BaseValueDAO<R, D>>
        extends BaseUpdatableService<R, D, O> {

    private static final String VALUE_ET_KEY = "valueEtKey";
    protected ProductTemplateService productTemplateService;

    public String getValueEtKey() {
        return VALUE_ET_KEY;
    }

    public abstract Mono<D> applyOrder(D entity, ProcessorAccess access);

    @Autowired
    private void setValueTemplateService(ProductTemplateService productTemplateService) {
        this.productTemplateService = productTemplateService;
    }

    @Override
    protected Mono<Boolean> evictCache(D entity) {
        return Mono.zip(super.evictCache(entity), this.evictEtCache(entity), this.evictMapCache(entity))
                .map(evicted -> evicted.getT1() && evicted.getT2() && evicted.getT3());
    }

    private Mono<Boolean> evictEtCache(D entity) {
        return Mono.zip(
                super.cacheService.evict(
                        getCacheName(),
                        super.getCacheKey(
                                this.getValueEtKey(),
                                entity.getAppCode(),
                                entity.getClientCode(),
                                entity.getPlatform(),
                                entity.getProductTemplateId())),
                super.cacheService.evict(
                        getCacheName(),
                        super.getCacheKey(
                                this.getValueEtKey(),
                                entity.getAppCode(),
                                entity.getClientCode(),
                                entity.getProductTemplateId())),
                (pEvicted, evicted) -> pEvicted && evicted);
    }

    private Mono<Boolean> evictMapCache(D entity) {
        return Mono.zip(
                super.cacheService.evict(
                        getCacheName(),
                        super.getCacheKey(
                                entity.getAppCode(),
                                entity.getClientCode(),
                                entity.getPlatform(),
                                entity.getProductTemplateId())),
                super.cacheService.evict(
                        getCacheName(),
                        super.getCacheKey(entity.getAppCode(), entity.getClientCode(), entity.getProductTemplateId())),
                (pEvicted, evicted) -> pEvicted && evicted);
    }

    @Override
    protected Mono<D> updatableEntity(D entity) {
        return FlatMapUtil.flatMapMono(() -> super.updatableEntity(entity), existing -> {
            existing.setIsParent(entity.getParentLevel0() == null && entity.getParentLevel1() == null);

            if (Boolean.FALSE.equals(existing.getIsParent())) {
                if ((entity.getParentLevel0() != null
                                && entity.getParentLevel0().equals(existing.getId()))
                        || (entity.getParentLevel1() != null
                                && entity.getParentLevel1().equals(existing.getId()))) return Mono.just(existing);

                existing.setParentLevel0(entity.getParentLevel0());
                existing.setParentLevel1(entity.getParentLevel1());
            }

            return Mono.just(existing);
        });
    }

    private Mono<D> validateEntity(D entity, ProcessorAccess access) {
        return FlatMapUtil.flatMapMono(
                () -> Mono.zip(
                        this.existsByName(
                                access.getAppCode(),
                                access.getClientCode(),
                                entity.getPlatform(),
                                entity.getProductTemplateId(),
                                entity.getId(),
                                entity.getName()),
                        entity.hasParentLevels()
                                ? this.existsById(
                                        access.getAppCode(),
                                        access.getClientCode(),
                                        entity.getPlatform(),
                                        entity.getProductTemplateId(),
                                        entity.getParentLevel0(),
                                        entity.getParentLevel1())
                                : Mono.just(Boolean.TRUE)),
                exists -> {
                    if (exists.getT2())
                        return this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                                ProcessorMessageResourceService.DUPLICATE_NAME_FOR_ENTITY,
                                entity.getName(),
                                entity.getEntityName());

                    if (exists.getT2())
                        return this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                                ProcessorMessageResourceService.INVALID_PARENT,
                                entity.getEntityName());

                    entity.setName(NameUtil.normalize(entity.getName()));
                    return Mono.just(entity);
                });
    }

    @Override
    public Mono<D> create(D entity) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> this.validateEntity(entity, access),
                (access, vEntity) -> this.applyOrder(vEntity, access),
                (access, vEntity, aEntity) -> {
                    aEntity.setAppCode(access.getAppCode());
                    aEntity.setClientCode(access.getClientCode());
                    aEntity.setCreatedBy(access.getUserId());
                    aEntity.setIsParent(Boolean.TRUE);

                    return super.create(aEntity);
                },
                (access, vEntity, aEntity, cEntity) -> this.evictCache(cEntity).map(evicted -> cEntity));
    }

    public Mono<D> createChild(ProcessorAccess access, D entity, D parentEntity) {

        return FlatMapUtil.flatMapMono(() -> this.validateEntity(entity, access), vEntity -> {
            entity.setName(vEntity.getName());
            entity.setAppCode(parentEntity.getAppCode());
            entity.setClientCode(parentEntity.getClientCode());
            entity.setCreatedBy(parentEntity.getCreatedBy());
            entity.setIsParent(Boolean.FALSE);
            entity.setParentLevel0(parentEntity.getId());

            if (parentEntity.getParentLevel0() != null) entity.setParentLevel1(parentEntity.getParentLevel0());

            return super.create(entity);
        });
    }

    @Override
    public Mono<D> update(D entity) {
        return FlatMapUtil.flatMapMono(super::hasAccess, access -> this.updateInternal(access, entity));
    }

    public Mono<D> updateInternal(ProcessorAccess access, D entity) {
        return this.validateEntity(entity, access).flatMap(this::updateInternal);
    }

    @Override
    public Mono<D> update(ULong key, Map<String, Object> fields) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> key != null ? this.read(key) : Mono.empty(),
                (access, entity) -> super.update(key, fields),
                (access, entity, updated) ->
                        this.evictCache(entity).map(evicted -> updated).switchIfEmpty(Mono.just(updated)));
    }

    public Mono<D> updateInternal(D entity) {
        return FlatMapUtil.flatMapMono(
                () -> super.update(entity), updated -> this.evictCache(entity).map(evicted -> updated));
    }

    @Override
    public Mono<Integer> delete(ULong id) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> this.read(id),
                (access, entity) -> super.delete(entity.getId()),
                (access, entity, deleted) -> this.evictCache(entity).map(evicted -> deleted));
    }

    protected Mono<Boolean> existsById(
            String appCode, String clientCode, Platform platform, ULong productTemplateId, ULong... valueEntityIds) {
        return this.dao.existsById(appCode, clientCode, platform, productTemplateId, valueEntityIds);
    }

    protected Mono<Boolean> existsByName(
            String appCode,
            String clientCode,
            Platform platform,
            ULong productTemplateId,
            ULong entityId,
            String... names) {
        return this.dao.existsByName(appCode, clientCode, platform, productTemplateId, entityId, names);
    }

    public Mono<List<BaseValueResponse<D>>> getAllValuesInOrder(
            Platform platform, ULong productTemplateId, ULong parentId) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.getAllValuesInOrderInternal(
                                access.getAppCode(), access.getClientCode(), platform, productTemplateId, parentId))
                .map(BaseValueResponse::toList)
                .switchIfEmpty(Mono.just(new ArrayList<>()));
    }

    public Mono<List<BaseValueResponse<D>>> getAllValues(Platform platform, ULong productTemplateId, ULong parentId) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.getAllValues(
                                access.getAppCode(), access.getClientCode(), platform, productTemplateId, parentId))
                .map(BaseValueResponse::toList)
                .switchIfEmpty(Mono.just(new ArrayList<>()));
    }

    public Mono<NavigableMap<D, NavigableSet<D>>> getAllValuesInOrder(
            String appCode, String clientCode, Platform platform, ULong productTemplateId) {
        return this.getAllValuesInOrderInternal(appCode, clientCode, platform, productTemplateId)
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                        ProcessorMessageResourceService.NO_VALUES_FOUND,
                        this.getEntityName(),
                        this.getEntityName()));
    }

    public Mono<NavigableMap<D, NavigableSet<D>>> getAllValuesInOrderInternal(
            String appCode, String clientCode, Platform platform, ULong productTemplateId, ULong... parentIds) {
        return this.getAllValues(appCode, clientCode, platform, productTemplateId, parentIds)
                .flatMap(map -> {
                    if (map == null || map.isEmpty()) return Mono.empty();

                    NavigableMap<D, NavigableSet<D>> orderedMap =
                            new TreeMap<>(Comparator.comparingInt(D::getOrder).thenComparing(D::getId));

                    map.forEach((key, value) -> {
                        TreeSet<D> orderedSet = new TreeSet<>(
                                Comparator.comparingInt(D::getOrder).thenComparing(D::getId));
                        orderedSet.addAll(value);
                        orderedMap.put(key, orderedSet);
                    });

                    return Mono.just(orderedMap);
                });
    }

    public Mono<Set<ULong>> getAllValueIds(
            String appCode, String clientCode, Platform platform, ULong productTemplateId) {
        return this.getAllValues(appCode, clientCode, platform, productTemplateId)
                .map(map -> map.keySet().stream().map(D::getId).collect(Collectors.toSet()));
    }

    public Mono<Map.Entry<D, List<D>>> getParentChild(ProcessorAccess access, Identity parent, Identity child) {

        if (parent == null || parent.isNull()) return Mono.empty();

        if (child == null || child.isNull())
            return this.readIdentityWithAccess(access, parent).map(pEntity -> Map.entry(pEntity, List.of()));

        return FlatMapUtil.flatMapMonoWithNull(
                () -> this.readIdentityWithAccess(access, parent),
                pEntity -> this.readIdentityWithAccess(access, child),
                (pEntity, cEntity) -> {
                    if (pEntity == null) return Mono.empty();

	                if (cEntity == null || !cEntity.hasParent(pEntity.getId()))
		                return Mono.just(Map.entry(pEntity, List.of()));

	                return Mono.just(Map.entry(pEntity, List.of(cEntity)));
                });
    }

    public Mono<Map.Entry<D, Set<D>>> getValue(
            String appCode, String clientCode, Platform platform, ULong productTemplateId, ULong parentId) {

        if (parentId == null) return Mono.empty();

        return this.getAllValues(appCode, clientCode, platform, productTemplateId, parentId)
                .mapNotNull(map -> map.entrySet().stream()
                        .filter(entry -> parentId.equals(entry.getKey().getId()))
                        .findFirst()
                        .orElse(null));
    }

    public Mono<Map<D, Set<D>>> getAllValues(
            String appCode, String clientCode, Platform platform, ULong productTemplateId, ULong... parentIds) {

        if (parentIds == null
                || parentIds.length == 0
                || Arrays.stream(parentIds).allMatch(Objects::isNull))
            return this.getAllValues(appCode, clientCode, platform, productTemplateId);

        Set<ULong> parents = Set.of(parentIds);

        return this.getAllValues(appCode, clientCode, platform, productTemplateId)
                .map(values -> values.entrySet().stream()
                        .filter(entry -> parents.contains(entry.getKey().getId()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    private Mono<Map<D, Set<D>>> getAllValues(
            String appCode, String clientCode, Platform platform, ULong productTemplateId) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.getAllValuesInternal(appCode, clientCode, platform, productTemplateId),
                super.getCacheKey(this.getValueEtKey(), appCode, clientCode, platform, productTemplateId));
    }

    private Mono<Map<D, Set<D>>> getAllValuesInternal(
            String appCode, String clientCode, Platform platform, ULong productTemplateId) {
        return FlatMapUtil.flatMapMono(
                () -> Mono.zip(
                        this.dao.getAllValues(appCode, clientCode, platform, productTemplateId, null),
                        this.getAllValueMap(appCode, clientCode, platform, productTemplateId)),
                this::processValuesAndBuildHierarchy);
    }

    private Mono<Map<D, Set<D>>> processValuesAndBuildHierarchy(Tuple2<List<D>, Map<ULong, D>> tuple) {
        Map<D, Set<D>> result = new HashMap<>();

        Map<ULong, Set<D>> parentToChildrenMap = this.buildParentChildrenMap(tuple.getT1(), result);
        this.linkParentsWithChildren(result, parentToChildrenMap);

        return Mono.just(result);
    }

    private Map<ULong, Set<D>> buildParentChildrenMap(List<D> values, Map<D, Set<D>> result) {
        Map<ULong, Set<D>> parentToChildrenMap = new HashMap<>();

        for (D value : values) {

            this.addToParentMap(parentToChildrenMap, value.getParentLevel0(), value);
            this.addToParentMap(parentToChildrenMap, value.getParentLevel1(), value);

            if (Boolean.TRUE.equals(value.getIsParent()) || !value.hasParentLevels())
                result.put(value, new HashSet<>());
        }

        return parentToChildrenMap;
    }

    private void addToParentMap(Map<ULong, Set<D>> parentToChildrenMap, ULong parentId, D childValue) {
        if (parentId != null)
            parentToChildrenMap.computeIfAbsent(parentId, k -> new HashSet<>()).add(childValue);
    }

    private void linkParentsWithChildren(Map<D, Set<D>> result, Map<ULong, Set<D>> parentToChildrenMap) {
        parentToChildrenMap.forEach((parentId, children) -> result.keySet().stream()
                .filter(bv -> bv.getId().equals(parentId))
                .findFirst()
                .ifPresent(parentValue -> result.get(parentValue).addAll(children)));
    }

    private Mono<Map<ULong, D>> getAllValueMap(
            String appCode, String clientCode, Platform platform, ULong productTemplateId) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao
                        .getAllProductTemplates(appCode, clientCode, platform, productTemplateId)
                        .map(BaseUpdatableDto::toIdMap),
                super.getCacheKey(appCode, clientCode, platform, productTemplateId));
    }
}
