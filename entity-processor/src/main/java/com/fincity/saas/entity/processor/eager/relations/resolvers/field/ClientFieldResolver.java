package com.fincity.saas.entity.processor.eager.relations.resolvers.field;

import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.entity.processor.eager.relations.resolvers.RelationResolver;
import com.fincity.saas.entity.processor.eager.EagerUtil;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
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
            Set<ULong> idsToResolve, MultiValueMap<String, String> queryParams) {
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
                .map(clientList -> clientList.stream()
                        .collect(Collectors.toMap(client -> ULongUtil.valueOf(client.get("id")), Function.identity())))
                .flatMap(clientMap -> this.applyEagerFiltering(clientMap, eager, eagerFields));
    }
}
