package com.modlix.saas.commons2.jooq.flow.service;

import com.modlix.saas.commons2.jooq.flow.dao.AbstractFlowUpdatableDAO;
import com.modlix.saas.commons2.jooq.flow.dto.AbstractFlowUpdatableDTO;
import com.modlix.saas.commons2.jooq.service.AbstractJOOQUpdatableDataService;
import java.io.Serializable;
import org.jooq.UpdatableRecord;

public abstract class AbstractFlowUpdatableService<
                R extends UpdatableRecord<R>,
                I extends Serializable,
                D extends AbstractFlowUpdatableDTO<I, I>,
                O extends AbstractFlowUpdatableDAO<R, I, D>>
        extends AbstractJOOQUpdatableDataService<R, I, D, O> {}
