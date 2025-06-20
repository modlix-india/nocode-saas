package com.fincity.saas.entity.processor.controller.content.base;

import com.fincity.saas.entity.processor.controller.base.BaseUpdatableController;
import com.fincity.saas.entity.processor.dao.content.base.BaseContentDAO;
import com.fincity.saas.entity.processor.dto.content.base.BaseContentDto;
import com.fincity.saas.entity.processor.service.content.base.BaseContentService;
import org.jooq.UpdatableRecord;

public abstract class BaseContentController<
                R extends UpdatableRecord<R>,
                D extends BaseContentDto<D>,
                O extends BaseContentDAO<R, D>,
                S extends BaseContentService<R, D, O>>
        extends BaseUpdatableController<R, D, O, S> {}
