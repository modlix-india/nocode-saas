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
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.ProductTemplateService;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
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
                                entity.getValueTemplateId())),
                (baseEvicted, mapEvicted) -> super.cacheService.evict(
                        getCacheName(),
                        super.getCacheKey(
                                this.getValueIdValueKey(),
                                entity.getAppCode(),
                                entity.getClientCode(),
                                entity.getValueTemplateId())));
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
                                entity.getValueTemplateId(),
                                entity.getParentLevel0(),
                                entity.getParentLevel1())
                        : Mono.just(Boolean.TRUE),
                parentExists -> this.existsByName(
                        accessInfo.getT1(),
                        accessInfo.getT2(),
                        entity.getPlatform(),
                        entity.getValueTemplateId(),
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
                    cEntity.setAddedByUserId(hasAccess.getT1().getT3());
                    cEntity.setIsParent(Boolean.TRUE);

                    return super.create(cEntity);
                });
    }

    public Mono<D> createChild(D entity, D parentEntity) {
        entity.setName(StringUtil.toTitleCase(entity.getName()));
        entity.setAppCode(parentEntity.getAppCode());
        entity.setClientCode(parentEntity.getClientCode());
        entity.setAddedByUserId(parentEntity.getAddedByUserId());
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
            String appCode, String clientCode, Platform platform, ULong valueTemplateId, Identity valueEntity) {

        return FlatMapUtil.flatMapMono(
                () -> this.checkAndUpdateIdentity(valueEntity),
                identity -> this.existsById(appCode, clientCode, platform, valueTemplateId, identity.getULongId()));
    }

    protected Mono<Boolean> existsById(
            String appCode, String clientCode, Platform platform, ULong valueTemplateId, ULong... valueEntityIds) {
        return this.dao.existsById(appCode, clientCode, platform, valueTemplateId, valueEntityIds);
    }

    protected Mono<Boolean> existsByName(
            String appCode, String clientCode, Platform platform, ULong valueTemplateId, String... names) {
        return this.dao.existsByName(appCode, clientCode, platform, valueTemplateId, names);
    }

    public Mono<Boolean> isValidParentChild(
            String appCode,
            String clientCode,
            Platform platform,
            ULong valueTemplateId,
            ULong parent,
            ULong... children) {
        return FlatMapUtil.flatMapMono(
                        () -> this.getAllValues(appCode, clientCode, platform, valueTemplateId), valueEntityMap -> {
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

    public Mono<TreeMap<BaseValue, TreeSet<BaseValue>>> getAllValuesInOrder(
            String appCode, String clientCode, Platform platform, ULong valueTemplateId) {
        return this.getAllValues(appCode, clientCode, platform, valueTemplateId).map(map -> {
            TreeMap<BaseValue, TreeSet<BaseValue>> orderedMap =
                    new TreeMap<>(Comparator.comparingInt(BaseValue::getOrder).thenComparing(BaseValue::getId));

            map.forEach((key, value) -> {
                TreeSet<BaseValue> orderedSet = new TreeSet<>(
                        Comparator.comparingInt(BaseValue::getOrder).thenComparing(BaseValue::getId));
                orderedSet.addAll(value);
                orderedMap.put(key, orderedSet);
            });

            return orderedMap;
        });
    }

    public Mono<Map<BaseValue, Set<BaseValue>>> getAllValues(
            String appCode, String clientCode, Platform platform, ULong valueTemplateId) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.getAllValuesInternal(appCode, clientCode, platform, valueTemplateId),
                super.getCacheKey(this.getValueEtKey(), appCode, clientCode, platform, valueTemplateId));
    }

    private Mono<Map<BaseValue, Set<BaseValue>>> getAllValuesInternal(
            String appCode, String clientCode, Platform platform, ULong valueTemplateId) {
        return FlatMapUtil.flatMapMono(
                () -> Mono.zip(
                        this.dao.getAllValues(appCode, clientCode, platform, valueTemplateId, null),
                        this.getAllValueMap(appCode, clientCode, platform, valueTemplateId)),
                tup -> Mono.just(tup.getT1().stream()
                        .collect(Collectors.toMap(
                                value -> BaseValue.of(value.getId(), value.getName(), value.getOrder()),
                                value -> this.getValueChildNamesSet(value, tup.getT2()),
                                (a, b) -> b))));
    }

    private Mono<Map<ULong, BaseValue>> getAllValueMap(
            String appCode, String clientCode, Platform platform, ULong valueTemplateId) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao
                        .getAllValueTemplateIdAndNames(appCode, clientCode, platform, valueTemplateId)
                        .map(BaseValue::toIdMap),
                super.getCacheKey(this.getValueIdValueKey(), appCode, clientCode, valueTemplateId));
    }

    private Set<BaseValue> getValueChildNamesSet(D valueEntity, Map<ULong, BaseValue> valueEntityMap) {
        return Stream.of(valueEntity.getParentLevel0(), valueEntity.getParentLevel1())
                .filter(Objects::nonNull)
                .map(valueEntityMap::get)
                .filter(baseValue -> baseValue.getValue() != null)
                .collect(Collectors.toSet());
    }
}
