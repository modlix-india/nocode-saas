package com.fincity.saas.entity.processor.analytics.dao.base;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;

public abstract class BaseAnalyticsDAO<R extends UpdatableRecord<R>, D extends AbstractDTO<ULong, ULong>>
        extends AbstractDAO<R, ULong, D> {

    protected BaseAnalyticsDAO(Class<D> pojoClass, Table<R> table, Field<ULong> idField) {
        super(pojoClass, table, idField);
    }
}
