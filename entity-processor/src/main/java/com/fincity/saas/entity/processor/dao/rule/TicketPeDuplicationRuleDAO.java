package com.fincity.saas.entity.processor.dao.rule;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTicketPeDuplicationRules.ENTITY_PROCESSOR_TICKET_PE_DUPLICATION_RULES;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.rule.TicketPeDuplicationRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketPeDuplicationRulesRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class TicketPeDuplicationRuleDAO
        extends BaseUpdatableDAO<EntityProcessorTicketPeDuplicationRulesRecord, TicketPeDuplicationRule> {

    protected TicketPeDuplicationRuleDAO() {
        super(
                TicketPeDuplicationRule.class,
                ENTITY_PROCESSOR_TICKET_PE_DUPLICATION_RULES,
                ENTITY_PROCESSOR_TICKET_PE_DUPLICATION_RULES.ID);
    }

    public Mono<TicketPeDuplicationRule> readByAppCodeAndClientCode(ProcessorAccess access) {
        return FlatMapUtil.flatMapMono(
                () -> Mono.just(super.addAppCodeAndClientCode(null, access)),
                super::filter,
                (pCondition, jCondition) -> Mono.from(this.dslContext
                                .selectFrom(ENTITY_PROCESSOR_TICKET_PE_DUPLICATION_RULES)
                                .where(jCondition)
                                .limit(1))
                        .map(rec -> rec.into(this.pojoClass)));
    }
}
