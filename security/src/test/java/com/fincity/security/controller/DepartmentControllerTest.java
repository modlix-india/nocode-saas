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

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.security.dto.Department;
import com.fincity.security.service.DepartmentService;
import com.fincity.security.testutil.TestWebSecurityConfig;

import reactor.core.publisher.Mono;

@WebFluxTest
@ContextConfiguration(classes = { DepartmentController.class, TestWebSecurityConfig.class })
class DepartmentControllerTest {

	@Autowired
	private WebTestClient webTestClient;

	@MockBean
	private DepartmentService departmentService;

	private Department sampleDepartment;

	@BeforeEach
	void setUp() {
		sampleDepartment = new Department();
		sampleDepartment.setId(ULong.valueOf(1));
		sampleDepartment.setName("Engineering");
		sampleDepartment.setDescription("Engineering Department");
		sampleDepartment.setClientId(ULong.valueOf(100));
	}

	@Test
	@DisplayName("GET /api/security/departments - Should return page of departments")
	void readPageFilter_returnsPageOfDepartments() {

		Page<Department> page = new PageImpl<>(List.of(sampleDepartment), PageRequest.of(0, 10), 1);

		when(departmentService.readPageFilter(any(), any()))
				.thenReturn(Mono.just(page));
		when(departmentService.fillDetails(anyList(), any()))
				.thenReturn(Mono.just(List.of(sampleDepartment)));

		webTestClient.get()
				.uri("/api/security/departments")
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.content").isArray()
				.jsonPath("$.content[0].name").isEqualTo("Engineering");
	}

	@Test
	@DisplayName("POST /api/security/departments - Should create and return department")
	void create_returnsDepartment() {

		when(departmentService.create(any(Department.class)))
				.thenReturn(Mono.just(sampleDepartment));

		webTestClient.post()
				.uri("/api/security/departments")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(sampleDepartment)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.name").isEqualTo("Engineering")
				.jsonPath("$.description").isEqualTo("Engineering Department");
	}

	@Test
	@DisplayName("GET /api/security/departments/internal/{id} - Should return department by id")
	void getDepartmentInternalById_returnsDepartment() {

		when(departmentService.readById(eq(ULong.valueOf(1)), any()))
				.thenReturn(Mono.just(sampleDepartment));

		webTestClient.get()
				.uri("/api/security/departments/internal/1")
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.name").isEqualTo("Engineering")
				.jsonPath("$.description").isEqualTo("Engineering Department");
	}

	@Test
	@DisplayName("GET /api/security/departments/internal?departmentIds= - Should return list of departments by ids")
	void getDepartmentsInternalByIds_returnsList() {

		Department secondDepartment = new Department();
		secondDepartment.setId(ULong.valueOf(2));
		secondDepartment.setName("Marketing");
		secondDepartment.setDescription("Marketing Department");
		secondDepartment.setClientId(ULong.valueOf(100));

		List<Department> departments = List.of(sampleDepartment, secondDepartment);

		when(departmentService.readByIds(anyList(), any()))
				.thenReturn(Mono.just(departments));

		webTestClient.get()
				.uri(uriBuilder -> uriBuilder
						.path("/api/security/departments/internal")
						.queryParam("departmentIds", "1", "2")
						.build())
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$").isArray()
				.jsonPath("$[0].name").isEqualTo("Engineering")
				.jsonPath("$[1].name").isEqualTo("Marketing");
	}
}
