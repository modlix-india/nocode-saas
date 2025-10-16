package com.fincity.saas.entity.processor.dao;

import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.ProductWalkInForm;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductWalkInFormRecord;
import org.springframework.stereotype.Component;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorProductWalkInForm.ENTITY_PROCESSOR_PRODUCT_WALK_IN_FORM;

@Component
public class ProductWalkInFormDAO extends BaseUpdatableDAO<EntityProcessorProductWalkInFormRecord,ProductWalkInForm>{

    public ProductWalkInFormDAO(){
        super(
                ProductWalkInForm.class,
                ENTITY_PROCESSOR_PRODUCT_WALK_IN_FORM,
                ENTITY_PROCESSOR_PRODUCT_WALK_IN_FORM.ID
        );
    }
}
