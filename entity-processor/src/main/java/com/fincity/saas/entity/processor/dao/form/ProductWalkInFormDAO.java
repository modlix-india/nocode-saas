package com.fincity.saas.entity.processor.dao.form;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorProductWalkInForms.ENTITY_PROCESSOR_PRODUCT_WALK_IN_FORMS;

import com.fincity.saas.entity.processor.dto.form.ProductWalkInForm;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductWalkInFormsRecord;
import org.springframework.stereotype.Component;

@Component
public class ProductWalkInFormDAO
        extends BaseWalkInFromDAO<EntityProcessorProductWalkInFormsRecord, ProductWalkInForm> {

    public ProductWalkInFormDAO() {
        super(
                ProductWalkInForm.class,
                ENTITY_PROCESSOR_PRODUCT_WALK_IN_FORMS,
                ENTITY_PROCESSOR_PRODUCT_WALK_IN_FORMS.ID,
                ENTITY_PROCESSOR_PRODUCT_WALK_IN_FORMS.PRODUCT_ID);
    }
}
