package com.fincity.saas.ui.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import com.fincity.saas.ui.document.UISchema;
import com.fincity.saas.ui.repository.UISchemaDocumentRepository;
import com.fincity.saas.ui.service.UISchemaService;

@RestController
@RequestMapping("api/ui/schemas")
public class SchemaController
        extends AbstractOverridableDataController<UISchema, UISchemaDocumentRepository, UISchemaService> {
}
