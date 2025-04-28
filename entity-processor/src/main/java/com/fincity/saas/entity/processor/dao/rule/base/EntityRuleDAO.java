package com.fincity.saas.entity.processor.dao.rule.base;

import com.fincity.saas.entity.processor.dao.base.BaseDAO;
import com.fincity.saas.entity.processor.dto.rule.base.EntityRule;
import com.fincity.saas.entity.processor.enums.rule.RuleType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class EntityRuleDAO<R extends UpdatableRecord<R>, D extends EntityRule<D>> extends BaseDAO<R, D> {

    private static final String ENTITY_ID = "ENTITY_ID";
    private static final String RULE_TYPE = "RULE_TYPE";

    protected final Field<ULong> entityIdField;
    protected final Field<RuleType> ruleTypeField;

    protected EntityRuleDAO(Class<D> flowPojoClass, Table<R> flowTable, Field<ULong> flowTableId) {
        super(flowPojoClass, flowTable, flowTableId);
        this.entityIdField = flowTable.field(ENTITY_ID, ULong.class);
        this.ruleTypeField = flowTable.field(RULE_TYPE, RuleType.class);
    }

    public Mono<List<D>> getAllRules(String appCode, String clientCode, ULong productId, RuleType ruleType) {
        return Flux.from(this.dslContext
                        .selectFrom(this.table)
                        .where(DSL.and(this.getBaseConditions(appCode, clientCode, productId, ruleType))))
                .map(e -> e.into(super.pojoClass))
                .collectSortedList(Comparator.comparing(EntityRule::getRuleOrder));
    }

    private List<Condition> getBaseConditions(String appCode, String clientCode, ULong productId, RuleType ruleType) {

        List<Condition> conditions = new ArrayList<>();

        conditions.add(this.appCodeField.eq(appCode));
        conditions.add(this.clientCodeField.eq(clientCode));
        conditions.add(this.entityIdField.eq(productId));
        conditions.add(this.ruleTypeField.eq(ruleType));

        // we always need Active product entities
        conditions.add(super.isActiveTrue());

        return conditions;
    }
}
