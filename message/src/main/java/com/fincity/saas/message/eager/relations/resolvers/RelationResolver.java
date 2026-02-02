package com.fincity.saas.message.eager.relations.resolvers;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jooq.types.ULong;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

public interface RelationResolver {

    Set<String> getFields();

    default boolean supports(String field) {
        int lastUnderscoreIndex = field.lastIndexOf('_');
        return lastUnderscoreIndex > 0
                ? getFields().contains(field.substring(0, lastUnderscoreIndex))
                : getFields().contains(field);
    }

    default Mono<Map<ULong, Map<String, Object>>> applyEagerFiltering(
            Map<ULong, Map<String, Object>> resolvedMap, Boolean eager, List<String> eagerFields) {
        if (eager == null || !eager) return Mono.just(resolvedMap);
        if (eagerFields == null || eagerFields.isEmpty()) return Mono.just(resolvedMap);

        Set<String> eagerFieldSet = new HashSet<>(eagerFields);

        Map<ULong, Map<String, Object>> filtered = resolvedMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                    Map<String, Object> originalMap = entry.getValue();
                    return originalMap.keySet().stream()
                            .filter(eagerFieldSet::contains)
                            .collect(Collectors.toMap(key -> key, originalMap::get, (a, b) -> b, LinkedHashMap::new));
                }));
        return Mono.just(filtered);
    }

    Mono<Map<ULong, Map<String, Object>>> resolveBatch(
            Set<ULong> idsToResolve, MultiValueMap<String, String> queryParams);
}
