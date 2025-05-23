package com.fincity.saas.entity.processor.controller.base;

import com.fincity.saas.entity.processor.dao.base.BaseProcessorDAO;
import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.service.base.BaseProcessorService;
import org.jooq.UpdatableRecord;

public abstract class BaseProcessorController<
                R extends UpdatableRecord<R>,
                D extends BaseProcessorDto<D>,
                O extends BaseProcessorDAO<R, D>,
                S extends BaseProcessorService<R, D, O>>
        extends BaseController<R, D, O, S> {}
