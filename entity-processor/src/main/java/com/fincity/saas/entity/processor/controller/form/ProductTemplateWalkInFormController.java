package com.fincity.saas.entity.processor.controller.form;

import com.fincity.saas.entity.processor.dao.form.ProductTemplateWalkInFormDAO;
import com.fincity.saas.entity.processor.dto.form.ProductTemplateWalkInForm;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTemplateWalkInFormsRecord;
import com.fincity.saas.entity.processor.service.form.ProductTemplateWalkInFormService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/entity/processor/products/templates/forms")
public class ProductTemplateWalkInFormController
        extends BaseWalkInFormController<
                EntityProcessorProductTemplateWalkInFormsRecord,
                ProductTemplateWalkInForm,
                ProductTemplateWalkInFormDAO,
                ProductTemplateWalkInFormService> {}
