package com.fincity.saas.entity.processor.relations.resolvers.field;

import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.entity.processor.relations.resolvers.RelationResolver;
import com.fincity.saas.entity.processor.util.EagerUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ClientFieldResolver implements RelationResolver {

    private static final Set<String> SUPPORTED_FIELDS = Set.of("clientId");

    private final IFeignSecurityService securityService;

    public ClientFieldResolver(IFeignSecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public Set<String> getFields() {
        return SUPPORTED_FIELDS;
    }

    @Override
    public Mono<Map<ULong, Map<String, Object>>> resolveBatch(
            Set<ULong> idsToResolve, org.springframework.util.MultiValueMap<String, String> queryParams) {
        if (idsToResolve == null || idsToResolve.isEmpty()) return Mono.just(Map.of());

        Boolean eager = EagerUtil.getIsEagerParams(queryParams);
        List<String> eagerFields = EagerUtil.getEagerParams(queryParams);

        if (idsToResolve.size() == 1)
            return this.securityService
                    .getClientInternal(idsToResolve.iterator().next().toBigInteger(), queryParams)
                    .map(userResponse -> Map.of(ULongUtil.valueOf(userResponse.get("id")), userResponse))
                    .flatMap(clientMap -> this.applyEagerFiltering(clientMap, eager, eagerFields));

        return this.securityService
                .getClientInternal(
                        idsToResolve.stream().map(ULong::toBigInteger).toList(), queryParams)
                .map(list -> {
                    java.util.HashMap<ULong, Map<String, Object>> res = new java.util.HashMap<>();
                    for (int i = 0; i < list.size(); i++) {
                        Map<String, Object> m = list.get(i);
                        Object idObj = m.get("id");
                        if (idObj instanceof java.math.BigInteger bi) {
                            res.put(ULong.valueOf(bi.longValue()), m);
                        } else if (idObj instanceof Number num) {
                            res.put(ULong.valueOf(num.longValue()), m);
                        }
                    }
                    return res;
                });
    }
}
