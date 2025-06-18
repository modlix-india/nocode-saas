package com.fincity.saas.entity.processor.relations.resolvers;

import java.util.Map;
import java.util.Set;
import org.jooq.types.ULong;
import reactor.core.publisher.Mono;

public interface RelationResolver {

    Set<String> getFields();

    default boolean supports(String field) {
        int lastUnderscoreIndex = field.lastIndexOf('_');
        return lastUnderscoreIndex > 0
                ? getFields().contains(field.substring(0, lastUnderscoreIndex))
                : getFields().contains(field);
    }

    Mono<Map<ULong, Map<String, Object>>> resolveBatch(Set<ULong> idsToResolve);
}
