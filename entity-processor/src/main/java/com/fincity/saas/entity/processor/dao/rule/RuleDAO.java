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
    private static final String ORDER = "ORDER";

    protected final Field<ULong> entityIdField;
    protected final Field<ULong> stageIdField;
    protected final Field<Boolean> isDefauktField;
    protected final Field<Integer> orderField;

    protected RuleDAO(
            Class<D> flowPojoClass, Table<R> flowTable, Field<ULong> flowTableId, Field<ULong> entityIdField) {
        super(flowPojoClass, flowTable, flowTableId);
        this.entityIdField = entityIdField;
        this.stageIdField = flowTable.field(STAGE_ID, ULong.class);
        this.isDefauktField = flowTable.field(IS_DEFAULT, Boolean.class);
        this.orderField = flowTable.field(ORDER, Integer.class);
    }

    public Mono<D> getRule(String appCode, String clientCode, ULong entityId, ULong stageId, Integer order) {
        return Mono.from(this.dslContext
                        .selectFrom(this.table)
                        .where(DSL.and(
                                this.getBaseConditions(appCode, clientCode, entityId, stageId, order, Boolean.FALSE)))
                        .limit(1))
                .map(e -> e.into(super.pojoClass));
    }

    public Flux<D> getRules(String appCode, String clientCode, ULong entityId, ULong stageId) {
        return Flux.from(this.dslContext
                        .selectFrom(this.table)
                        .where(DSL.and(
                                this.getBaseConditions(appCode, clientCode, entityId, stageId, null, Boolean.FALSE))))
                .map(e -> e.into(super.pojoClass));
    }

    public Mono<D> getDefaultRule(String appCode, String clientCode, ULong entityId) {
        return Mono.from(this.dslContext
                        .selectFrom(this.table)
                        .where(DSL.and(
                                this.getBaseConditions(appCode, clientCode, entityId, null, null, Boolean.TRUE))))
                .map(e -> e.into(super.pojoClass));
    }

    private List<Condition> getBaseConditions(
            String appCode, String clientCode, ULong entityId, ULong stageId, Integer order, boolean getDefault) {

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

        if (order != null) conditions.add(this.orderField.eq(order));

        // we always need Active product entities
        conditions.add(super.isActiveTrue());

        return conditions;
    }
}
