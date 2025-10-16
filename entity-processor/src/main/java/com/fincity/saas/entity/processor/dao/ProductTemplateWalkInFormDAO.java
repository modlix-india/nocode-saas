package com.fincity.saas.entity.processor.dao;

import com.fincity.saas.entity.processor.dto.ProductTemplateWalkInForm;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTemplatesWalkInFormRecord;
import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import org.springframework.stereotype.Component;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorProductTemplatesWalkInForm.ENTITY_PROCESSOR_PRODUCT_TEMPLATES_WALK_IN_FORM;

@Component
public class ProductTemplateWalkInFormDAO extends BaseUpdatableDAO<EntityProcessorProductTemplatesWalkInFormRecord, ProductTemplateWalkInForm> {

    public ProductTemplateWalkInFormDAO(){
        super(
                ProductTemplateWalkInForm.class,
                ENTITY_PROCESSOR_PRODUCT_TEMPLATES_WALK_IN_FORM,
                ENTITY_PROCESSOR_PRODUCT_TEMPLATES_WALK_IN_FORM.ID
               );
    }
}
