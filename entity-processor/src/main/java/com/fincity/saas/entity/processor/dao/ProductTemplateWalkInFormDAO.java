package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorProductTemplatesWalkInForms.ENTITY_PROCESSOR_PRODUCT_TEMPLATES_WALK_IN_FORMS;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.ProductTemplateWalkInForm;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTemplatesWalkInFormsRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ProductTemplateWalkInFormDAO
        extends BaseUpdatableDAO<EntityProcessorProductTemplatesWalkInFormsRecord, ProductTemplateWalkInForm> {

    public ProductTemplateWalkInFormDAO() {
        super(
                ProductTemplateWalkInForm.class,
                ENTITY_PROCESSOR_PRODUCT_TEMPLATES_WALK_IN_FORMS,
                ENTITY_PROCESSOR_PRODUCT_TEMPLATES_WALK_IN_FORMS.ID);
    }

    public Mono<ProductTemplateWalkInForm> findByAppClientAndProductTemplate(
            ProcessorAccess access, ULong productTemplateId) {

        return FlatMapUtil.flatMapMono(
                () -> super.processorAccessCondition(
                        FilterCondition.make(ProductTemplateWalkInForm.Fields.productTemplateId, productTemplateId),
                        access),
                super::filter,
                (abstractCondition, jooqCondition) -> Mono.from(
                                dslContext.selectFrom(this.table).where(jooqCondition.and(super.isActiveTrue())))
                        .map(record -> record.into(ProductTemplateWalkInForm.class)));
    }
}
