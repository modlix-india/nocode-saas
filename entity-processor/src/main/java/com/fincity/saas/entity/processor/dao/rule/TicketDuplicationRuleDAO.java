package com.fincity.saas.entity.processor.dao.rule;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_TICKET_DUPLICATION_RULES;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.entity.processor.dto.rule.NoOpUserDistribution;
import com.fincity.saas.entity.processor.dto.rule.TicketDuplicationRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketDuplicationRulesRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import java.util.ArrayList;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class TicketDuplicationRuleDAO
        extends BaseRuleDAO<EntityProcessorTicketDuplicationRulesRecord, NoOpUserDistribution, TicketDuplicationRule> {

    protected TicketDuplicationRuleDAO() {
        super(
                TicketDuplicationRule.class,
                ENTITY_PROCESSOR_TICKET_DUPLICATION_RULES,
                ENTITY_PROCESSOR_TICKET_DUPLICATION_RULES.ID);
    }

    public Mono<List<TicketDuplicationRule>> getRules(
            ProcessorAccess access, ULong productId, ULong productTemplateId, String source, String subSource) {
        return FlatMapUtil.flatMapMono(
                () -> this.getBaseConditions(null, access, productId, productTemplateId, source, subSource),
                super::filter,
                (pCondition, jCondition) -> Flux.from(
                                dslContext.selectFrom(this.table).where(jCondition.and(super.isActiveTrue())))
                        .map(rec -> rec.into(this.pojoClass))
                        .collectList());
    }

    private AbstractCondition buildSourceCondition(String source, String subSource) {
        AbstractCondition sourceCondition = FilterCondition.make(TicketDuplicationRule.Fields.source, source);

        if (subSource == null) return sourceCondition;

        return ComplexCondition.and(
                sourceCondition, FilterCondition.make(TicketDuplicationRule.Fields.subSource, subSource));
    }

    private Mono<AbstractCondition> getBaseConditions(
            AbstractCondition condition,
            ProcessorAccess access,
            ULong productId,
            ULong productTemplateId,
            String source,
            String subSource) {

        if (source == null) return Mono.empty();

        List<AbstractCondition> conditions = new ArrayList<>(5);
        if (condition != null) conditions.add(condition);

        conditions.add(this.buildSourceCondition(source, subSource));

        AbstractCondition productCondition = super.buildProductCondition(productId, productTemplateId);
        if (productCondition != null) conditions.add(productCondition);

        return super.processorAccessCondition(ComplexCondition.and(conditions), access);
    }
}
