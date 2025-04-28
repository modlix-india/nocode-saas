package com.fincity.saas.entity.processor.dao.rule.base;

import org.jooq.UpdatableRecord;

import com.fincity.saas.entity.processor.dao.base.BaseDAO;
import com.fincity.saas.entity.processor.dto.rule.base.BaseRule;

public class BaseRuleDAO<R extends UpdatableRecord<R>, D extends BaseRule<D>>
		extends BaseDAO<R, D> {
}
