package com.fincity.saas.commons.security;

import java.math.BigInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.WebFilterChainProxy;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.service.IAuthenticationService;

import reactor.core.publisher.Mono;

/**
 * A 401 used to carry `WWW-Authenticate: Basic realm="Realm"` (the default entry point of
 * ExceptionTranslationWebFilter), and a browser answers that on a credentialed request with its
 * native "Sign in" dialog. The shell's WhatsApp live-update stream (EventSource withCredentials)
 * hit it on stage /deals whenever the Authorization cookie was missing. A 401 must be plain, and
 * Basic credentials sent by API clients must still authenticate.
 */
class ISecurityConfigurationTest {

	private static final String GOOD_BASIC = "dXNlcjpwYXNz";

	private WebTestClient client;

	@BeforeEach
	void setUp() {

		IAuthenticationService authService = (isBasic, token, clientCode, appCode, request) -> {
			ContextAuthentication ca = new ContextAuthentication()
					.setLoggedInFromClientId(BigInteger.ONE)
					.setClientCode("SYSTEM");
			ca.setAuthenticated(isBasic && GOOD_BASIC.equals(token));
			return Mono.just(ca);
		};

		ISecurityConfiguration config = new ISecurityConfiguration() {
		};

		var chain = config.springSecurityFilterChain(ServerHttpSecurity.http(), authService,
				// As Spring Boot configures it: unknown properties are ignored.
				JsonMapper.builder().findAndAddModules()
						.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build(),
				"/api/public/**");

		this.client = WebTestClient
				.bindToWebHandler(exchange -> exchange.getResponse().setComplete())
				.webFilter(new WebFilterChainProxy(chain))
				.build();
	}

	@Test
	void anonymousRequestGets401WithoutBasicChallenge() {
		this.client.get().uri("/api/core/data/x")
				.exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().doesNotExist(HttpHeaders.WWW_AUTHENTICATE);
	}

	@Test
	void eventStreamWithoutCookieGets401WithoutBasicChallenge() {
		this.client.get().uri("/api/message/events/whatsapp/stream?appCode=leadzumptwo&clientCode=FIN")
				.accept(MediaType.TEXT_EVENT_STREAM)
				.exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().doesNotExist(HttpHeaders.WWW_AUTHENTICATE);
	}

	@Test
	void documentRequestGets401WithoutBasicChallenge() {
		this.client.get().uri("/api/core/data/x")
				.accept(MediaType.TEXT_HTML)
				.exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().doesNotExist(HttpHeaders.WWW_AUTHENTICATE);
	}

	@Test
	void wrongBasicCredentialsGet401WithoutBasicChallenge() {
		this.client.get().uri("/api/core/data/x")
				.header(HttpHeaders.AUTHORIZATION, "Basic d3Jvbmc6d3Jvbmc=")
				.exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().doesNotExist(HttpHeaders.WWW_AUTHENTICATE);
	}

	@Test
	void basicCredentialsInHeaderStillAuthenticate() {
		this.client.get().uri("/api/core/data/x")
				.header(HttpHeaders.AUTHORIZATION, "Basic " + GOOD_BASIC)
				.exchange()
				.expectStatus().isOk();
	}

	@Test
	void basicCredentialsInCookieStillAuthenticate() {
		this.client.get().uri("/api/message/events/whatsapp/stream")
				.cookie(HttpHeaders.AUTHORIZATION, "Basic " + GOOD_BASIC)
				.exchange()
				.expectStatus().isOk();
	}

	@Test
	void excludedPathStaysOpen() {
		this.client.get().uri("/api/public/ping")
				.exchange()
				.expectStatus().isOk();
	}
}
