package com.fincity.saas.commons.jooq.flow.dao;

import java.io.Serializable;

import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.springframework.transaction.annotation.Transactional;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.commons.jooq.flow.dto.AbstractFlowUpdatableDTO;

@Transactional
public abstract class AbstractFlowUpdatableDAO<
                R extends UpdatableRecord<R>, I extends Serializable, D extends AbstractFlowUpdatableDTO<I, I>>
        extends AbstractUpdatableDAO<R, I, D> {

    private static final String APP_CODE = "APP_CODE";
    private static final String CLIENT_CODE = "CLIENT_CODE";

    protected final Field<String> appCodeField;
    protected final Field<String> clientCodeField;

    protected AbstractFlowUpdatableDAO(Class<D> pojoClass, Table<R> table, Field<I> idField) {
        super(pojoClass, table, idField);
        this.appCodeField = table.field(APP_CODE, String.class);
        this.clientCodeField = table.field(CLIENT_CODE, String.class);
    }
}
