package com.fincity.saas.ui.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.mongo.service.AbstractTransportService;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;

@Service
public class TransportService extends AbstractTransportService {

	private final List<AbstractOverridableDataService<?, ?>> serviceList;

	public TransportService(IFeignSecurityService feignSecurityService,
			ApplicationService appService,
			PageService pageService,
			StyleService styleService,
			StyleThemeService themeService,
			UIFunctionService funService,
			UISchemaService schemaService,
			UIFillerService fillerService,
			URIPathService uriPathService) {
		super(feignSecurityService);
		serviceList = List.of(
				appService,
				pageService,
				styleService,
				themeService,
				funService,
				schemaService,
				fillerService,
				uriPathService);
	}

	@Override
	public List<AbstractOverridableDataService<?, ?>> getServieMap() {
		return serviceList;
	}

	@Override
	protected String getTransportType() {
		return "ui";
	}

	@Override
	protected String getExtension() {
		return "umodl";
	}
}
