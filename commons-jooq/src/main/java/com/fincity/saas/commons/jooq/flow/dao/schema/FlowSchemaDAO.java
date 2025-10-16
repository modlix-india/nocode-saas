package com.fincity.saas.commons.jooq.flow.dao.schema;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.commons.jooq.flow.dto.schema.FlowSchema;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import java.io.Serializable;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Transactional
public abstract class FlowSchemaDAO<R extends UpdatableRecord<R>, I extends Serializable, D extends FlowSchema<I, I>>
        extends AbstractUpdatableDAO<R, I, D> {

    protected FlowSchemaDAO(Class<D> pojoClass, Table<R> table, Field<I> idField) {
        super(pojoClass, table, idField);
    }

    public Mono<D> getFlowSchema(AbstractCondition condition, String dbSchema, String dbTableName) {
        return FlatMapUtil.flatMapMono(
                () -> this.getFlowSchemaCondition(condition, dbSchema, dbTableName, null),
                super::filter,
                (bCondition, jCondition) -> Mono.from(
                                this.dslContext.selectFrom(this.table).where(jCondition))
                        .map(rec -> rec.into(this.pojoClass)));
    }

    public Mono<D> getFlowSchema(AbstractCondition condition, String dbSchema, String dbTableName, ULong dbId) {
        return FlatMapUtil.flatMapMono(
                () -> this.getFlowSchemaCondition(condition, dbSchema, dbTableName, dbId),
                super::filter,
                (bCondition, jCondition) -> Mono.from(
                                this.dslContext.selectFrom(this.table).where(jCondition))
                        .map(rec -> rec.into(this.pojoClass)));
    }

    public Mono<AbstractCondition> getFlowSchemaCondition(
            AbstractCondition condition, String dbSchema, String dbTableName, ULong dbId) {

        if (dbId == null)
            return Mono.just(ComplexCondition.and(
                    condition,
                    FilterCondition.make(FlowSchema.Fields.dbSchema, dbSchema),
                    FilterCondition.make(FlowSchema.Fields.dbTableName, dbTableName)));

        return Mono.just(ComplexCondition.and(
                condition,
                FilterCondition.make(FlowSchema.Fields.dbSchema, dbSchema),
                FilterCondition.make(FlowSchema.Fields.dbTableName, dbTableName),
                FilterCondition.make(FlowSchema.Fields.dbId, dbId)));
    }
}
