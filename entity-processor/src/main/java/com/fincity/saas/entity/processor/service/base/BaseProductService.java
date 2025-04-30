package com.fincity.saas.entity.processor.service.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.dao.base.BaseProductDAO;
import com.fincity.saas.entity.processor.dto.base.BaseDto;
import com.fincity.saas.entity.processor.dto.base.BaseProductDto;
import com.fincity.saas.entity.processor.model.base.IdAndValue;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public abstract class BaseProductService<
                R extends UpdatableRecord<R>, D extends BaseProductDto<D>, O extends BaseProductDAO<R, D>>
        extends BaseService<R, D, O> {

    private static final String PRODUCT_ET_KEY = "productEtKey";

    public String getProductEtKey() {
        return PRODUCT_ET_KEY;
    }

    public String getProductIdValueKey() {
        return IdAndValue.ID_CACHE_KEY;
    }

    @Override
    protected Mono<Boolean> evictCache(D entity) {
        return FlatMapUtil.flatMapMono(
                () -> super.evictCache(entity),
                baseEvicted -> super.cacheService.evict(
                        getCacheName(),
                        super.getCacheKey(
                                this.getProductEtKey(),
                                entity.getAppCode(),
                                entity.getClientCode(),
                                entity.getProductId())),
                (baseEvicted, mapEvicted) -> super.cacheService.evict(
                        getCacheName(),
                        super.getCacheKey(
                                this.getProductIdValueKey(),
                                entity.getAppCode(),
                                entity.getClientCode(),
                                entity.getProductId())));
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

    @Override
    public Mono<D> create(D entity) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                hasAccess -> entity.hasParentLevels()
                        ? this.existsById(
                                hasAccess.getT1().getT1(),
                                hasAccess.getT1().getT2(),
                                entity.getProductId(),
                                entity.getParentLevel0(),
                                entity.getParentLevel1())
                        : Mono.just(Boolean.TRUE),
                (hasAccess, parentExists) -> {
                    entity.setName(StringUtil.toTitleCase(entity.getName()));
                    entity.setAppCode(hasAccess.getT1().getT1());
                    entity.setClientCode(hasAccess.getT1().getT2());
                    entity.setAddedByUserId(hasAccess.getT1().getT3());
                    entity.setIsParent(entity.getParentLevel0() == null || entity.getParentLevel1() == null);

                    return super.create(entity);
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
                super::hasAccess, hasAccess -> super.update(entity), (hasAccess, updated) -> this.evictCache(entity)
                        .map(evicted -> updated));
    }

    @Override
    public Mono<Integer> delete(ULong id) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                hasAccess -> this.read(id),
                (hasAccess, entity) -> super.delete(entity.getId()),
                (ca, entity, deleted) -> this.evictCache(entity).map(evicted -> deleted));
    }

    protected Mono<Boolean> existsById(String appCode, String clientCode, ULong productId, ULong... productEntityIds) {
        return this.dao.existsById(appCode, clientCode, productId, productEntityIds);
    }

    public Mono<Map<Integer, String>> getAllProductMap(String appCode, String clientCode, ULong productId) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao
                        .getAllProductIdAndNames(appCode, clientCode, productId)
                        .map(IdAndValue::toMap),
                super.getCacheKey(this.getProductIdValueKey(), appCode, clientCode, productId));
    }

    public Mono<Boolean> isValidParentChild(
            String appCode, String clientCode, ULong productId, String parent, String... children) {
        return FlatMapUtil.flatMapMono(() -> this.getAllProducts(appCode, clientCode, productId), productEntityMap -> {
                    if (!productEntityMap.containsKey(parent)) return Mono.just(Boolean.FALSE);

                    if (!productEntityMap.get(parent).containsAll(List.of(children))) return Mono.just(Boolean.FALSE);

                    return Mono.just(Boolean.TRUE);
                })
                .switchIfEmpty(Mono.just(Boolean.FALSE));
    }

    public Mono<Map<String, Set<String>>> getAllProducts(String appCode, String clientCode, ULong productId) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.getAllProductsInternal(appCode, clientCode, productId),
                super.getCacheKey(this.getProductEtKey(), appCode, clientCode, productId));
    }

    private Mono<Map<String, Set<String>>> getAllProductsInternal(String appCode, String clientCode, ULong productId) {
        return FlatMapUtil.flatMapMono(
                () -> Mono.zip(
                        this.dao.getAllProducts(appCode, clientCode, productId, null),
                        this.getAllProductMap(appCode, clientCode, productId)),
                tup -> Mono.just(tup.getT1().stream()
                        .collect(Collectors.toMap(
                                BaseDto::getName,
                                product -> this.getProductChildNamesSet(product, tup.getT2()),
                                (a, b) -> b))));
    }

    private Set<String> getProductChildNamesSet(D productEntity, Map<Integer, String> productEntityMap) {
        return Stream.of(productEntity.getParentLevel0(), productEntity.getParentLevel1())
                .filter(Objects::nonNull)
                .map(ULong::intValue)
                .map(productEntityMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
