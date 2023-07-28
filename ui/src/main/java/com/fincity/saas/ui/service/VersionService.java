package com.fincity.saas.ui.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.service.AbstractVersionService;

@Service
public class VersionService extends AbstractVersionService {

	private static final Map<String, String> authNames = Map.ofEntries(

	        Map.entry("APPLICATION", "Application"),

	        Map.entry("PAGE", "Page"),

	        Map.entry("PERSONALIZATION", "Personalization"),

	        Map.entry("STYLE", "Style"),

	        Map.entry("THEME", "Theme"),

	        Map.entry("FUNCTION", "Function"),

	        Map.entry("SCHEMA", "Schema"));

	@Override
	protected String mapAuthName(String objectType) {
		return authNames.get(objectType);
	}
}
