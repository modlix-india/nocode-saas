package com.fincity.saas.entity.processor.service.rule;

import com.fincity.saas.entity.processor.dao.rule.TicketCUserDistributionDAO;
import com.fincity.saas.entity.processor.dto.rule.TicketCUserDistribution;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketCUserDistributionsRecord;
import org.springframework.stereotype.Service;

@Service
public class TicketCUserDistributionService
        extends BaseUserDistributionService<
                EntityProcessorTicketCUserDistributionsRecord, TicketCUserDistribution, TicketCUserDistributionDAO> {

    private static final String TICKET_C_USER_DISTRIBUTION = "ticketCUserDistribution";

    @Override
    protected String getCacheName() {
        return TICKET_C_USER_DISTRIBUTION;
    }
}
