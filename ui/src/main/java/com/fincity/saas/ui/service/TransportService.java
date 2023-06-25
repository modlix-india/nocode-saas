package com.fincity.saas.ui.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.mongo.service.AbstractTransportService;

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

	@SuppressWarnings("rawtypes")
	@Override
	public List<AbstractOverridableDataService> getServieMap() {
		return List.of(appService, pageService, styleService, themeService, funService, schemaService);
	}
	
	@Override
	protected String getTransportType() {
		return "ui";
	}
}
