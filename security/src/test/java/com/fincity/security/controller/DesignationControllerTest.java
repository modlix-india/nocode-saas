package com.fincity.security.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fincity.security.dto.Designation;
import com.fincity.security.service.DesignationService;
import com.fincity.security.testutil.TestWebSecurityConfig;

import reactor.core.publisher.Mono;

@WebFluxTest
@ContextConfiguration(classes = { DesignationController.class, TestWebSecurityConfig.class })
class DesignationControllerTest {

	@Autowired
	private WebTestClient webTestClient;

	@MockBean
	private DesignationService designationService;

	private Designation sampleDesignation;

	@BeforeEach
	void setUp() {
		sampleDesignation = new Designation();
		sampleDesignation.setId(ULong.valueOf(1));
		sampleDesignation.setName("Senior Engineer");
		sampleDesignation.setDescription("Senior Engineering Role");
		sampleDesignation.setClientId(ULong.valueOf(100));
		sampleDesignation.setDepartmentId(ULong.valueOf(10));
	}

	@Test
	@DisplayName("GET /api/security/designations - Should return page of designations")
	void readPageFilter_returnsPageOfDesignations() {

		Page<Designation> page = new PageImpl<>(List.of(sampleDesignation), PageRequest.of(0, 10), 1);

		when(designationService.readPageFilter(any(), any()))
				.thenReturn(Mono.just(page));
		when(designationService.fillDetails(anyList(), any()))
				.thenReturn(Mono.just(List.of(sampleDesignation)));

		webTestClient.get()
				.uri("/api/security/designations")
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.content").isArray()
				.jsonPath("$.content[0].name").isEqualTo("Senior Engineer");
	}

	@Test
	@DisplayName("POST /api/security/designations - Should create and return designation")
	void create_returnsDesignation() {

		when(designationService.create(any(Designation.class)))
				.thenReturn(Mono.just(sampleDesignation));

		webTestClient.post()
				.uri("/api/security/designations")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(sampleDesignation)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.name").isEqualTo("Senior Engineer")
				.jsonPath("$.description").isEqualTo("Senior Engineering Role");
	}

	@Test
	@DisplayName("GET /api/security/designations/internal/{id} - Should return designation by id")
	void getDesignationInternalById_returnsDesignation() {

		when(designationService.readById(eq(ULong.valueOf(1)), any()))
				.thenReturn(Mono.just(sampleDesignation));

		webTestClient.get()
				.uri("/api/security/designations/internal/1")
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.name").isEqualTo("Senior Engineer")
				.jsonPath("$.description").isEqualTo("Senior Engineering Role");
	}

	@Test
	@DisplayName("GET /api/security/designations/internal?designationIds= - Should return list of designations by ids")
	void getDesignationsInternalByIds_returnsList() {

		Designation secondDesignation = new Designation();
		secondDesignation.setId(ULong.valueOf(2));
		secondDesignation.setName("Tech Lead");
		secondDesignation.setDescription("Technical Lead Role");
		secondDesignation.setClientId(ULong.valueOf(100));
		secondDesignation.setDepartmentId(ULong.valueOf(10));

		List<Designation> designations = List.of(sampleDesignation, secondDesignation);

		when(designationService.readByIds(anyList(), any()))
				.thenReturn(Mono.just(designations));

		webTestClient.get()
				.uri(uriBuilder -> uriBuilder
						.path("/api/security/designations/internal")
						.queryParam("designationIds", "1", "2")
						.build())
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$").isArray()
				.jsonPath("$[0].name").isEqualTo("Senior Engineer")
				.jsonPath("$[1].name").isEqualTo("Tech Lead");
	}
}
