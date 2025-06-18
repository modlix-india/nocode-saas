package com.fincity.saas.entity.processor.relations.resolvers;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.collections4.SetValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class RelationResolverRegistry {

    private final List<RelationResolver> resolvers;

    private final Map<Class<? extends RelationResolver>, RelationResolver> resolverClassMap = new HashMap<>();
    private final Map<String, RelationResolver> fieldToResolverMap = new HashMap<>();
    private SetValuedMap<RelationResolver, String> allResolverFieldsCache;

    public RelationResolverRegistry(List<RelationResolver> resolvers) {
        this.resolvers = resolvers;
    }

    @PostConstruct
    private void initializeMaps() {
        for (RelationResolver resolver : resolvers) {
            resolverClassMap.put(resolver.getClass(), resolver);
            resolver.getFields().forEach(field -> fieldToResolverMap.put(field, resolver));
        }

        this.allResolverFieldsCache = new HashSetValuedHashMap<>();

        resolverClassMap
                .values()
                .forEach(resolver -> this.allResolverFieldsCache.putAll(resolver, resolver.getFields()));
    }

    public Set<String> getSupportedFields() {
        return fieldToResolverMap.keySet();
    }

    public SetValuedMap<RelationResolver, String> getResolverFields(
            SetValuedMap<Class<? extends RelationResolver>, String> resolverFieldsMap) {

        if (resolverFieldsMap == null || resolverFieldsMap.isEmpty()) return this.getAllResolverFields();

        SetValuedMap<RelationResolver, String> result = new HashSetValuedHashMap<>();

        for (Map.Entry<Class<? extends RelationResolver>, String> entry : resolverFieldsMap.entries()) {
            Class<? extends RelationResolver> cls = entry.getKey();
            String allowedField = entry.getValue();

            RelationResolver resolver = resolverClassMap.get(cls);
            if (resolver == null) continue;

            if (allowedField == null || allowedField.isEmpty()) {
                Set<String> fields = allResolverFieldsCache.get(resolver);
                if (fields != null && !fields.isEmpty()) {
                    result.putAll(resolver, fields);
                }
            } else if (resolver.supports(allowedField)) {
                result.put(resolver, allowedField);
            }
        }

        return result;
    }

    public Mono<Map<ULong, Map<String, Object>>> resolveBatch(
            Class<? extends RelationResolver> relationResolver, Set<ULong> idsToResolve, List<String> eagerFields) {
        RelationResolver resolver = resolverClassMap.get(relationResolver);
        if (resolver == null) return Mono.empty();

        return resolver.resolveBatch(idsToResolve, eagerFields);
    }

    public RelationResolver getResolverForField(String field) {
        return fieldToResolverMap.get(field);
    }

    public SetValuedMap<RelationResolver, String> getAllResolverFields() {
        SetValuedMap<RelationResolver, String> result = new HashSetValuedHashMap<>();
        result.putAll(allResolverFieldsCache);
        return result;
    }
}
