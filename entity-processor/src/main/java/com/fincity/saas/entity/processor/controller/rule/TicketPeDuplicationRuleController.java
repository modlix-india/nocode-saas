package com.fincity.saas.entity.processor.controller.rule;

import com.fincity.saas.entity.processor.controller.base.BaseUpdatableController;
import com.fincity.saas.entity.processor.dao.rule.TicketPeDuplicationRuleDAO;
import com.fincity.saas.entity.processor.dto.rule.TicketPeDuplicationRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketPeDuplicationRulesRecord;
import com.fincity.saas.entity.processor.service.rule.TicketPeDuplicationRuleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/tickets/duplicate/pe/rules")
public class TicketPeDuplicationRuleController
        extends BaseUpdatableController<
                EntityProcessorTicketPeDuplicationRulesRecord,
                TicketPeDuplicationRule,
                TicketPeDuplicationRuleDAO,
                TicketPeDuplicationRuleService> {

    @GetMapping("/me")
    public Mono<ResponseEntity<TicketPeDuplicationRule>> getLoggedInRule() {
        return this.service.getLoggedInRule().map(ResponseEntity::ok);
    }
}
