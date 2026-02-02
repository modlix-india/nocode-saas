package com.fincity.saas.entity.processor.controller.rule;

import com.fincity.saas.entity.processor.dao.rule.TicketDuplicationRuleDAO;
import com.fincity.saas.entity.processor.dto.rule.NoOpUserDistribution;
import com.fincity.saas.entity.processor.dto.rule.TicketDuplicationRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketDuplicationRulesRecord;
import com.fincity.saas.entity.processor.service.rule.TicketDuplicationRuleService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/entity/processor/tickets/duplicate/rules")
public class TicketDuplicationRuleController
        extends BaseRuleController<
                EntityProcessorTicketDuplicationRulesRecord,
                TicketDuplicationRule,
                TicketDuplicationRuleDAO,
                NoOpUserDistribution,
                TicketDuplicationRuleService> {}
