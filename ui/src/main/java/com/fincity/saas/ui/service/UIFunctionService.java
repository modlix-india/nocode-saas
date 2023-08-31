package com.fincity.saas.ui.service;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.service.AbstractFunctionService;
import com.fincity.saas.ui.document.UIFunction;
import com.fincity.saas.ui.repository.UIFunctionDocumentRepository;

@Service
public class UIFunctionService extends AbstractFunctionService<UIFunction, UIFunctionDocumentRepository> {

	protected UIFunctionService() {
		super(UIFunction.class);
	}

	@Override
	public String getObjectName() {
		return "Function";
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
