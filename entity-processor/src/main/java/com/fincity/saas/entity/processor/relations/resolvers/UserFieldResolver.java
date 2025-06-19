package com.fincity.saas.entity.processor.relations.resolvers;

import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class UserFieldResolver implements RelationResolver {

    private static final Set<String> SUPPORTED_FIELDS = Set.of("actorId", "assignedUserId", "createdBy", "updatedBy");

    private final IFeignSecurityService securityService;

    public UserFieldResolver(IFeignSecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public Set<String> getFields() {
        return SUPPORTED_FIELDS;
    }

    @Override
    public Mono<Map<ULong, Map<String, Object>>> resolveBatch(Set<ULong> idsToResolve) {

        if (idsToResolve == null || idsToResolve.isEmpty()) return Mono.just(Map.of());

        if (idsToResolve.size() == 1)
            return this.securityService
                    .getUserInternal(idsToResolve.iterator().next().toBigInteger())
                    .map(user -> Map.of(ULongUtil.valueOf(user.get("id")), user));

        return securityService
                .getUserInternal(idsToResolve.stream().map(ULong::toBigInteger).toList())
                .map(userList -> userList.stream()
                        .collect(
                                Collectors.toMap(userMap -> ULongUtil.valueOf(userMap.get("id")), userMap -> userMap)));
    }

    public Mono<Map<ULong, Map<String, Object>>> resolveBatch(Set<ULong> idsToResolve, List<String> eagerFields) {
        return this.resolveBatch(idsToResolve).map(resolvedMap -> {
            if (eagerFields == null || eagerFields.isEmpty()) return resolvedMap;

            Set<String> eagerFieldSet = new HashSet<>(eagerFields);

            return resolvedMap.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                Map<String, Object> originalMap = entry.getValue();
                return originalMap.keySet().stream()
                        .filter(eagerFieldSet::contains)
                        .collect(Collectors.toMap(key -> key, originalMap::get, (a, b) -> b, LinkedHashMap::new));
            }));
        });
    }
}
