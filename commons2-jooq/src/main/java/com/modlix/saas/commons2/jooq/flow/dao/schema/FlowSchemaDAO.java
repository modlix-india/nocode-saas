package com.modlix.saas.commons2.jooq.flow.dao.schema;

import com.modlix.saas.commons2.jooq.dao.AbstractUpdatableDAO;
import com.modlix.saas.commons2.jooq.flow.dto.schema.FlowSchema;
import com.modlix.saas.commons2.model.condition.AbstractCondition;
import com.modlix.saas.commons2.model.condition.ComplexCondition;
import com.modlix.saas.commons2.model.condition.FilterCondition;
import java.io.Serializable;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public abstract class FlowSchemaDAO<R extends UpdatableRecord<R>, I extends Serializable, D extends FlowSchema<I, I>>
        extends AbstractUpdatableDAO<R, I, D> {

    protected FlowSchemaDAO(Class<D> pojoClass, Table<R> table, Field<I> idField) {
        super(pojoClass, table, idField);
    }

    public D getFlowSchema(AbstractCondition condition, String dbSchema, String dbTableName) {

        Condition jCOndition = super.filter(this.getFlowSchemaCondition(condition, dbSchema, dbTableName, null));

        Record rec = this.dslContext.selectFrom(this.table).where(jCOndition).fetchOne();

        if (rec == null) return null;

        return rec.into(this.pojoClass);
    }

    public D getFlowSchema(AbstractCondition condition, String dbSchema, String dbTableName, ULong dbId) {

        Condition jCOndition = super.filter(this.getFlowSchemaCondition(condition, dbSchema, dbTableName, dbId));

        Record rec = this.dslContext.selectFrom(this.table).where(jCOndition).fetchOne();

        if (rec == null) return null;

        return rec.into(this.pojoClass);
    }

    public AbstractCondition getFlowSchemaCondition(
            AbstractCondition condition, String dbSchema, String dbTableName, ULong dbId) {

        if (dbId == null)
            return ComplexCondition.and(
                    condition,
                    FilterCondition.make(FlowSchema.Fields.dbSchema, dbSchema),
                    FilterCondition.make(FlowSchema.Fields.dbTableName, dbTableName));

        return ComplexCondition.and(
                condition,
                FilterCondition.make(FlowSchema.Fields.dbSchema, dbSchema),
                FilterCondition.make(FlowSchema.Fields.dbTableName, dbTableName),
                FilterCondition.make(FlowSchema.Fields.dbId, dbId));
    }
}
