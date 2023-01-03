package com.fincity.saas.ui.service;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.service.AbstractSchemaService;
import com.fincity.saas.ui.document.UISchema;
import com.fincity.saas.ui.repository.UISchemaDocumentRepository;

@Service
public class UISchemaService extends AbstractSchemaService<UISchema, UISchemaDocumentRepository> {

	protected UISchemaService() {
		super(UISchema.class);
	}

	@Override
	protected String getObjectName() {
		return "Schema";
	}
}
