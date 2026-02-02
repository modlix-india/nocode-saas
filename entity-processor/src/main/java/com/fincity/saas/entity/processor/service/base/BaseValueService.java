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
import com.fincity.saas.entity.processor.service.product.template.ProductTemplateService;
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
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

public abstract class BaseValueService<
                R extends UpdatableRecord<R>, D extends BaseValueDto<D>, O extends BaseValueDAO<R, D>>
        extends BaseUpdatableService<R, D, O> {

    protected ProductTemplateService productTemplateService;

    protected abstract Mono<D> applyOrder(D entity, ProcessorAccess access);

    @Autowired
    private void setValueTemplateService(ProductTemplateService productTemplateService) {
        this.productTemplateService = productTemplateService;
    }

    @Override
    protected Mono<D> checkEntity(D entity, ProcessorAccess access) {
        return FlatMapUtil.flatMapMono(
                () -> Mono.zip(
                        Boolean.TRUE.equals(entity.getIsParent())
                                ? this.existsByName(
                                        access.getAppCode(),
                                        access.getClientCode(),
                                        entity.getPlatform(),
                                        entity.getProductTemplateId(),
                                        entity.getId(),
                                        entity.getName())
                                : Mono.just(Boolean.FALSE),
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
                    if (exists.getT1())
                        return this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                                ProcessorMessageResourceService.DUPLICATE_NAME_FOR_ENTITY,
                                entity.getName(),
                                entity.getEntityName());

                    if (!exists.getT2())
                        return this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                                ProcessorMessageResourceService.INVALID_PARENT,
                                entity.getEntityName());

                    entity.setName(NameUtil.normalize(entity.getName()));
                    return Mono.just(entity);
                });
    }

    @Override
    protected Mono<Boolean> evictCache(D entity) {
        return Mono.zip(
                super.evictCache(entity),
                this.evictMapCache(entity),
                (baseEvicted, mapEvicted) -> baseEvicted && mapEvicted);
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

            existing.setOrder(entity.getOrder());

            return Mono.just(existing);
        });
    }

    @Override
    public Mono<D> create(D entity) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> this.checkEntity(entity, access),
                (access, vEntity) -> this.applyOrder(vEntity, access),
                (access, vEntity, aEntity) -> {
                    aEntity.setIsParent(Boolean.TRUE);

                    return super.createInternal(access, aEntity);
                },
                (access, vEntity, aEntity, cEntity) -> this.evictCache(cEntity).map(evicted -> cEntity));
    }

    @Override
    protected Mono<D> create(ProcessorAccess access, D entity) {
        return FlatMapUtil.flatMapMono(
                () -> this.checkEntity(entity, access),
                vEntity -> this.applyOrder(vEntity, access),
                (vEntity, aEntity) -> {
                    aEntity.setIsParent(Boolean.TRUE);

                    return super.createInternal(access, aEntity);
                },
                (vEntity, aEntity, cEntity) -> this.evictCache(cEntity).map(evicted -> cEntity));
    }

    protected Mono<D> createChild(ProcessorAccess access, D entity, D parentEntity) {

        return FlatMapUtil.flatMapMono(() -> this.checkEntity(entity, access), vEntity -> {
            entity.setIsParent(Boolean.FALSE);
            entity.setParentLevel0(parentEntity.getId());

            if (parentEntity.getParentLevel0() != null) entity.setParentLevel1(parentEntity.getParentLevel0());

            return super.createInternal(access, entity)
                    .flatMap(cEntity -> this.evictCache(cEntity).map(evicted -> cEntity));
        });
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
                        access -> this.getAllValuesInOrderInternal(access, platform, productTemplateId, parentId))
                .map(BaseValueResponse::toList)
                .switchIfEmpty(Mono.just(new ArrayList<>()));
    }

    public Mono<List<BaseValueResponse<D>>> getAllValues(Platform platform, ULong productTemplateId, ULong parentId) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.getAllValues(
                                access.getAppCode(),
                                access.getEffectiveClientCode(),
                                platform,
                                productTemplateId,
                                parentId))
                .map(BaseValueResponse::toList)
                .switchIfEmpty(Mono.just(new ArrayList<>()));
    }

    public Mono<NavigableMap<D, NavigableSet<D>>> getAllValuesInOrder(
            ProcessorAccess access, Platform platform, ULong productTemplateId) {
        return this.getAllValuesInOrderInternal(access, platform, productTemplateId)
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                        ProcessorMessageResourceService.NO_VALUES_FOUND,
                        this.getEntityName(),
                        this.getEntityName()));
    }

    public Mono<NavigableMap<D, NavigableSet<D>>> getAllValuesInOrderInternal(
            ProcessorAccess access, Platform platform, ULong productTemplateId, ULong... parentIds) {
        return this.getAllValues(
                        access.getAppCode(), access.getEffectiveClientCode(), platform, productTemplateId, parentIds)
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

    public Mono<Set<ULong>> getAllValueIds(ProcessorAccess access, Platform platform, ULong productTemplateId) {
        return this.getAllValues(access.getAppCode(), access.getEffectiveClientCode(), platform, productTemplateId)
                .map(map -> map.keySet().stream().map(D::getId).collect(Collectors.toSet()));
    }

    public Mono<Map.Entry<D, List<D>>> getParentChild(
            ProcessorAccess access, ULong productTemplateId, Identity parent, Identity child) {

        if (parent == null || parent.isNull()) return Mono.empty();

        return this.readByIdentity(access, parent).flatMap(pEntity -> {
            if (pEntity == null || !productTemplateId.equals(pEntity.getProductTemplateId())) return Mono.empty();

            if (child == null || child.isNull()) return Mono.just(Map.entry(pEntity, List.of()));

            return this.readByIdentity(access, child).flatMap(cEntity -> {
                if (cEntity == null || !productTemplateId.equals(cEntity.getProductTemplateId()))
                    return Mono.just(Map.entry(pEntity, List.of()));

                if (cEntity.hasParent(pEntity.getId())) return Mono.just(Map.entry(pEntity, List.of(cEntity)));

                return Mono.just(Map.entry(pEntity, List.of()));
            });
        });
    }

    protected Mono<Map.Entry<D, Set<D>>> getValue(
            String appCode, String clientCode, Platform platform, ULong productTemplateId, ULong parentId) {

        if (parentId == null) return Mono.empty();

        return this.getAllValues(appCode, clientCode, platform, productTemplateId, parentId)
                .flatMap(map -> Mono.justOrEmpty(map.entrySet().stream()
                        .filter(entry -> parentId.equals(entry.getKey().getId()))
                        .findFirst()));
    }

    protected Mono<Map<D, Set<D>>> getAllValues(
            String appCode, String clientCode, Platform platform, ULong productTemplateId, ULong... parentIds) {

        if (parentIds == null || parentIds.length == 0)
            return this.getAllValues(appCode, clientCode, platform, productTemplateId);

        Set<ULong> parents = new HashSet<>(Arrays.asList(parentIds));
        parents.remove(null);

        if (parents.isEmpty()) return this.getAllValues(appCode, clientCode, platform, productTemplateId);

        return this.getAllValues(appCode, clientCode, platform, productTemplateId)
                .map(values -> {
                    Map<D, Set<D>> filtered = HashMap.newHashMap(parents.size());
                    values.entrySet().stream()
                            .filter(entry -> parents.contains(entry.getKey().getId()))
                            .forEach(entry -> filtered.put(entry.getKey(), entry.getValue()));
                    return filtered;
                });
    }

    private Mono<Map<D, Set<D>>> getAllValues(
            String appCode, String clientCode, Platform platform, ULong productTemplateId) {
        return this.getAllValueMap(appCode, clientCode, platform, productTemplateId)
                .flatMap(this::processValuesAndBuildHierarchy);
    }

    private Mono<Map<D, Set<D>>> processValuesAndBuildHierarchy(Map<ULong, D> idToEntityMap) {
        Map<D, Set<D>> result = new HashMap<>();
        Map<ULong, Set<D>> parentToChildrenMap = new HashMap<>();

        for (D value : idToEntityMap.values()) {
            if (Boolean.TRUE.equals(value.getIsParent()) || !value.hasParentLevels())
                result.put(value, new HashSet<>());

            if (value.getParentLevel0() != null)
                parentToChildrenMap
                        .computeIfAbsent(value.getParentLevel0(), k -> new HashSet<>())
                        .add(value);

            if (value.getParentLevel1() != null)
                parentToChildrenMap
                        .computeIfAbsent(value.getParentLevel1(), k -> new HashSet<>())
                        .add(value);
        }

        parentToChildrenMap.forEach((parentId, children) -> {
            D parent = idToEntityMap.get(parentId);
            if (parent != null && result.containsKey(parent)) {
                result.get(parent).addAll(children);
            }
        });

        return Mono.just(result);
    }

    private Mono<Map<ULong, D>> getAllValueMap(
            String appCode, String clientCode, Platform platform, ULong productTemplateId) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.getValuesFlat(appCode, clientCode, platform, productTemplateId, null)
                        .map(BaseUpdatableDto::toIdMap),
                super.getCacheKey(appCode, clientCode, platform, productTemplateId));
    }

    public Mono<List<D>> getValuesFlat(
            String appCode,
            String clientCode,
            Platform platform,
            ULong productTemplateId,
            Boolean isParent,
            ULong... valueEntityIds) {
        return this.dao.getValues(appCode, clientCode, platform, productTemplateId, isParent, valueEntityIds);
    }
}
