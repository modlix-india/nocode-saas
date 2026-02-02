package com.fincity.saas.entity.processor.controller.rule;

import com.fincity.saas.entity.processor.controller.base.BaseUpdatableController;
import com.fincity.saas.entity.processor.dao.rule.BaseUserDistributionDAO;
import com.fincity.saas.entity.processor.dto.rule.BaseUserDistributionDto;
import com.fincity.saas.entity.processor.service.rule.BaseUserDistributionService;
import org.jooq.UpdatableRecord;

public class BaseUserDistributionController<
                R extends UpdatableRecord<R>,
                D extends BaseUserDistributionDto<D>,
                O extends BaseUserDistributionDAO<R, D>,
                S extends BaseUserDistributionService<R, D, O>>
        extends BaseUpdatableController<R, D, O, S> {}
