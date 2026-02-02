package com.fincity.saas.entity.processor.controller.rule;

import com.fincity.saas.entity.processor.dao.rule.TicketCUserDistributionDAO;
import com.fincity.saas.entity.processor.dto.rule.TicketCUserDistribution;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketCUserDistributionsRecord;
import com.fincity.saas.entity.processor.service.rule.TicketCUserDistributionService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/entity/processor/tickets/c/users/distributions")
public class TicketCUserDistributionController
        extends BaseUserDistributionController<
                EntityProcessorTicketCUserDistributionsRecord,
                TicketCUserDistribution,
                TicketCUserDistributionDAO,
                TicketCUserDistributionService> {}
