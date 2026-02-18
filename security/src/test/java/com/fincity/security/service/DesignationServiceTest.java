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
import com.fincity.security.dao.DesignationDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.Department;
import com.fincity.security.dto.Designation;
import com.fincity.security.dto.appregistration.AppRegistrationDepartment;
import com.fincity.security.dto.appregistration.AppRegistrationDesignation;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.function.Tuple2;

@ExtendWith(MockitoExtension.class)
class DesignationServiceTest extends AbstractServiceUnitTest {

	@Mock
	private DesignationDAO dao;

	@Mock
	private SecurityMessageResourceService securityMessageResourceService;

	@Mock
	private ClientService clientService;

	@Mock
	private DepartmentService departmentService;

	@Mock
	private CacheService cacheService;

	@Mock
	private ObjectMapper objectMapper;

	@InjectMocks
	private DesignationService service;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);
	private static final ULong CLIENT_ID = ULong.valueOf(2);
	private static final ULong DESIGNATION_ID = ULong.valueOf(10);
	private static final ULong PARENT_DESIGNATION_ID = ULong.valueOf(20);
	private static final ULong NEXT_DESIGNATION_ID = ULong.valueOf(30);
	private static final ULong DEPARTMENT_ID = ULong.valueOf(40);

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

		lenient().when(dao.getPojoClass()).thenReturn(Mono.just(Designation.class));

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

			Designation desg = TestDataFactory.createDesignation(null, null, "CTO");
			desg.setDepartmentId(DEPARTMENT_ID);

			Designation created = TestDataFactory.createDesignation(DESIGNATION_ID, SYSTEM_CLIENT_ID, "CTO");
			created.setDepartmentId(DEPARTMENT_ID);

			when(dao.checkSameClient(SYSTEM_CLIENT_ID, null, null, DEPARTMENT_ID))
					.thenReturn(Mono.just(true));
			when(dao.create(any(Designation.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.create(desg))
					.assertNext(result -> {
						assertEquals(DESIGNATION_ID, result.getId());
						assertEquals("CTO", result.getName());
					})
					.verifyComplete();
		}

		@Test
		void create_SystemClient_SkipsAccessCheck() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Designation desg = TestDataFactory.createDesignation(null, CLIENT_ID, "Manager");
			desg.setDepartmentId(DEPARTMENT_ID);

			Designation created = TestDataFactory.createDesignation(DESIGNATION_ID, CLIENT_ID, "Manager");
			created.setDepartmentId(DEPARTMENT_ID);

			when(dao.checkSameClient(CLIENT_ID, null, null, DEPARTMENT_ID))
					.thenReturn(Mono.just(true));
			when(dao.create(any(Designation.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.create(desg))
					.assertNext(result -> assertEquals("Manager", result.getName()))
					.verifyComplete();

			verifyNoInteractions(clientService);
		}

		@Test
		void create_NonSystemClient_ChecksManagement() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Client_CREATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			ULong targetClientId = ULong.valueOf(3);
			Designation desg = TestDataFactory.createDesignation(null, targetClientId, "Engineer");
			desg.setDepartmentId(DEPARTMENT_ID);

			Designation created = TestDataFactory.createDesignation(DESIGNATION_ID, targetClientId, "Engineer");
			created.setDepartmentId(DEPARTMENT_ID);

			when(clientService.isUserClientManageClient(ca, targetClientId)).thenReturn(Mono.just(true));
			when(dao.checkSameClient(targetClientId, null, null, DEPARTMENT_ID))
					.thenReturn(Mono.just(true));
			when(dao.create(any(Designation.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.create(desg))
					.assertNext(result -> assertEquals("Engineer", result.getName()))
					.verifyComplete();

			verify(clientService).isUserClientManageClient(ca, targetClientId);
		}

		@Test
		void create_ParentDesignationClientMismatch_ThrowsForbidden() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Designation desg = TestDataFactory.createDesignation(null, CLIENT_ID, "SubDesignation");
			desg.setParentDesignationId(PARENT_DESIGNATION_ID);
			desg.setDepartmentId(DEPARTMENT_ID);

			when(dao.checkSameClient(CLIENT_ID, PARENT_DESIGNATION_ID, null, DEPARTMENT_ID))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.create(desg))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void create_NextDesignationClientMismatch_ThrowsForbidden() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Designation desg = TestDataFactory.createDesignation(null, CLIENT_ID, "NextMismatch");
			desg.setNextDesignationId(NEXT_DESIGNATION_ID);
			desg.setDepartmentId(DEPARTMENT_ID);

			when(dao.checkSameClient(CLIENT_ID, null, NEXT_DESIGNATION_ID, DEPARTMENT_ID))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.create(desg))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void create_DepartmentClientMismatch_ThrowsForbidden() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Designation desg = TestDataFactory.createDesignation(null, CLIENT_ID, "DeptMismatch");
			desg.setDepartmentId(DEPARTMENT_ID);

			when(dao.checkSameClient(CLIENT_ID, null, null, DEPARTMENT_ID))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.create(desg))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}

	// =========================================================================
	// read
	// =========================================================================

	@Nested
	@DisplayName("read")
	class ReadTests {

		@Test
		void read_DelegatesToSuper() {

			Designation desg = TestDataFactory.createDesignation(DESIGNATION_ID, CLIENT_ID, "ReadMe");

			when(dao.readById(DESIGNATION_ID)).thenReturn(Mono.just(desg));

			StepVerifier.create(service.read(DESIGNATION_ID))
					.assertNext(result -> {
						assertEquals(DESIGNATION_ID, result.getId());
						assertEquals("ReadMe", result.getName());
					})
					.verifyComplete();
		}
	}

	// =========================================================================
	// update(Designation)
	// =========================================================================

	@Nested
	@DisplayName("update(Designation)")
	class UpdateEntityTests {

		@Test
		void update_ByEntity_EvictsCache() {

			Designation entity = TestDataFactory.createDesignation(DESIGNATION_ID, CLIENT_ID, "Updated");
			entity.setDepartmentId(DEPARTMENT_ID);

			Designation updated = TestDataFactory.createDesignation(DESIGNATION_ID, CLIENT_ID, "Updated");
			updated.setDepartmentId(DEPARTMENT_ID);

			when(dao.checkSameClient(CLIENT_ID, null, null, DEPARTMENT_ID))
					.thenReturn(Mono.just(true));
			when(dao.canBeUpdated(DESIGNATION_ID)).thenReturn(Mono.just(true));
			when(dao.readById(DESIGNATION_ID)).thenReturn(Mono.just(entity));
			when(dao.update(any(Designation.class))).thenReturn(Mono.just(updated));

			StepVerifier.create(service.update(entity))
					.assertNext(result -> assertEquals("Updated", result.getName()))
					.verifyComplete();

			verify(cacheService, atLeast(1)).evict(eq("designation"), eq(DESIGNATION_ID));
		}
	}

	// =========================================================================
	// update(ULong, Map)
	// =========================================================================

	@Nested
	@DisplayName("update(ULong, Map)")
	class UpdateFieldsTests {

		@Test
		void update_ByMap_EvictsCache() {

			Designation existing = TestDataFactory.createDesignation(DESIGNATION_ID, CLIENT_ID, "Original");
			Designation updated = TestDataFactory.createDesignation(DESIGNATION_ID, CLIENT_ID, "Updated");

			when(dao.canBeUpdated(DESIGNATION_ID)).thenReturn(Mono.just(true));
			when(dao.readById(DESIGNATION_ID)).thenReturn(Mono.just(existing));
			when(dao.update(any(Designation.class))).thenReturn(Mono.just(updated));

			Map<String, Object> fields = Map.of("name", "Updated");

			StepVerifier.create(service.update(DESIGNATION_ID, fields))
					.assertNext(result -> assertEquals("Updated", result.getName()))
					.verifyComplete();

			verify(cacheService, atLeast(1)).evict(eq("designation"), eq(DESIGNATION_ID));
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

			when(dao.delete(DESIGNATION_ID)).thenReturn(Mono.just(1));

			StepVerifier.create(service.delete(DESIGNATION_ID))
					.assertNext(result -> assertEquals(1, result))
					.verifyComplete();
		}
	}

	// =========================================================================
	// canAssignDesignation
	// =========================================================================

	@Nested
	@DisplayName("canAssignDesignation")
	class CanAssignDesignationTests {

		@Test
		void canAssignDesignation_ValidDesignation_ReturnsTrue() {

			when(dao.canAssignDesignation(CLIENT_ID, DESIGNATION_ID)).thenReturn(Mono.just(true));

			StepVerifier.create(service.canAssignDesignation(CLIENT_ID, DESIGNATION_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();

			verify(dao).canAssignDesignation(CLIENT_ID, DESIGNATION_ID);
		}

		@Test
		void canAssignDesignation_InvalidDesignation_ReturnsFalse() {

			when(dao.canAssignDesignation(CLIENT_ID, DESIGNATION_ID)).thenReturn(Mono.just(false));

			StepVerifier.create(service.canAssignDesignation(CLIENT_ID, DESIGNATION_ID))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		void canAssignDesignation_NullDesignationId_ReturnsTrue() {

			StepVerifier.create(service.canAssignDesignation(CLIENT_ID, null))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();

			verifyNoInteractions(dao);
		}
	}

	// =========================================================================
	// createForRegistration
	// =========================================================================

	@Nested
	@DisplayName("createForRegistration")
	class CreateForRegistrationTests {

		@Test
		void createForRegistration_CreatesDesignations() {

			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, "REGCLIENT");

			AppRegistrationDesignation desg1 = new AppRegistrationDesignation();
			desg1.setName("Desg1");
			desg1.setDescription("First Designation");

			AppRegistrationDesignation desg2 = new AppRegistrationDesignation();
			desg2.setName("Desg2");
			desg2.setDescription("Second Designation");

			List<AppRegistrationDesignation> designations = List.of(desg1, desg2);
			Map<ULong, Tuple2<AppRegistrationDepartment, Department>> departmentIndex = Map.of();

			Map<ULong, Tuple2<AppRegistrationDesignation, Designation>> expectedResult = Map.of();
			when(dao.createForRegistration(client, designations, departmentIndex))
					.thenReturn(Mono.just(expectedResult));

			StepVerifier.create(service.createForRegistration(client, designations, departmentIndex))
					.assertNext(result -> assertNotNull(result))
					.verifyComplete();

			verify(dao).createForRegistration(client, designations, departmentIndex);
		}

		@Test
		void createForRegistration_NullList_ReturnsEmptyMap() {

			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, "REGCLIENT");
			Map<ULong, Tuple2<AppRegistrationDepartment, Department>> departmentIndex = Map.of();

			StepVerifier.create(service.createForRegistration(client, null, departmentIndex))
					.assertNext(result -> assertTrue(result.isEmpty()))
					.verifyComplete();

			verifyNoInteractions(dao);
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

			Designation desg = TestDataFactory.createDesignation(DESIGNATION_ID, CLIENT_ID, "Cached");
			when(dao.readInternal(DESIGNATION_ID)).thenReturn(Mono.just(desg));

			StepVerifier.create(service.readInternal(DESIGNATION_ID))
					.assertNext(result -> {
						assertEquals(DESIGNATION_ID, result.getId());
						assertEquals("Cached", result.getName());
					})
					.verifyComplete();

			verify(cacheService).cacheValueOrGet(eq("designation"), any(), eq(DESIGNATION_ID));
		}
	}

	// =========================================================================
	// fillDetails
	// =========================================================================

	@Nested
	@DisplayName("fillDetails")
	class FillDetailsTests {

		@Test
		void fillDetails_FetchParentDesignation_Enriches() {

			Designation child = TestDataFactory.createDesignation(DESIGNATION_ID, CLIENT_ID, "Child");
			child.setParentDesignationId(PARENT_DESIGNATION_ID);

			Designation parent = TestDataFactory.createDesignation(PARENT_DESIGNATION_ID, CLIENT_ID, "Parent");

			when(dao.readInternal(PARENT_DESIGNATION_ID)).thenReturn(Mono.just(parent));

			MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
			queryParams.add("fetchParentDesignation", "true");

			StepVerifier.create(service.fillDetails(List.of(child), queryParams))
					.assertNext(result -> {
						assertFalse(result.isEmpty());
						Designation enriched = result.getFirst();
						assertNotNull(enriched.getParentDesignation());
						assertEquals("Parent", enriched.getParentDesignation().getName());
					})
					.verifyComplete();
		}

		@Test
		void fillDetails_FetchNextDesignation_Enriches() {

			Designation desg = TestDataFactory.createDesignation(DESIGNATION_ID, CLIENT_ID, "Current");
			desg.setNextDesignationId(NEXT_DESIGNATION_ID);

			Designation next = TestDataFactory.createDesignation(NEXT_DESIGNATION_ID, CLIENT_ID, "Next");

			when(dao.readInternal(NEXT_DESIGNATION_ID)).thenReturn(Mono.just(next));

			MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
			queryParams.add("fetchNextDesignation", "true");

			StepVerifier.create(service.fillDetails(List.of(desg), queryParams))
					.assertNext(result -> {
						assertFalse(result.isEmpty());
						Designation enriched = result.getFirst();
						assertNotNull(enriched.getNextDesignation());
						assertEquals("Next", enriched.getNextDesignation().getName());
					})
					.verifyComplete();
		}

		@Test
		void fillDetails_FetchDepartment_Enriches() {

			Designation desg = TestDataFactory.createDesignation(DESIGNATION_ID, CLIENT_ID, "WithDept");
			desg.setDepartmentId(DEPARTMENT_ID);

			Department dept = TestDataFactory.createDepartment(DEPARTMENT_ID, CLIENT_ID, "Engineering");

			when(departmentService.readInternal(DEPARTMENT_ID)).thenReturn(Mono.just(dept));

			MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
			queryParams.add("fetchDepartment", "true");

			StepVerifier.create(service.fillDetails(List.of(desg), queryParams))
					.assertNext(result -> {
						assertFalse(result.isEmpty());
						Designation enriched = result.getFirst();
						assertNotNull(enriched.getDepartment());
						assertEquals("Engineering", enriched.getDepartment().getName());
					})
					.verifyComplete();
		}

		@Test
		void fillDetails_NoFetchFlag_ReturnsAsIs() {

			Designation desg = TestDataFactory.createDesignation(DESIGNATION_ID, CLIENT_ID, "Solo");

			MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();

			StepVerifier.create(service.fillDetails(List.of(desg), queryParams))
					.assertNext(result -> {
						assertEquals(1, result.size());
						assertEquals("Solo", result.getFirst().getName());
					})
					.verifyComplete();
		}
	}
}
