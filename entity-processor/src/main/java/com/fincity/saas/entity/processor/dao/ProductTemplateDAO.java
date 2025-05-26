package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_PRODUCT_TEMPLATES;

import com.fincity.saas.entity.processor.dao.base.BaseDAO;
import com.fincity.saas.entity.processor.dto.ProductTemplate;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTemplatesRecord;
import org.springframework.stereotype.Component;

@Component
public class ProductTemplateDAO extends BaseDAO<EntityProcessorProductTemplatesRecord, ProductTemplate> {

    protected ProductTemplateDAO() {
        super(ProductTemplate.class, ENTITY_PROCESSOR_PRODUCT_TEMPLATES, ENTITY_PROCESSOR_PRODUCT_TEMPLATES.ID);
    }
}
