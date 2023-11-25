package com.fincity.saas.ui.service;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.service.AbstractFillerService;
import com.fincity.saas.ui.document.UIFiller;
import com.fincity.saas.ui.repository.UIFillerDocumentRepository;

@Service
public class UIFillerService extends AbstractFillerService<UIFiller, UIFillerDocumentRepository> {

	protected UIFillerService() {
		super(UIFiller.class);
	}

	@Override
	public String getObjectName() {
		return "Filler";
	}

	@Override
	public String getAccessCheckName() {
		return "Application";
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
