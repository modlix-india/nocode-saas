package com.fincity.saas.entity.processor.controller.rule;

import com.fincity.saas.entity.processor.controller.base.BaseController;
import com.fincity.saas.entity.processor.dao.rule.base.RuleConfigDAO;
import com.fincity.saas.entity.processor.dto.rule.base.RuleConfig;
import com.fincity.saas.entity.processor.model.request.rule.RuleConfigRequest;
import com.fincity.saas.entity.processor.service.rule.base.RuleConfigService;
import org.jooq.UpdatableRecord;

public abstract class RuleConfigController<
                T extends RuleConfigRequest,
                R extends UpdatableRecord<R>,
                D extends RuleConfig<D>,
                O extends RuleConfigDAO<R, D>,
                S extends RuleConfigService<T, R, D, O>>
        extends BaseController<R, D, O, S> {}
