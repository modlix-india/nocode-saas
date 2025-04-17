package com.fincity.saas.commons.core.service;

import com.fincity.saas.commons.core.document.CoreSchema;
import com.fincity.saas.commons.core.repository.CoreSchemaDocumentRepository;
import com.fincity.saas.commons.mongo.service.AbstractSchemaService;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.google.gson.Gson;
import org.springframework.stereotype.Service;

@Service
public class CoreSchemaService extends AbstractSchemaService<CoreSchema, CoreSchemaDocumentRepository> {

    protected CoreSchemaService(FeignAuthenticationService feignAuthenticationService, Gson gson) {
        super(CoreSchema.class, feignAuthenticationService, gson);
    }

    @Override
    public String getObjectName() {
        return "Schema";
    }
}
