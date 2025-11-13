package com.fincity.saas.entity.processor.dao.rule;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_TICKET_RU_USER_DISTRIBUTIONS;

import com.fincity.saas.entity.processor.dto.rule.TicketRuUserDistribution;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketRuUserDistributionsRecord;
import org.springframework.stereotype.Component;

@Component
public class TicketRuUserDistributionDAO
        extends BaseUserDistributionDAO<EntityProcessorTicketRuUserDistributionsRecord, TicketRuUserDistribution> {

    protected TicketRuUserDistributionDAO() {
        super(
                TicketRuUserDistribution.class,
                ENTITY_PROCESSOR_TICKET_RU_USER_DISTRIBUTIONS,
                ENTITY_PROCESSOR_TICKET_RU_USER_DISTRIBUTIONS.ID);
    }
}
