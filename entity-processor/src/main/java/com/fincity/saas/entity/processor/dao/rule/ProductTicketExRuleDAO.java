package com.fincity.saas.entity.processor.dao.rule;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorProductTicketExRules.ENTITY_PROCESSOR_PRODUCT_TICKET_EX_RULES;

import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.rule.ProductTicketExRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTicketExRulesRecord;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class ProductTicketExRuleDAO
        extends BaseUpdatableDAO<EntityProcessorProductTicketExRulesRecord, ProductTicketExRule> {

    protected ProductTicketExRuleDAO() {
        super(
                ProductTicketExRule.class,
                ENTITY_PROCESSOR_PRODUCT_TICKET_EX_RULES,
                ENTITY_PROCESSOR_PRODUCT_TICKET_EX_RULES.ID);
    }

    public Flux<ProductTicketExRule> findActiveByAppCodeAndClientCode(String appCode, String clientCode) {
        return Flux.from(dslContext
                        .selectFrom(ENTITY_PROCESSOR_PRODUCT_TICKET_EX_RULES)
                        .where(ENTITY_PROCESSOR_PRODUCT_TICKET_EX_RULES.APP_CODE.eq(appCode))
                        .and(ENTITY_PROCESSOR_PRODUCT_TICKET_EX_RULES.CLIENT_CODE.eq(clientCode))
                        .and(ENTITY_PROCESSOR_PRODUCT_TICKET_EX_RULES.IS_ACTIVE.eq(true)))
                .map(rec -> rec.into(ProductTicketExRule.class));
    }
}
