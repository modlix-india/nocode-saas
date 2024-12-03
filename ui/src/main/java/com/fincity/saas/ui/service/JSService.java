package com.fincity.saas.ui.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.service.CacheService;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;

@Service
public class JSService {

	public static final String CACHE_NAME_JS = "jsCache";

	private static final String CACHE_OBJECT_JS_KEY = "jsObject";

	public static final String CACHE_NAME_JS_MAP = "jsMapCache";

	private static final String CACHE_OBJECT_JS_MAP_KEY = "jsMapObject";

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

	public Mono<ObjectWithUniqueID<String>> getJSObject() {

		if (jsURL == null || jsURL.isBlank())
			return Mono.just(new ObjectWithUniqueID<>(""));

		Mono<ObjectWithUniqueID<String>> cacheEmptyDefer = Mono.defer(() -> webClient.get()
				.uri("/index.js")
				.retrieve()
				.bodyToMono(String.class)
				.flatMap(jsString -> cacheService.put(CACHE_NAME_JS, new ObjectWithUniqueID<>(jsString),
						CACHE_OBJECT_JS_KEY))
				.defaultIfEmpty(new ObjectWithUniqueID<>("")));

		return cacheService.<ObjectWithUniqueID<String>>get(CACHE_NAME_JS, CACHE_OBJECT_JS_KEY)
				.switchIfEmpty(cacheEmptyDefer);
	}

	public Mono<ObjectWithUniqueID<String>> getJSMapObject() {
		if (jsURL == null || jsURL.isBlank())
			return Mono.just(new ObjectWithUniqueID<String>(""));

		Mono<ObjectWithUniqueID<String>> cacheEmptyDefer = Mono.defer(() -> webClient.get()
				.uri("/index.js.map")
				.retrieve()
				.bodyToMono(String.class)
				.flatMap(jsString -> cacheService.put(CACHE_NAME_JS_MAP, new ObjectWithUniqueID<>(jsString),
						CACHE_OBJECT_JS_MAP_KEY))
				.defaultIfEmpty(new ObjectWithUniqueID<>("")));

		return cacheService.<ObjectWithUniqueID<String>>get(CACHE_NAME_JS_MAP, CACHE_OBJECT_JS_MAP_KEY)
				.switchIfEmpty(cacheEmptyDefer);
	}
}
