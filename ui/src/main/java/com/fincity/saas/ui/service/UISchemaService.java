package com.fincity.saas.ui.service;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.service.AbstractSchemaService;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.fincity.saas.ui.document.UISchema;
import com.fincity.saas.ui.repository.UISchemaDocumentRepository;
import com.google.gson.Gson;

@Service
public class UISchemaService extends AbstractSchemaService<UISchema, UISchemaDocumentRepository> {

	protected UISchemaService(FeignAuthenticationService feignAuthenticationService, Gson gson) {
		super(UISchema.class, feignAuthenticationService, gson);
	}

	@Override
	public String getObjectName() {
		return "Schema";
	}

	@Override
	public String getCacheName(String appCode, String name) {

		return new StringBuilder("UI").append(this.getObjectName())
				.append(CACHE_NAME)
				.append("_")
				.append(appCode)
				.append("_")
				.append(name)
				.toString();
	}

}
