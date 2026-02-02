package com.fincity.saas.entity.processor.controller.form;

import com.fincity.saas.entity.processor.dao.form.ProductWalkInFormDAO;
import com.fincity.saas.entity.processor.dto.form.ProductWalkInForm;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductWalkInFormsRecord;
import com.fincity.saas.entity.processor.service.form.ProductWalkInFormService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/entity/processor/products/forms")
public class ProductWalkInFormController
        extends BaseWalkInFormController<
                EntityProcessorProductWalkInFormsRecord,
                ProductWalkInForm,
                ProductWalkInFormDAO,
                ProductWalkInFormService> {}
