package com.fincity.saas.entity.processor.dao.rule;

import com.fincity.saas.entity.processor.dao.base.BaseDAO;
import com.fincity.saas.entity.processor.dto.rule.Rule;
import java.util.ArrayList;
import java.util.List;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class RuleDAO<R extends UpdatableRecord<R>, D extends Rule<D>> extends BaseDAO<R, D> {

    private static final String STAGE_ID = "STAGE_ID";
    private static final String IS_DEFAULT = "IS_DEFAULT";

    protected final Field<ULong> entityIdField;
    protected final Field<ULong> stageIdField;
    protected final Field<Boolean> isDefauktField;

    protected RuleDAO(
            Class<D> flowPojoClass, Table<R> flowTable, Field<ULong> flowTableId, Field<ULong> entityIdField) {
        super(flowPojoClass, flowTable, flowTableId);
        this.entityIdField = entityIdField;
        this.stageIdField = flowTable.field(STAGE_ID, ULong.class);
        this.isDefauktField = flowTable.field(IS_DEFAULT, Boolean.class);
    }

    public Flux<D> getRules(String appCode, String clientCode, ULong entityId, ULong stageId) {
        return Flux.from(this.dslContext
                        .selectFrom(this.table)
                        .where(DSL.and(this.getBaseConditions(appCode, clientCode, entityId, stageId, Boolean.FALSE))))
                .map(e -> e.into(super.pojoClass));
    }

    public Mono<D> getDefaultRule(String appCode, String clientCode, ULong entityId) {
        return Mono.from(this.dslContext
                        .selectFrom(this.table)
                        .where(DSL.and(this.getBaseConditions(appCode, clientCode, entityId, null, Boolean.TRUE))))
                .map(e -> e.into(super.pojoClass));
    }

    private List<Condition> getBaseConditions(
            String appCode, String clientCode, ULong entityId, ULong stageId, boolean getDefault) {

        List<Condition> conditions = new ArrayList<>();

        conditions.add(this.appCodeField.eq(appCode));
        conditions.add(this.clientCodeField.eq(clientCode));
        conditions.add(this.entityIdField.eq(entityId));

        Condition stageCondition = this.stageIdField.eq(stageId);

        if (stageId != null) conditions.add(stageCondition);

        if (getDefault) {
            conditions.remove(stageCondition);
            conditions.add(this.isDefauktField.eq(Boolean.TRUE));
        }

        // we always need Active product entities
        conditions.add(super.isActiveTrue());

        return conditions;
    }
}
