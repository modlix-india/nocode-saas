package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_VALUE_TEMPLATES;

import com.fincity.saas.entity.processor.dao.base.BaseDAO;
import com.fincity.saas.entity.processor.dto.ProductTemplate;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorValueTemplatesRecord;
import org.springframework.stereotype.Component;

@Component
public class ProductTemplateDAO extends BaseDAO<EntityProcessorValueTemplatesRecord, ProductTemplate> {

    protected ProductTemplateDAO() {
        super(ProductTemplate.class, ENTITY_PROCESSOR_VALUE_TEMPLATES, ENTITY_PROCESSOR_VALUE_TEMPLATES.ID);
    }
}
