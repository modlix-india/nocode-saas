package com.fincity.saas.commons.jooq.flow.service;

import java.io.Serializable;

import org.jooq.UpdatableRecord;

import com.fincity.saas.commons.jooq.flow.dao.AbstractFlowUpdatableDAO;
import com.fincity.saas.commons.jooq.flow.dto.AbstractFlowUpdatableDTO;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;

public abstract class AbstractFlowUpdatableService<
                R extends UpdatableRecord<R>,
                I extends Serializable,
                D extends AbstractFlowUpdatableDTO<I, I>,
                O extends AbstractFlowUpdatableDAO<R, I, D>>
        extends AbstractJOOQUpdatableDataService<R, I, D, O> {}
