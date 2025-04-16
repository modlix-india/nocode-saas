package com.fincity.saas.entity.processor.dao;

import com.fincity.saas.commons.jooq.flow.dao.AbstractFlowUpdatableDAO;
import com.fincity.saas.entity.processor.dto.BaseProcessorDto;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;

public abstract class BaseProcessorDAO<R extends UpdatableRecord<R>, D extends BaseProcessorDto<D>>
        extends AbstractFlowUpdatableDAO<R, ULong, D> {

    protected BaseProcessorDAO(Class<D> flowPojoClass, Table<R> flowTable, Field<ULong> flowTableId) {
        super(flowPojoClass, flowTable, flowTableId);
    }
}
