package com.fincity.saas.ui.service;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.ui.model.ChecksumObject;

import reactor.core.publisher.Mono;

@Service
public class JSService {

	public static final String CACHE_NAME_JS = "jsCache";

	private static final String CACHE_OBJECT_JS_KEY = "jsObject";

	@Autowired
	private CacheService cacheService;

	@Autowired
	private WebClient.Builder builder;

	@Value("${ui.jsURL:}")
	private String jsURL;

	private WebClient webClient;

	@PostConstruct
	public void initialize() {

		webClient = builder.baseUrl(jsURL)
		        .build();
	}

	public Mono<ChecksumObject> getJSObject() {

		if (jsURL == null || jsURL.isBlank())
			return Mono.just(new ChecksumObject(""));

		Mono<ChecksumObject> cacheEmptyDefer = Mono.defer(() -> webClient.get()
		        .uri("/index.js")
		        .retrieve()
		        .bodyToMono(String.class)
		        .flatMap(jsString -> cacheService.put(CACHE_NAME_JS, new ChecksumObject(jsString), CACHE_OBJECT_JS_KEY))
		        .defaultIfEmpty(new ChecksumObject("")));

		return cacheService.<ChecksumObject>get(CACHE_NAME_JS, CACHE_OBJECT_JS_KEY)
		        .switchIfEmpty(cacheEmptyDefer);
	}
}
