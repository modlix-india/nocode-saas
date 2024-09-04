package com.fincity.saas.ui.utils;

import java.util.HashMap;
import java.util.Map;

public class URIPathBuilder {

	private final StringBuilder pathBuilder;
	private final StringBuilder queryBuilder;
	private boolean isFirstQueryParam = true;
	private final Map<String, String> pathParams;
	private final Map<String, String> queryParams;

	private URIPathBuilder(String basePath) {
		this.pathBuilder = new StringBuilder(basePath);
		this.queryBuilder = new StringBuilder();
		this.pathParams = new HashMap<>();
		this.queryParams = new HashMap<>();
	}

	public URIPathBuilder addPathParams(Map<String, String> incomingParams, Map<String, String> paramsMapping) {
		if (paramsMapping == null || incomingParams == null) {
			return this;
		}

		paramsMapping.forEach((key, value) -> {
			String paramValue = incomingParams.getOrDefault(key, null);
			if (paramValue != null) {
				pathParams.put(value, paramValue);
				pathBuilder.append('/').append(paramValue);
			}
		});

		return this;
	}

	public URIPathBuilder addQueryParams(Map<String, String> incomingParams, Map<String, String> paramsMapping) {
		if (paramsMapping == null || incomingParams == null) {
			return this;
		}

		paramsMapping.forEach((key, value) -> {
			String paramValue = incomingParams.getOrDefault(key, null);
			if (paramValue != null) {
				queryParams.put(value, paramValue);
				addQueryParam(value, paramValue);
			}
		});

		return this;
	}

	public String build() {
		return pathBuilder + queryBuilder.toString();
	}

	public static URIPathBuilder buildURI(String path) {
		return new URIPathBuilder(path);
	}

	public Map<String, String> extractPathParams() {
		return new HashMap<>(pathParams);
	}

	public Map<String, String> extractQueryParams() {
		return new HashMap<>(queryParams);
	}

	public Map<String, String> extractAllParams() {
		Map<String, String> allParams = new HashMap<>(pathParams);
		allParams.putAll(queryParams);
		return allParams;
	}

	private void addQueryParam(String key, String value) {
		queryBuilder.append(isFirstQueryParam ? '?' : '&')
				.append(key)
				.append('=')
				.append(value);
		isFirstQueryParam = false;
	}
}
