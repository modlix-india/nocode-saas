package com.fincity.saas.core.service;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.service.AbstractSchemaService;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.fincity.saas.core.document.CoreSchema;
import com.fincity.saas.core.repository.CoreSchemaDocumentRepository;
import com.google.gson.Gson;

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
