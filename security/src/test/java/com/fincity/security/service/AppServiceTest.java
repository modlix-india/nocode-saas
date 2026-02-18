package com.fincity.security.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dao.AppDAO;
import com.fincity.security.dao.appregistration.AppRegistrationV2DAO;
import com.fincity.security.dto.App;
import com.fincity.security.dto.AppProperty;
import com.fincity.security.dto.Client;
import com.fincity.security.jooq.enums.SecurityAppAppAccessType;
import com.fincity.security.jooq.enums.SecurityAppStatus;
import com.fincity.security.model.AppDependency;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.function.Tuples;

@ExtendWith(MockitoExtension.class)
class AppServiceTest extends AbstractServiceUnitTest {

	@Mock
	private AppDAO dao;

	@Mock
	private SecurityMessageResourceService messageResourceService;

	@Mock
	private CacheService cacheService;

	@Mock
	private ClientService clientService;

	@Mock
	private AppRegistrationV2DAO appRegistrationDao;

	private AppService service;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);
	private static final ULong BUS_CLIENT_ID = ULong.valueOf(2);
	private static final ULong MANAGED_CLIENT_ID = ULong.valueOf(3);
	private static final ULong APP_ID = ULong.valueOf(100);
	private static final ULong APP_ID_2 = ULong.valueOf(101);
	private static final ULong ACCESS_ID = ULong.valueOf(200);
	private static final ULong PROPERTY_ID = ULong.valueOf(300);

	@BeforeEach
	void setUp() {
		service = new AppService(clientService, messageResourceService, cacheService, appRegistrationDao);

		// Inject the mocked DAO using reflection
		// AppService -> AbstractJOOQUpdatableDataService -> AbstractJOOQDataService (has dao)
		try {
			var daoField = service.getClass().getSuperclass().getSuperclass().getDeclaredField("dao");
			daoField.setAccessible(true);
			daoField.set(service, dao);
		} catch (Exception e) {
			throw new RuntimeException("Failed to inject DAO", e);
		}

		setupMessageResourceService(messageResourceService);
		setupCacheService(cacheService);
		setupEvictionMocks();
	}

	private void setupEvictionMocks() {
		lenient().when(cacheService.evictAll(anyString())).thenReturn(Mono.just(true));
		lenient().when(cacheService.evictAllFunction(anyString()))
				.thenReturn(Mono::just);
		lenient().when(cacheService.evictFunction(anyString(), any(Object[].class)))
				.thenReturn(Mono::just);
		lenient().when(cacheService.cacheEmptyValueOrGet(anyString(), any(), any()))
				.thenAnswer(invocation -> {
					java.util.function.Supplier<Mono<?>> supplier = invocation.getArgument(1);
					return supplier.get();
				});
	}

	// =========================================================================
	// create() tests
	// =========================================================================

	@Nested
	@DisplayName("create()")
	class CreateTests {

		@Test
		void create_SystemClient_OwnApp_CreatesSuccessfully() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			App entity = TestDataFactory.createOwnApp(null, null, "testapp");
			App created = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");

			when(dao.readById(any(ULong.class))).thenReturn(Mono.just(created));
			when(dao.create(any(App.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.create(entity))
					.assertNext(app -> {
						assertEquals(APP_ID, app.getId());
						assertEquals("testapp", app.getAppCode());
					})
					.verifyComplete();
		}

		@Test
		void create_SystemClient_SetsClientIdFromContext_WhenNull() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			App entity = TestDataFactory.createOwnApp(null, null, "testapp");
			entity.setClientId(null);

			App created = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");

			when(dao.create(any(App.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.create(entity))
					.assertNext(app -> assertNotNull(app.getId()))
					.verifyComplete();
		}

		@Test
		void create_NonSystemClient_OwnClientId_CreatesSuccessfully() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_CREATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			App entity = TestDataFactory.createOwnApp(null, BUS_CLIENT_ID, "testapp");
			App created = TestDataFactory.createOwnApp(APP_ID, BUS_CLIENT_ID, "testapp");

			when(dao.create(any(App.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.create(entity))
					.assertNext(app -> assertEquals(APP_ID, app.getId()))
					.verifyComplete();
		}

		@Test
		void create_NonSystemClient_ManagedClient_CreatesSuccessfully() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_CREATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			App entity = TestDataFactory.createOwnApp(null, MANAGED_CLIENT_ID, "testapp");
			App created = TestDataFactory.createOwnApp(APP_ID, MANAGED_CLIENT_ID, "testapp");

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(MANAGED_CLIENT_ID)))
					.thenReturn(Mono.just(true));
			when(dao.create(any(App.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.create(entity))
					.assertNext(app -> assertEquals(APP_ID, app.getId()))
					.verifyComplete();
		}

		@Test
		void create_NonSystemClient_NotManagedClient_ThrowsForbidden() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_CREATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			App entity = TestDataFactory.createOwnApp(null, MANAGED_CLIENT_ID, "testapp");

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(MANAGED_CLIENT_ID)))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.create(entity))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void create_GeneratesAppCode_WhenNull() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			App entity = TestDataFactory.createOwnApp(null, SYSTEM_CLIENT_ID, null);
			entity.setAppCode(null);

			App created = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "generatedcode");

			when(dao.generateAppCode(any(App.class))).thenReturn(Mono.just("generatedcode"));
			when(dao.create(any(App.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.create(entity))
					.assertNext(app -> assertEquals("generatedcode", app.getAppCode()))
					.verifyComplete();
		}

		@Test
		void create_UsesExistingAppCode_WhenProvided() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			App entity = TestDataFactory.createOwnApp(null, SYSTEM_CLIENT_ID, "mycode");
			App created = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "mycode");

			when(dao.create(any(App.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.create(entity))
					.assertNext(app -> assertEquals("mycode", app.getAppCode()))
					.verifyComplete();

			verify(dao, never()).generateAppCode(any(App.class));
		}

		@Test
		void create_AppCodeWithSpecialChars_ThrowsBadRequest() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			App entity = TestDataFactory.createOwnApp(null, SYSTEM_CLIENT_ID, "test-app!");

			StepVerifier.create(service.create(entity))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void create_AppCodeWithNumbers_ThrowsBadRequest() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			App entity = TestDataFactory.createOwnApp(null, SYSTEM_CLIENT_ID, "test123");

			StepVerifier.create(service.create(entity))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void create_ExplicitApp_UsesExplicitFlow() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_CREATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			Client managedClient = TestDataFactory.createBusinessClient(MANAGED_CLIENT_ID, "MANAGED");

			App entity = TestDataFactory.createExplicitApp(null, null, "explicitapp");
			App created = TestDataFactory.createExplicitApp(APP_ID, MANAGED_CLIENT_ID, "explicitapp");

			when(clientService.getManagedClientOfClientById(BUS_CLIENT_ID))
					.thenReturn(Mono.just(managedClient));
			when(dao.create(any(App.class))).thenReturn(Mono.just(created));
			when(dao.addClientAccess(APP_ID, BUS_CLIENT_ID, true))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.create(entity))
					.assertNext(app -> {
						assertEquals(APP_ID, app.getId());
						assertEquals(SecurityAppAppAccessType.EXPLICIT, app.getAppAccessType());
					})
					.verifyComplete();
		}

		@Test
		void create_ExplicitApp_FallsBackToSystemClientId() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_CREATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			App entity = TestDataFactory.createExplicitApp(null, null, "explicitapp");
			App created = TestDataFactory.createExplicitApp(APP_ID, SYSTEM_CLIENT_ID, "explicitapp");

			when(clientService.getManagedClientOfClientById(BUS_CLIENT_ID))
					.thenReturn(Mono.empty());
			when(clientService.getSystemClientId())
					.thenReturn(Mono.just(SYSTEM_CLIENT_ID));
			when(dao.create(any(App.class))).thenReturn(Mono.just(created));
			when(dao.addClientAccess(APP_ID, BUS_CLIENT_ID, true))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.create(entity))
					.assertNext(app -> assertEquals(APP_ID, app.getId()))
					.verifyComplete();
		}

		@Test
		void create_ExplicitApp_GeneratesAppCode_WhenNull() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_CREATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			Client managedClient = TestDataFactory.createBusinessClient(MANAGED_CLIENT_ID, "MANAGED");

			App entity = TestDataFactory.createExplicitApp(null, null, null);
			entity.setAppCode(null);

			App created = TestDataFactory.createExplicitApp(APP_ID, MANAGED_CLIENT_ID, "gencode");

			when(clientService.getManagedClientOfClientById(BUS_CLIENT_ID))
					.thenReturn(Mono.just(managedClient));
			when(dao.generateAppCode(any(App.class))).thenReturn(Mono.just("gencode"));
			when(dao.create(any(App.class))).thenReturn(Mono.just(created));
			when(dao.addClientAccess(APP_ID, BUS_CLIENT_ID, true))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.create(entity))
					.assertNext(app -> assertEquals("gencode", app.getAppCode()))
					.verifyComplete();
		}

		@Test
		void create_EvictsCaches() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			App entity = TestDataFactory.createOwnApp(null, SYSTEM_CLIENT_ID, "testapp");
			App created = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");

			when(dao.create(any(App.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.create(entity))
					.assertNext(AppServiceTest.this::assertNotNull)
					.verifyComplete();

			verify(cacheService).evictAllFunction("appInheritance");
			verify(cacheService).evictAllFunction("uri");
		}
	}

	// =========================================================================
	// update(App entity) tests
	// =========================================================================

	@Nested
	@DisplayName("update(App entity)")
	class UpdateEntityTests {

		@Test
		void update_ExistingApp_UpdatesSuccessfully() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			App existing = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");
			App updated = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");
			updated.setAppName("Updated Name");

			when(dao.readById(APP_ID)).thenReturn(Mono.just(existing));
			when(dao.update(any(App.class))).thenReturn(Mono.just(updated));

			StepVerifier.create(service.update(updated))
					.assertNext(app -> assertEquals("Updated Name", app.getAppName()))
					.verifyComplete();
		}

		@Test
		void update_NonExistingApp_ThrowsNotFound() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			App entity = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");

			when(dao.readById(APP_ID)).thenReturn(Mono.empty());

			StepVerifier.create(service.update(entity))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.NOT_FOUND)
					.verify();
		}

		@Test
		void update_EvictsCaches() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			App existing = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");
			App updated = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");

			when(dao.readById(APP_ID)).thenReturn(Mono.just(existing));
			when(dao.update(any(App.class))).thenReturn(Mono.just(updated));

			StepVerifier.create(service.update(updated))
					.assertNext(AppServiceTest.this::assertNotNull)
					.verifyComplete();

			verify(cacheService).evictAllFunction("appInheritance");
			verify(cacheService).evictAllFunction("uri");
			verify(cacheService).evictAllFunction("clientUrl");
			verify(cacheService).evictAllFunction("gatewayClientAppCode");
			verify(cacheService).evict("byAppCode", "testapp");
			verify(cacheService).evict("byAppId", APP_ID);
		}
	}

	// =========================================================================
	// update(ULong key, Map fields) tests
	// =========================================================================

	@Nested
	@DisplayName("update(ULong key, Map fields)")
	class UpdateMapTests {

		@Test
		void updateByMap_ExistingApp_UpdatesSuccessfully() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			App existing = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");
			App updated = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");
			updated.setAppName("Updated Name");

			when(dao.readById(APP_ID)).thenReturn(Mono.just(existing));
			when(dao.getPojoClass()).thenReturn(Mono.just(App.class));
			when(dao.update(any(App.class))).thenReturn(Mono.just(updated));

			Map<String, Object> fields = Map.of("appName", "Updated Name");

			StepVerifier.create(service.update(APP_ID, fields))
					.assertNext(app -> assertEquals("Updated Name", app.getAppName()))
					.verifyComplete();
		}

		@Test
		void updateByMap_NonExistingApp_ThrowsNotFound() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			when(dao.readById(APP_ID)).thenReturn(Mono.empty());

			Map<String, Object> fields = Map.of("appName", "Updated Name");

			StepVerifier.create(service.update(APP_ID, fields))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.NOT_FOUND)
					.verify();
		}

		@Test
		void updateByMap_EvictsCaches() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			App existing = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");
			App updated = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");

			when(dao.readById(APP_ID)).thenReturn(Mono.just(existing));
			when(dao.getPojoClass()).thenReturn(Mono.just(App.class));
			when(dao.update(any(App.class))).thenReturn(Mono.just(updated));

			Map<String, Object> fields = Map.of("appName", "Updated Name");

			StepVerifier.create(service.update(APP_ID, fields))
					.assertNext(AppServiceTest.this::assertNotNull)
					.verifyComplete();

			verify(cacheService).evictAllFunction("appInheritance");
			verify(cacheService).evict("byAppCode", "testapp");
			verify(cacheService).evict("byAppId", APP_ID);
		}
	}

	// =========================================================================
	// delete() tests
	// =========================================================================

	@Nested
	@DisplayName("delete()")
	class DeleteTests {

		@Test
		void delete_SystemClient_ActiveApp_ArchivesApp() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			App activeApp = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");
			activeApp.setStatus(SecurityAppStatus.ACTIVE);

			App archivedApp = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");
			archivedApp.setStatus(SecurityAppStatus.ARCHIVED);

			when(dao.readById(APP_ID)).thenReturn(Mono.just(activeApp));
			when(dao.update(any(App.class))).thenReturn(Mono.just(archivedApp));

			StepVerifier.create(service.delete(APP_ID))
					.assertNext(result -> assertEquals(1, result))
					.verifyComplete();

			verify(dao, never()).delete(any(ULong.class));
		}

		@Test
		void delete_SystemClient_ArchivedApp_DeletesPermanently() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			App archivedApp = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");
			archivedApp.setStatus(SecurityAppStatus.ARCHIVED);

			when(dao.readById(APP_ID)).thenReturn(Mono.just(archivedApp));
			when(dao.delete(APP_ID)).thenReturn(Mono.just(1));

			StepVerifier.create(service.delete(APP_ID))
					.assertNext(result -> assertEquals(1, result))
					.verifyComplete();
		}

		@Test
		void delete_ExplicitApp_ChecksWriteAccess() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_DELETE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			App explicitApp = TestDataFactory.createExplicitApp(APP_ID, MANAGED_CLIENT_ID, "testapp");
			explicitApp.setStatus(SecurityAppStatus.ACTIVE);

			App archivedApp = TestDataFactory.createExplicitApp(APP_ID, MANAGED_CLIENT_ID, "testapp");
			archivedApp.setStatus(SecurityAppStatus.ARCHIVED);

			when(dao.readById(APP_ID)).thenReturn(Mono.just(explicitApp));
			when(dao.hasWriteAccess(APP_ID, BUS_CLIENT_ID)).thenReturn(Mono.just(true));
			when(dao.update(any(App.class))).thenReturn(Mono.just(archivedApp));

			StepVerifier.create(service.delete(APP_ID))
					.assertNext(result -> assertEquals(1, result))
					.verifyComplete();
		}

		@Test
		void delete_NonExplicitApp_ChecksManagement() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_DELETE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			App ownApp = TestDataFactory.createOwnApp(APP_ID, MANAGED_CLIENT_ID, "testapp");
			ownApp.setStatus(SecurityAppStatus.ACTIVE);

			App archivedApp = TestDataFactory.createOwnApp(APP_ID, MANAGED_CLIENT_ID, "testapp");
			archivedApp.setStatus(SecurityAppStatus.ARCHIVED);

			when(dao.readById(APP_ID)).thenReturn(Mono.just(ownApp));
			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(MANAGED_CLIENT_ID)))
					.thenReturn(Mono.just(true));
			when(dao.update(any(App.class))).thenReturn(Mono.just(archivedApp));

			StepVerifier.create(service.delete(APP_ID))
					.assertNext(result -> assertEquals(1, result))
					.verifyComplete();
		}

		@Test
		void delete_NoAccess_ThrowsNotFound() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_DELETE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			App ownApp = TestDataFactory.createOwnApp(APP_ID, MANAGED_CLIENT_ID, "testapp");
			ownApp.setStatus(SecurityAppStatus.ACTIVE);

			when(dao.readById(APP_ID)).thenReturn(Mono.just(ownApp));
			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(MANAGED_CLIENT_ID)))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.delete(APP_ID))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.NOT_FOUND)
					.verify();
		}

		@Test
		void delete_ArchivedApp_EvictsCaches() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			App archivedApp = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");
			archivedApp.setStatus(SecurityAppStatus.ARCHIVED);

			when(dao.readById(APP_ID)).thenReturn(Mono.just(archivedApp));
			when(dao.delete(APP_ID)).thenReturn(Mono.just(1));

			StepVerifier.create(service.delete(APP_ID))
					.assertNext(result -> assertEquals(1, result))
					.verifyComplete();

			verify(cacheService).evictAllFunction("appInheritance");
			verify(cacheService).evict("byAppCode", "testapp");
			verify(cacheService).evict("byAppId", APP_ID);
		}
	}

	// =========================================================================
	// hasReadAccess() tests
	// =========================================================================

	@Nested
	@DisplayName("hasReadAccess()")
	class HasReadAccessTests {

		@Test
		void hasReadAccess_ByAppCodeAndClientCode_ReturnsTrue() {
			when(dao.hasReadAccess("testapp", "BUSCLIENT")).thenReturn(Mono.just(true));

			StepVerifier.create(service.hasReadAccess("testapp", "BUSCLIENT"))
					.expectNext(true)
					.verifyComplete();
		}

		@Test
		void hasReadAccess_ByAppCodeAndClientCode_ReturnsFalse() {
			when(dao.hasReadAccess("testapp", "BUSCLIENT")).thenReturn(Mono.just(false));

			StepVerifier.create(service.hasReadAccess("testapp", "BUSCLIENT"))
					.expectNext(false)
					.verifyComplete();
		}

		@Test
		void hasReadAccess_ByIds_ReturnsTrue() {
			when(dao.hasReadAccess(APP_ID, BUS_CLIENT_ID)).thenReturn(Mono.just(true));

			StepVerifier.create(service.hasReadAccess(APP_ID, BUS_CLIENT_ID))
					.expectNext(true)
					.verifyComplete();
		}

		@Test
		void hasReadAccess_ByIds_ReturnsFalse() {
			when(dao.hasReadAccess(APP_ID, BUS_CLIENT_ID)).thenReturn(Mono.just(false));

			StepVerifier.create(service.hasReadAccess(APP_ID, BUS_CLIENT_ID))
					.expectNext(false)
					.verifyComplete();
		}
	}

	// =========================================================================
	// hasWriteAccess() tests
	// =========================================================================

	@Nested
	@DisplayName("hasWriteAccess()")
	class HasWriteAccessTests {

		@Test
		void hasWriteAccess_ByAppCodeAndClientCode_ReturnsTrue() {
			when(dao.hasWriteAccess("testapp", "BUSCLIENT")).thenReturn(Mono.just(true));

			StepVerifier.create(service.hasWriteAccess("testapp", "BUSCLIENT"))
					.expectNext(true)
					.verifyComplete();
		}

		@Test
		void hasWriteAccess_ByAppCodeAndClientCode_ReturnsFalse() {
			when(dao.hasWriteAccess("testapp", "BUSCLIENT")).thenReturn(Mono.just(false));

			StepVerifier.create(service.hasWriteAccess("testapp", "BUSCLIENT"))
					.expectNext(false)
					.verifyComplete();
		}

		@Test
		void hasWriteAccess_ByIds_ReturnsTrue() {
			when(dao.hasWriteAccess(APP_ID, BUS_CLIENT_ID)).thenReturn(Mono.just(true));

			StepVerifier.create(service.hasWriteAccess(APP_ID, BUS_CLIENT_ID))
					.expectNext(true)
					.verifyComplete();
		}

		@Test
		void hasWriteAccess_ByIds_ReturnsFalse() {
			when(dao.hasWriteAccess(APP_ID, BUS_CLIENT_ID)).thenReturn(Mono.just(false));

			StepVerifier.create(service.hasWriteAccess(APP_ID, BUS_CLIENT_ID))
					.expectNext(false)
					.verifyComplete();
		}
	}

	// =========================================================================
	// addClientAccess() tests
	// =========================================================================

	@Nested
	@DisplayName("addClientAccess()")
	class AddClientAccessTests {

		@Test
		void addClientAccess_SystemClient_OwnApp_AddsAccess() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");

			when(dao.readById(APP_ID)).thenReturn(Mono.just(app));
			when(dao.addClientAccess(APP_ID, MANAGED_CLIENT_ID, true)).thenReturn(Mono.just(true));
			when(clientService.getClientTypeNCodeNClientLevel(MANAGED_CLIENT_ID))
					.thenReturn(Mono.just(Tuples.of("BUS", "MANAGED", "LEVEL0")));

			StepVerifier.create(service.addClientAccess(APP_ID, MANAGED_CLIENT_ID, true))
					.expectNext(true)
					.verifyComplete();
		}

		@Test
		void addClientAccess_NonSystemClient_ManagedClient_AnyAccessType_AddsAccess() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_CREATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, BUS_CLIENT_ID, "testapp");
			app.setAppAccessType(SecurityAppAppAccessType.ANY);

			when(dao.readById(APP_ID)).thenReturn(Mono.just(app));
			when(clientService.doesClientManageClient(BUS_CLIENT_ID, MANAGED_CLIENT_ID))
					.thenReturn(Mono.just(true));
			when(dao.addClientAccess(APP_ID, MANAGED_CLIENT_ID, false)).thenReturn(Mono.just(true));
			when(clientService.getClientTypeNCodeNClientLevel(MANAGED_CLIENT_ID))
					.thenReturn(Mono.just(Tuples.of("BUS", "MANAGED", "LEVEL0")));

			StepVerifier.create(service.addClientAccess(APP_ID, MANAGED_CLIENT_ID, false))
					.expectNext(true)
					.verifyComplete();
		}

		@Test
		void addClientAccess_NonSystemClient_NotManaged_ThrowsForbidden() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_CREATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, BUS_CLIENT_ID, "testapp");

			when(dao.readById(APP_ID)).thenReturn(Mono.just(app));
			when(clientService.doesClientManageClient(BUS_CLIENT_ID, MANAGED_CLIENT_ID))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.addClientAccess(APP_ID, MANAGED_CLIENT_ID, true))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void addClientAccess_NonOwner_NonAnyAccessType_ThrowsForbidden() {
			ULong otherClientId = ULong.valueOf(5);
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(otherClientId, "OTHER",
					List.of("Authorities.Application_CREATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, BUS_CLIENT_ID, "testapp");
			app.setAppAccessType(SecurityAppAppAccessType.OWN);

			when(dao.readById(APP_ID)).thenReturn(Mono.just(app));
			when(clientService.doesClientManageClient(BUS_CLIENT_ID, MANAGED_CLIENT_ID))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.addClientAccess(APP_ID, MANAGED_CLIENT_ID, true))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}

	// =========================================================================
	// removeClient() tests
	// =========================================================================

	@Nested
	@DisplayName("removeClient()")
	class RemoveClientTests {

		@Test
		void removeClient_SystemClient_RemovesAccess() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			when(dao.removeClientAccess(APP_ID, ACCESS_ID)).thenReturn(Mono.just(true));

			StepVerifier.create(service.removeClient(APP_ID, ACCESS_ID))
					.expectNext(true)
					.verifyComplete();
		}

		@Test
		void removeClient_NonSystemClient_OwnerOfApp_RemovesAccess() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_UPDATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, BUS_CLIENT_ID, "testapp");

			when(dao.readById(APP_ID)).thenReturn(Mono.just(app));
			when(dao.removeClientAccess(APP_ID, ACCESS_ID)).thenReturn(Mono.just(true));

			StepVerifier.create(service.removeClient(APP_ID, ACCESS_ID))
					.expectNext(true)
					.verifyComplete();
		}

		@Test
		void removeClient_NonSystemClient_NotOwner_ThrowsForbidden() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_UPDATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, MANAGED_CLIENT_ID, "testapp");

			when(dao.readById(APP_ID)).thenReturn(Mono.just(app));

			StepVerifier.create(service.removeClient(APP_ID, ACCESS_ID))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}

	// =========================================================================
	// updateClientAccess() tests
	// =========================================================================

	@Nested
	@DisplayName("updateClientAccess()")
	class UpdateClientAccessTests {

		@Test
		void updateClientAccess_SystemClient_UpdatesAccess() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			when(dao.updateClientAccess(ACCESS_ID, true)).thenReturn(Mono.just(true));

			StepVerifier.create(service.updateClientAccess(ACCESS_ID, true))
					.expectNext(true)
					.verifyComplete();
		}

		@Test
		void updateClientAccess_NonSystemClient_OwnerOfApp_UpdatesAccess() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_UPDATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(ACCESS_ID, BUS_CLIENT_ID, "testapp");

			when(dao.readById(ACCESS_ID)).thenReturn(Mono.just(app));
			when(dao.updateClientAccess(ACCESS_ID, false)).thenReturn(Mono.just(true));

			StepVerifier.create(service.updateClientAccess(ACCESS_ID, false))
					.expectNext(true)
					.verifyComplete();
		}

		@Test
		void updateClientAccess_NonSystemClient_NotOwner_ThrowsForbidden() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_UPDATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(ACCESS_ID, MANAGED_CLIENT_ID, "testapp");

			when(dao.readById(ACCESS_ID)).thenReturn(Mono.just(app));

			StepVerifier.create(service.updateClientAccess(ACCESS_ID, true))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}

	// =========================================================================
	// getAppByCode() tests
	// =========================================================================

	@Nested
	@DisplayName("getAppByCode()")
	class GetAppByCodeTests {

		@Test
		void getAppByCode_ReturnsApp() {
			App app = TestDataFactory.createOwnApp(APP_ID, BUS_CLIENT_ID, "testapp");
			when(dao.getByAppCode("testapp")).thenReturn(Mono.just(app));

			StepVerifier.create(service.getAppByCode("testapp"))
					.assertNext(result -> {
						assertEquals(APP_ID, result.getId());
						assertEquals("testapp", result.getAppCode());
					})
					.verifyComplete();
		}

		@Test
		void getAppByCode_NotFound_ReturnsEmpty() {
			when(dao.getByAppCode("unknown")).thenReturn(Mono.empty());

			StepVerifier.create(service.getAppByCode("unknown"))
					.verifyComplete();
		}

		@Test
		void getAppByCode_UsesCache() {
			App app = TestDataFactory.createOwnApp(APP_ID, BUS_CLIENT_ID, "testapp");
			when(dao.getByAppCode("testapp")).thenReturn(Mono.just(app));

			StepVerifier.create(service.getAppByCode("testapp"))
					.assertNext(AppServiceTest.this::assertNotNull)
					.verifyComplete();

			verify(cacheService).cacheValueOrGet(eq("byAppCode"), any(), eq("testapp"));
		}
	}

	// =========================================================================
	// getAppById() tests
	// =========================================================================

	@Nested
	@DisplayName("getAppById()")
	class GetAppByIdTests {

		@Test
		void getAppById_ReturnsApp() {
			App app = TestDataFactory.createOwnApp(APP_ID, BUS_CLIENT_ID, "testapp");
			when(dao.readById(APP_ID)).thenReturn(Mono.just(app));

			StepVerifier.create(service.getAppById(APP_ID))
					.assertNext(result -> {
						assertEquals(APP_ID, result.getId());
						assertEquals("testapp", result.getAppCode());
					})
					.verifyComplete();
		}

		@Test
		void getAppById_NullId_ReturnsEmpty() {
			StepVerifier.create(service.getAppById(null))
					.verifyComplete();
		}

		@Test
		void getAppById_UsesCache() {
			App app = TestDataFactory.createOwnApp(APP_ID, BUS_CLIENT_ID, "testapp");
			when(dao.readById(APP_ID)).thenReturn(Mono.just(app));

			StepVerifier.create(service.getAppById(APP_ID))
					.assertNext(AppServiceTest.this::assertNotNull)
					.verifyComplete();

			verify(cacheService).cacheValueOrGet(eq("byAppId"), any(), eq(APP_ID));
		}
	}

	// =========================================================================
	// getAppId() tests
	// =========================================================================

	@Nested
	@DisplayName("getAppId()")
	class GetAppIdTests {

		@Test
		void getAppId_ReturnsId() {
			App app = TestDataFactory.createOwnApp(APP_ID, BUS_CLIENT_ID, "testapp");
			when(dao.getByAppCode("testapp")).thenReturn(Mono.just(app));

			StepVerifier.create(service.getAppId("testapp"))
					.assertNext(id -> assertEquals(APP_ID, id))
					.verifyComplete();
		}

		@Test
		void getAppId_AppNotFound_ReturnsEmpty() {
			when(dao.getByAppCode("unknown")).thenReturn(Mono.empty());

			StepVerifier.create(service.getAppId("unknown"))
					.verifyComplete();
		}
	}

	// =========================================================================
	// getProperties() tests
	// =========================================================================

	@Nested
	@DisplayName("getProperties()")
	class GetPropertiesTests {

		@Test
		void getProperties_SystemClient_WithClientId_ReturnsProperties() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Map<ULong, Map<String, AppProperty>> props = Map.of(BUS_CLIENT_ID,
					Map.of("key", new AppProperty().setAppId(APP_ID).setClientId(BUS_CLIENT_ID).setName("key")
							.setValue("value")));

			when(dao.getProperties(List.of(BUS_CLIENT_ID), APP_ID, null, null))
					.thenReturn(Mono.just(props));

			StepVerifier.create(service.getProperties(BUS_CLIENT_ID, APP_ID, null, null))
					.assertNext(result -> {
						assertNotNull(result);
						assertTrue(result.containsKey(BUS_CLIENT_ID));
					})
					.verifyComplete();
		}

		@Test
		void getProperties_SystemClient_NoClientId_ReturnsProperties() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Map<ULong, Map<String, AppProperty>> props = Map.of();

			when(dao.getProperties(List.of(), APP_ID, null, null))
					.thenReturn(Mono.just(props));

			StepVerifier.create(service.getProperties(null, APP_ID, null, null))
					.assertNext(AppServiceTest.this::assertNotNull)
					.verifyComplete();
		}

		@Test
		void getProperties_NonSystemClient_OwnClient_ReturnsProperties() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_READ", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			Map<ULong, Map<String, AppProperty>> props = Map.of(BUS_CLIENT_ID, Map.of());

			when(dao.getProperties(List.of(BUS_CLIENT_ID), APP_ID, null, null))
					.thenReturn(Mono.just(props));

			StepVerifier.create(service.getProperties(null, APP_ID, null, null))
					.assertNext(AppServiceTest.this::assertNotNull)
					.verifyComplete();
		}

		@Test
		void getProperties_NonSystemClient_ManagedClient_ReturnsProperties() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_READ", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			Map<ULong, Map<String, AppProperty>> props = Map.of(MANAGED_CLIENT_ID, Map.of());

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(MANAGED_CLIENT_ID)))
					.thenReturn(Mono.just(true));
			when(dao.getProperties(List.of(MANAGED_CLIENT_ID), APP_ID, null, null))
					.thenReturn(Mono.just(props));

			StepVerifier.create(service.getProperties(MANAGED_CLIENT_ID, APP_ID, null, null))
					.assertNext(AppServiceTest.this::assertNotNull)
					.verifyComplete();
		}

		@Test
		void getProperties_BothAppIdAndAppCodeNull_ThrowsBadRequest() {
			StepVerifier.create(service.getProperties(BUS_CLIENT_ID, null, null, null))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void getProperties_WithAppCode_ReturnsProperties() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Map<ULong, Map<String, AppProperty>> props = Map.of();

			when(dao.getProperties(List.of(), null, "testapp", null))
					.thenReturn(Mono.just(props));

			StepVerifier.create(service.getProperties(null, null, "testapp", null))
					.assertNext(AppServiceTest.this::assertNotNull)
					.verifyComplete();
		}

		@Test
		void getProperties_NonSystemClient_NotManaged_ReturnsEmpty() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_READ", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(MANAGED_CLIENT_ID)))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.getProperties(MANAGED_CLIENT_ID, APP_ID, null, null))
					.assertNext(result -> assertTrue(result.isEmpty()))
					.verifyComplete();
		}
	}

	// =========================================================================
	// updateProperty() tests
	// =========================================================================

	@Nested
	@DisplayName("updateProperty()")
	class UpdatePropertyTests {

		@Test
		void updateProperty_SystemClient_UpdatesSuccessfully() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			AppProperty property = new AppProperty();
			property.setAppId(APP_ID);
			property.setClientId(BUS_CLIENT_ID);
			property.setName("key");
			property.setValue("value");

			when(dao.hasWriteAccess(APP_ID, BUS_CLIENT_ID)).thenReturn(Mono.just(true));
			when(dao.updateProperty(property)).thenReturn(Mono.just(true));

			StepVerifier.create(service.updateProperty(property))
					.expectNext(true)
					.verifyComplete();
		}

		@Test
		void updateProperty_OwnClient_UpdatesSuccessfully() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_UPDATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			AppProperty property = new AppProperty();
			property.setAppId(APP_ID);
			property.setClientId(BUS_CLIENT_ID);
			property.setName("key");
			property.setValue("value");

			when(dao.hasWriteAccess(APP_ID, BUS_CLIENT_ID)).thenReturn(Mono.just(true));
			when(dao.updateProperty(property)).thenReturn(Mono.just(true));

			StepVerifier.create(service.updateProperty(property))
					.expectNext(true)
					.verifyComplete();
		}

		@Test
		void updateProperty_MissingAppId_ThrowsBadRequest() {
			AppProperty property = new AppProperty();
			property.setAppId(null);
			property.setClientId(BUS_CLIENT_ID);
			property.setName("key");

			StepVerifier.create(service.updateProperty(property))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void updateProperty_MissingName_ThrowsBadRequest() {
			AppProperty property = new AppProperty();
			property.setAppId(APP_ID);
			property.setClientId(BUS_CLIENT_ID);
			property.setName(null);

			StepVerifier.create(service.updateProperty(property))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void updateProperty_NoWriteAccess_ThrowsForbidden() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_UPDATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			AppProperty property = new AppProperty();
			property.setAppId(APP_ID);
			property.setClientId(BUS_CLIENT_ID);
			property.setName("key");
			property.setValue("value");

			when(dao.hasWriteAccess(APP_ID, BUS_CLIENT_ID)).thenReturn(Mono.just(false));

			StepVerifier.create(service.updateProperty(property))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void updateProperty_ManagedClient_UpdatesSuccessfully() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_UPDATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			AppProperty property = new AppProperty();
			property.setAppId(APP_ID);
			property.setClientId(MANAGED_CLIENT_ID);
			property.setName("key");
			property.setValue("value");

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(MANAGED_CLIENT_ID)))
					.thenReturn(Mono.just(true));
			when(dao.hasWriteAccess(APP_ID, MANAGED_CLIENT_ID)).thenReturn(Mono.just(true));
			when(dao.updateProperty(property)).thenReturn(Mono.just(true));

			StepVerifier.create(service.updateProperty(property))
					.expectNext(true)
					.verifyComplete();
		}
	}

	// =========================================================================
	// deleteProperty() tests
	// =========================================================================

	@Nested
	@DisplayName("deleteProperty()")
	class DeletePropertyTests {

		@Test
		void deleteProperty_SystemClient_DeletesSuccessfully() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			when(dao.hasWriteAccess(APP_ID, BUS_CLIENT_ID)).thenReturn(Mono.just(true));
			when(dao.deleteProperty(BUS_CLIENT_ID, APP_ID, "key")).thenReturn(Mono.just(true));

			StepVerifier.create(service.deleteProperty(BUS_CLIENT_ID, APP_ID, "key"))
					.expectNext(true)
					.verifyComplete();
		}

		@Test
		void deleteProperty_AppIdNull_ThrowsBadRequest() {
			StepVerifier.create(service.deleteProperty(BUS_CLIENT_ID, null, "key"))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void deleteProperty_OwnClient_DeletesSuccessfully() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_UPDATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			when(dao.hasWriteAccess(APP_ID, BUS_CLIENT_ID)).thenReturn(Mono.just(true));
			when(dao.deleteProperty(BUS_CLIENT_ID, APP_ID, "key")).thenReturn(Mono.just(true));

			StepVerifier.create(service.deleteProperty(BUS_CLIENT_ID, APP_ID, "key"))
					.expectNext(true)
					.verifyComplete();
		}

		@Test
		void deleteProperty_NoWriteAccess_ReturnsFalse() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			when(dao.hasWriteAccess(APP_ID, BUS_CLIENT_ID)).thenReturn(Mono.just(false));

			StepVerifier.create(service.deleteProperty(BUS_CLIENT_ID, APP_ID, "key"))
					.expectNext(false)
					.verifyComplete();
		}

		@Test
		void deleteProperty_ManagedClient_DeletesSuccessfully() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_UPDATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(MANAGED_CLIENT_ID)))
					.thenReturn(Mono.just(true));
			when(dao.hasWriteAccess(APP_ID, MANAGED_CLIENT_ID)).thenReturn(Mono.just(true));
			when(dao.deleteProperty(MANAGED_CLIENT_ID, APP_ID, "key")).thenReturn(Mono.just(true));

			StepVerifier.create(service.deleteProperty(MANAGED_CLIENT_ID, APP_ID, "key"))
					.expectNext(true)
					.verifyComplete();
		}

		@Test
		void deleteProperty_NotManagedClient_ReturnsEmpty() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_UPDATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(MANAGED_CLIENT_ID)))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.deleteProperty(MANAGED_CLIENT_ID, APP_ID, "key"))
					.verifyComplete();
		}
	}

	// =========================================================================
	// deletePropertyById() tests
	// =========================================================================

	@Nested
	@DisplayName("deletePropertyById()")
	class DeletePropertyByIdTests {

		@Test
		void deletePropertyById_SystemClient_DeletesSuccessfully() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			AppProperty property = new AppProperty();
			property.setId(PROPERTY_ID);
			property.setAppId(APP_ID);
			property.setClientId(BUS_CLIENT_ID);
			property.setName("key");

			when(dao.getPropertyById(PROPERTY_ID)).thenReturn(Mono.just(property));
			when(dao.hasWriteAccess(APP_ID, BUS_CLIENT_ID)).thenReturn(Mono.just(true));
			when(dao.deletePropertyById(PROPERTY_ID)).thenReturn(Mono.just(true));

			StepVerifier.create(service.deletePropertyById(PROPERTY_ID))
					.expectNext(true)
					.verifyComplete();
		}

		@Test
		void deletePropertyById_OwnClient_DeletesSuccessfully() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_UPDATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			AppProperty property = new AppProperty();
			property.setId(PROPERTY_ID);
			property.setAppId(APP_ID);
			property.setClientId(BUS_CLIENT_ID);
			property.setName("key");

			when(dao.getPropertyById(PROPERTY_ID)).thenReturn(Mono.just(property));
			when(dao.hasWriteAccess(APP_ID, BUS_CLIENT_ID)).thenReturn(Mono.just(true));
			when(dao.deletePropertyById(PROPERTY_ID)).thenReturn(Mono.just(true));

			StepVerifier.create(service.deletePropertyById(PROPERTY_ID))
					.expectNext(true)
					.verifyComplete();
		}

		@Test
		void deletePropertyById_NoWriteAccess_ReturnsFalse() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			AppProperty property = new AppProperty();
			property.setId(PROPERTY_ID);
			property.setAppId(APP_ID);
			property.setClientId(BUS_CLIENT_ID);
			property.setName("key");

			when(dao.getPropertyById(PROPERTY_ID)).thenReturn(Mono.just(property));
			when(dao.hasWriteAccess(APP_ID, BUS_CLIENT_ID)).thenReturn(Mono.just(false));

			StepVerifier.create(service.deletePropertyById(PROPERTY_ID))
					.expectNext(false)
					.verifyComplete();
		}
	}

	// =========================================================================
	// addAppDependency() tests
	// =========================================================================

	@Nested
	@DisplayName("addAppDependency()")
	class AddAppDependencyTests {

		@Test
		void addAppDependency_SystemClient_AddsSuccessfully() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "mainapp");
			App depApp = TestDataFactory.createOwnApp(APP_ID_2, SYSTEM_CLIENT_ID, "depapp");
			AppDependency dep = new AppDependency().setAppId(APP_ID).setDependentAppId(APP_ID_2);

			when(dao.getByAppCode("mainapp")).thenReturn(Mono.just(app));
			when(dao.getByAppCode("depapp")).thenReturn(Mono.just(depApp));
			when(dao.addAppDependency(APP_ID, APP_ID_2)).thenReturn(Mono.just(dep));

			StepVerifier.create(service.addAppDependency("mainapp", "depapp"))
					.assertNext(result -> {
						assertEquals(APP_ID, result.getAppId());
						assertEquals(APP_ID_2, result.getDependentAppId());
					})
					.verifyComplete();
		}

		@Test
		void addAppDependency_SameAppCode_ThrowsBadRequest() {
			StepVerifier.create(service.addAppDependency("mainapp", "mainapp"))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void addAppDependency_BlankAppCode_ThrowsBadRequest() {
			StepVerifier.create(service.addAppDependency("", "depapp"))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void addAppDependency_BlankDependentAppCode_ThrowsBadRequest() {
			StepVerifier.create(service.addAppDependency("mainapp", ""))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void addAppDependency_NonSystemClient_WithAccess_AddsSuccessfully() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_CREATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, BUS_CLIENT_ID, "mainapp");
			App depApp = TestDataFactory.createOwnApp(APP_ID_2, BUS_CLIENT_ID, "depapp");
			AppDependency dep = new AppDependency().setAppId(APP_ID).setDependentAppId(APP_ID_2);

			when(dao.getByAppCode("mainapp")).thenReturn(Mono.just(app));
			when(dao.getByAppCode("depapp")).thenReturn(Mono.just(depApp));
			when(dao.hasWriteAccess("mainapp", "BUSCLIENT")).thenReturn(Mono.just(true));
			when(dao.hasReadAccess("depapp", "BUSCLIENT")).thenReturn(Mono.just(true));
			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(true));
			when(dao.addAppDependency(APP_ID, APP_ID_2)).thenReturn(Mono.just(dep));

			StepVerifier.create(service.addAppDependency("mainapp", "depapp"))
					.assertNext(AppServiceTest.this::assertNotNull)
					.verifyComplete();
		}

		@Test
		void addAppDependency_NonSystemClient_NoWriteAccess_ThrowsForbidden() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_CREATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, BUS_CLIENT_ID, "mainapp");
			App depApp = TestDataFactory.createOwnApp(APP_ID_2, BUS_CLIENT_ID, "depapp");

			when(dao.getByAppCode("mainapp")).thenReturn(Mono.just(app));
			when(dao.getByAppCode("depapp")).thenReturn(Mono.just(depApp));
			when(dao.hasWriteAccess("mainapp", "BUSCLIENT")).thenReturn(Mono.just(false));
			when(dao.hasReadAccess("depapp", "BUSCLIENT")).thenReturn(Mono.just(true));

			StepVerifier.create(service.addAppDependency("mainapp", "depapp"))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void addAppDependency_NonSystemClient_NoReadAccessOnDepApp_ThrowsForbidden() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_CREATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, BUS_CLIENT_ID, "mainapp");
			App depApp = TestDataFactory.createOwnApp(APP_ID_2, BUS_CLIENT_ID, "depapp");

			when(dao.getByAppCode("mainapp")).thenReturn(Mono.just(app));
			when(dao.getByAppCode("depapp")).thenReturn(Mono.just(depApp));
			when(dao.hasWriteAccess("mainapp", "BUSCLIENT")).thenReturn(Mono.just(true));
			when(dao.hasReadAccess("depapp", "BUSCLIENT")).thenReturn(Mono.just(false));

			StepVerifier.create(service.addAppDependency("mainapp", "depapp"))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void addAppDependency_EvictsDependencyCaches() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "mainapp");
			App depApp = TestDataFactory.createOwnApp(APP_ID_2, SYSTEM_CLIENT_ID, "depapp");
			AppDependency dep = new AppDependency().setAppId(APP_ID).setDependentAppId(APP_ID_2);

			when(dao.getByAppCode("mainapp")).thenReturn(Mono.just(app));
			when(dao.getByAppCode("depapp")).thenReturn(Mono.just(depApp));
			when(dao.addAppDependency(APP_ID, APP_ID_2)).thenReturn(Mono.just(dep));

			StepVerifier.create(service.addAppDependency("mainapp", "depapp"))
					.assertNext(AppServiceTest.this::assertNotNull)
					.verifyComplete();

			verify(cacheService).evictFunction("appDependencies", "mainapp");
			verify(cacheService).evictFunction("appDepList", "mainapp");
		}
	}

	// =========================================================================
	// removeAppDependency() tests
	// =========================================================================

	@Nested
	@DisplayName("removeAppDependency()")
	class RemoveAppDependencyTests {

		@Test
		void removeAppDependency_SystemClient_SameClientId_RemovesSuccessfully() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "mainapp");
			App depApp = TestDataFactory.createOwnApp(APP_ID_2, SYSTEM_CLIENT_ID, "depapp");

			when(dao.getByAppCode("mainapp")).thenReturn(Mono.just(app));
			when(dao.getByAppCode("depapp")).thenReturn(Mono.just(depApp));
			when(dao.removeAppDependency(APP_ID, APP_ID_2)).thenReturn(Mono.just(true));

			StepVerifier.create(service.removeAppDependency("mainapp", "depapp"))
					.expectNext(true)
					.verifyComplete();
		}

		@Test
		void removeAppDependency_DifferentClientIds_ThrowsForbidden() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "mainapp");
			App depApp = TestDataFactory.createOwnApp(APP_ID_2, BUS_CLIENT_ID, "depapp");

			when(dao.getByAppCode("mainapp")).thenReturn(Mono.just(app));
			when(dao.getByAppCode("depapp")).thenReturn(Mono.just(depApp));

			StepVerifier.create(service.removeAppDependency("mainapp", "depapp"))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void removeAppDependency_NonSystemClient_ManagedClient_RemovesSuccessfully() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_DELETE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, BUS_CLIENT_ID, "mainapp");
			App depApp = TestDataFactory.createOwnApp(APP_ID_2, BUS_CLIENT_ID, "depapp");

			when(dao.getByAppCode("mainapp")).thenReturn(Mono.just(app));
			when(dao.getByAppCode("depapp")).thenReturn(Mono.just(depApp));
			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(true));
			when(dao.removeAppDependency(APP_ID, APP_ID_2)).thenReturn(Mono.just(true));

			StepVerifier.create(service.removeAppDependency("mainapp", "depapp"))
					.expectNext(true)
					.verifyComplete();
		}

		@Test
		void removeAppDependency_NonSystemClient_NotManaged_ThrowsForbidden() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_DELETE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, MANAGED_CLIENT_ID, "mainapp");
			App depApp = TestDataFactory.createOwnApp(APP_ID_2, MANAGED_CLIENT_ID, "depapp");

			when(dao.getByAppCode("mainapp")).thenReturn(Mono.just(app));
			when(dao.getByAppCode("depapp")).thenReturn(Mono.just(depApp));
			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(MANAGED_CLIENT_ID)))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.removeAppDependency("mainapp", "depapp"))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void removeAppDependency_EvictsDependencyCaches() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "mainapp");
			App depApp = TestDataFactory.createOwnApp(APP_ID_2, SYSTEM_CLIENT_ID, "depapp");

			when(dao.getByAppCode("mainapp")).thenReturn(Mono.just(app));
			when(dao.getByAppCode("depapp")).thenReturn(Mono.just(depApp));
			when(dao.removeAppDependency(APP_ID, APP_ID_2)).thenReturn(Mono.just(true));

			StepVerifier.create(service.removeAppDependency("mainapp", "depapp"))
					.expectNext(true)
					.verifyComplete();

			verify(cacheService).evictFunction("appDepList", "mainapp");
			verify(cacheService).evictFunction("appDependencies", "mainapp");
		}
	}

	// =========================================================================
	// getAppClients() tests
	// =========================================================================

	@Nested
	@DisplayName("getAppClients()")
	class GetAppClientsTests {

		@Test
		void getAppClients_SystemClient_ReturnsClients() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Pageable pageable = PageRequest.of(0, 10);
			Client client = TestDataFactory.createBusinessClient(BUS_CLIENT_ID, "BUSCLIENT");
			Page<Client> clientPage = new PageImpl<>(List.of(client), pageable, 1);

			when(dao.getAppClients("testapp", false, null, null, pageable))
					.thenReturn(Mono.just(clientPage));

			StepVerifier.create(service.getAppClients("testapp", false, null, pageable))
					.assertNext(page -> {
						assertEquals(1, page.getTotalElements());
						assertEquals("BUSCLIENT", page.getContent().get(0).getCode());
					})
					.verifyComplete();
		}

		@Test
		void getAppClients_NonSystemClient_WithWriteAccess_ReturnsClients() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_READ", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			Pageable pageable = PageRequest.of(0, 10);
			Client client = TestDataFactory.createBusinessClient(MANAGED_CLIENT_ID, "MANAGED");
			Page<Client> clientPage = new PageImpl<>(List.of(client), pageable, 1);

			when(dao.hasWriteAccess("testapp", "BUSCLIENT")).thenReturn(Mono.just(true));
			when(dao.getAppClients("testapp", false, null, BUS_CLIENT_ID, pageable))
					.thenReturn(Mono.just(clientPage));

			StepVerifier.create(service.getAppClients("testapp", false, null, pageable))
					.assertNext(page -> assertEquals(1, page.getTotalElements()))
					.verifyComplete();
		}

		@Test
		void getAppClients_NonSystemClient_NoWriteAccess_ReturnsEmptyPage() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_READ", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			Pageable pageable = PageRequest.of(0, 10);

			when(dao.hasWriteAccess("testapp", "BUSCLIENT")).thenReturn(Mono.just(false));

			StepVerifier.create(service.getAppClients("testapp", false, null, pageable))
					.assertNext(page -> assertTrue(page.isEmpty()))
					.verifyComplete();
		}
	}

	// =========================================================================
	// appInheritance() tests
	// =========================================================================

	@Nested
	@DisplayName("appInheritance()")
	class AppInheritanceTests {

		@Test
		void appInheritance_ReturnsList() {
			when(dao.appInheritance("testapp", "urlClient", "client"))
					.thenReturn(Mono.just(List.of("SYSTEM", "urlClient", "client")));

			StepVerifier.create(service.appInheritance("testapp", "urlClient", "client"))
					.assertNext(result -> {
						assertEquals(3, result.size());
						assertEquals("SYSTEM", result.get(0));
						assertEquals("urlClient", result.get(1));
						assertEquals("client", result.get(2));
					})
					.verifyComplete();
		}

		@Test
		void appInheritance_UsesCache() {
			when(dao.appInheritance("testapp", "urlClient", "client"))
					.thenReturn(Mono.just(List.of("SYSTEM", "client")));

			StepVerifier.create(service.appInheritance("testapp", "urlClient", "client"))
					.assertNext(AppServiceTest.this::assertNotNull)
					.verifyComplete();

			verify(cacheService).cacheValueOrGet(eq("appInheritance"), any(),
					eq("testapp"), eq(":"), eq("urlClient"), eq(":"), eq("client"));
		}

		@Test
		void appInheritance_NullUrlClientCode_ReturnsList() {
			when(dao.appInheritance("testapp", null, "client"))
					.thenReturn(Mono.just(List.of("SYSTEM", "client")));

			StepVerifier.create(service.appInheritance("testapp", null, "client"))
					.assertNext(result -> assertEquals(2, result.size()))
					.verifyComplete();
		}
	}

	// =========================================================================
	// getAppDependencies() tests
	// =========================================================================

	@Nested
	@DisplayName("getAppDependencies()")
	class GetAppDependenciesTests {

		@Test
		void getAppDependencies_ReturnsDependencies() {
			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");
			AppDependency dep = new AppDependency()
					.setAppId(APP_ID)
					.setDependentAppId(APP_ID_2)
					.setDependentAppCode("depapp");

			when(dao.getByAppCode("testapp")).thenReturn(Mono.just(app));
			when(dao.getAppDependencies(APP_ID)).thenReturn(Mono.just(List.of(dep)));

			StepVerifier.create(service.getAppDependencies("testapp"))
					.assertNext(result -> {
						assertEquals(1, result.size());
						assertEquals("depapp", result.get(0).getDependentAppCode());
					})
					.verifyComplete();
		}

		@Test
		void getAppDependencies_NoDependencies_ReturnsEmpty() {
			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");

			when(dao.getByAppCode("testapp")).thenReturn(Mono.just(app));
			when(dao.getAppDependencies(APP_ID)).thenReturn(Mono.empty());

			StepVerifier.create(service.getAppDependencies("testapp"))
					.verifyComplete();
		}

		@Test
		void getAppDependencies_UsesCacheEmptyValue() {
			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");
			AppDependency dep = new AppDependency().setAppId(APP_ID).setDependentAppId(APP_ID_2);

			when(dao.getByAppCode("testapp")).thenReturn(Mono.just(app));
			when(dao.getAppDependencies(APP_ID)).thenReturn(Mono.just(List.of(dep)));

			StepVerifier.create(service.getAppDependencies("testapp"))
					.assertNext(AppServiceTest.this::assertNotNull)
					.verifyComplete();

			verify(cacheService).cacheEmptyValueOrGet(eq("appDependencies"), any(), eq("testapp"));
		}
	}

	// =========================================================================
	// getAppByCodeCheckAccess() tests
	// =========================================================================

	@Nested
	@DisplayName("getAppByCodeCheckAccess()")
	class GetAppByCodeCheckAccessTests {

		@Test
		void getAppByCodeCheckAccess_SystemClient_ReturnsApp() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");
			when(dao.getByAppCode("testapp")).thenReturn(Mono.just(app));

			StepVerifier.create(service.getAppByCodeCheckAccess("testapp"))
					.assertNext(result -> assertEquals(APP_ID, result.getId()))
					.verifyComplete();
		}

		@Test
		void getAppByCodeCheckAccess_NonSystemClient_WithReadAccess_ReturnsApp() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_READ", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, BUS_CLIENT_ID, "testapp");
			when(dao.getByAppCode("testapp")).thenReturn(Mono.just(app));
			when(dao.hasReadAccess("testapp", "BUSCLIENT")).thenReturn(Mono.just(true));

			StepVerifier.create(service.getAppByCodeCheckAccess("testapp"))
					.assertNext(result -> assertEquals(APP_ID, result.getId()))
					.verifyComplete();
		}

		@Test
		void getAppByCodeCheckAccess_NonSystemClient_NoReadAccess_ReturnsEmpty() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_READ", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, BUS_CLIENT_ID, "testapp");
			when(dao.getByAppCode("testapp")).thenReturn(Mono.just(app));
			when(dao.hasReadAccess("testapp", "BUSCLIENT")).thenReturn(Mono.just(false));

			StepVerifier.create(service.getAppByCodeCheckAccess("testapp"))
					.verifyComplete();
		}
	}

	// =========================================================================
	// getAppStatus() tests
	// =========================================================================

	@Nested
	@DisplayName("getAppStatus()")
	class GetAppStatusTests {

		@Test
		void getAppStatus_ReturnsStatus() {
			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");
			app.setStatus(SecurityAppStatus.ACTIVE);
			when(dao.getByAppCode("testapp")).thenReturn(Mono.just(app));

			StepVerifier.create(service.getAppStatus("testapp"))
					.assertNext(status -> assertEquals("ACTIVE", status))
					.verifyComplete();
		}

		@Test
		void getAppStatus_ArchivedApp_ReturnsArchived() {
			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");
			app.setStatus(SecurityAppStatus.ARCHIVED);
			when(dao.getByAppCode("testapp")).thenReturn(Mono.just(app));

			StepVerifier.create(service.getAppStatus("testapp"))
					.assertNext(status -> assertEquals("ARCHIVED", status))
					.verifyComplete();
		}

		@Test
		void getAppStatus_UsesCache() {
			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");
			app.setStatus(SecurityAppStatus.ACTIVE);
			when(dao.getByAppCode("testapp")).thenReturn(Mono.just(app));

			StepVerifier.create(service.getAppStatus("testapp"))
					.assertNext(AppServiceTest.this::assertNotNull)
					.verifyComplete();

			verify(cacheService).cacheValueOrGet(eq("appStatus"), any(), eq("testapp"));
		}
	}

	// =========================================================================
	// isNoneUsingTheAppOtherThan() tests
	// =========================================================================

	@Nested
	@DisplayName("isNoneUsingTheAppOtherThan()")
	class IsNoneUsingTheAppOtherThanTests {

		@Test
		void isNoneUsingTheAppOtherThan_NoOtherUsers_ReturnsTrue() {
			when(dao.isNoneUsingTheAppOtherThan(APP_ID, BUS_CLIENT_ID.toBigInteger()))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.isNoneUsingTheAppOtherThan(APP_ID, BUS_CLIENT_ID.toBigInteger()))
					.expectNext(true)
					.verifyComplete();
		}

		@Test
		void isNoneUsingTheAppOtherThan_OtherUsersExist_ReturnsFalse() {
			when(dao.isNoneUsingTheAppOtherThan(APP_ID, BUS_CLIENT_ID.toBigInteger()))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.isNoneUsingTheAppOtherThan(APP_ID, BUS_CLIENT_ID.toBigInteger()))
					.expectNext(false)
					.verifyComplete();
		}
	}

	// =========================================================================
	// hasReadAccess(String[]) tests
	// =========================================================================

	@Nested
	@DisplayName("hasReadAccess(String[])")
	class HasReadAccessArrayTests {

		@Test
		void hasReadAccess_MultipleAppCodes_ReturnsMap() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_READ", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			when(dao.hasReadAccess("app1", "BUSCLIENT")).thenReturn(Mono.just(true));
			when(dao.hasReadAccess("app2", "BUSCLIENT")).thenReturn(Mono.just(false));

			String[] appCodes = {"app1", "app2"};

			StepVerifier.create(service.hasReadAccess(appCodes))
					.assertNext(result -> {
						assertTrue(result.get("app1"));
						assertFalse(result.get("app2"));
					})
					.verifyComplete();
		}
	}

	// =========================================================================
	// deleteEverything() tests
	// =========================================================================

	@Nested
	@DisplayName("deleteEverything()")
	class DeleteEverythingTests {

		@Test
		void deleteEverything_SystemClient_ForceDelete_DeletesEverything() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");

			when(dao.readById(APP_ID)).thenReturn(Mono.just(app));
			when(dao.deleteEverythingRelated(APP_ID, "testapp")).thenReturn(Mono.just(true));

			StepVerifier.create(service.deleteEverything(APP_ID, true))
					.expectNext(true)
					.verifyComplete();
		}

		@Test
		void deleteEverything_SystemClient_NoForce_ChecksUsage() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");

			when(dao.readById(APP_ID)).thenReturn(Mono.just(app));
			when(dao.isNoneUsingTheAppOtherThan(APP_ID, ca.getUser().getClientId()))
					.thenReturn(Mono.just(true));
			when(dao.deleteEverythingRelated(APP_ID, "testapp")).thenReturn(Mono.just(true));

			StepVerifier.create(service.deleteEverything(APP_ID, false))
					.expectNext(true)
					.verifyComplete();
		}

		@Test
		void deleteEverything_NonSystemClient_NoAccess_ThrowsNotFound() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Application_DELETE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, MANAGED_CLIENT_ID, "testapp");

			when(dao.readById(APP_ID)).thenReturn(Mono.just(app));

			StepVerifier.create(service.deleteEverything(APP_ID, true))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.NOT_FOUND)
					.verify();
		}

		@Test
		void deleteEverything_EvictsCaches() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");

			when(dao.readById(APP_ID)).thenReturn(Mono.just(app));
			when(dao.deleteEverythingRelated(APP_ID, "testapp")).thenReturn(Mono.just(true));

			StepVerifier.create(service.deleteEverything(APP_ID, true))
					.expectNext(true)
					.verifyComplete();

			verify(cacheService).evictAllFunction("appInheritance");
			verify(cacheService).evict("byAppCode", "testapp");
			verify(cacheService).evict("byAppId", APP_ID);
		}
	}

	private void assertNotNull(Object obj) {
		org.junit.jupiter.api.Assertions.assertNotNull(obj);
	}
}
