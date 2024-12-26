package com.fincity.saas.ui.service;

import java.util.Map;

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

	private final CacheService cacheService;
	private final WebClient.Builder builder;

	@Value("${ui.jsURL:}")
	private String jsURL;

	private WebClient webClient;

	public JSService(CacheService cacheService, WebClient.Builder builder) {
		this.cacheService = cacheService;
		this.builder = builder;
	}

	@PostConstruct
	public void initialize() {

		webClient = builder.baseUrl(jsURL)
				.build();
	}

	public Mono<ObjectWithUniqueID<String>> getJSResource(String filePath) {
		if (jsURL == null || jsURL.isBlank())
			return Mono.just(new ObjectWithUniqueID<>(""));

		Mono<ObjectWithUniqueID<String>> cacheEmptyDefer = Mono.defer(() -> webClient.get()
				.uri(filePath)
				.retrieve()
				.bodyToMono(String.class)
				.flatMap(jsString -> cacheService.put(filePath,
						(new ObjectWithUniqueID<>(jsString)).setHeaders(this.getHeaders(filePath)),
						CACHE_OBJECT_JS_KEY))
				.defaultIfEmpty(new ObjectWithUniqueID<>("")));

		return cacheService.<ObjectWithUniqueID<String>>get(filePath, CACHE_OBJECT_JS_KEY)
				.switchIfEmpty(cacheEmptyDefer);
	}

	private Map<String, String> getHeaders(String filePath) {
		return Map.of("Content-Type", getMimeType(filePath));
	}

	private String getMimeType(String filePath) {
		if (filePath.endsWith(".js"))
			return "text/javascript";
		if (filePath.endsWith(".css"))
			return "text/css";
		if (filePath.endsWith(".html"))
			return "text/html";
		if (filePath.endsWith(".json"))
			return "application/json";
		return "application/octet-stream";
	}
}
