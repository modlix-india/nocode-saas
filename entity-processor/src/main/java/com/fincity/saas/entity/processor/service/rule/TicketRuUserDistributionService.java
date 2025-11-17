package com.fincity.saas.entity.processor.service.rule;

import com.fincity.saas.entity.processor.dao.rule.TicketRuUserDistributionDAO;
import com.fincity.saas.entity.processor.dto.rule.TicketRuUserDistribution;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketRuUserDistributionsRecord;
import org.springframework.stereotype.Service;

@Service
public class TicketRuUserDistributionService
        extends BaseUserDistributionService<
                EntityProcessorTicketRuUserDistributionsRecord, TicketRuUserDistribution, TicketRuUserDistributionDAO> {

    private static final String TICKET_RU_USER_DISTRIBUTION = "ticketRUUserDistribution";

    @Override
    protected String getCacheName() {
        return TICKET_RU_USER_DISTRIBUTION;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.TICKET_RU_USER_DISTRIBUTION;
    }
}
