package com.fincity.saas.ui.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.ui.document.Application;
import com.fincity.saas.ui.model.ChecksumObject;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ManifestService {

	public static final String CACHE_NAME_MANIFEST = "manifestCache";

	@Autowired
	private ApplicationService appService;

	@Autowired
	private CacheService cacheService;

	@Autowired
	private ObjectMapper mapper;

	public Mono<ChecksumObject> getManifest(String appCode, String clientCode) {

		return cacheService.cacheValueOrGet(this.appService.getCacheName(appCode + "_" + CACHE_NAME_MANIFEST, appCode),
				() -> FlatMapUtil
						.flatMapMonoWithNull(() -> appService.read(appCode, appCode, clientCode),
								app -> Mono.just(new ChecksumObject(this.manifestFromApp(app))))
						.contextWrite(Context.of(LogUtil.METHOD_NAME, "ManifestService.getManifest")),
				clientCode);
	}

	@SuppressWarnings("unchecked")
	public String manifestFromApp(Application app) {

		if (app == null || app.getProperties() == null)
			return "";

		Map<String, Object> props = app.getProperties();

		Map<String, Object> manifest = (Map<String, Object>) props.get("manifest");

		if (manifest == null)
			return "";

		try {
			return this.mapper.writeValueAsString(manifest);
		} catch (JsonProcessingException e) {
			throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}
}
