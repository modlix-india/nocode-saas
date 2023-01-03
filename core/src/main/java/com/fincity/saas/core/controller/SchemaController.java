package com.fincity.saas.core.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import com.fincity.saas.core.document.CoreSchema;
import com.fincity.saas.core.repository.CoreSchemaDocumentRepository;
import com.fincity.saas.core.service.CoreSchemaService;

@RestController
@RequestMapping("api/core/schemas")
public class SchemaController
        extends AbstractOverridableDataController<CoreSchema, CoreSchemaDocumentRepository, CoreSchemaService> {
}
