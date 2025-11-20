package com.fincity.saas.entity.processor.dao.rule;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_TICKET_DUPLICATION_RULES;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.entity.processor.dto.rule.NoOpUserDistribution;
import com.fincity.saas.entity.processor.dto.rule.TicketDuplicationRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketDuplicationRulesRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
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

        if (source == null && subSource == null) return super.getRules(null, access, productId, productTemplateId);

        AbstractCondition sourceCondition = FilterCondition.make(TicketDuplicationRule.Fields.source, source);

        if (subSource == null) return super.getRules(sourceCondition, access, productId, productTemplateId);

        return super.getRules(
                ComplexCondition.and(
                        sourceCondition, FilterCondition.make(TicketDuplicationRule.Fields.subSource, subSource)),
                access,
                productId,
                productTemplateId);
    }
}
