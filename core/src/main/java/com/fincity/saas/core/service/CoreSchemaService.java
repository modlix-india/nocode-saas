package com.fincity.saas.core.service;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.service.AbstractSchemaService;
import com.fincity.saas.core.document.CoreSchema;
import com.fincity.saas.core.repository.CoreSchemaDocumentRepository;

@Service
public class CoreSchemaService extends AbstractSchemaService<CoreSchema, CoreSchemaDocumentRepository> {

	protected CoreSchemaService() {
		super(CoreSchema.class);
	}

	@Override
	protected String getObjectName() {
		return "Schema";
	}
}
