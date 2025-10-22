package com.fincity.saas.entity.processor.service.form;

import com.fincity.saas.entity.processor.dao.form.ProductTemplateWalkInFormDAO;
import com.fincity.saas.entity.processor.dto.form.ProductTemplateWalkInForm;
import com.fincity.saas.entity.processor.enums.AssignmentType;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTemplateWalkInFormsRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ProductTemplateService;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class ProductTemplateWalkInFormService
        extends BaseWalkInFormService<
                EntityProcessorProductTemplateWalkInFormsRecord,
                ProductTemplateWalkInForm,
                ProductTemplateWalkInFormDAO> {

    private static final String PRODUCT_TEMPLATE_WALK_IN_FORM_CACHE = "productTemplateWalkInForm";

    private final ProductTemplateService productTemplateService;

    public ProductTemplateWalkInFormService(ProductTemplateService productTemplateService) {
        this.productTemplateService = productTemplateService;
    }

    @Override
    protected String getCacheName() {
        return PRODUCT_TEMPLATE_WALK_IN_FORM_CACHE;
    }

    @Override
    protected String getProductEntityName() {
        return productTemplateService.getEntityName();
    }

    @Override
    protected Mono<Tuple2<ULong, ULong>> resolveProduct(ProcessorAccess access, Identity productId) {
        return productTemplateService
                .readIdentityWithAccess(access, productId)
                .map(productTemplate -> Tuples.of(productTemplate.getId(), productTemplate.getId()));
    }

	@Override
	protected ProductTemplateWalkInForm create(
			ULong entityId, ULong stageId, ULong statusId, AssignmentType assignmentType) {
		return (ProductTemplateWalkInForm) new ProductTemplateWalkInForm()
				.setProductTemplateId(entityId)
				.setStageId(stageId)
				.setStatusId(statusId)
				.setAssignmentType(assignmentType);
	}


}
