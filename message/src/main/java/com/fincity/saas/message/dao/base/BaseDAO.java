package com.fincity.saas.message.dao.base;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.saas.message.dto.base.BaseDto;
import lombok.Getter;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;

@Getter
public abstract class BaseDAO<R extends UpdatableRecord<R>, D extends BaseDto<D>> extends AbstractDAO<R, ULong, D> {

    private static final String IS_ACTIVE = "IS_ACTIVE";

    protected final Field<Boolean> isActiveField;

    protected BaseDAO(Class<D> flowPojoClass, Table<R> flowTable, Field<ULong> flowTableId) {
        super(flowPojoClass, flowTable, flowTableId);
        this.isActiveField = flowTable.field(IS_ACTIVE, Boolean.class);
    }

    protected Condition isActiveTrue() {
        return isActiveField.eq(Boolean.TRUE);
    }

    protected Condition isActiveFalse() {
        return isActiveField.eq(Boolean.FALSE);
    }

    protected Condition isActive(Boolean isActive) {
        if (isActive == null) return DSL.trueCondition();
        return isActiveField.eq(isActive);
    }

    protected Condition isActiveWithFalse(Boolean isActive) {
        if (isActive == null) return DSL.falseCondition();
        return isActiveField.eq(isActive);
    }
}
