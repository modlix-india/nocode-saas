package com.fincity.saas.entity.processor.service.rule;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.rule.SimpleComplexRuleRelationDAO;
import com.fincity.saas.entity.processor.dto.rule.SimpleComplexRuleRelation;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorSimpleComplexRuleRelationsRecord;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

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
        return super.hasAccess()
                .flatMap(access -> this.createInternal(access, entity))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "SimpleComplexRuleRelationService.create"));
    }

    public Mono<Integer> deleteByComplexRuleId(ULong complexRuleId) {
        return FlatMapUtil.flatMapMono(
                        () -> this.dao.findByComplexConditionId(complexRuleId).collectList(), relations -> {
                            if (relations.isEmpty()) return Mono.just(0);

                            return this.evictCaches(Flux.fromIterable(relations))
                                    .then(this.dao.deleteByComplexConditionId(complexRuleId));
                        })
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "SimpleComplexRuleRelationService.deleteByComplexRuleId"));
    }

    public Flux<ULong> getSimpleRuleIdsByComplexRuleId(ULong complexRuleId) {
        return this.dao
                .findByComplexConditionId(complexRuleId)
                .map(SimpleComplexRuleRelation::getSimpleConditionId)
                .contextWrite(Context.of(
                        LogUtil.METHOD_NAME, "SimpleComplexRuleRelationService.getSimpleRuleIdsByComplexRuleId"));
    }

    public Flux<ULong> getSimpleRuleIdsByComplexRuleIds(Flux<ULong> complexRuleIds) {
        return complexRuleIds
                .flatMap(this::getSimpleRuleIdsByComplexRuleId)
                .distinct()
                .contextWrite(Context.of(
                        LogUtil.METHOD_NAME, "SimpleComplexRuleRelationService.getSimpleRuleIdsByComplexRuleIds"));
    }
}
