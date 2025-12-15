package com.fincity.saas.entity.processor.service.rule;

import org.springframework.stereotype.Service;

import com.fincity.saas.entity.processor.dao.rule.TicketCUserDistributionDAO;
import com.fincity.saas.entity.processor.dto.rule.TicketCUserDistribution;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.functions.annotations.IgnoreGeneration;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketCUserDistributionsRecord;

@Service
@IgnoreGeneration
public class TicketCUserDistributionService
        extends
        BaseUserDistributionService<EntityProcessorTicketCUserDistributionsRecord, TicketCUserDistribution, TicketCUserDistributionDAO> {

    private static final String TICKET_C_USER_DISTRIBUTION = "ticketCUserDistribution";

    @Override
    protected String getCacheName() {
        return TICKET_C_USER_DISTRIBUTION;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.TICKET_C_USER_DISTRIBUTION;
    }
}
