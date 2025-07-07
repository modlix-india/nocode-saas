package com.fincity.saas.commons.jooq.flow.service;

import java.io.Serializable;

import org.jooq.UpdatableRecord;

import com.fincity.saas.commons.jooq.flow.dao.AbstractFlowDAO;
import com.fincity.saas.commons.jooq.flow.dto.AbstractFlowDTO;
import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;

public class AbstractFlowDataService<
                R extends UpdatableRecord<R>,
                I extends Serializable,
                D extends AbstractFlowDTO<I, I>,
                O extends AbstractFlowDAO<R, I, D>>
        extends AbstractJOOQDataService<R, I, D, O> {}
