package com.fincity.saas.entity.processor.relations;

import com.fincity.saas.entity.processor.relations.resolvers.RelationResolver;
import com.fincity.saas.entity.processor.relations.resolvers.RelationResolverRegistry;
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
import reactor.core.publisher.Mono;

@Service
public class RecordEnrichmentService {

    private final RelationResolverRegistry resolverRegistry;

    public RecordEnrichmentService(RelationResolverRegistry resolverRegistry) {
        this.resolverRegistry = resolverRegistry;
    }

    public Mono<Map<String, Object>> enrich(
            Map<String, Object> rec, SetValuedMap<Class<? extends RelationResolver>, String> resolverFieldsMap) {
        if (rec == null || rec.isEmpty()) return Mono.empty();

        SetValuedMap<RelationResolver, String> resolverFields = resolverRegistry.getResolverFields(resolverFieldsMap);
        if (resolverFields.isEmpty()) return Mono.just(rec);

        return this.processEnrichment(List.of(rec), resolverFields).thenReturn(rec);
    }

    public Mono<List<Map<String, Object>>> enrich(
            List<Map<String, Object>> recs, SetValuedMap<Class<? extends RelationResolver>, String> resolverFieldsMap) {
        if (recs == null || recs.isEmpty()) return Mono.empty();

        SetValuedMap<RelationResolver, String> resolverFields = resolverRegistry.getResolverFields(resolverFieldsMap);
        if (resolverFields.isEmpty()) return Mono.just(recs);

        return this.processEnrichment(recs, resolverFields).thenReturn(recs);
    }

    private Mono<Void> processEnrichment(
            List<Map<String, Object>> records, SetValuedMap<RelationResolver, String> resolverFields) {

        return Mono.just(resolverFields).flatMap(resolverMap -> {
            List<Mono<Void>> resolverOperations = resolverMap.asMap().entrySet().stream()
                    .map(entry -> {
                        Map<String, Set<ULong>> fieldIdsMap = this.extractIdsForRecords(records, entry.getValue());
                        if (fieldIdsMap.isEmpty()) return Mono.<Void>empty();

                        Set<ULong> allIds = this.collectAllIds(fieldIdsMap);
                        if (allIds.isEmpty()) return Mono.<Void>empty();

                        return resolverRegistry
                                .resolveBatch(entry.getKey().getClass(), allIds)
                                .flatMap(resolvedData -> {
                                    this.enrichRecordsWithResolvedData(records, fieldIdsMap, resolvedData);
                                    return Mono.<Void>empty();
                                })
                                .then();
                    })
                    .toList();

            return Mono.when(resolverOperations);
        });
    }

    private Set<ULong> collectAllIds(Map<String, Set<ULong>> fieldIdsMap) {
        return fieldIdsMap.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
    }

    private Map<String, Set<ULong>> extractIdsForRecords(List<Map<String, Object>> recs, Collection<String> fields) {
        Map<String, Set<ULong>> fieldIdsMap = new HashMap<>();

        boolean isSingleRecord = recs.size() == 1;
        Map<String, Object> sRec = isSingleRecord ? recs.getFirst() : null;

        for (String field : fields) {
            Set<ULong> ids = new HashSet<>();

            if (isSingleRecord) {
                this.extractIdFromRecord(sRec, field, ids);
            } else {
                recs.forEach(rec -> this.extractIdFromRecord(rec, field, ids));
            }

            if (!ids.isEmpty()) fieldIdsMap.put(field, ids);
        }

        return fieldIdsMap;
    }

    private void extractIdFromRecord(Map<String, Object> rec, String field, Set<ULong> ids) {
        Object idObj = rec.get(field);
        if (idObj instanceof ULong id) ids.add(id);
    }

    private void enrichRecordsWithResolvedData(
            List<Map<String, Object>> recs,
            Map<String, Set<ULong>> fieldIdsMap,
            Map<ULong, Map<String, Object>> resolvedData) {

        boolean isSingleRecord = recs.size() == 1;
        Map<String, Object> sRec = isSingleRecord ? recs.getFirst() : null;

        for (Map.Entry<String, Set<ULong>> entry : fieldIdsMap.entrySet()) {
            String field = entry.getKey();

            if (isSingleRecord) {
                this.enrichSingleRecord(sRec, field, resolvedData);
            } else {
                recs.forEach(rec -> this.enrichSingleRecord(rec, field, resolvedData));
            }
        }
    }

    private void enrichSingleRecord(
            Map<String, Object> rec, String field, Map<ULong, Map<String, Object>> resolvedData) {
        Object idObj = rec.get(field);
        if (idObj instanceof ULong id) {
            Map<String, Object> resolvedInfo = resolvedData.get(id);
            if (resolvedInfo != null) rec.put(field, resolvedInfo);
        }
    }
}
