package com.fincity.saas.entity.processor.dao.product;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_PRODUCT_TICKET_RU_RULES;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.security.model.EntityProcessorUser;
import com.fincity.saas.entity.processor.dao.rule.BaseRuleDAO;
import com.fincity.saas.entity.processor.dto.product.ProductTicketRuRule;
import com.fincity.saas.entity.processor.dto.rule.BaseUserDistributionDto;
import com.fincity.saas.entity.processor.dto.rule.TicketRuUserDistribution;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTicketRuUserDistributions;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTicketRuRulesRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jooq.Record;
import org.jooq.SelectJoinStep;
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

        if (user == null) return Flux.empty();

        AbstractCondition userCondition = this.buildUserCondition(user);

        AbstractCondition conditionWithAppAndClient = super.addAppCodeAndClientCode(userCondition, access);

        SelectJoinStep<Record> selectJoinStep = super.dslContext
                .select(this.table.fields())
                .from(this.table)
                .join(EntityProcessorTicketRuUserDistributions.ENTITY_PROCESSOR_TICKET_RU_USER_DISTRIBUTIONS)
                .on(this.idField.eq(
                        EntityProcessorTicketRuUserDistributions.ENTITY_PROCESSOR_TICKET_RU_USER_DISTRIBUTIONS
                                .RULE_ID));

        return super.filter(conditionWithAppAndClient, selectJoinStep).flatMapMany(matchCond -> {
            var allRulesQuery = selectJoinStep.where(matchCond.and(super.isActiveTrue()));

            if (Boolean.TRUE.equals(isEdit))
                allRulesQuery = allRulesQuery.and(ENTITY_PROCESSOR_PRODUCT_TICKET_RU_RULES.CAN_EDIT.isTrue());

            return Flux.from(allRulesQuery.groupBy(this.idField)).map(rec -> rec.into(this.pojoClass));
        });
    }

    private AbstractCondition buildUserCondition(EntityProcessorUser user) {

        List<AbstractCondition> conditions = new ArrayList<>();

        if (user.getId() != null)
            conditions.add(FilterCondition.make(BaseUserDistributionDto.Fields.userId, ULong.valueOf(user.getId())));

        if (user.getRoleId() != null)
            conditions.add(
                    FilterCondition.make(BaseUserDistributionDto.Fields.roleId, ULong.valueOf(user.getRoleId())));

        if (user.getDesignationId() != null)
            conditions.add(FilterCondition.make(
                    BaseUserDistributionDto.Fields.designationId, ULong.valueOf(user.getDesignationId())));

        if (user.getDepartmentId() != null)
            conditions.add(FilterCondition.make(
                    BaseUserDistributionDto.Fields.departmentId, ULong.valueOf(user.getDepartmentId())));

        Set<Long> profileIds = user.getProfileIds();
        if (profileIds != null && !profileIds.isEmpty())
            conditions.add(new FilterCondition()
                    .setField(BaseUserDistributionDto.Fields.profileId)
                    .setOperator(FilterConditionOperator.IN)
                    .setMultiValue(profileIds.stream().map(ULong::valueOf).toList()));

        return conditions.isEmpty() ? null : ComplexCondition.or(conditions);
    }
}
