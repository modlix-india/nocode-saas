package com.fincity.saas.entity.processor.controller.base;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQDataController;
import com.fincity.saas.entity.processor.dao.base.BaseDAO;
import com.fincity.saas.entity.processor.dto.base.BaseDto;
import com.fincity.saas.entity.processor.service.base.BaseService;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;

public abstract class BaseController<
                R extends UpdatableRecord<R>,
                D extends BaseDto<D>,
                O extends BaseDAO<R, D>,
                S extends BaseService<R, D, O>>
        extends AbstractJOOQDataController<R, ULong, D, O, S> {}
