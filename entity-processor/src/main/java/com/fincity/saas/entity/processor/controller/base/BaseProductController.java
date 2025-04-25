package com.fincity.saas.entity.processor.controller.base;

import com.fincity.saas.entity.processor.dao.base.BaseProductDAO;
import com.fincity.saas.entity.processor.dto.base.BaseProductDto;
import com.fincity.saas.entity.processor.service.base.BaseProductService;
import org.jooq.UpdatableRecord;

public class BaseProductController<
                R extends UpdatableRecord<R>,
                D extends BaseProductDto<D>,
                O extends BaseProductDAO<R, D>,
                S extends BaseProductService<R, D, O>>
        extends BaseController<R, D, O, S> {}
