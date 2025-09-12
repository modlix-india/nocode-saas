package com.modlix.saas.commons2.jooq.flow.service;

import java.io.Serializable;

import org.jooq.UpdatableRecord;

import com.modlix.saas.commons2.jooq.flow.dao.AbstractFlowDAO;
import com.modlix.saas.commons2.jooq.flow.dto.AbstractFlowDTO;
import com.modlix.saas.commons2.jooq.service.AbstractJOOQDataService;

public class AbstractFlowDataService<
                R extends UpdatableRecord<R>,
                I extends Serializable,
                D extends AbstractFlowDTO<I, I>,
                O extends AbstractFlowDAO<R, I, D>>
        extends AbstractJOOQDataService<R, I, D, O> {}

