package com.fincity.saas.entity.processor.controller.rule;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.entity.processor.dao.rule.TicketRUUserDistributionDAO;
import com.fincity.saas.entity.processor.dto.rule.TicketRUUserDistribution;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketRuUserDistributionsRecord;
import com.fincity.saas.entity.processor.service.rule.TicketRUUserDistributionService;

@RestController
@RequestMapping("api/entity/processor/tickets/ru/users/distributions")
public class TicketRUUserDistributionController
        extends BaseUserDistributionController<
                EntityProcessorTicketRuUserDistributionsRecord,
                TicketRUUserDistribution,
                TicketRUUserDistributionDAO,
                TicketRUUserDistributionService> {}
