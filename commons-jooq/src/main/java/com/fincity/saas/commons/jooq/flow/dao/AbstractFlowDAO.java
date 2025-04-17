package com.fincity.saas.commons.jooq.flow.dao;

import java.io.Serializable;

import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.springframework.transaction.annotation.Transactional;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.saas.commons.jooq.flow.dto.AbstractFlowDTO;

@Transactional
public class AbstractFlowDAO<R extends UpdatableRecord<R>, I extends Serializable, D extends AbstractFlowDTO<I , I>> extends AbstractDAO<R, I, D> {

	protected AbstractFlowDAO(Class<D> flowPojoClass, Table<R> flowTable, Field<I> flowTableId) {
		super(flowPojoClass, flowTable, flowTableId);
	}

}
