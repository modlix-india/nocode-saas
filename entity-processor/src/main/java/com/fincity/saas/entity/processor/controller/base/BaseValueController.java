package com.fincity.saas.entity.processor.controller.base;

import org.jooq.UpdatableRecord;

import com.fincity.saas.entity.processor.dao.base.BaseValueDAO;
import com.fincity.saas.entity.processor.dto.base.BaseValueDto;
import com.fincity.saas.entity.processor.service.base.BaseValueService;

public abstract class BaseValueController<
                R extends UpdatableRecord<R>,
                D extends BaseValueDto<D>,
                O extends BaseValueDAO<R, D>,
                S extends BaseValueService<R, D, O>>
        extends BaseController<R, D, O, S> {}
