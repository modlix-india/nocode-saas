package com.fincity.saas.entity.processor.service.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.entity.processor.dao.base.BaseProductDAO;
import com.fincity.saas.entity.processor.dto.base.BaseDto;
import com.fincity.saas.entity.processor.dto.base.BaseProductDto;
import com.fincity.saas.entity.processor.model.IdAndValue;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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

    @Override
    protected Mono<D> updatableEntity(D entity) {
        return FlatMapUtil.flatMapMono(() -> super.updatableEntity(entity), e -> {
            if (e.isValidChild(entity.getParentLevel0(), entity.getParentLevel1()))
                return this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                        ProcessorMessageResourceService.INVALID_CHILD_FOR_PARENT);

            e.setParentLevel0(entity.getParentLevel0());
            e.setParentLevel1(entity.getParentLevel1());
            e.setIsParent(entity.getParentLevel0() == null && entity.getParentLevel1() == null);

            return Mono.just(e);
        });
    }

    @Override
    protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

        if (fields == null || key == null) return Mono.just(new HashMap<>());

        return super.updatableFields(key, fields).flatMap(f -> {
            f.remove(BaseProductDto.Fields.productId);

            return Mono.just(f);
        });
    }

    public Mono<D> create(D entity, String appCode, String clientCode) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> {
                    if (!ca.isAuthenticated())
                        return msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                ProcessorMessageResourceService.INVALID_USER_FOR_CLIENT);

                    return this.hasAccess(appCode, clientCode, ca.getUser().getId());
                },
                (ca, hasAccess) -> entity.hasParentLevels()
                        ? this.existsById(
                                appCode,
                                clientCode,
                                entity.getProductId(),
                                entity.getParentLevel0(),
                                entity.getParentLevel1())
                        : Mono.just(Boolean.TRUE),
                (ca, hasAccess, parentExists) -> {
                    entity.setAppCode(hasAccess.getT1().getT1());
                    entity.setClientCode(hasAccess.getT1().getT2());
                    entity.setAddedByUserId(hasAccess.getT1().getT3());
                    entity.setIsParent(entity.getParentLevel0() == null || entity.getParentLevel1() == null);

                    return super.create(entity);
                });
    }

    @Override
    public Mono<Integer> delete(ULong id) {
        return super.delete(id);
    }

    protected Mono<Boolean> existsById(String appCode, String clientCode, ULong productId, ULong... productEntityIds) {
        return this.dao.existsById(appCode, clientCode, productId, productEntityIds);
    }

    protected Mono<Map<Integer, String>> getAllProductMap(String appCode, String clientCode, ULong productId) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao
                        .getAllProductIdAndNames(appCode, clientCode, productId)
                        .map(IdAndValue::toMap),
                super.getCacheKey(IdAndValue.ID_CACHE_KEY, appCode, clientCode, productId));
    }

    protected Mono<Map<String, Set<String>>> getAllByProduct(String appCode, String clientCode, ULong productId) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.getAllByProductInternal(appCode, clientCode, productId),
                super.getCacheKey(getProductEtKey(), appCode, clientCode, productId));
    }

    private Mono<Map<String, Set<String>>> getAllByProductInternal(String appCode, String clientCode, ULong productId) {
        return FlatMapUtil.flatMapMono(
                () -> this.dao.getAllProducts(appCode, clientCode, productId, null),
                productList -> this.getAllProductMap(appCode, clientCode, productId),
                (productList, productMap) -> Mono.just(productList.stream()
                        .collect(Collectors.toMap(
                                BaseDto::getName,
                                product -> this.getProductChildNamesSet(product, productMap),
                                (a, b) -> b))));
    }

    private Set<String> getProductChildNamesSet(D productEntity, Map<Integer, String> productEntityMap) {
        Set<String> productEntityChildren = new HashSet<>();
        if (productEntityMap.containsKey(productEntity.getParentLevel0().intValue()))
            productEntityChildren.add(
                    productEntityMap.get(productEntity.getParentLevel0().intValue()));
        if (productEntityMap.containsKey(productEntity.getParentLevel1().intValue()))
            productEntityChildren.add(
                    productEntityMap.get(productEntity.getParentLevel1().intValue()));
        return productEntityChildren;
    }
}
