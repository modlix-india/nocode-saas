package com.fincity.saas.entity.processor.dao.rule;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_TICKET_RU_USER_DISTRIBUTIONS;

import org.springframework.stereotype.Component;

import com.fincity.saas.entity.processor.dto.rule.TicketRUUserDistribution;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketRuUserDistributionsRecord;

@Component
public class TicketRUUserDistributionDAO
		extends BaseUserDistributionDAO<EntityProcessorTicketRuUserDistributionsRecord, TicketRUUserDistribution> {

	protected TicketRUUserDistributionDAO() {
		super(
				TicketRUUserDistribution.class,
				ENTITY_PROCESSOR_TICKET_RU_USER_DISTRIBUTIONS,
				ENTITY_PROCESSOR_TICKET_RU_USER_DISTRIBUTIONS.ID);
	}}
