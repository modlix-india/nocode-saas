package com.fincity.saas.core.service;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.service.AbstractFillerService;
import com.fincity.saas.core.document.CoreFiller;
import com.fincity.saas.core.repository.CoreFillerDocumentRepository;

@Service
public class CoreFillerService extends AbstractFillerService<CoreFiller, CoreFillerDocumentRepository> {

	protected CoreFillerService() {
		super(CoreFiller.class);
	}

	@Override
	public String getObjectName() {
		return "Filler";
	}

	@Override
	public String getAccessCheckName() {
		return "Application";
	}
}
