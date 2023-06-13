package com.fincity.saas.core.service;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.service.AbstractFunctionService;
import com.fincity.saas.core.document.CoreFunction;
import com.fincity.saas.core.repository.CoreFunctionDocumentRepository;

@Service
public class CoreFunctionService extends AbstractFunctionService<CoreFunction, CoreFunctionDocumentRepository> {

	protected CoreFunctionService() {
		super(CoreFunction.class);
	}

	@Override
	public String getObjectName() {
		return "Function";
	}
}
