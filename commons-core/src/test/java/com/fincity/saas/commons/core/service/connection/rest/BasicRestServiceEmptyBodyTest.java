package com.fincity.saas.commons.core.service.connection.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;

import com.fincity.saas.commons.core.dto.RestResponse;
import com.google.gson.Gson;

import reactor.test.StepVerifier;

/**
 * A response with no body completes {@code bodyToMono} EMPTY, and an empty result used to reach
 * {@code RestService.doCall}'s fallback: a successful 204 was reported as 500 "Connection Not
 * found". Every content-type branch must keep the remote's real status.
 */
class BasicRestServiceEmptyBodyTest {

	private static final Duration TIMEOUT = Duration.ofSeconds(5);

	private BasicRestService service;

	@BeforeEach
	void setUp() {
		service = new BasicRestService();
		service.gson = new Gson();
	}

	private RestResponse handle(ClientResponse response, boolean fileDownload) {
		return service.handleResponse(response, TIMEOUT, fileDownload).block(TIMEOUT);
	}

	@Test
	void noContentWithoutContentType() {
		RestResponse r = handle(ClientResponse.create(HttpStatus.NO_CONTENT).build(), false);

		assertEquals(204, r.getStatus());
		assertNull(r.getData());
	}

	@Test
	void emptyJsonBody() {
		RestResponse r = handle(ClientResponse.create(HttpStatus.OK)
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.build(), false);

		assertEquals(200, r.getStatus());
		assertNull(r.getData());
	}

	@Test
	void emptyBinaryBody() {
		RestResponse r = handle(ClientResponse.create(HttpStatus.ACCEPTED)
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
				.build(), true);

		assertEquals(202, r.getStatus());
		assertNull(r.getData());
	}

	@Test
	void emptyTextBody() {
		RestResponse r = handle(ClientResponse.create(HttpStatus.CREATED)
				.header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
				.build(), false);

		assertEquals(201, r.getStatus());
		assertNull(r.getData());
	}

	@Test
	void whitespaceJsonBodyIsNoData() {
		RestResponse r = handle(ClientResponse.create(HttpStatus.OK)
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.body("   ")
				.build(), false);

		assertEquals(200, r.getStatus());
		assertNull(r.getData());
	}

	@Test
	void jsonBodyStillParsed() {
		StepVerifier.create(service.handleResponse(ClientResponse.create(HttpStatus.OK)
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.body("{\"a\":1}")
				.build(), TIMEOUT, false))
				.assertNext(r -> {
					assertEquals(200, r.getStatus());
					assertEquals(1.0, ((Map<?, ?>) r.getData()).get("a"));
				})
				.verifyComplete();
	}
}
