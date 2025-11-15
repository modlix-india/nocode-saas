package com.fincity.saas.entity.processor.controller.rule;

import com.fincity.saas.entity.processor.dao.rule.TicketRuUserDistributionDAO;
import com.fincity.saas.entity.processor.dto.rule.TicketRuUserDistribution;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketRuUserDistributionsRecord;
import com.fincity.saas.entity.processor.service.rule.TicketRuUserDistributionService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/entity/processor/tickets/ru/users/distributions")
public class TicketRUUserDistributionController
        extends BaseUserDistributionController<
                EntityProcessorTicketRuUserDistributionsRecord,
                TicketRuUserDistribution,
                TicketRuUserDistributionDAO,
                TicketRuUserDistributionService> {}
