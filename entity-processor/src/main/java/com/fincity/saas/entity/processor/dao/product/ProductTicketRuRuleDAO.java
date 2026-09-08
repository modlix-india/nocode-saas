package com.fincity.saas.entity.processor.dao.product;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_PRODUCT_TICKET_RU_RULES;

import com.fincity.saas.commons.security.model.EntityProcessorUser;
import com.fincity.saas.entity.processor.dao.rule.BaseRuleDAO;
import com.fincity.saas.entity.processor.dto.product.ProductTicketRuRule;
import com.fincity.saas.entity.processor.dto.rule.TicketRuUserDistribution;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTicketRuUserDistributions;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTicketRuRulesRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
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

    public Flux<ProductTicketRuRule> getUserConditions(
            ProcessorAccess access, Boolean isEdit, EntityProcessorUser user) {

        var dist = EntityProcessorTicketRuUserDistributions.ENTITY_PROCESSOR_TICKET_RU_USER_DISTRIBUTIONS;

        Condition matchCond = DSL.falseCondition();

        if (user != null) {
            if (user.getId() != null) matchCond = matchCond.or(dist.USER_ID.eq(ULong.valueOf(user.getId())));

            if (user.getRoleId() != null) matchCond = matchCond.or(dist.ROLE_ID.eq(ULong.valueOf(user.getRoleId())));

            if (user.getDesignationId() != null)
                matchCond = matchCond.or(dist.DESIGNATION_ID.eq(ULong.valueOf(user.getDesignationId())));

            if (user.getDepartmentId() != null)
                matchCond = matchCond.or(dist.DEPARTMENT_ID.eq(ULong.valueOf(user.getDepartmentId())));

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
                .where(matchCond
                        .and(super.isActiveTrue())
                        .and(super.appCodeField.eq(access.getAppCode()))
                        .and(super.clientCodeField.eq(access.getEffectiveClientCode())));

        if (Boolean.TRUE.equals(isEdit))
            allRulesQuery = allRulesQuery.and(ENTITY_PROCESSOR_PRODUCT_TICKET_RU_RULES.CAN_EDIT.isTrue());

        return Flux.from(allRulesQuery.groupBy(this.idField)).map(rec -> rec.into(this.pojoClass));
    }

    /**
     * The distribution rows of every read rule covering a product or its template.
     *
     * <p>The inverse of {@link #getUserConditions}: that one starts from a user and finds the rules
     * that match them, this starts from a product and finds who the rules point at. Same two tables,
     * same join, read the other way round.
     *
     * <p>Returns the raw distribution rows rather than user ids, because a row can name a user, a
     * role, a designation, a department or a profile, and expanding the last four takes a call to
     * the security service that a DAO has no business making.
     */
    public Flux<TicketRuUserDistribution> getReadDistributions(
            String appCode, String clientCode, ULong productId, ULong productTemplateId) {

        var dist = EntityProcessorTicketRuUserDistributions.ENTITY_PROCESSOR_TICKET_RU_USER_DISTRIBUTIONS;

        Condition covers = DSL.falseCondition();
        if (productId != null)
            covers = covers.or(ENTITY_PROCESSOR_PRODUCT_TICKET_RU_RULES.PRODUCT_ID.eq(productId));
        if (productTemplateId != null)
            covers = covers.or(
                    ENTITY_PROCESSOR_PRODUCT_TICKET_RU_RULES.PRODUCT_TEMPLATE_ID.eq(productTemplateId));

        if (productId == null && productTemplateId == null) return Flux.empty();

        return Flux.from(super.dslContext
                        .select(dist.fields())
                        .from(dist)
                        .join(ENTITY_PROCESSOR_PRODUCT_TICKET_RU_RULES)
                        .on(ENTITY_PROCESSOR_PRODUCT_TICKET_RU_RULES.ID.eq(dist.RULE_ID))
                        .where(covers.and(super.isActiveTrue())
                                .and(super.appCodeField.eq(appCode))
                                .and(super.clientCodeField.eq(clientCode))))
                .map(rec -> rec.into(TicketRuUserDistribution.class));
    }
}
