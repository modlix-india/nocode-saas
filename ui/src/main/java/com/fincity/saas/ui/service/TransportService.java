package com.fincity.saas.ui.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.mongo.service.AbstractTransportService;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;

@Service
public class TransportService extends AbstractTransportService {

	@Autowired
	private ApplicationService appService;

	@Autowired
	private PageService pageService;

	@Autowired
	private StyleService styleService;

	@Autowired
	private StyleThemeService themeService;

	@Autowired
	private UIFunctionService funService;

	@Autowired
	private UISchemaService schemaService;

	@Autowired
	private UIFillerService fillerService;

	public TransportService(IFeignSecurityService feignSecurityService) {
		super(feignSecurityService);
	}

	@SuppressWarnings("rawtypes")
	@Override
	public List<AbstractOverridableDataService> getServieMap() {
		return List.of(appService, pageService, styleService, themeService, funService, schemaService, fillerService);
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
