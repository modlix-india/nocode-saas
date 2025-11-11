package com.fincity.saas.entity.processor.controller.rule;

import com.fincity.saas.entity.processor.controller.base.BaseUpdatableController;
import com.fincity.saas.entity.processor.dao.rule.BaseRuleDAO;
import com.fincity.saas.entity.processor.dto.rule.BaseRuleDto;
import com.fincity.saas.entity.processor.dto.rule.BaseUserDistributionDto;
import com.fincity.saas.entity.processor.service.rule.BaseRuleService;
import org.jooq.UpdatableRecord;

public abstract class BaseRuleController<
                R extends UpdatableRecord<R>,
                U extends BaseUserDistributionDto<U>,
                D extends BaseRuleDto<U, D>,
                O extends BaseRuleDAO<R, U, D>,
                S extends BaseRuleService<R, U, D, O>>
        extends BaseUpdatableController<R, D, O, S> {}
