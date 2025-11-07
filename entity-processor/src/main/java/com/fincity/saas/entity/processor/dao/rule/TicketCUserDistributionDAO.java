package com.fincity.saas.entity.processor.dao.rule;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_TICKET_C_USER_DISTRIBUTIONS;

import com.fincity.saas.entity.processor.dto.rule.TicketCUserDistribution;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketCUserDistributionsRecord;
import org.springframework.stereotype.Component;

@Component
public class TicketCUserDistributionDAO
        extends BaseUserDistributionDAO<EntityProcessorTicketCUserDistributionsRecord, TicketCUserDistribution> {

    protected TicketCUserDistributionDAO() {
        super(
                TicketCUserDistribution.class,
                ENTITY_PROCESSOR_TICKET_C_USER_DISTRIBUTIONS,
                ENTITY_PROCESSOR_TICKET_C_USER_DISTRIBUTIONS.ID);
    }
}
