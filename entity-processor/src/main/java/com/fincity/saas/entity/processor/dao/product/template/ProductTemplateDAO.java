package com.fincity.saas.entity.processor.dao.product.template;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_PRODUCT_TEMPLATES;

import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.product.ProductTemplate;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTemplatesRecord;
import org.springframework.stereotype.Component;

@Component
public class ProductTemplateDAO extends BaseUpdatableDAO<EntityProcessorProductTemplatesRecord, ProductTemplate> {

    protected ProductTemplateDAO() {
        super(ProductTemplate.class, ENTITY_PROCESSOR_PRODUCT_TEMPLATES, ENTITY_PROCESSOR_PRODUCT_TEMPLATES.ID);
    }
}
