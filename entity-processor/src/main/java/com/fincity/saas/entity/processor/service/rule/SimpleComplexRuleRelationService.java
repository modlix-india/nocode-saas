package com.fincity.saas.entity.processor.service.rule;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.processor.dao.rule.SimpleComplexRuleRelationDAO;
import com.fincity.saas.entity.processor.dto.rule.SimpleComplexRuleRelation;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorSimpleComplexRuleRelationsRecord;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;

@Service
public class SimpleComplexRuleRelationService
        extends BaseUpdatableService<
                EntityProcessorSimpleComplexRuleRelationsRecord,
                SimpleComplexRuleRelation,
                SimpleComplexRuleRelationDAO> {

    private static final String SIMPLE_COMPLEX_RULE_RELATION = "simpleComplexRuleRelation";

    @Override
    protected String getCacheName() {
        return SIMPLE_COMPLEX_RULE_RELATION;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.SIMPLE_COMPLEX_CONDITION_RELATION;
    }

    @Override
    public Mono<SimpleComplexRuleRelation> create(SimpleComplexRuleRelation entity) {
        return super.hasAccess().flatMap(hasAccess -> this.createInternal(entity, hasAccess.getT1()));
    }

    public Mono<SimpleComplexRuleRelation> createInternal(
            SimpleComplexRuleRelation entity, Tuple3<String, String, ULong> access) {

        entity.setAppCode(access.getT1());
        entity.setClientCode(access.getT2());

        entity.setCreatedBy(access.getT3());

        return super.create(entity);
    }

    public Mono<Integer> deleteByComplexRuleId(ULong complexRuleId) {
        return FlatMapUtil.flatMapMono(
                () -> this.dao.findByComplexConditionId(complexRuleId).collectList(), relations -> {
                    if (relations.isEmpty()) return Mono.just(0);

                    return this.evictCaches(Flux.fromIterable(relations))
                            .then(this.dao.deleteByComplexConditionId(complexRuleId));
                });
    }

    public Flux<ULong> getSimpleRuleIdsByComplexRuleId(ULong complexRuleId) {
        return this.dao.findByComplexConditionId(complexRuleId).map(SimpleComplexRuleRelation::getSimpleConditionId);
    }

    public Flux<ULong> getSimpleRuleIdsByComplexRuleIds(Flux<ULong> complexRuleIds) {
        return complexRuleIds.flatMap(this::getSimpleRuleIdsByComplexRuleId).distinct();
    }
}
