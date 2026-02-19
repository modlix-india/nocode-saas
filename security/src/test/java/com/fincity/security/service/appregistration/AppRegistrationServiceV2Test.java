package com.fincity.security.service.appregistration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.security.dao.appregistration.AppRegistrationV2DAO;
import com.fincity.security.dto.App;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.appregistration.AppRegistrationAccess;
import com.fincity.security.dto.appregistration.AppRegistrationFileAccess;
import com.fincity.security.enums.AppRegistrationObjectType;
import com.fincity.security.service.AbstractServiceUnitTest;
import com.fincity.security.service.AppService;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.ProfileService;
import com.fincity.security.service.RoleV2Service;
import com.fincity.security.service.SecurityMessageResourceService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AppRegistrationServiceV2Test extends AbstractServiceUnitTest {

	@Mock
	private SecurityMessageResourceService messageService;

	@Mock
	private AppRegistrationV2DAO dao;

	@Mock
	private ClientService clientService;

	@Mock
	private AppService appService;

	@Mock
	private ProfileService profileService;

	@Mock
	private RoleV2Service roleV2Service;

	private AppRegistrationServiceV2 service;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);
	private static final ULong BUS_CLIENT_ID = ULong.valueOf(2);
	private static final ULong APP_ID = ULong.valueOf(100);
	private static final ULong ENTITY_ID = ULong.valueOf(50);
	private static final ULong ALLOW_APP_ID = ULong.valueOf(200);

	@BeforeEach
	void setUp() {
		service = new AppRegistrationServiceV2(messageService, dao, clientService, appService, profileService,
				roleV2Service);
		setupMessageResourceService(messageService);
	}

	// =========================================================================
	// hasAccessToInnerIds (tested indirectly through create)
	// =========================================================================

	@Nested
	@DisplayName("hasAccessToInnerIds")
	class HasAccessToInnerIdsTests {

		@Test
		void hasAccessToInnerIds_NoExtraValues_ReturnsTrue() {
			// FILE_ACCESS has no extraValues, so hasAccessToInnerIds returns true immediately
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");
			AppRegistrationFileAccess entity = new AppRegistrationFileAccess();
			entity.setResourceType("STATIC");
			entity.setAccessName("images");
			entity.setPath("/images");

			AppRegistrationFileAccess created = new AppRegistrationFileAccess();
			created.setId(ENTITY_ID);
			created.setAppId(APP_ID);
			created.setClientId(SYSTEM_CLIENT_ID);
			created.setResourceType("STATIC");

			Client client = TestDataFactory.createSystemClient();

			when(appService.getAppByCode("testapp")).thenReturn(Mono.just(app));
			when(appService.hasWriteAccess(eq("testapp"), eq("SYSTEM"))).thenReturn(Mono.just(true));
			doReturn(Mono.just(created)).when(dao).create(eq(AppRegistrationObjectType.FILE_ACCESS), any());
			when(appService.read(APP_ID)).thenReturn(Mono.just(app));
			when(clientService.read(SYSTEM_CLIENT_ID)).thenReturn(Mono.just(client));

			StepVerifier.create(service.create(AppRegistrationObjectType.FILE_ACCESS, "testapp", entity))
					.assertNext(result -> {
						assertEquals(ENTITY_ID, result.getId());
						assertEquals(APP_ID, result.getAppId());
					})
					.verifyComplete();
		}

		@Test
		void hasAccessToInnerIds_AllAccessible_Succeeds() {
			// APPLICATION_ACCESS has extraValues with allowAppId -> AppService.hasAccessTo
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");
			App allowApp = TestDataFactory.createOwnApp(ALLOW_APP_ID, SYSTEM_CLIENT_ID, "allowedapp");

			AppRegistrationAccess entity = new AppRegistrationAccess();
			entity.setAllowAppId(ALLOW_APP_ID);

			AppRegistrationAccess created = new AppRegistrationAccess();
			created.setId(ENTITY_ID);
			created.setAppId(APP_ID);
			created.setClientId(SYSTEM_CLIENT_ID);
			created.setAllowAppId(ALLOW_APP_ID);

			Client client = TestDataFactory.createSystemClient();

			when(appService.getAppByCode("testapp")).thenReturn(Mono.just(app));
			when(appService.hasWriteAccess(eq("testapp"), eq("SYSTEM"))).thenReturn(Mono.just(true));
			when(appService.hasAccessTo(eq(ALLOW_APP_ID), eq(SYSTEM_CLIENT_ID),
					eq(AppRegistrationObjectType.APPLICATION_ACCESS)))
					.thenReturn(Mono.just(true));
			doReturn(Mono.just(created)).when(dao).create(eq(AppRegistrationObjectType.APPLICATION_ACCESS), any());
			when(appService.read(APP_ID)).thenReturn(Mono.just(app));
			when(appService.read(ALLOW_APP_ID)).thenReturn(Mono.just(allowApp));
			when(clientService.read(SYSTEM_CLIENT_ID)).thenReturn(Mono.just(client));

			StepVerifier.create(service.create(AppRegistrationObjectType.APPLICATION_ACCESS, "testapp", entity))
					.assertNext(result -> {
						assertEquals(ENTITY_ID, result.getId());
						assertNotNull(result.getApp());
					})
					.verifyComplete();
		}

		@Test
		void hasAccessToInnerIds_OneNotAccessible_ThrowsForbidden() {
			// When hasAccessTo returns false for the inner id, the create should fail
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");

			AppRegistrationAccess entity = new AppRegistrationAccess();
			entity.setAllowAppId(ALLOW_APP_ID);

			when(appService.getAppByCode("testapp")).thenReturn(Mono.just(app));
			when(appService.hasWriteAccess(eq("testapp"), eq("SYSTEM"))).thenReturn(Mono.just(true));
			when(appService.hasAccessTo(eq(ALLOW_APP_ID), eq(SYSTEM_CLIENT_ID),
					eq(AppRegistrationObjectType.APPLICATION_ACCESS)))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.create(AppRegistrationObjectType.APPLICATION_ACCESS, "testapp", entity))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}

	// =========================================================================
	// create
	// =========================================================================

	@Nested
	@DisplayName("create")
	class CreateTests {

		@Test
		void create_DelegatesToDAO() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");
			Client client = TestDataFactory.createSystemClient();

			AppRegistrationFileAccess entity = new AppRegistrationFileAccess();
			entity.setResourceType("STATIC");
			entity.setAccessName("images");
			entity.setPath("/images");

			AppRegistrationFileAccess created = new AppRegistrationFileAccess();
			created.setId(ENTITY_ID);
			created.setAppId(APP_ID);
			created.setClientId(SYSTEM_CLIENT_ID);
			created.setResourceType("STATIC");

			when(appService.getAppByCode("testapp")).thenReturn(Mono.just(app));
			when(appService.hasWriteAccess(eq("testapp"), eq("SYSTEM"))).thenReturn(Mono.just(true));
			doReturn(Mono.just(created)).when(dao).create(eq(AppRegistrationObjectType.FILE_ACCESS), any());
			when(appService.read(APP_ID)).thenReturn(Mono.just(app));
			when(clientService.read(SYSTEM_CLIENT_ID)).thenReturn(Mono.just(client));

			StepVerifier.create(service.create(AppRegistrationObjectType.FILE_ACCESS, "testapp", entity))
					.assertNext(result -> {
						assertEquals(ENTITY_ID, result.getId());
						assertEquals(APP_ID, result.getAppId());
						assertNotNull(result.getApp());
						assertNotNull(result.getClient());
					})
					.verifyComplete();

			verify(dao).create(eq(AppRegistrationObjectType.FILE_ACCESS), any());
		}

		@Test
		void create_BusinessClient_SetsClientIdFromContext() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					java.util.List.of("Authorities.Logged_IN"));
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, BUS_CLIENT_ID, "testapp");
			Client client = TestDataFactory.createBusinessClient(BUS_CLIENT_ID, "BUSCLIENT");

			AppRegistrationFileAccess entity = new AppRegistrationFileAccess();
			entity.setResourceType("STATIC");
			entity.setAccessName("images");
			entity.setPath("/images");
			// clientId is null, should be set from context

			AppRegistrationFileAccess created = new AppRegistrationFileAccess();
			created.setId(ENTITY_ID);
			created.setAppId(APP_ID);
			created.setClientId(BUS_CLIENT_ID);
			created.setResourceType("STATIC");

			when(appService.getAppByCode("testapp")).thenReturn(Mono.just(app));
			when(appService.hasWriteAccess(eq("testapp"), eq("BUSCLIENT"))).thenReturn(Mono.just(true));
			doReturn(Mono.just(created)).when(dao).create(eq(AppRegistrationObjectType.FILE_ACCESS), any());
			when(appService.read(APP_ID)).thenReturn(Mono.just(app));
			when(clientService.read(BUS_CLIENT_ID)).thenReturn(Mono.just(client));

			StepVerifier.create(service.create(AppRegistrationObjectType.FILE_ACCESS, "testapp", entity))
					.assertNext(result -> {
						assertEquals(BUS_CLIENT_ID, result.getClientId());
					})
					.verifyComplete();
		}
	}

	// =========================================================================
	// delete
	// =========================================================================

	@Nested
	@DisplayName("delete")
	class DeleteTests {

		@Test
		void delete_DelegatesToDAO() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testapp");
			Client client = TestDataFactory.createSystemClient();

			AppRegistrationFileAccess existing = new AppRegistrationFileAccess();
			existing.setId(ENTITY_ID);
			existing.setAppId(APP_ID);
			existing.setClientId(SYSTEM_CLIENT_ID);
			existing.setResourceType("STATIC");

			doReturn(Mono.just(existing)).when(dao).getById(AppRegistrationObjectType.FILE_ACCESS, ENTITY_ID);
			when(appService.hasWriteAccess(eq(APP_ID), eq(SYSTEM_CLIENT_ID))).thenReturn(Mono.just(true));
			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(SYSTEM_CLIENT_ID)))
					.thenReturn(Mono.just(true));
			when(appService.read(APP_ID)).thenReturn(Mono.just(app));
			when(clientService.read(SYSTEM_CLIENT_ID)).thenReturn(Mono.just(client));
			when(dao.delete(AppRegistrationObjectType.FILE_ACCESS, ENTITY_ID)).thenReturn(Mono.just(true));

			StepVerifier.create(service.delete(AppRegistrationObjectType.FILE_ACCESS, ENTITY_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();

			verify(dao).delete(AppRegistrationObjectType.FILE_ACCESS, ENTITY_ID);
		}
	}
}
