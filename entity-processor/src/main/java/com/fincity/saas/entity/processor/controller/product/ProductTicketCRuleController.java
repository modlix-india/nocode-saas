package com.fincity.saas.entity.processor.controller.product;

import com.fincity.saas.entity.processor.controller.rule.BaseRuleController;
import com.fincity.saas.entity.processor.dao.product.ProductTicketCRuleDAO;
import com.fincity.saas.entity.processor.dto.product.ProductTicketCRule;
import com.fincity.saas.entity.processor.dto.rule.TicketCUserDistribution;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTicketCRulesRecord;
import com.fincity.saas.entity.processor.service.product.ProductTicketCRuleService;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/products/tickets/c/rules")
public class ProductTicketCRuleController
        extends BaseRuleController<
                EntityProcessorProductTicketCRulesRecord,
                ProductTicketCRule,
                ProductTicketCRuleDAO,
                TicketCUserDistribution,
                ProductTicketCRuleService> {

    @PostMapping("/multi")
    public Mono<ResponseEntity<List<ProductTicketCRule>>> createMultiple(
            @RequestBody ProductTicketCRule rule, @RequestParam List<ULong> stageIds) {
        return this.service.createMultiple(rule, stageIds).collectList().map(ResponseEntity::ok);
    }
}
