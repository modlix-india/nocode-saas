package com.fincity.security.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.security.dao.RoleV2DAO;
import com.fincity.security.dto.ClientHierarchy;
import com.fincity.security.dto.RoleV2;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class RoleV2ServiceTest extends AbstractServiceUnitTest {

	@Mock
	private RoleV2DAO dao;

	@Mock
	private SecurityMessageResourceService messageResourceService;

	@Mock
	private ClientService clientService;

	@Mock
	private ClientHierarchyService clientHierarchyService;

	@Mock
	private SoxLogService soxLogService;

	private RoleV2Service service;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);
	private static final ULong BUS_CLIENT_ID = ULong.valueOf(2);
	private static final ULong APP_ID = ULong.valueOf(100);
	private static final ULong ROLE_ID = ULong.valueOf(300);

	@BeforeEach
	void setUp() {
		service = new RoleV2Service(messageResourceService, clientService, clientHierarchyService);

		// RoleV2Service -> AbstractSecurityUpdatableDataService ->
		// AbstractJOOQUpdatableDataService -> AbstractJOOQDataService (has dao)
		// 3 getSuperclass() calls
		try {
			var daoField = service.getClass().getSuperclass().getSuperclass().getSuperclass()
					.getDeclaredField("dao");
			daoField.setAccessible(true);
			daoField.set(service, dao);
		} catch (Exception e) {
			throw new RuntimeException("Failed to inject DAO", e);
		}

		// SoxLogService is @Autowired private in AbstractSecurityUpdatableDataService
		// 1 getSuperclass() call from RoleV2Service
		try {
			var soxLogField = service.getClass().getSuperclass().getDeclaredField("soxLogService");
			soxLogField.setAccessible(true);
			soxLogField.set(service, soxLogService);
		} catch (Exception e) {
			throw new RuntimeException("Failed to inject SoxLogService", e);
		}

		setupMessageResourceService(messageResourceService);
		setupSoxLogService(soxLogService);
	}

	@Nested
	class CreateTests {

		@Test
		void create_SystemClient_CreatesRole() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			RoleV2 role = TestDataFactory.createRoleV2(null, BUS_CLIENT_ID, APP_ID, "AdminRole");

			RoleV2 createdRole = TestDataFactory.createRoleV2(ROLE_ID, BUS_CLIENT_ID, APP_ID, "AdminRole");

			when(dao.getPojoClass()).thenReturn(Mono.just(RoleV2.class));
			when(dao.create(any(RoleV2.class))).thenReturn(Mono.just(createdRole));

			StepVerifier.create(service.create(role))
					.assertNext(result -> {
						assertNotNull(result);
						assertEquals(ROLE_ID, result.getId());
						assertEquals("AdminRole", result.getName());
					})
					.verifyComplete();
		}

		@Test
		void create_NonSystemClient_NullClientId_SetsClientIdFromContext() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Role_CREATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			RoleV2 role = TestDataFactory.createRoleV2(null, null, APP_ID, "TestRole");

			RoleV2 createdRole = TestDataFactory.createRoleV2(ROLE_ID, BUS_CLIENT_ID, APP_ID, "TestRole");

			when(dao.getPojoClass()).thenReturn(Mono.just(RoleV2.class));
			when(dao.create(any(RoleV2.class))).thenReturn(Mono.just(createdRole));

			StepVerifier.create(service.create(role))
					.assertNext(result -> {
						assertNotNull(result);
						assertEquals(ROLE_ID, result.getId());
					})
					.verifyComplete();
		}

		@Test
		void create_NonSystemClient_WithClientId_ManagedClient_CreatesRole() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Role_CREATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			ULong targetClientId = ULong.valueOf(3);
			RoleV2 role = TestDataFactory.createRoleV2(null, targetClientId, APP_ID, "TestRole");

			when(clientService.isUserClientManageClient(eq(ca), eq(targetClientId)))
					.thenReturn(Mono.just(true));

			RoleV2 createdRole = TestDataFactory.createRoleV2(ROLE_ID, targetClientId, APP_ID, "TestRole");

			when(dao.getPojoClass()).thenReturn(Mono.just(RoleV2.class));
			when(dao.create(any(RoleV2.class))).thenReturn(Mono.just(createdRole));

			StepVerifier.create(service.create(role))
					.assertNext(result -> {
						assertNotNull(result);
						assertEquals(ROLE_ID, result.getId());
					})
					.verifyComplete();
		}

		@Test
		void create_NonSystemClient_NotManagedClient_ThrowsForbidden() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Role_CREATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			ULong targetClientId = ULong.valueOf(3);
			RoleV2 role = TestDataFactory.createRoleV2(null, targetClientId, APP_ID, "TestRole");

			when(clientService.isUserClientManageClient(eq(ca), eq(targetClientId)))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.create(role))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}

	@Nested
	class ReadTests {

		@Test
		void read_ReturnsRole() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			RoleV2 role = TestDataFactory.createRoleV2(ROLE_ID, SYSTEM_CLIENT_ID, APP_ID, "AdminRole");
			when(dao.readById(ROLE_ID)).thenReturn(Mono.just(role));

			StepVerifier.create(service.read(ROLE_ID))
					.assertNext(result -> {
						assertEquals(ROLE_ID, result.getId());
						assertEquals("AdminRole", result.getName());
					})
					.verifyComplete();
		}

		@Test
		void read_NotFound_ReturnsEmpty() {
			when(dao.readById(ROLE_ID)).thenReturn(Mono.empty());

			StepVerifier.create(service.read(ROLE_ID))
					.verifyComplete();
		}
	}

	@Nested
	class UpdateTests {

		@Test
		void update_ByEntity_CanBeUpdated_UpdatesRole() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			RoleV2 existingRole = TestDataFactory.createRoleV2(ROLE_ID, SYSTEM_CLIENT_ID, APP_ID,
					"OldName");
			RoleV2 updateEntity = TestDataFactory.createRoleV2(ROLE_ID, SYSTEM_CLIENT_ID, APP_ID,
					"NewName");
			updateEntity.setShortName("NEWNAME");
			updateEntity.setDescription("Updated description");

			when(dao.canBeUpdated(ROLE_ID)).thenReturn(Mono.just(true));
			when(dao.readById(ROLE_ID)).thenReturn(Mono.just(existingRole));
			when(dao.getPojoClass()).thenReturn(Mono.just(RoleV2.class));
			when(dao.update(any(RoleV2.class))).thenReturn(Mono.just(updateEntity));

			StepVerifier.create(service.update(updateEntity))
					.assertNext(result -> {
						assertEquals("NewName", result.getName());
						assertEquals("NEWNAME", result.getShortName());
					})
					.verifyComplete();
		}

		@Test
		void update_ByEntity_CannotBeUpdated_ThrowsNotFound() {
			RoleV2 updateEntity = TestDataFactory.createRoleV2(ROLE_ID, SYSTEM_CLIENT_ID, APP_ID,
					"NewName");

			when(dao.canBeUpdated(ROLE_ID)).thenReturn(Mono.just(false));

			StepVerifier.create(service.update(updateEntity))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.NOT_FOUND)
					.verify();
		}

		@Test
		void update_ByMap_CanBeUpdated_UpdatesRole() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			RoleV2 existingRole = TestDataFactory.createRoleV2(ROLE_ID, SYSTEM_CLIENT_ID, APP_ID,
					"OldName");

			RoleV2 updatedRole = TestDataFactory.createRoleV2(ROLE_ID, SYSTEM_CLIENT_ID, APP_ID,
					"NewName");
			updatedRole.setDescription("Updated description");

			when(dao.canBeUpdated(ROLE_ID)).thenReturn(Mono.just(true));
			when(dao.readById(ROLE_ID)).thenReturn(Mono.just(existingRole));
			when(dao.getPojoClass()).thenReturn(Mono.just(RoleV2.class));
			when(dao.update(any(RoleV2.class))).thenReturn(Mono.just(updatedRole));

			Map<String, Object> fields = Map.of("name", "NewName", "description", "Updated description");

			StepVerifier.create(service.update(ROLE_ID, fields))
					.assertNext(result -> {
						assertEquals("NewName", result.getName());
						assertEquals("Updated description", result.getDescription());
					})
					.verifyComplete();
		}

		@Test
		void update_ByMap_CannotBeUpdated_ThrowsNotFound() {
			when(dao.canBeUpdated(ROLE_ID)).thenReturn(Mono.just(false));

			Map<String, Object> fields = Map.of("name", "NewName");

			StepVerifier.create(service.update(ROLE_ID, fields))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.NOT_FOUND)
					.verify();
		}
	}

	@Nested
	class DeleteTests {

		@Test
		void delete_HappyPath_DeletesRole() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			RoleV2 role = TestDataFactory.createRoleV2(ROLE_ID, SYSTEM_CLIENT_ID, APP_ID, "AdminRole");
			when(dao.readById(ROLE_ID)).thenReturn(Mono.just(role));
			when(dao.delete(ROLE_ID)).thenReturn(Mono.just(1));
			when(dao.getPojoClass()).thenReturn(Mono.just(RoleV2.class));

			StepVerifier.create(service.delete(ROLE_ID))
					.assertNext(result -> assertEquals(1, result))
					.verifyComplete();
		}

		@Test
		void delete_DataAccessException_ThrowsForbidden() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			RoleV2 role = TestDataFactory.createRoleV2(ROLE_ID, SYSTEM_CLIENT_ID, APP_ID, "AdminRole");
			when(dao.readById(ROLE_ID)).thenReturn(Mono.just(role));
			when(dao.delete(ROLE_ID)).thenReturn(
					Mono.error(new DataAccessException("integrity violation") {
					}));
			when(dao.getPojoClass()).thenReturn(Mono.just(RoleV2.class));

			StepVerifier.create(service.delete(ROLE_ID))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void delete_RoleNotFound_ReturnsEmpty() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			when(dao.readById(ROLE_ID)).thenReturn(Mono.empty());

			StepVerifier.create(service.delete(ROLE_ID))
					.verifyComplete();
		}
	}

	@Nested
	class HasAccessToTests {

		@Test
		void hasAccessTo_ClientManagesRoleClient_ReturnsTrue() {
			ULong targetClientId = ULong.valueOf(3);

			RoleV2 role = TestDataFactory.createRoleV2(ROLE_ID, BUS_CLIENT_ID, APP_ID, "AdminRole");
			when(dao.readById(ROLE_ID)).thenReturn(Mono.just(role));

			when(clientService.doesClientManageClient(BUS_CLIENT_ID, targetClientId))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.hasAccessTo(ROLE_ID, targetClientId, null))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void hasAccessTo_ReverseManagement_ReturnsTrue() {
			ULong targetClientId = ULong.valueOf(3);

			RoleV2 role = TestDataFactory.createRoleV2(ROLE_ID, BUS_CLIENT_ID, APP_ID, "AdminRole");
			when(dao.readById(ROLE_ID)).thenReturn(Mono.just(role));

			when(clientService.doesClientManageClient(BUS_CLIENT_ID, targetClientId))
					.thenReturn(Mono.just(false));
			when(clientService.doesClientManageClient(targetClientId, BUS_CLIENT_ID))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.hasAccessTo(ROLE_ID, targetClientId, null))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void hasAccessTo_NoManagement_ReturnsFalse() {
			ULong targetClientId = ULong.valueOf(3);

			RoleV2 role = TestDataFactory.createRoleV2(ROLE_ID, BUS_CLIENT_ID, APP_ID, "AdminRole");
			when(dao.readById(ROLE_ID)).thenReturn(Mono.just(role));

			when(clientService.doesClientManageClient(BUS_CLIENT_ID, targetClientId))
					.thenReturn(Mono.just(false));
			when(clientService.doesClientManageClient(targetClientId, BUS_CLIENT_ID))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.hasAccessTo(ROLE_ID, targetClientId, null))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	@Nested
	class GetRolesForProfileServiceTests {

		@Test
		void getRolesForProfileService_ReturnsRoleMap() {
			RoleV2 role1 = TestDataFactory.createRoleV2(ROLE_ID, SYSTEM_CLIENT_ID, APP_ID, "Admin");
			ULong roleId2 = ULong.valueOf(301);
			RoleV2 role2 = TestDataFactory.createRoleV2(roleId2, SYSTEM_CLIENT_ID, APP_ID, "User");

			Set<ULong> roleIds = Set.of(ROLE_ID, roleId2);

			when(dao.getRoles(roleIds)).thenReturn(Mono.just(List.of(role1, role2)));

			StepVerifier.create(service.getRolesForProfileService(roleIds))
					.assertNext(result -> {
						assertEquals(2, result.size());
						assertEquals("Admin", result.get(ROLE_ID).getName());
						assertEquals("User", result.get(roleId2).getName());
					})
					.verifyComplete();
		}

		@Test
		void getRolesForProfileService_EmptyCollection_ReturnsEmptyMap() {
			Set<ULong> roleIds = Set.of();

			when(dao.getRoles(roleIds)).thenReturn(Mono.just(List.of()));

			StepVerifier.create(service.getRolesForProfileService(roleIds))
					.assertNext(result -> assertTrue(result.isEmpty()))
					.verifyComplete();
		}
	}

	@Nested
	class GetRoleAuthoritiesPerAppTests {

		@Test
		void getRoleAuthoritiesPerApp_ReturnsAuthorities() {
			ULong userId = ULong.valueOf(10);

			Map<String, List<String>> authMap = Map.of(
					"app1", List.of("Authorities.ROLE_Admin", "Authorities.User_READ"),
					"app2", List.of("Authorities.ROLE_User"));

			when(dao.getRoleAuthoritiesPerApp(userId)).thenReturn(Mono.just(authMap));

			StepVerifier.create(service.getRoleAuthoritiesPerApp(userId))
					.assertNext(result -> {
						assertEquals(2, result.size());
						assertTrue(result.containsKey("app1"));
						assertTrue(result.containsKey("app2"));
						assertEquals(2, result.get("app1").size());
					})
					.verifyComplete();
		}
	}

	@Nested
	class GetRolesForAssignmentInAppTests {

		@Test
		void getRolesForAssignmentInApp_ReturnsRoles() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			String appCode = "testApp";

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			RoleV2 role = TestDataFactory.createRoleV2(ROLE_ID, SYSTEM_CLIENT_ID, APP_ID, "AdminRole");
			when(dao.getRolesForAssignmentInApp(appCode, hierarchy))
					.thenReturn(Mono.just(List.of(role)));

			StepVerifier.create(service.getRolesForAssignmentInApp(appCode))
					.assertNext(result -> {
						assertEquals(1, result.size());
						assertEquals("AdminRole", result.get(0).getName());
					})
					.verifyComplete();
		}

		@Test
		void getRolesForAssignmentInApp_EmptyResult_ReturnsEmptyList() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			String appCode = "testApp";

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			when(dao.getRolesForAssignmentInApp(appCode, hierarchy))
					.thenReturn(Mono.just(List.of()));

			StepVerifier.create(service.getRolesForAssignmentInApp(appCode))
					.assertNext(result -> assertTrue(result.isEmpty()))
					.verifyComplete();
		}
	}
}
