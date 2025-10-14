package com.fincity.saas.message.eager.relations.resolvers.field;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.IClassConvertor;
import com.fincity.saas.message.eager.EagerUtil;
import com.fincity.saas.message.eager.relations.resolvers.RelationResolver;
import com.fincity.saas.message.feign.IFeignEntityProcessorService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

@Component
public class ProductFieldResolver implements RelationResolver {

    private static final Set<String> SUPPORTED_FIELDS = Set.of("productId");

    private final IFeignEntityProcessorService entityProcessorService;

    public ProductFieldResolver(IFeignEntityProcessorService entityProcessorService) {
        this.entityProcessorService = entityProcessorService;
    }

    @Override
    public Set<String> getFields() {
        return SUPPORTED_FIELDS;
    }

    @Override
    public Mono<Map<ULong, Map<String, Object>>> resolveBatch(
            Set<ULong> idsToResolve, MultiValueMap<String, String> queryParams) {
        if (idsToResolve == null || idsToResolve.isEmpty()) return Mono.just(Map.of());

        Boolean eager = EagerUtil.getIsEagerParams(queryParams);
        List<String> eagerFields = EagerUtil.getEagerParams(queryParams);

        if (idsToResolve.size() == 1)
            return FlatMapUtil.flatMapMono(
                    () -> SecurityContextUtil.resolveAppAndClientCode(null, null),
                    ca -> this.entityProcessorService
                            .getProductInternal(
                                    ca.getT1(),
                                    ca.getT2(),
                                    idsToResolve.iterator().next().toBigInteger())
                            .map(client -> Map.of(ULongUtil.valueOf(client.getId()), client.toMap())),
                    (ca, products) -> this.applyEagerFiltering(products, eager, eagerFields));

        return FlatMapUtil.flatMapMono(
                () -> SecurityContextUtil.resolveAppAndClientCode(null, null),
                ca -> this.entityProcessorService
                        .getProductsInternal(
                                ca.getT1(),
                                ca.getT2(),
                                idsToResolve.stream().map(ULong::toBigInteger).toList())
                        .map(clientList -> clientList.stream()
                                .collect(Collectors.toMap(
                                        client -> ULongUtil.valueOf(client.getId()), IClassConvertor::toMap))),
                (ca, products) -> this.applyEagerFiltering(products, eager, eagerFields));
    }
}
