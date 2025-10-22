package com.fincity.saas.entity.processor.dao.form;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorProductTemplateWalkInForms.ENTITY_PROCESSOR_PRODUCT_TEMPLATE_WALK_IN_FORMS;

import com.fincity.saas.entity.processor.dto.form.ProductTemplateWalkInForm;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTemplateWalkInFormsRecord;
import org.springframework.stereotype.Component;

@Component
public class ProductTemplateWalkInFormDAO
        extends BaseWalkInFromDAO<EntityProcessorProductTemplateWalkInFormsRecord, ProductTemplateWalkInForm> {

    public ProductTemplateWalkInFormDAO() {
        super(
                ProductTemplateWalkInForm.class,
                ENTITY_PROCESSOR_PRODUCT_TEMPLATE_WALK_IN_FORMS,
                ENTITY_PROCESSOR_PRODUCT_TEMPLATE_WALK_IN_FORMS.ID,
                ENTITY_PROCESSOR_PRODUCT_TEMPLATE_WALK_IN_FORMS.PRODUCT_TEMPLATE_ID);
    }
}
