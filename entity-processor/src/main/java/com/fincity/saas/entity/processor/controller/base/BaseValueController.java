package com.fincity.saas.entity.processor.controller.base;

import com.fincity.saas.entity.processor.dao.base.BaseValueDAO;
import com.fincity.saas.entity.processor.dto.base.BaseValueDto;
import com.fincity.saas.entity.processor.service.base.BaseValueService;
import org.jooq.UpdatableRecord;

public abstract class BaseValueController<
                R extends UpdatableRecord<R>,
                D extends BaseValueDto<D>,
                O extends BaseValueDAO<R, D>,
                S extends BaseValueService<R, D, O>>
        extends BaseUpdatableController<R, D, O, S> {

    public static final String PATH_VALUES = "/values";
    public static final String PATH_VALUES_ORDERED = "/values/ordered";
}
