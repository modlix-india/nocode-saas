package com.fincity.saas.entity.processor.dao.product;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_PRODUCT_TICKET_C_RULES;

import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.entity.processor.dao.rule.BaseRuleDAO;
import com.fincity.saas.entity.processor.dto.product.ProductTicketCRule;
import com.fincity.saas.entity.processor.dto.rule.TicketCUserDistribution;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTicketCRulesRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ProductTicketCRuleDAO
        extends BaseRuleDAO<EntityProcessorProductTicketCRulesRecord, TicketCUserDistribution, ProductTicketCRule> {

    protected ProductTicketCRuleDAO() {
        super(
                ProductTicketCRule.class,
                ENTITY_PROCESSOR_PRODUCT_TICKET_C_RULES,
                ENTITY_PROCESSOR_PRODUCT_TICKET_C_RULES.ID);
    }

    public Mono<List<ProductTicketCRule>> getRules(
            ProcessorAccess access, ULong productId, ULong productTemplateId, ULong stageId) {

        if (stageId == null) return super.getRules(null, access, productId, productTemplateId);

        return super.getRules(
                FilterCondition.make(ProductTicketCRule.Fields.stageId, stageId), access, productId, productTemplateId);
    }
}
