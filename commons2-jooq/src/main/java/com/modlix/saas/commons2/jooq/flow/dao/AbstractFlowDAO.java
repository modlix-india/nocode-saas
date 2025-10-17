package com.modlix.saas.commons2.jooq.flow.dao;

import com.modlix.saas.commons2.jooq.dao.AbstractDAO;
import com.modlix.saas.commons2.jooq.flow.dto.AbstractFlowDTO;
import java.io.Serializable;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class AbstractFlowDAO<R extends UpdatableRecord<R>, I extends Serializable, D extends AbstractFlowDTO<I, I>>
        extends AbstractDAO<R, I, D> {

    protected AbstractFlowDAO(Class<D> flowPojoClass, Table<R> flowTable, Field<I> flowTableId) {
        super(flowPojoClass, flowTable, flowTableId);
    }
}
