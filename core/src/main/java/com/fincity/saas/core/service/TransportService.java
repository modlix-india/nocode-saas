package com.fincity.saas.core.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.mongo.service.AbstractTransportService;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;

@Service
public class TransportService extends AbstractTransportService {

	private final List<AbstractOverridableDataService<?, ?>> serviceList;

	public TransportService(IFeignSecurityService feignSecurityService,
			TemplateService templateService,
			StorageService storageService,
			CoreFunctionService funService,
			CoreSchemaService schemaService,
			EventActionService evaService,
			EventDefinitionService edService,
			CoreFillerService fillerService) {
		super(feignSecurityService);
		this.serviceList = List.of(
				templateService,
				storageService,
				funService,
				schemaService,
				evaService,
				edService,
				fillerService);
	}

	@Override
	public List<AbstractOverridableDataService<?, ?>> getServieMap() {
		return this.serviceList;
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
