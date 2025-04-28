package com.fincity.saas.entity.processor.service.rule.base;

import com.fincity.saas.entity.processor.dao.rule.base.RuleExecutionConfigDAO;
import com.fincity.saas.entity.processor.dto.rule.base.RuleExecutionConfig;
import com.fincity.saas.entity.processor.service.base.BaseService;
import org.jooq.UpdatableRecord;
import org.springframework.stereotype.Service;

@Service
public abstract class RuleExecutionConfigService<
                R extends UpdatableRecord<R>, D extends RuleExecutionConfig<D>, O extends RuleExecutionConfigDAO<R, D>>
        extends BaseService<R, D, O> {}
