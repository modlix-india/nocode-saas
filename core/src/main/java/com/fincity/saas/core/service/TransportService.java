package com.fincity.saas.core.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.mongo.service.AbstractTransportService;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;

@Service
public class TransportService extends AbstractTransportService {

	@Autowired
	private TemplateService templateService;

	@Autowired
	private StorageService storageService;

	@Autowired
	private CoreFunctionService funService;

	@Autowired
	private CoreSchemaService schemaService;

	@Autowired
	private EventActionService evaService;

	@Autowired
	private EventDefinitionService edService;

	@Autowired
	private CoreFillerService fillerService;

	public TransportService(IFeignSecurityService feignSecurityService) {
		super(feignSecurityService);
	}

	@SuppressWarnings("rawtypes")
	@Override
	public List<AbstractOverridableDataService> getServieMap() {
		return List.of(funService, schemaService, storageService, templateService, evaService, edService,
				fillerService);
	}

	@Override
	protected String getTransportType() {
		return "core";
	}

	@Override
	protected String getExtension() {
		return "cmodl";
	}
}
