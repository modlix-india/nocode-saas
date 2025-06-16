package com.fincity.saas.entity.processor.dao.base;

import com.fincity.saas.commons.jooq.flow.dao.AbstractFlowDAO;
import com.fincity.saas.entity.processor.dto.base.BaseDto;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;

public abstract class BaseDAO<R extends UpdatableRecord<R>, D extends BaseDto<D>> extends AbstractFlowDAO<R, ULong, D> {

    protected BaseDAO(Class<D> flowPojoClass, Table<R> flowTable, Field<ULong> flowTableId) {
        super(flowPojoClass, flowTable, flowTableId);
    }
}
