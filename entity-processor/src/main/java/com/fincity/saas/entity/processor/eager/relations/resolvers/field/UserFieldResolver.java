package com.fincity.saas.entity.processor.eager.relations.resolvers.field;

import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.util.IClassConvertor;
import com.fincity.saas.entity.processor.eager.relations.resolvers.RelationResolver;
import com.fincity.saas.entity.processor.eager.EagerUtil;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

@Component
public class UserFieldResolver implements RelationResolver {

    private static final Set<String> SUPPORTED_FIELDS =
            Set.of("actorId", "assignedUserId", "managerId", "createdBy", "updatedBy");

    private final IFeignSecurityService securityService;

    public UserFieldResolver(IFeignSecurityService securityService) {
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
                    .getUserInternal(idsToResolve.iterator().next().toBigInteger(), queryParams)
                    .map(userResponse -> Map.of(ULongUtil.valueOf(userResponse.getId()), userResponse.toMap()))
                    .flatMap(userMap -> this.applyEagerFiltering(userMap, eager, eagerFields));

        return securityService
                .getUsersInternal(idsToResolve.stream().map(ULong::toBigInteger).toList(), queryParams)
                .map(userList -> userList.stream()
                        .collect(Collectors.toMap(
                                userResponse -> ULongUtil.valueOf(userResponse.getId()), IClassConvertor::toMap)))
                .flatMap(userMap -> this.applyEagerFiltering(userMap, eager, eagerFields));
    }
}
