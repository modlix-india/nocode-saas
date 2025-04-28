package com.fincity.saas.entity.processor.service.rule;

import com.fincity.saas.entity.processor.dao.rule.RuleDAO;
import com.fincity.saas.entity.processor.dto.rule.Rule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorRulesRecord;
import com.fincity.saas.entity.processor.service.base.BaseService;
import org.springframework.stereotype.Service;

@Service
public class RuleService extends BaseService<EntityProcessorRulesRecord, Rule, RuleDAO> {

    private static final String RULE = "rule";

    @Override
    protected String getCacheName() {
        return RULE;
    }
}
