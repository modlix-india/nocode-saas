package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_VALUE_TEMPLATES;

import com.fincity.saas.entity.processor.dao.base.BaseDAO;
import com.fincity.saas.entity.processor.dto.ValueTemplate;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorValueTemplatesRecord;
import org.springframework.stereotype.Component;

@Component
public class ValueTemplateDAO extends BaseDAO<EntityProcessorValueTemplatesRecord, ValueTemplate> {

    protected ValueTemplateDAO() {
        super(ValueTemplate.class, ENTITY_PROCESSOR_VALUE_TEMPLATES, ENTITY_PROCESSOR_VALUE_TEMPLATES.ID);
    }
}
