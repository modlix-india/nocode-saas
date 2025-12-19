package com.fincity.saas.entity.processor.eager.relations;

import com.fincity.saas.entity.processor.eager.EagerUtil;
import com.fincity.saas.entity.processor.eager.relations.resolvers.RelationResolver;
import com.fincity.saas.entity.processor.eager.relations.resolvers.RelationResolverRegistry;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections4.SetValuedMap;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

@Service
public class RecordEnrichmentService {

    private final RelationResolverRegistry resolverRegistry;

    public RecordEnrichmentService(RelationResolverRegistry resolverRegistry) {
        this.resolverRegistry = resolverRegistry;
    }

    public Mono<Map<String, Object>> enrich(
            Map<String, Object> rec,
            SetValuedMap<Class<? extends RelationResolver>, String> resolverFieldsMap,
            MultiValueMap<String, String> queryParams) {

        if (rec == null || rec.isEmpty()) return Mono.empty();

        Boolean eager = EagerUtil.getIsEagerParams(queryParams);
        if (eager == null || !eager) return Mono.just(rec);

        return this.enrichInternal(List.of(rec), resolverFieldsMap, queryParams).thenReturn(rec);
    }

    public Mono<List<Map<String, Object>>> enrich(
            List<Map<String, Object>> recs,
            SetValuedMap<Class<? extends RelationResolver>, String> resolverFieldsMap,
            MultiValueMap<String, String> queryParams) {

        if (recs == null || recs.isEmpty()) return Mono.just(List.of());
        Boolean eager = EagerUtil.getIsEagerParams(queryParams);
        if (eager == null || !eager) return Mono.just(recs);

        return this.enrichInternal(recs, resolverFieldsMap, queryParams).thenReturn(recs);
    }

    public Mono<Map<String, Object>> enrich(
            Map<String, Object> rec, SetValuedMap<Class<? extends RelationResolver>, String> resolverFieldsMap) {

        if (rec == null || rec.isEmpty()) return Mono.empty();

        return this.enrichInternal(List.of(rec), resolverFieldsMap, null).thenReturn(rec);
    }

    public Mono<List<Map<String, Object>>> enrich(
            List<Map<String, Object>> recs, SetValuedMap<Class<? extends RelationResolver>, String> resolverFieldsMap) {

        if (recs == null || recs.isEmpty()) return Mono.just(List.of());

        return this.enrichInternal(recs, resolverFieldsMap, null).thenReturn(recs);
    }

    private Mono<Void> enrichInternal(
            List<Map<String, Object>> recs,
            SetValuedMap<Class<? extends RelationResolver>, String> resolverFieldsMap,
            MultiValueMap<String, String> queryParams) {

        SetValuedMap<RelationResolver, String> resolverFields = resolverRegistry.getResolverFields(resolverFieldsMap);
        if (resolverFields.isEmpty()) return Mono.empty();

        return this.processEnrichment(recs, resolverFields, queryParams);
    }

    private Mono<Void> processEnrichment(
            List<Map<String, Object>> recs,
            SetValuedMap<RelationResolver, String> resolverFields,
            MultiValueMap<String, String> queryParams) {

        return Mono.just(resolverFields).flatMap(resolverMap -> {
            List<Mono<Void>> resolverOperations = resolverMap.asMap().entrySet().stream()
                    .map(entry -> {
                        Map<String, Set<ULong>> fieldIdsMap = this.extractIdsForRecords(recs, entry.getValue());
                        if (fieldIdsMap.isEmpty()) return Mono.<Void>empty();

                        Set<ULong> allIds = fieldIdsMap.values().stream()
                                .flatMap(Set::stream)
                                .collect(Collectors.toSet());
                        if (allIds.isEmpty()) return Mono.<Void>empty();

                        return resolverRegistry
                                .resolveBatch(entry.getKey().getClass(), allIds, queryParams)
                                .flatMap(resolvedData -> {
                                    this.enrichRecordsWithResolvedData(recs, fieldIdsMap, resolvedData);
                                    return Mono.<Void>empty();
                                })
                                .then();
                    })
                    .toList();

            return Mono.when(resolverOperations);
        });
    }

    private Map<String, Set<ULong>> extractIdsForRecords(List<Map<String, Object>> recs, Collection<String> fields) {
        Map<String, Set<ULong>> fieldIdsMap = new HashMap<>();

        for (String field : fields) {
            Set<ULong> ids = new HashSet<>();

            for (Map<String, Object> rec : recs) {
                Object idObj = rec.get(field);
                if (idObj instanceof ULong id) ids.add(id);
                if (idObj instanceof Number numberId) ids.add(ULong.valueOf(numberId.longValue()));
            }

            if (!ids.isEmpty()) fieldIdsMap.put(field, ids);
        }

        return fieldIdsMap;
    }

    private void enrichRecordsWithResolvedData(
            List<Map<String, Object>> recs,
            Map<String, Set<ULong>> fieldIdsMap,
            Map<ULong, Map<String, Object>> resolvedData) {

        for (Map.Entry<String, Set<ULong>> entry : fieldIdsMap.entrySet())
            recs.forEach(rec -> this.enrichSingleRecord(rec, entry.getKey(), resolvedData));
    }

    private void enrichSingleRecord(
            Map<String, Object> rec, String field, Map<ULong, Map<String, Object>> resolvedData) {
        if (rec.get(field) instanceof ULong id) {
            Map<String, Object> resolvedInfo = resolvedData.get(id);
            if (resolvedInfo != null) rec.put(field, resolvedInfo);
        }

        if (rec.get(field) instanceof Number numberId) {
            Map<String, Object> resolvedInfo = resolvedData.get(ULong.valueOf(numberId.longValue()));
            if (resolvedInfo != null) rec.put(field, resolvedInfo);
        }
    }
}
