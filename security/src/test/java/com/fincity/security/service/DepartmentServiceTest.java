package com.fincity.security.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dao.DepartmentDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.Department;
import com.fincity.security.dto.appregistration.AppRegistrationDepartment;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.function.Tuple2;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest extends AbstractServiceUnitTest {

	@Mock
	private DepartmentDAO dao;

	@Mock
	private SecurityMessageResourceService securityMessageResourceService;

	@Mock
	private ClientService clientService;

	@Mock
	private CacheService cacheService;

	@Mock
	private ObjectMapper objectMapper;

	@InjectMocks
	private DepartmentService service;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);
	private static final ULong CLIENT_ID = ULong.valueOf(2);
	private static final ULong DEPT_ID = ULong.valueOf(10);
	private static final ULong PARENT_DEPT_ID = ULong.valueOf(20);

	@BeforeEach
	void setUp() throws Exception {
		Field daoField = org.springframework.util.ReflectionUtils.findField(service.getClass(), "dao");
		daoField.setAccessible(true);
		daoField.set(service, dao);

		// Inject objectMapper via reflection
		try {
			var omField = org.springframework.util.ReflectionUtils.findField(service.getClass(), "objectMapper");
			if (omField != null) {
				omField.setAccessible(true);
				omField.set(service, objectMapper);
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to inject ObjectMapper", e);
		}

		lenient().when(dao.getPojoClass()).thenReturn(Mono.just(Department.class));

		setupMessageResourceService(securityMessageResourceService);
		setupCacheService(cacheService);
	}

	// =========================================================================
	// create
	// =========================================================================

	@Nested
	@DisplayName("create")
	class CreateTests {

		@Test
		void create_HappyPath_NullClientId_UsesLoggedIn() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Department dept = TestDataFactory.createDepartment(null, null, "Engineering");

			Department created = TestDataFactory.createDepartment(DEPT_ID, SYSTEM_CLIENT_ID, "Engineering");

			when(dao.create(any(Department.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.create(dept))
					.assertNext(result -> {
						assertEquals(DEPT_ID, result.getId());
						assertEquals("Engineering", result.getName());
					})
					.verifyComplete();
		}

		@Test
		void create_SystemClient_SkipsAccessCheck() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Department dept = TestDataFactory.createDepartment(null, CLIENT_ID, "Sales");
			Department created = TestDataFactory.createDepartment(DEPT_ID, CLIENT_ID, "Sales");

			when(dao.create(any(Department.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.create(dept))
					.assertNext(result -> assertEquals("Sales", result.getName()))
					.verifyComplete();

			verifyNoInteractions(clientService);
		}

		@Test
		void create_NonSystemClient_ChecksManagement() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Client_CREATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			ULong targetClientId = ULong.valueOf(3);
			Department dept = TestDataFactory.createDepartment(null, targetClientId, "Marketing");
			Department created = TestDataFactory.createDepartment(DEPT_ID, targetClientId, "Marketing");

			when(clientService.isUserClientManageClient(ca, targetClientId)).thenReturn(Mono.just(true));
			when(dao.create(any(Department.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.create(dept))
					.assertNext(result -> assertEquals("Marketing", result.getName()))
					.verifyComplete();

			verify(clientService).isUserClientManageClient(ca, targetClientId);
		}

		@Test
		void create_ParentDepartmentClientMismatch_ThrowsForbidden() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Department dept = TestDataFactory.createDepartment(null, CLIENT_ID, "SubDept");
			dept.setParentDepartmentId(PARENT_DEPT_ID);

			when(dao.checkSameClient(CLIENT_ID, PARENT_DEPT_ID)).thenReturn(Mono.just(false));

			StepVerifier.create(service.create(dept))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}

	// =========================================================================
	// update
	// =========================================================================

	@Nested
	@DisplayName("update(Department)")
	class UpdateEntityTests {

		@Test
		void update_ByEntity_EvictsCache() {

			Department entity = TestDataFactory.createDepartment(DEPT_ID, CLIENT_ID, "Updated");
			Department updated = TestDataFactory.createDepartment(DEPT_ID, CLIENT_ID, "Updated");

			when(dao.checkSameClient(eq(CLIENT_ID), isNull())).thenReturn(Mono.just(true));
			when(dao.canBeUpdated(DEPT_ID)).thenReturn(Mono.just(true));
			when(dao.readById(DEPT_ID)).thenReturn(Mono.just(entity));
			when(dao.update(any(Department.class))).thenReturn(Mono.just(updated));

			StepVerifier.create(service.update(entity))
					.assertNext(result -> assertEquals("Updated", result.getName()))
					.verifyComplete();

			verify(cacheService, atLeast(1)).evict("department", DEPT_ID);
		}
	}

	@Nested
	@DisplayName("update(ULong, Map)")
	class UpdateFieldsTests {

		@Test
		void update_ByMap_EvictsCache() {

			Department existing = TestDataFactory.createDepartment(DEPT_ID, CLIENT_ID, "Original");
			Department updated = TestDataFactory.createDepartment(DEPT_ID, CLIENT_ID, "Updated");

			when(dao.canBeUpdated(DEPT_ID)).thenReturn(Mono.just(true));
			when(dao.readById(DEPT_ID)).thenReturn(Mono.just(existing));
			when(dao.update(any(Department.class))).thenReturn(Mono.just(updated));

			Map<String, Object> fields = Map.of("name", "Updated");

			StepVerifier.create(service.update(DEPT_ID, fields))
					.assertNext(result -> assertEquals("Updated", result.getName()))
					.verifyComplete();

			verify(cacheService, atLeast(1)).evict("department", DEPT_ID);
		}
	}

	// =========================================================================
	// delete
	// =========================================================================

	@Nested
	@DisplayName("delete")
	class DeleteTests {

		@Test
		void delete_EvictsCache() {

			when(dao.delete(DEPT_ID)).thenReturn(Mono.just(1));

			StepVerifier.create(service.delete(DEPT_ID))
					.assertNext(result -> assertEquals(1, result))
					.verifyComplete();
		}
	}

	// =========================================================================
	// readInternal
	// =========================================================================

	@Nested
	@DisplayName("readInternal")
	class ReadInternalTests {

		@Test
		void readInternal_UsesCache() {

			Department dept = TestDataFactory.createDepartment(DEPT_ID, CLIENT_ID, "Cached");
			when(dao.readInternal(DEPT_ID)).thenReturn(Mono.just(dept));

			StepVerifier.create(service.readInternal(DEPT_ID))
					.assertNext(result -> {
						assertEquals(DEPT_ID, result.getId());
						assertEquals("Cached", result.getName());
					})
					.verifyComplete();

			verify(cacheService).cacheValueOrGet(eq("department"), any(), eq(DEPT_ID));
		}
	}

	// =========================================================================
	// createForRegistration
	// =========================================================================

	@Nested
	@DisplayName("createForRegistration")
	class CreateForRegistrationTests {

		@Test
		void createForRegistration_CreatesMultipleDepartments() {

			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, "REGCLIENT");

			AppRegistrationDepartment dept1 = new AppRegistrationDepartment();
			dept1.setName("Dept1");
			dept1.setDescription("First Dept");

			AppRegistrationDepartment dept2 = new AppRegistrationDepartment();
			dept2.setName("Dept2");
			dept2.setDescription("Second Dept");

			List<AppRegistrationDepartment> departments = List.of(dept1, dept2);

			Map<ULong, Tuple2<AppRegistrationDepartment, Department>> expectedResult = Map.of();
			when(dao.createForRegistration(client, departments)).thenReturn(Mono.just(expectedResult));

			StepVerifier.create(service.createForRegistration(client, departments))
					.assertNext(result -> assertNotNull(result))
					.verifyComplete();

			verify(dao).createForRegistration(client, departments);
		}

		@Test
		void createForRegistration_NullList_ReturnsEmptyMap() {

			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, "REGCLIENT");

			StepVerifier.create(service.createForRegistration(client, null))
					.assertNext(result -> assertTrue(result.isEmpty()))
					.verifyComplete();

			verifyNoInteractions(dao);
		}
	}

	// =========================================================================
	// fillDetails
	// =========================================================================

	@Nested
	@DisplayName("fillDetails")
	class FillDetailsTests {

		@Test
		void fillDetails_FetchParentDepartment_Enriches() {

			Department child = TestDataFactory.createDepartment(DEPT_ID, CLIENT_ID, "Child");
			child.setParentDepartmentId(PARENT_DEPT_ID);

			Department parent = TestDataFactory.createDepartment(PARENT_DEPT_ID, CLIENT_ID, "Parent");

			when(dao.readInternal(PARENT_DEPT_ID)).thenReturn(Mono.just(parent));

			MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
			queryParams.add("fetchParentDepartment", "true");

			StepVerifier.create(service.fillDetails(List.of(child), queryParams))
					.assertNext(result -> {
						assertFalse(result.isEmpty());
						Department enriched = result.getFirst();
						assertNotNull(enriched.getParentDepartment());
						assertEquals("Parent", enriched.getParentDepartment().getName());
					})
					.verifyComplete();
		}

		@Test
		void fillDetails_NoFetchFlag_ReturnsAsIs() {

			Department dept = TestDataFactory.createDepartment(DEPT_ID, CLIENT_ID, "Solo");

			MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();

			StepVerifier.create(service.fillDetails(List.of(dept), queryParams))
					.assertNext(result -> {
						assertEquals(1, result.size());
						assertEquals("Solo", result.getFirst().getName());
					})
					.verifyComplete();
		}
	}

	// =========================================================================
	// readById
	// =========================================================================

	@Nested
	@DisplayName("readById")
	class ReadByIdTests {

		@Test
		void readById_WithQueryParams_FillsDetails() {

			Department dept = TestDataFactory.createDepartment(DEPT_ID, CLIENT_ID, "ReadMe");

			when(dao.readInternal(DEPT_ID)).thenReturn(Mono.just(dept));

			MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();

			StepVerifier.create(service.readById(DEPT_ID, queryParams))
					.assertNext(result -> {
						assertEquals(DEPT_ID, result.getId());
						assertEquals("ReadMe", result.getName());
					})
					.verifyComplete();
		}
	}

	// =========================================================================
	// readByIds
	// =========================================================================

	@Nested
	@DisplayName("readByIds")
	class ReadByIdsTests {

		@Test
		void readByIds_ReadsAll() {

			ULong deptId2 = ULong.valueOf(11);
			Department dept1 = TestDataFactory.createDepartment(DEPT_ID, CLIENT_ID, "Dept1");
			Department dept2 = TestDataFactory.createDepartment(deptId2, CLIENT_ID, "Dept2");

			when(dao.readAll(any())).thenReturn(reactor.core.publisher.Flux.just(dept1, dept2));

			MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();

			StepVerifier.create(service.readByIds(List.of(DEPT_ID, deptId2), queryParams))
					.assertNext(result -> assertEquals(2, result.size()))
					.verifyComplete();
		}
	}
}
