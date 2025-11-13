package com.fincity.saas.entity.processor.service.rule;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class TicketRuRuleExecutionService {

    private final TicketRuUserDistributionService userDistributionService;

    public TicketRuRuleExecutionService(TicketRuUserDistributionService userDistributionService) {
        this.userDistributionService = userDistributionService;
    }

    public Mono<AbstractCondition> getUserTicketReadCondition(ProcessorAccess access) {

        return Mono.empty();
    }
}
