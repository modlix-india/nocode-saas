package com.fincity.saas.entity.processor.service.rule;

import com.fincity.saas.entity.processor.dao.rule.TicketRUUserDistributionDAO;
import com.fincity.saas.entity.processor.dto.rule.TicketRUUserDistribution;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketRuUserDistributionsRecord;
import org.springframework.stereotype.Service;

@Service
public class TicketRUUserDistributionService
        extends BaseUserDistributionService<
                EntityProcessorTicketRuUserDistributionsRecord, TicketRUUserDistribution, TicketRUUserDistributionDAO> {

    private static final String TICKET_RU_USER_DISTRIBUTION = "ticketRUUserDistribution";

    @Override
    protected String getCacheName() {
        return TICKET_RU_USER_DISTRIBUTION;
    }
}
