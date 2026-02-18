package com.fincity.security.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fincity.security.integration.AbstractIntegrationTest;

public abstract class AbstractControllerTest extends AbstractIntegrationTest {

	@Autowired
	protected WebTestClient webTestClient;

	protected WebTestClient.RequestHeadersSpec<?> withAuth(WebTestClient.RequestHeadersSpec<?> spec,
			String token) {
		return spec.header("Authorization", "Bearer " + token);
	}

	protected WebTestClient.RequestHeadersSpec<?> withAppCode(WebTestClient.RequestHeadersSpec<?> spec,
			String appCode) {
		return spec.header("appCode", appCode);
	}

	protected WebTestClient.RequestHeadersSpec<?> withClientCode(WebTestClient.RequestHeadersSpec<?> spec,
			String clientCode) {
		return spec.header("clientCode", clientCode);
	}
}
