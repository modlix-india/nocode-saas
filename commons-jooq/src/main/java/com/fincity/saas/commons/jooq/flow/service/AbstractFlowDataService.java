package com.fincity.saas.commons.jooq.flow.service;

import com.fincity.saas.commons.jooq.flow.dao.AbstractFlowDAO;
import com.fincity.saas.commons.jooq.flow.dao.schema.FlowSchemaDAO;
import com.fincity.saas.commons.jooq.flow.dto.AbstractFlowDTO;
import com.fincity.saas.commons.jooq.flow.dto.schema.FlowSchema;
import com.fincity.saas.commons.jooq.flow.service.schema.FlowSchemaService;
import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import java.io.Serializable;
import org.jooq.UpdatableRecord;

public abstract class AbstractFlowDataService<
                R extends UpdatableRecord<R>,
                I extends Serializable,
                D extends AbstractFlowDTO<I, I>,
                O extends AbstractFlowDAO<R, I, D>>
        extends AbstractJOOQDataService<R, I, D, O> {

    protected abstract <
                    R0 extends UpdatableRecord<R0>,
                    I0 extends Serializable,
                    D0 extends FlowSchema<I0, I0>,
                    O0 extends FlowSchemaDAO<R0, I0, D0>,
                    S0 extends FlowSchemaService<R0, I0, D0, O0>>
            S0 getFlowSchemaService();


}
