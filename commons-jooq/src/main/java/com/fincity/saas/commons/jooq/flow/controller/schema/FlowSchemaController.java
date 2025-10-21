package com.fincity.saas.commons.jooq.flow.controller.schema;

import java.io.Serializable;

import org.jooq.UpdatableRecord;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQDataController;
import com.fincity.saas.commons.jooq.flow.dao.schema.FlowSchemaDAO;
import com.fincity.saas.commons.jooq.flow.dto.schema.FlowSchema;
import com.fincity.saas.commons.jooq.flow.service.schema.FlowSchemaService;

public abstract class FlowSchemaController<
                R extends UpdatableRecord<R>,
                I extends Serializable,
                D extends FlowSchema<I, I>,
                O extends FlowSchemaDAO<R, I, D>,
                S extends FlowSchemaService<R, I, D, O>>
        extends AbstractJOOQDataController<R, I, D, O, S> {


}
