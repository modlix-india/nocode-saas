package com.fincity.saas.entity.processor.service.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.dao.base.BaseValueDAO;
import com.fincity.saas.entity.processor.dto.base.BaseValueDto;
import com.fincity.saas.entity.processor.enums.Platform;
import com.fincity.saas.entity.processor.model.common.BaseValue;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.response.BaseValueResponse;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.ProductTemplateService;
import java.util.ArrayList;
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
import java.util.stream.Stream;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;

@Service
public abstract class BaseValueService<
                R extends UpdatableRecord<R>, D extends BaseValueDto<D>, O extends BaseValueDAO<R, D>>
        extends BaseService<R, D, O> {

    private static final String VALUE_ET_KEY = "valueEtKey";
    protected ProductTemplateService productTemplateService;

    public String getValueEtKey() {
        return VALUE_ET_KEY;
    }

    public String getValueIdValueKey() {
        return IdAndValue.ID_CACHE_KEY;
    }

    public abstract Mono<D> applyOrder(D entity, Tuple3<String, String, ULong> accessInfo);

    @Autowired
    private void setValueTemplateService(ProductTemplateService productTemplateService) {
        this.productTemplateService = productTemplateService;
    }

    @Override
    protected Mono<Boolean> evictCache(D entity) {
        return FlatMapUtil.flatMapMono(
                () -> super.evictCache(entity),
                baseEvicted -> super.cacheService.evict(
                        getCacheName(),
                        super.getCacheKey(
                                this.getValueEtKey(),
                                entity.getAppCode(),
                                entity.getClientCode(),
                                entity.getProductTemplateId())),
                (baseEvicted, mapEvicted) -> super.cacheService.evict(
                        getCacheName(),
                        super.getCacheKey(
                                this.getValueIdValueKey(),
                                entity.getAppCode(),
                                entity.getClientCode(),
                                entity.getProductTemplateId())));
    }

    @Override
    protected Mono<D> updatableEntity(D entity) {
        return FlatMapUtil.flatMapMono(() -> super.updatableEntity(entity), existing -> {
            if (existing.isValidChild(entity.getParentLevel0(), entity.getParentLevel1()))
                return this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                        ProcessorMessageResourceService.INVALID_CHILD_FOR_PARENT);

            existing.setParentLevel0(entity.getParentLevel0());
            existing.setParentLevel1(entity.getParentLevel1());
            existing.setIsParent(entity.getParentLevel0() == null && entity.getParentLevel1() == null);

            return Mono.just(existing);
        });
    }

    private Mono<D> validateEntity(D entity, Tuple3<String, String, ULong> accessInfo) {
        return FlatMapUtil.flatMapMono(
                () -> entity.hasParentLevels()
                        ? this.existsById(
                                accessInfo.getT1(),
                                accessInfo.getT2(),
                                entity.getPlatform(),
                                entity.getProductTemplateId(),
                                entity.getParentLevel0(),
                                entity.getParentLevel1())
                        : Mono.just(Boolean.TRUE),
                parentExists -> this.existsByName(
                        accessInfo.getT1(),
                        accessInfo.getT2(),
                        entity.getPlatform(),
                        entity.getProductTemplateId(),
                        entity.getName()),
                (parentExists, nameExists) -> {
                    if (Boolean.TRUE.equals(nameExists))
                        return this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                                ProcessorMessageResourceService.DUPLICATE_NAME_FOR_ENTITY,
                                entity.getName(),
                                entity.getEntityName());

                    entity.setName(StringUtil.toTitleCase(entity.getName()));
                    return Mono.just(entity);
                });
    }

    @Override
    public Mono<D> create(D entity) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                hasAccess -> this.validateEntity(entity, hasAccess.getT1()),
                (hasAccess, vEntity) -> this.applyOrder(vEntity, hasAccess.getT1()),
                (hasAccess, vEntity, cEntity) -> {
                    cEntity.setAppCode(hasAccess.getT1().getT1());
                    cEntity.setClientCode(hasAccess.getT1().getT2());
                    cEntity.setCreatedBy(hasAccess.getT1().getT3());
                    cEntity.setIsParent(Boolean.TRUE);

                    return super.create(cEntity);
                });
    }

    public Mono<D> createChild(D entity, D parentEntity) {
        entity.setName(StringUtil.toTitleCase(entity.getName()));
        entity.setAppCode(parentEntity.getAppCode());
        entity.setClientCode(parentEntity.getClientCode());
        entity.setCreatedBy(parentEntity.getCreatedBy());
        entity.setIsParent(Boolean.FALSE);
        entity.setParentLevel0(parentEntity.getId());

        if (parentEntity.getParentLevel0() != null) entity.setParentLevel1(parentEntity.getParentLevel0());

        return super.create(entity);
    }

    @Override
    public Mono<D> update(D entity) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                hasAccess -> this.validateEntity(entity, hasAccess.getT1()),
                (hasAccess, validated) -> this.updateInternal(validated));
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

    public Mono<D> updateInternal(D entity) {
        return FlatMapUtil.flatMapMono(
                () -> super.update(entity), updated -> this.evictCache(entity).map(evicted -> updated));
    }

    @Override
    public Mono<Integer> delete(ULong id) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                hasAccess -> this.read(id),
                (hasAccess, entity) -> super.delete(entity.getId()),
                (ca, entity, deleted) -> this.evictCache(entity).map(evicted -> deleted));
    }

    public Mono<Boolean> existsById(
            String appCode, String clientCode, Platform platform, ULong productTemplateId, Identity valueEntity) {

        return FlatMapUtil.flatMapMono(
                () -> this.checkAndUpdateIdentity(valueEntity),
                identity -> this.existsById(appCode, clientCode, platform, productTemplateId, identity.getULongId()));
    }

    protected Mono<Boolean> existsById(
            String appCode, String clientCode, Platform platform, ULong productTemplateId, ULong... valueEntityIds) {
        return this.dao.existsById(appCode, clientCode, platform, productTemplateId, valueEntityIds);
    }

    protected Mono<Boolean> existsByName(
            String appCode, String clientCode, Platform platform, ULong productTemplateId, String... names) {
        return this.dao.existsByName(appCode, clientCode, platform, productTemplateId, names);
    }

    public Mono<Boolean> isValidParentChild(
            String appCode,
            String clientCode,
            Platform platform,
            ULong productTemplateId,
            ULong parent,
            ULong... children) {
        return FlatMapUtil.flatMapMono(
                        () -> this.getAllValues(appCode, clientCode, platform, productTemplateId), valueEntityMap -> {
                            BaseValue parentKey = valueEntityMap.keySet().stream()
                                    .filter(k -> k.getId().equals(parent))
                                    .findFirst()
                                    .orElse(null);

                            if (parentKey == null) return Mono.just(Boolean.FALSE);

                            Set<BaseValue> parentChildren = valueEntityMap.get(parentKey);
                            Set<ULong> parentChildIds = parentChildren.stream()
                                    .map(BaseValue::getId)
                                    .collect(Collectors.toSet());

                            boolean allChildrenValid = Stream.of(children).allMatch(parentChildIds::contains);

                            return Mono.just(allChildrenValid);
                        })
                .switchIfEmpty(Mono.just(Boolean.FALSE));
    }

    public Mono<NavigableMap<BaseValue, NavigableSet<BaseValue>>> getAllValuesInOrderInternal(
            String appCode, String clientCode, Platform platform, ULong productTemplateId) {
        return this.getAllValues(appCode, clientCode, platform, productTemplateId)
                .flatMap(map -> {
                    if (map == null || map.isEmpty()) return Mono.empty();

                    NavigableMap<BaseValue, NavigableSet<BaseValue>> orderedMap = new TreeMap<>(
                            Comparator.comparingInt(BaseValue::getOrder).thenComparing(BaseValue::getId));

                    map.forEach((key, value) -> {
                        TreeSet<BaseValue> orderedSet = new TreeSet<>(
                                Comparator.comparingInt(BaseValue::getOrder).thenComparing(BaseValue::getId));
                        orderedSet.addAll(value);
                        orderedMap.put(key, orderedSet);
                    });

                    return Mono.just(orderedMap);
                });
    }

    public Mono<NavigableMap<BaseValue, NavigableSet<BaseValue>>> getAllValuesInOrder(
            String appCode, String clientCode, Platform platform, ULong productTemplateId) {
        return this.getAllValuesInOrderInternal(appCode, clientCode, platform, productTemplateId)
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                        ProcessorMessageResourceService.NO_VALUES_FOUND,
                        this.getEntityName(),
                        this.getEntityName()));
    }

    public Mono<List<BaseValueResponse>> getAllValuesInOrder(Platform platform, ULong productTemplateId) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.getAllValuesInOrderInternal(
                                access.getT1().getT1(), access.getT1().getT2(), platform, productTemplateId))
                .map(BaseValueResponse::toList)
                .switchIfEmpty(Mono.just(new ArrayList<>()));
    }

    public Mono<List<BaseValueResponse>> getAllValues(Platform platform, ULong productTemplateId) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.getAllValues(
                                access.getT1().getT1(), access.getT1().getT2(), platform, productTemplateId))
                .map(BaseValueResponse::toList)
                .switchIfEmpty(Mono.just(new ArrayList<>()));
    }

    public Mono<Map<BaseValue, Set<BaseValue>>> getAllValues(
            String appCode, String clientCode, Platform platform, ULong productTemplateId) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.getAllValuesInternal(appCode, clientCode, platform, productTemplateId),
                super.getCacheKey(this.getValueEtKey(), appCode, clientCode, platform, productTemplateId));
    }

    private Mono<Map<BaseValue, Set<BaseValue>>> getAllValuesInternal(
            String appCode, String clientCode, Platform platform, ULong productTemplateId) {
        return FlatMapUtil.flatMapMono(
                () -> Mono.zip(
                        this.dao.getAllValues(appCode, clientCode, platform, productTemplateId, null),
                        this.getAllValueMap(appCode, clientCode, platform, productTemplateId)),
                this::processValuesAndBuildHierarchy);
    }

    private Mono<Map<BaseValue, Set<BaseValue>>> processValuesAndBuildHierarchy(
            Tuple2<List<D>, Map<ULong, BaseValue>> tuple) {
        Map<BaseValue, Set<BaseValue>> result = new HashMap<>();

        Map<ULong, Set<BaseValue>> parentToChildrenMap = this.buildParentChildrenMap(tuple.getT1(), result);
        this.linkParentsWithChildren(result, parentToChildrenMap);

        return Mono.just(result);
    }

    private Map<ULong, Set<BaseValue>> buildParentChildrenMap(List<D> values, Map<BaseValue, Set<BaseValue>> result) {
        Map<ULong, Set<BaseValue>> parentToChildrenMap = new HashMap<>();

        for (D value : values) {
            BaseValue baseValue = BaseValue.of(value.getId(), value.getCode(), value.getName(), value.getOrder());

            addToParentMap(parentToChildrenMap, value.getParentLevel0(), baseValue);
            addToParentMap(parentToChildrenMap, value.getParentLevel1(), baseValue);

            if (Boolean.TRUE.equals(value.getIsParent()) || !value.hasParentLevels())
                result.put(baseValue, new HashSet<>());
        }

        return parentToChildrenMap;
    }

    private void addToParentMap(Map<ULong, Set<BaseValue>> parentToChildrenMap, ULong parentId, BaseValue childValue) {
        if (parentId != null)
            parentToChildrenMap.computeIfAbsent(parentId, k -> new HashSet<>()).add(childValue);
    }

    private void linkParentsWithChildren(
            Map<BaseValue, Set<BaseValue>> result, Map<ULong, Set<BaseValue>> parentToChildrenMap) {
        parentToChildrenMap.forEach((parentId, children) -> result.keySet().stream()
                .filter(bv -> bv.getId().equals(parentId))
                .findFirst()
                .ifPresent(parentValue -> result.get(parentValue).addAll(children)));
    }

    private Mono<Map<ULong, BaseValue>> getAllValueMap(
            String appCode, String clientCode, Platform platform, ULong productTemplateId) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao
                        .getAllProductTemplateIdAndNames(appCode, clientCode, platform, productTemplateId)
                        .map(BaseValue::toIdMap),
                super.getCacheKey(this.getValueIdValueKey(), appCode, clientCode, productTemplateId));
    }
}
