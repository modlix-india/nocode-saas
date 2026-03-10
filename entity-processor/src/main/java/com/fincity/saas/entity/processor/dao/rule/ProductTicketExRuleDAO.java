package com.fincity.saas.entity.processor.dao.rule;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorProductTicketExRules.ENTITY_PROCESSOR_PRODUCT_TICKET_EX_RULES;

import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.rule.ProductTicketExRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTicketExRulesRecord;
import java.util.List;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class ProductTicketExRuleDAO
        extends BaseUpdatableDAO<EntityProcessorProductTicketExRulesRecord, ProductTicketExRule> {

    protected ProductTicketExRuleDAO() {
        super(
                ProductTicketExRule.class,
                ENTITY_PROCESSOR_PRODUCT_TICKET_EX_RULES,
                ENTITY_PROCESSOR_PRODUCT_TICKET_EX_RULES.ID);
    }

    public Mono<List<ProductTicketExRule>> findActiveByAppCodeAndClientCode(String appCode, String clientCode) {

        return Flux.from(this.dslContext.selectFrom(ENTITY_PROCESSOR_PRODUCT_TICKET_EX_RULES)
                        .where(DSL.and(
                                ENTITY_PROCESSOR_PRODUCT_TICKET_EX_RULES.APP_CODE.eq(appCode),
                                ENTITY_PROCESSOR_PRODUCT_TICKET_EX_RULES.CLIENT_CODE.eq(clientCode),
                                ENTITY_PROCESSOR_PRODUCT_TICKET_EX_RULES.IS_ACTIVE.eq((byte) 1))))
                .map(e -> e.into(this.pojoClass))
                .collectList();
    }
}
