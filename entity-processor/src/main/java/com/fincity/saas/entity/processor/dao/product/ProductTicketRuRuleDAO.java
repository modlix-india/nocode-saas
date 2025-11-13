package com.fincity.saas.entity.processor.dao.product;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_PRODUCT_TICKET_RU_RULES;

import com.fincity.saas.commons.security.model.EntityProcessorUser;
import com.fincity.saas.entity.processor.dao.rule.BaseRuleDAO;
import com.fincity.saas.entity.processor.dto.product.ProductTicketRuRule;
import com.fincity.saas.entity.processor.dto.rule.TicketRuUserDistribution;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTicketRuUserDistributions;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTicketRuRulesRecord;
import java.util.List;
import java.util.Set;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class ProductTicketRuRuleDAO
        extends BaseRuleDAO<EntityProcessorProductTicketRuRulesRecord, TicketRuUserDistribution, ProductTicketRuRule> {

    protected ProductTicketRuRuleDAO() {
        super(
                ProductTicketRuRule.class,
                ENTITY_PROCESSOR_PRODUCT_TICKET_RU_RULES,
                ENTITY_PROCESSOR_PRODUCT_TICKET_RU_RULES.ID);
    }

    public Flux<ProductTicketRuRule> getUserConditions(Boolean isEdit, EntityProcessorUser user) {

        var dist = EntityProcessorTicketRuUserDistributions.ENTITY_PROCESSOR_TICKET_RU_USER_DISTRIBUTIONS;

        Condition matchCond = DSL.falseCondition();

        if (user != null) {
            if (user.getId() != null) {
                matchCond = matchCond.or(dist.USER_ID.eq(ULong.valueOf(user.getId())));
            }
            if (user.getRoleId() != null) {
                matchCond = matchCond.or(dist.ROLE_ID.eq(ULong.valueOf(user.getRoleId())));
            }
            if (user.getDesignationId() != null) {
                matchCond = matchCond.or(dist.DESIGNATION_ID.eq(ULong.valueOf(user.getDesignationId())));
            }
            if (user.getDepartmentId() != null) {
                matchCond = matchCond.or(dist.DEPARTMENT_ID.eq(ULong.valueOf(user.getDepartmentId())));
            }
            Set<Long> profileIds = user.getProfileIds();
            if (profileIds != null && !profileIds.isEmpty()) {
                List<ULong> pid = profileIds.stream().map(ULong::valueOf).toList();
                matchCond = matchCond.or(dist.PROFILE_ID.in(pid));
            }
        }

        var allRulesQuery = super.dslContext
                .select(this.table.fields())
                .from(this.table)
                .join(dist)
                .on(this.idField.eq(dist.RULE_ID))
                .where(matchCond.and(super.isActiveTrue()));

        if (Boolean.TRUE.equals(isEdit))
            allRulesQuery = allRulesQuery.and(ENTITY_PROCESSOR_PRODUCT_TICKET_RU_RULES.CAN_EDIT.isTrue());

        return Flux.from(allRulesQuery.groupBy(this.idField)).map(rec -> rec.into(this.pojoClass));
    }
}
