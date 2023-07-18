package com.fincity.saas.core.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.service.AbstractVersionService;

@Service
public class VersionService extends AbstractVersionService {

	private static final Map<String, String> authNames = Map.ofEntries(

	        Map.entry("ACTION", "Action"),

	        Map.entry("CONNECTION", "Connection"),

	        Map.entry("FUNCTION", "Function"),

	        Map.entry("SCHEMA", "Schema"),

	        Map.entry("EVENTACTION", "EventAction"),

	        Map.entry("EVENTDEFINITION", "EventDefinition"),

	        Map.entry("STORAGE", "Storage"),

	        Map.entry("TEMPLATE", "Template"));

	@Override
	protected String mapAuthName(String objectType) {
		return authNames.get(objectType);
	}
}
