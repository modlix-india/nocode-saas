package com.modlix.saas.commons2.jooq.flow.dao;

import com.modlix.saas.commons2.jooq.dao.AbstractUpdatableDAO;
import com.modlix.saas.commons2.jooq.flow.dto.AbstractFlowUpdatableDTO;
import java.io.Serializable;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public abstract class AbstractFlowUpdatableDAO<
                R extends UpdatableRecord<R>, I extends Serializable, D extends AbstractFlowUpdatableDTO<I, I>>
        extends AbstractUpdatableDAO<R, I, D> {

    protected AbstractFlowUpdatableDAO(Class<D> pojoClass, Table<R> table, Field<I> idField) {
        super(pojoClass, table, idField);
    }
}
