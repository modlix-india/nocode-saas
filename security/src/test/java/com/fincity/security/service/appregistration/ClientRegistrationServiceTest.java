package com.fincity.security.service.appregistration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.model.ClientUrlPattern;
import com.fincity.security.dao.ClientDAO;
import com.fincity.security.dao.appregistration.AppRegistrationV2DAO;
import com.fincity.security.dto.App;
import com.fincity.security.dto.AppProperty;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.ClientHierarchy;
import com.fincity.security.dto.TokenObject;
import com.fincity.security.dto.User;
import com.fincity.security.dto.policy.ClientPasswordPolicy;
import com.fincity.security.enums.ClientLevelType;
import com.fincity.security.enums.otp.OtpPurpose;
import com.fincity.security.feign.IFeignFilesService;
import com.fincity.security.jooq.enums.SecurityAppAppUsageType;
import com.fincity.security.jooq.enums.SecurityAppRegIntegrationPlatform;
import com.fincity.security.model.AuthenticationPasswordType;
import com.fincity.security.model.AuthenticationResponse;
import com.fincity.security.model.ClientRegistrationRequest;
import com.fincity.security.model.RegistrationResponse;
import com.fincity.security.model.otp.OtpGenerationRequest;
import com.fincity.security.service.AbstractServiceUnitTest;
import com.fincity.security.service.AppService;
import com.fincity.security.service.AuthenticationService;
import com.fincity.security.service.ClientHierarchyService;
import com.fincity.security.service.ClientManagerService;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.ClientUrlService;
import com.fincity.security.service.OtpService;
import com.fincity.security.service.SecurityMessageResourceService;
import com.fincity.security.service.UserService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.function.Tuples;

@ExtendWith(MockitoExtension.class)
class ClientRegistrationServiceTest extends AbstractServiceUnitTest {

	@Mock
	private ClientDAO dao;

	@Mock
	private AppService appService;

	@Mock
	private UserService userService;

	@Mock
	private OtpService otpService;

	@Mock
	private AuthenticationService authenticationService;

	@Mock
	private ClientService clientService;

	@Mock
	private ClientHierarchyService clientHierarchyService;

	@Mock
	private ClientManagerService clientManagerService;

	@Mock
	private EventCreationService ecService;

	@Mock
	private ClientUrlService clientUrlService;

	@Mock
	private AppRegistrationV2DAO appRegistrationDAO;

	@Mock
	private IFeignFilesService filesService;

	@Mock
	private AppRegistrationIntegrationService appRegistrationIntegrationService;

	@Mock
	private AppRegistrationIntegrationTokenService appRegistrationIntegrationTokenService;

	@Mock
	private SecurityMessageResourceService securityMessageResourceService;

	private ClientRegistrationService service;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);
	private static final ULong BUS_CLIENT_ID = ULong.valueOf(2);
	private static final ULong NEW_CLIENT_ID = ULong.valueOf(50);
	private static final ULong APP_ID = ULong.valueOf(100);
	private static final ULong USER_ID = ULong.valueOf(10);
	private static final ULong NEW_USER_ID = ULong.valueOf(20);
	private static final String APP_CODE = "testapp";
	private static final String CLIENT_CODE = "TESTCLIENT";

	@BeforeEach
	void setUp() {
		service = new ClientRegistrationService(
				dao, appService, userService, otpService, authenticationService,
				clientService, clientHierarchyService, clientManagerService, ecService, clientUrlService,
				appRegistrationDAO, filesService, appRegistrationIntegrationService,
				appRegistrationIntegrationTokenService, securityMessageResourceService);

		// Set subDomainEndings via reflection since it's @Value injected
		try {
			var field = ClientRegistrationService.class.getDeclaredField("subDomainEndings");
			field.setAccessible(true);
			field.set(service, new String[] { ".modlix.com", ".fincity.com" });
		} catch (Exception e) {
			throw new RuntimeException("Failed to inject subDomainEndings", e);
		}

		setupMessageResourceService(securityMessageResourceService);
	}

	private ContextAuthentication createUnauthenticatedContext() {
		ContextAuthentication ca = TestDataFactory.createBusinessAuth(SYSTEM_CLIENT_ID, CLIENT_CODE,
				List.of("Authorities.Logged_IN"));
		ca.setAuthenticated(false);
		ca.setUrlAppCode(APP_CODE);
		ca.setUrlClientCode(CLIENT_CODE);
		return ca;
	}

	private ContextAuthentication createAuthenticatedContext() {
		ContextAuthentication ca = TestDataFactory.createBusinessAuth(SYSTEM_CLIENT_ID, CLIENT_CODE,
				List.of("Authorities.Logged_IN", "Authorities.Client_CREATE"));
		ca.setAuthenticated(true);
		ca.setUrlAppCode(APP_CODE);
		ca.setUrlClientCode(CLIENT_CODE);
		return ca;
	}

	private ClientRegistrationRequest createBasicRegistrationRequest() {
		ClientRegistrationRequest req = new ClientRegistrationRequest();
		req.setClientName("Test Client");
		req.setEmailId("test@example.com");
		req.setFirstName("Test");
		req.setLastName("User");
		req.setUserName("testuser");
		req.setPassword("StrongP@ss123");
		req.setPassType(AuthenticationPasswordType.PASSWORD);
		req.setLocaleCode("en");
		return req;
	}

	private ServerHttpRequest createMockRequest() {
		ServerHttpRequest request = mock(ServerHttpRequest.class);
		HttpHeaders headers = new HttpHeaders();
		headers.add(AppService.AC, APP_CODE);
		headers.add(ClientService.CC, CLIENT_CODE);
		headers.add("X-Forwarded-Host", "test.modlix.com");
		headers.add("X-Forwarded-Proto", "https");
		headers.add("X-Forwarded-Port", "443");
		when(request.getHeaders()).thenReturn(headers);
		return request;
	}

	// =========================================================================
	// register() tests
	// =========================================================================

	@Nested
	@DisplayName("register()")
	class RegisterTests {

		@Test
		void register_HappyPath_CreatesClientAndUser() {
			ContextAuthentication ca = createUnauthenticatedContext();
			setupSecurityContext(ca);

			ClientRegistrationRequest req = createBasicRegistrationRequest();
			ServerHttpRequest request = createMockRequest();
			ServerHttpResponse response = mock(ServerHttpResponse.class);

			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			Client createdClient = TestDataFactory.createIndividualClient(NEW_CLIENT_ID, "TESTCLIENT50");
			User createdUser = TestDataFactory.createActiveUser(NEW_USER_ID, NEW_CLIENT_ID);
			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, APP_CODE);

			// preRegisterCheck chain
			when(clientService.getClientAppPolicy(any(ULong.class), eq(APP_CODE), any()))
					.thenReturn(Mono.just(policy));
			when(clientService.validatePasswordPolicy(eq(policy), isNull(), any(), anyString()))
					.thenReturn(Mono.just(Boolean.TRUE));
			when(userService.checkIndividualClientUser(eq(CLIENT_CODE), any(ClientRegistrationRequest.class)))
					.thenReturn(Mono.just(Boolean.FALSE));
			when(appService.getAppByCode(APP_CODE)).thenReturn(Mono.just(app));
			when(clientService.getClientBy(CLIENT_CODE)).thenReturn(Mono.just(
					TestDataFactory.createBusinessClient(SYSTEM_CLIENT_ID, CLIENT_CODE)));
			when(clientService.getClientLevelType(any(ULong.class), eq(APP_ID)))
					.thenReturn(Mono.just(ClientLevelType.OWNER));

			// checkUsageType - app usage type needed
			app.setAppUsageType(SecurityAppAppUsageType.B2C);

			// fetchAppProp for URL suffix
			when(appService.getProperties(any(ULong.class), eq(APP_ID), isNull(), eq(AppService.APP_PROP_URL_SUFFIX)))
					.thenReturn(Mono.just(Map.of()));

			// checkSubDomainAvailability - not business client, returns ""
			// fetchAppProp for reg type
			AppProperty regProp = new AppProperty();
			regProp.setValue(AppService.APP_PROP_REG_TYPE_NO_VERIFICATION);
			when(appService.getProperties(any(ULong.class), isNull(), eq(APP_CODE), eq(AppService.APP_PROP_REG_TYPE)))
					.thenReturn(Mono.just(Map.of(SYSTEM_CLIENT_ID,
							Map.of(AppService.APP_PROP_REG_TYPE, regProp))));

			// registerClient chain
			when(dao.getValidClientCode(anyString())).thenReturn(Mono.just("TESTCLIENT50"));
			when(clientService.createForRegistration(any(Client.class), any(ULong.class)))
					.thenReturn(Mono.just(createdClient));
			when(clientHierarchyService.create(any(ULong.class), any(ULong.class)))
					.thenReturn(Mono.just(new ClientHierarchy()));
			when(clientService.addClientRegistrationObjects(any(), any(), any(), any()))
					.thenReturn(Mono.just(Boolean.TRUE));
			when(appService.addClientAccessAfterRegistration(anyString(), any(ULong.class), any(Client.class)))
					.thenReturn(Mono.just(Boolean.TRUE));

			// registerUser chain
			when(userService.createForRegistration(any(), any(), any(), any(), any(), any()))
					.thenReturn(Mono.just(createdUser));

			// makeOneTimeToken
			TokenObject tokenObj = TestDataFactory.createTokenObject(ULong.valueOf(1), NEW_USER_ID, "test-token",
					java.time.LocalDateTime.now().plusMinutes(30));
			when(userService.makeOneTimeToken(any(), any(), any(), any()))
					.thenReturn(Mono.just(tokenObj));

			// addFilesAccessPath
			when(appRegistrationDAO.getFileAccessForRegistration(any(), any(), any(), anyString(), any(), any()))
					.thenReturn(Mono.just(List.of()));

			// createRegistrationEvents
			when(clientUrlService.getAppUrl(anyString(), anyString()))
					.thenReturn(Mono.just(""));
			when(ecService.createEvent(any())).thenReturn(Mono.just(Boolean.TRUE));

			// getClientRegistrationResponse -> authenticate
			AuthenticationResponse authResp = new AuthenticationResponse();
			authResp.setAccessToken("access-token");
			when(authenticationService.authenticate(any(), any(ServerHttpRequest.class),
					any(ServerHttpResponse.class)))
					.thenReturn(Mono.just(authResp));

			// autoAddRegObjectsFromOtherApps
			when(appService.getAppIdsForAdditionalAppRegistration(anyString(), anyString(), any(Client.class)))
					.thenReturn(Mono.just(List.of()));

			StepVerifier.create(service.register(req, request, response))
					.assertNext(regResponse -> {
						assertNotNull(regResponse);
						assertTrue(regResponse.getCreated());
						assertEquals(NEW_USER_ID, regResponse.getUserId());
					})
					.verifyComplete();

			verify(clientService).createForRegistration(any(Client.class), any(ULong.class));
			verify(userService).createForRegistration(any(), any(), any(), any(), any(), any());
		}

		@Test
		void register_AuthenticatedNonOwner_SetsCreatingUserAsManager() {
			ContextAuthentication ca = createAuthenticatedContext();
			setupSecurityContext(ca);

			ClientRegistrationRequest req = createBasicRegistrationRequest();
			ServerHttpRequest request = createMockRequest();
			ServerHttpResponse response = mock(ServerHttpResponse.class);

			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			Client createdClient = TestDataFactory.createIndividualClient(NEW_CLIENT_ID, "TESTCLIENT50");
			User createdUser = TestDataFactory.createActiveUser(NEW_USER_ID, NEW_CLIENT_ID);
			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, APP_CODE);
			app.setAppUsageType(SecurityAppAppUsageType.B2C);

			// preRegisterCheck chain
			when(clientService.getClientAppPolicy(any(ULong.class), eq(APP_CODE), any()))
					.thenReturn(Mono.just(policy));
			when(clientService.validatePasswordPolicy(eq(policy), isNull(), any(), anyString()))
					.thenReturn(Mono.just(Boolean.TRUE));
			when(userService.checkIndividualClientUser(eq(CLIENT_CODE), any(ClientRegistrationRequest.class)))
					.thenReturn(Mono.just(Boolean.FALSE));
			when(appService.getAppByCode(APP_CODE)).thenReturn(Mono.just(app));
			when(clientService.getClientBy(CLIENT_CODE)).thenReturn(Mono.just(
					TestDataFactory.createBusinessClient(SYSTEM_CLIENT_ID, CLIENT_CODE)));
			when(clientService.getClientLevelType(any(ULong.class), eq(APP_ID)))
					.thenReturn(Mono.just(ClientLevelType.OWNER));
			when(appService.getProperties(any(ULong.class), eq(APP_ID), isNull(), eq(AppService.APP_PROP_URL_SUFFIX)))
					.thenReturn(Mono.just(Map.of()));

			AppProperty regProp = new AppProperty();
			regProp.setValue(AppService.APP_PROP_REG_TYPE_NO_VERIFICATION);
			when(appService.getProperties(any(ULong.class), isNull(), eq(APP_CODE), eq(AppService.APP_PROP_REG_TYPE)))
					.thenReturn(Mono.just(Map.of(SYSTEM_CLIENT_ID,
							Map.of(AppService.APP_PROP_REG_TYPE, regProp))));

			// registerClient chain
			when(dao.getValidClientCode(anyString())).thenReturn(Mono.just("TESTCLIENT50"));
			when(clientService.createForRegistration(any(Client.class), any(ULong.class)))
					.thenReturn(Mono.just(createdClient));
			when(clientHierarchyService.create(any(ULong.class), any(ULong.class)))
					.thenReturn(Mono.just(new ClientHierarchy()));
			when(clientManagerService.createInternal(any(ULong.class), any(ULong.class), any(ULong.class)))
					.thenReturn(Mono.just(1));
			when(clientService.addClientRegistrationObjects(any(), any(), any(), any()))
					.thenReturn(Mono.just(Boolean.TRUE));
			when(appService.addClientAccessAfterRegistration(anyString(), any(ULong.class), any(Client.class)))
					.thenReturn(Mono.just(Boolean.TRUE));

			// registerUser chain
			when(userService.createForRegistration(any(), any(), any(), any(), any(), any()))
					.thenReturn(Mono.just(createdUser));

			TokenObject tokenObj = TestDataFactory.createTokenObject(ULong.valueOf(1), NEW_USER_ID, "test-token",
					java.time.LocalDateTime.now().plusMinutes(30));
			when(userService.makeOneTimeToken(any(), any(), any(), any()))
					.thenReturn(Mono.just(tokenObj));
			when(appRegistrationDAO.getFileAccessForRegistration(any(), any(), any(), anyString(), any(), any()))
					.thenReturn(Mono.just(List.of()));
			when(clientUrlService.getAppUrl(anyString(), anyString()))
					.thenReturn(Mono.just(""));
			when(ecService.createEvent(any())).thenReturn(Mono.just(Boolean.TRUE));

			AuthenticationResponse authResp = new AuthenticationResponse();
			authResp.setAccessToken("access-token");
			when(authenticationService.authenticate(any(), any(ServerHttpRequest.class),
					any(ServerHttpResponse.class)))
					.thenReturn(Mono.just(authResp));
			when(appService.getAppIdsForAdditionalAppRegistration(anyString(), anyString(), any(Client.class)))
					.thenReturn(Mono.just(List.of()));

			StepVerifier.create(service.register(req, request, response))
					.assertNext(regResponse -> {
						assertNotNull(regResponse);
						assertTrue(regResponse.getCreated());
					})
					.verifyComplete();

			// Non-owner user should automatically become the manager
			verify(clientManagerService).createInternal(eq(NEW_CLIENT_ID), eq(USER_ID), eq(USER_ID));
		}

		@Test
		void register_AuthenticatedOwner_WithManagerId_SetsProvidedManager() {
			ULong managerId = ULong.valueOf(99);
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(USER_ID, SYSTEM_CLIENT_ID, CLIENT_CODE,
					List.of("Authorities.Logged_IN", "Authorities.Client_CREATE", "Authorities.ROLE_Owner"));
			ca.setAuthenticated(true);
			ca.setUrlAppCode(APP_CODE);
			ca.setUrlClientCode(CLIENT_CODE);
			setupSecurityContext(ca);

			ClientRegistrationRequest req = createBasicRegistrationRequest();
			req.setManagerId(managerId);
			ServerHttpRequest request = createMockRequest();
			ServerHttpResponse response = mock(ServerHttpResponse.class);

			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			Client createdClient = TestDataFactory.createIndividualClient(NEW_CLIENT_ID, "TESTCLIENT50");
			User createdUser = TestDataFactory.createActiveUser(NEW_USER_ID, NEW_CLIENT_ID);
			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, APP_CODE);
			app.setAppUsageType(SecurityAppAppUsageType.B2C);

			when(clientService.getClientAppPolicy(any(ULong.class), eq(APP_CODE), any()))
					.thenReturn(Mono.just(policy));
			when(clientService.validatePasswordPolicy(eq(policy), isNull(), any(), anyString()))
					.thenReturn(Mono.just(Boolean.TRUE));
			when(userService.checkIndividualClientUser(eq(CLIENT_CODE), any(ClientRegistrationRequest.class)))
					.thenReturn(Mono.just(Boolean.FALSE));
			when(appService.getAppByCode(APP_CODE)).thenReturn(Mono.just(app));
			when(clientService.getClientBy(CLIENT_CODE)).thenReturn(Mono.just(
					TestDataFactory.createBusinessClient(SYSTEM_CLIENT_ID, CLIENT_CODE)));
			when(clientService.getClientLevelType(any(ULong.class), eq(APP_ID)))
					.thenReturn(Mono.just(ClientLevelType.OWNER));
			when(appService.getProperties(any(ULong.class), eq(APP_ID), isNull(), eq(AppService.APP_PROP_URL_SUFFIX)))
					.thenReturn(Mono.just(Map.of()));

			AppProperty regProp = new AppProperty();
			regProp.setValue(AppService.APP_PROP_REG_TYPE_NO_VERIFICATION);
			when(appService.getProperties(any(ULong.class), isNull(), eq(APP_CODE), eq(AppService.APP_PROP_REG_TYPE)))
					.thenReturn(Mono.just(Map.of(SYSTEM_CLIENT_ID,
							Map.of(AppService.APP_PROP_REG_TYPE, regProp))));

			when(dao.getValidClientCode(anyString())).thenReturn(Mono.just("TESTCLIENT50"));
			when(clientService.createForRegistration(any(Client.class), any(ULong.class)))
					.thenReturn(Mono.just(createdClient));
			when(clientHierarchyService.create(any(ULong.class), any(ULong.class)))
					.thenReturn(Mono.just(new ClientHierarchy()));
			when(clientManagerService.createInternal(any(ULong.class), any(ULong.class), any(ULong.class)))
					.thenReturn(Mono.just(1));
			when(clientService.addClientRegistrationObjects(any(), any(), any(), any()))
					.thenReturn(Mono.just(Boolean.TRUE));
			when(appService.addClientAccessAfterRegistration(anyString(), any(ULong.class), any(Client.class)))
					.thenReturn(Mono.just(Boolean.TRUE));

			when(userService.createForRegistration(any(), any(), any(), any(), any(), any()))
					.thenReturn(Mono.just(createdUser));

			TokenObject tokenObj = TestDataFactory.createTokenObject(ULong.valueOf(1), NEW_USER_ID, "test-token",
					java.time.LocalDateTime.now().plusMinutes(30));
			when(userService.makeOneTimeToken(any(), any(), any(), any()))
					.thenReturn(Mono.just(tokenObj));
			when(appRegistrationDAO.getFileAccessForRegistration(any(), any(), any(), anyString(), any(), any()))
					.thenReturn(Mono.just(List.of()));
			when(clientUrlService.getAppUrl(anyString(), anyString()))
					.thenReturn(Mono.just(""));
			when(ecService.createEvent(any())).thenReturn(Mono.just(Boolean.TRUE));

			AuthenticationResponse authResp = new AuthenticationResponse();
			authResp.setAccessToken("access-token");
			when(authenticationService.authenticate(any(), any(ServerHttpRequest.class),
					any(ServerHttpResponse.class)))
					.thenReturn(Mono.just(authResp));
			when(appService.getAppIdsForAdditionalAppRegistration(anyString(), anyString(), any(Client.class)))
					.thenReturn(Mono.just(List.of()));

			StepVerifier.create(service.register(req, request, response))
					.assertNext(regResponse -> {
						assertNotNull(regResponse);
						assertTrue(regResponse.getCreated());
					})
					.verifyComplete();

			// Owner with managerId should set the provided manager
			verify(clientManagerService).createInternal(eq(NEW_CLIENT_ID), eq(managerId), eq(USER_ID));
		}

		@Test
		void register_AuthenticatedOwner_WithoutManagerId_NoManagerSet() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(USER_ID, SYSTEM_CLIENT_ID, CLIENT_CODE,
					List.of("Authorities.Logged_IN", "Authorities.Client_CREATE", "Authorities.ROLE_Owner"));
			ca.setAuthenticated(true);
			ca.setUrlAppCode(APP_CODE);
			ca.setUrlClientCode(CLIENT_CODE);
			setupSecurityContext(ca);

			ClientRegistrationRequest req = createBasicRegistrationRequest();
			// No managerId set
			ServerHttpRequest request = createMockRequest();
			ServerHttpResponse response = mock(ServerHttpResponse.class);

			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			Client createdClient = TestDataFactory.createIndividualClient(NEW_CLIENT_ID, "TESTCLIENT50");
			User createdUser = TestDataFactory.createActiveUser(NEW_USER_ID, NEW_CLIENT_ID);
			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, APP_CODE);
			app.setAppUsageType(SecurityAppAppUsageType.B2C);

			when(clientService.getClientAppPolicy(any(ULong.class), eq(APP_CODE), any()))
					.thenReturn(Mono.just(policy));
			when(clientService.validatePasswordPolicy(eq(policy), isNull(), any(), anyString()))
					.thenReturn(Mono.just(Boolean.TRUE));
			when(userService.checkIndividualClientUser(eq(CLIENT_CODE), any(ClientRegistrationRequest.class)))
					.thenReturn(Mono.just(Boolean.FALSE));
			when(appService.getAppByCode(APP_CODE)).thenReturn(Mono.just(app));
			when(clientService.getClientBy(CLIENT_CODE)).thenReturn(Mono.just(
					TestDataFactory.createBusinessClient(SYSTEM_CLIENT_ID, CLIENT_CODE)));
			when(clientService.getClientLevelType(any(ULong.class), eq(APP_ID)))
					.thenReturn(Mono.just(ClientLevelType.OWNER));
			when(appService.getProperties(any(ULong.class), eq(APP_ID), isNull(), eq(AppService.APP_PROP_URL_SUFFIX)))
					.thenReturn(Mono.just(Map.of()));

			AppProperty regProp = new AppProperty();
			regProp.setValue(AppService.APP_PROP_REG_TYPE_NO_VERIFICATION);
			when(appService.getProperties(any(ULong.class), isNull(), eq(APP_CODE), eq(AppService.APP_PROP_REG_TYPE)))
					.thenReturn(Mono.just(Map.of(SYSTEM_CLIENT_ID,
							Map.of(AppService.APP_PROP_REG_TYPE, regProp))));

			when(dao.getValidClientCode(anyString())).thenReturn(Mono.just("TESTCLIENT50"));
			when(clientService.createForRegistration(any(Client.class), any(ULong.class)))
					.thenReturn(Mono.just(createdClient));
			when(clientHierarchyService.create(any(ULong.class), any(ULong.class)))
					.thenReturn(Mono.just(new ClientHierarchy()));
			when(clientService.addClientRegistrationObjects(any(), any(), any(), any()))
					.thenReturn(Mono.just(Boolean.TRUE));
			when(appService.addClientAccessAfterRegistration(anyString(), any(ULong.class), any(Client.class)))
					.thenReturn(Mono.just(Boolean.TRUE));

			when(userService.createForRegistration(any(), any(), any(), any(), any(), any()))
					.thenReturn(Mono.just(createdUser));

			TokenObject tokenObj = TestDataFactory.createTokenObject(ULong.valueOf(1), NEW_USER_ID, "test-token",
					java.time.LocalDateTime.now().plusMinutes(30));
			when(userService.makeOneTimeToken(any(), any(), any(), any()))
					.thenReturn(Mono.just(tokenObj));
			when(appRegistrationDAO.getFileAccessForRegistration(any(), any(), any(), anyString(), any(), any()))
					.thenReturn(Mono.just(List.of()));
			when(clientUrlService.getAppUrl(anyString(), anyString()))
					.thenReturn(Mono.just(""));
			when(ecService.createEvent(any())).thenReturn(Mono.just(Boolean.TRUE));

			AuthenticationResponse authResp = new AuthenticationResponse();
			authResp.setAccessToken("access-token");
			when(authenticationService.authenticate(any(), any(ServerHttpRequest.class),
					any(ServerHttpResponse.class)))
					.thenReturn(Mono.just(authResp));
			when(appService.getAppIdsForAdditionalAppRegistration(anyString(), anyString(), any(Client.class)))
					.thenReturn(Mono.just(List.of()));

			StepVerifier.create(service.register(req, request, response))
					.assertNext(regResponse -> {
						assertNotNull(regResponse);
						assertTrue(regResponse.getCreated());
					})
					.verifyComplete();

			// Owner without managerId should NOT set any manager
			verify(clientManagerService, never()).createInternal(any(), any(), any());
		}

		@Test
		void register_Unauthenticated_NoManagerSet() {
			ContextAuthentication ca = createUnauthenticatedContext();
			setupSecurityContext(ca);

			ClientRegistrationRequest req = createBasicRegistrationRequest();
			ServerHttpRequest request = createMockRequest();
			ServerHttpResponse response = mock(ServerHttpResponse.class);

			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			Client createdClient = TestDataFactory.createIndividualClient(NEW_CLIENT_ID, "TESTCLIENT50");
			User createdUser = TestDataFactory.createActiveUser(NEW_USER_ID, NEW_CLIENT_ID);
			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, APP_CODE);
			app.setAppUsageType(SecurityAppAppUsageType.B2C);

			when(clientService.getClientAppPolicy(any(ULong.class), eq(APP_CODE), any()))
					.thenReturn(Mono.just(policy));
			when(clientService.validatePasswordPolicy(eq(policy), isNull(), any(), anyString()))
					.thenReturn(Mono.just(Boolean.TRUE));
			when(userService.checkIndividualClientUser(eq(CLIENT_CODE), any(ClientRegistrationRequest.class)))
					.thenReturn(Mono.just(Boolean.FALSE));
			when(appService.getAppByCode(APP_CODE)).thenReturn(Mono.just(app));
			when(clientService.getClientBy(CLIENT_CODE)).thenReturn(Mono.just(
					TestDataFactory.createBusinessClient(SYSTEM_CLIENT_ID, CLIENT_CODE)));
			when(clientService.getClientLevelType(any(ULong.class), eq(APP_ID)))
					.thenReturn(Mono.just(ClientLevelType.OWNER));
			when(appService.getProperties(any(ULong.class), eq(APP_ID), isNull(), eq(AppService.APP_PROP_URL_SUFFIX)))
					.thenReturn(Mono.just(Map.of()));

			AppProperty regProp = new AppProperty();
			regProp.setValue(AppService.APP_PROP_REG_TYPE_NO_VERIFICATION);
			when(appService.getProperties(any(ULong.class), isNull(), eq(APP_CODE), eq(AppService.APP_PROP_REG_TYPE)))
					.thenReturn(Mono.just(Map.of(SYSTEM_CLIENT_ID,
							Map.of(AppService.APP_PROP_REG_TYPE, regProp))));

			when(dao.getValidClientCode(anyString())).thenReturn(Mono.just("TESTCLIENT50"));
			when(clientService.createForRegistration(any(Client.class), any(ULong.class)))
					.thenReturn(Mono.just(createdClient));
			when(clientHierarchyService.create(any(ULong.class), any(ULong.class)))
					.thenReturn(Mono.just(new ClientHierarchy()));
			when(clientService.addClientRegistrationObjects(any(), any(), any(), any()))
					.thenReturn(Mono.just(Boolean.TRUE));
			when(appService.addClientAccessAfterRegistration(anyString(), any(ULong.class), any(Client.class)))
					.thenReturn(Mono.just(Boolean.TRUE));

			when(userService.createForRegistration(any(), any(), any(), any(), any(), any()))
					.thenReturn(Mono.just(createdUser));

			TokenObject tokenObj = TestDataFactory.createTokenObject(ULong.valueOf(1), NEW_USER_ID, "test-token",
					java.time.LocalDateTime.now().plusMinutes(30));
			when(userService.makeOneTimeToken(any(), any(), any(), any()))
					.thenReturn(Mono.just(tokenObj));
			when(appRegistrationDAO.getFileAccessForRegistration(any(), any(), any(), anyString(), any(), any()))
					.thenReturn(Mono.just(List.of()));
			when(clientUrlService.getAppUrl(anyString(), anyString()))
					.thenReturn(Mono.just(""));
			when(ecService.createEvent(any())).thenReturn(Mono.just(Boolean.TRUE));

			AuthenticationResponse authResp = new AuthenticationResponse();
			authResp.setAccessToken("access-token");
			when(authenticationService.authenticate(any(), any(ServerHttpRequest.class),
					any(ServerHttpResponse.class)))
					.thenReturn(Mono.just(authResp));
			when(appService.getAppIdsForAdditionalAppRegistration(anyString(), anyString(), any(Client.class)))
					.thenReturn(Mono.just(List.of()));

			StepVerifier.create(service.register(req, request, response))
					.assertNext(regResponse -> {
						assertNotNull(regResponse);
						assertTrue(regResponse.getCreated());
					})
					.verifyComplete();

			// Unauthenticated registration should NOT set any manager
			verify(clientManagerService, never()).createInternal(any(), any(), any());
		}

		@Test
		void register_ExistingUser_ThrowsError() {
			ContextAuthentication ca = createUnauthenticatedContext();
			setupSecurityContext(ca);

			ClientRegistrationRequest req = createBasicRegistrationRequest();
			ServerHttpRequest request = createMockRequest();
			ServerHttpResponse response = mock(ServerHttpResponse.class);

			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();

			when(clientService.getClientAppPolicy(any(ULong.class), eq(APP_CODE), any()))
					.thenReturn(Mono.just(policy));
			when(clientService.validatePasswordPolicy(eq(policy), isNull(), any(), anyString()))
					.thenReturn(Mono.just(Boolean.TRUE));

			// checkIndividualClientUser returns true => user already exists
			when(userService.checkIndividualClientUser(eq(CLIENT_CODE), any(ClientRegistrationRequest.class)))
					.thenReturn(Mono.just(Boolean.TRUE));

			StepVerifier.create(service.register(req, request, response))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.CONFLICT)
					.verify();
		}

		@Test
		void register_NoRegistrationAvailable_ThrowsForbidden() {
			ContextAuthentication ca = createUnauthenticatedContext();
			setupSecurityContext(ca);

			ClientRegistrationRequest req = createBasicRegistrationRequest();
			ServerHttpRequest request = createMockRequest();
			ServerHttpResponse response = mock(ServerHttpResponse.class);

			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, APP_CODE);
			app.setAppUsageType(SecurityAppAppUsageType.B2C);

			when(clientService.getClientAppPolicy(any(ULong.class), eq(APP_CODE), any()))
					.thenReturn(Mono.just(policy));
			when(clientService.validatePasswordPolicy(eq(policy), isNull(), any(), anyString()))
					.thenReturn(Mono.just(Boolean.TRUE));
			when(userService.checkIndividualClientUser(eq(CLIENT_CODE), any(ClientRegistrationRequest.class)))
					.thenReturn(Mono.just(Boolean.FALSE));
			when(appService.getAppByCode(APP_CODE)).thenReturn(Mono.just(app));
			when(clientService.getClientBy(CLIENT_CODE)).thenReturn(Mono.just(
					TestDataFactory.createBusinessClient(SYSTEM_CLIENT_ID, CLIENT_CODE)));
			when(clientService.getClientLevelType(any(ULong.class), eq(APP_ID)))
					.thenReturn(Mono.just(ClientLevelType.OWNER));
			when(appService.getProperties(any(ULong.class), eq(APP_ID), isNull(), eq(AppService.APP_PROP_URL_SUFFIX)))
					.thenReturn(Mono.just(Map.of()));

			// regProp returns NO_REGISTRATION
			AppProperty regProp = new AppProperty();
			regProp.setValue(AppService.APP_PROP_REG_TYPE_NO_REGISTRATION);
			when(appService.getProperties(any(ULong.class), isNull(), eq(APP_CODE), eq(AppService.APP_PROP_REG_TYPE)))
					.thenReturn(Mono.just(Map.of(SYSTEM_CLIENT_ID,
							Map.of(AppService.APP_PROP_REG_TYPE, regProp))));

			StepVerifier.create(service.register(req, request, response))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void register_NullPassType_ThrowsBadRequest() {
			ClientRegistrationRequest req = createBasicRegistrationRequest();
			req.setPassType(null);
			req.setPassword(null);
			req.setPin(null);
			req.setOtp(null);
			ServerHttpRequest request = createMockRequest();
			ServerHttpResponse response = mock(ServerHttpResponse.class);

			StepVerifier.create(service.register(req, request, response))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void register_BusinessType_CreatesBusinessClient() {
			ContextAuthentication ca = createUnauthenticatedContext();
			setupSecurityContext(ca);

			ClientRegistrationRequest req = createBasicRegistrationRequest();
			req.setBusinessClient(true);
			req.setBusinessType("RETAIL");
			req.setClientName("Business Corp");
			req.setSubDomain("businesscorp");
			req.setSubDomainSuffix(".modlix.com");

			ServerHttpRequest request = createMockRequest();
			ServerHttpResponse response = mock(ServerHttpResponse.class);
			HttpHeaders responseHeaders = new HttpHeaders();
			when(response.getHeaders()).thenReturn(responseHeaders);

			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			Client createdClient = TestDataFactory.createBusinessClient(NEW_CLIENT_ID, "BUSINESSCORP");
			User createdUser = TestDataFactory.createActiveUser(NEW_USER_ID, NEW_CLIENT_ID);
			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, APP_CODE);
			app.setAppUsageType(SecurityAppAppUsageType.B2X);

			when(clientService.getClientAppPolicy(any(ULong.class), eq(APP_CODE), any()))
					.thenReturn(Mono.just(policy));
			when(clientService.validatePasswordPolicy(eq(policy), isNull(), any(), anyString()))
					.thenReturn(Mono.just(Boolean.TRUE));
			when(appService.getAppByCode(APP_CODE)).thenReturn(Mono.just(app));
			when(clientService.getClientBy(CLIENT_CODE)).thenReturn(Mono.just(
					TestDataFactory.createBusinessClient(SYSTEM_CLIENT_ID, CLIENT_CODE)));
			when(clientService.getClientLevelType(any(ULong.class), eq(APP_ID)))
					.thenReturn(Mono.just(ClientLevelType.OWNER));
			when(appService.getProperties(any(ULong.class), eq(APP_ID), isNull(), eq(AppService.APP_PROP_URL_SUFFIX)))
					.thenReturn(Mono.just(Map.of()));
			when(clientUrlService.checkSubDomainAvailability(eq("businesscorp"), anyString()))
					.thenReturn(Mono.just(Boolean.TRUE));

			AppProperty regProp = new AppProperty();
			regProp.setValue(AppService.APP_PROP_REG_TYPE_NO_VERIFICATION);
			when(appService.getProperties(any(ULong.class), isNull(), eq(APP_CODE), eq(AppService.APP_PROP_REG_TYPE)))
					.thenReturn(Mono.just(Map.of(SYSTEM_CLIENT_ID,
							Map.of(AppService.APP_PROP_REG_TYPE, regProp))));

			when(dao.getValidClientCode(anyString())).thenReturn(Mono.just("BUSINESSCORP"));
			when(clientService.createForRegistration(any(Client.class), any(ULong.class)))
					.thenReturn(Mono.just(createdClient));
			when(clientHierarchyService.create(any(ULong.class), any(ULong.class)))
					.thenReturn(Mono.just(new ClientHierarchy()));
			when(clientService.addClientRegistrationObjects(any(), any(), any(), any()))
					.thenReturn(Mono.just(Boolean.TRUE));
			when(appService.addClientAccessAfterRegistration(anyString(), any(ULong.class), any(Client.class)))
					.thenReturn(Mono.just(Boolean.TRUE));
			when(userService.createForRegistration(any(), any(), any(), any(), any(), any()))
					.thenReturn(Mono.just(createdUser));

			TokenObject tokenObj = TestDataFactory.createTokenObject(ULong.valueOf(1), NEW_USER_ID, "test-token",
					java.time.LocalDateTime.now().plusMinutes(30));
			when(userService.makeOneTimeToken(any(), any(), any(), any()))
					.thenReturn(Mono.just(tokenObj));
			when(appRegistrationDAO.getFileAccessForRegistration(any(), any(), any(), anyString(), any(), any()))
					.thenReturn(Mono.just(List.of()));
			when(clientUrlService.getAppUrl(anyString(), anyString()))
					.thenReturn(Mono.just(""));
			when(ecService.createEvent(any())).thenReturn(Mono.just(Boolean.TRUE));

			AuthenticationResponse authResp = new AuthenticationResponse();
			authResp.setAccessToken("access-token");
			when(authenticationService.authenticate(any(), any(ServerHttpRequest.class),
					any(ServerHttpResponse.class)))
					.thenReturn(Mono.just(authResp));
			when(appService.getAppIdsForAdditionalAppRegistration(anyString(), anyString(), any(Client.class)))
					.thenReturn(Mono.just(List.of()));

			when(clientUrlService.createForRegistration(any()))
					.thenReturn(Mono.just(new com.fincity.security.dto.ClientUrl()));

			StepVerifier.create(service.register(req, request, response))
					.assertNext(regResponse -> {
						assertNotNull(regResponse);
						assertTrue(regResponse.getCreated());
					})
					.verifyComplete();

			verify(clientService).createForRegistration(argThat(client ->
					"BUS".equals(client.getTypeCode())), any(ULong.class));
		}
	}

	// =========================================================================
	// generateOtp() tests
	// =========================================================================

	@Nested
	@DisplayName("generateOtp()")
	class GenerateOtpTests {

		@Test
		void generateOtp_HappyPath_ReturnsTrue() {
			OtpGenerationRequest otpReq = new OtpGenerationRequest();
			otpReq.setEmailId("test@example.com");

			ServerHttpRequest request = createMockRequest();

			Client client = TestDataFactory.createBusinessClient(SYSTEM_CLIENT_ID, CLIENT_CODE);
			when(clientService.getClientBy(CLIENT_CODE)).thenReturn(Mono.just(client));

			AppProperty regProp = new AppProperty();
			regProp.setValue(AppService.APP_PROP_REG_TYPE_VERIFICATION);
			when(appService.getProperties(eq(SYSTEM_CLIENT_ID), isNull(), eq(APP_CODE),
					eq(AppService.APP_PROP_REG_TYPE)))
					.thenReturn(Mono.just(Map.of(SYSTEM_CLIENT_ID,
							Map.of(AppService.APP_PROP_REG_TYPE, regProp))));

			when(otpService.generateOtp(any(OtpGenerationRequest.class), any(ServerHttpRequest.class)))
					.thenReturn(Mono.just(Boolean.TRUE));

			StepVerifier.create(service.generateOtp(otpReq, request))
					.expectNext(Boolean.TRUE)
					.verifyComplete();

			verify(otpService).generateOtp(argThat(req ->
					OtpPurpose.REGISTRATION == req.getPurpose()), eq(request));
		}
	}

	// =========================================================================
	// preRegisterCheckOne() tests
	// =========================================================================

	@Nested
	@DisplayName("preRegisterCheckOne()")
	class PreRegisterCheckOneTests {

		@Test
		void preRegisterCheckOne_ValidRequest_ReturnsTrue() {
			ContextAuthentication ca = createUnauthenticatedContext();
			setupSecurityContext(ca);

			ClientRegistrationRequest req = createBasicRegistrationRequest();
			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, APP_CODE);
			app.setAppUsageType(SecurityAppAppUsageType.B2C);

			when(clientService.getClientAppPolicy(any(ULong.class), eq(APP_CODE), any()))
					.thenReturn(Mono.just(policy));
			when(clientService.validatePasswordPolicy(eq(policy), isNull(), any(), anyString()))
					.thenReturn(Mono.just(Boolean.TRUE));
			when(userService.checkIndividualClientUser(eq(CLIENT_CODE), any(ClientRegistrationRequest.class)))
					.thenReturn(Mono.just(Boolean.FALSE));
			when(appService.getAppByCode(APP_CODE)).thenReturn(Mono.just(app));
			when(clientService.getClientBy(CLIENT_CODE)).thenReturn(Mono.just(
					TestDataFactory.createBusinessClient(SYSTEM_CLIENT_ID, CLIENT_CODE)));
			when(clientService.getClientLevelType(any(ULong.class), eq(APP_ID)))
					.thenReturn(Mono.just(ClientLevelType.OWNER));
			when(appService.getProperties(any(ULong.class), eq(APP_ID), isNull(), eq(AppService.APP_PROP_URL_SUFFIX)))
					.thenReturn(Mono.just(Map.of()));

			AppProperty regProp = new AppProperty();
			regProp.setValue(AppService.APP_PROP_REG_TYPE_NO_VERIFICATION);
			when(appService.getProperties(any(ULong.class), isNull(), eq(APP_CODE), eq(AppService.APP_PROP_REG_TYPE)))
					.thenReturn(Mono.just(Map.of(SYSTEM_CLIENT_ID,
							Map.of(AppService.APP_PROP_REG_TYPE, regProp))));

			StepVerifier.create(service.preRegisterCheckOne(req))
					.expectNext(Boolean.TRUE)
					.verifyComplete();
		}
	}

	// =========================================================================
	// registerApp() tests
	// =========================================================================

	@Nested
	@DisplayName("registerApp()")
	class RegisterAppTests {

		@Test
		void registerApp_HappyPath_ReturnsTrue() {
			ContextAuthentication ca = createAuthenticatedContext();
			setupSecurityContext(ca);

			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, APP_CODE);
			Client client = TestDataFactory.createBusinessClient(NEW_CLIENT_ID, "NEWCLIENT");

			when(userService.checkIfUserIsOwner(USER_ID)).thenReturn(Mono.just(Boolean.TRUE));
			when(appService.getAppByCode(APP_CODE)).thenReturn(Mono.just(app));
			when(clientService.readInternal(NEW_CLIENT_ID)).thenReturn(Mono.just(client));
			when(clientService.addClientRegistrationObjects(eq(APP_ID), eq(SYSTEM_CLIENT_ID),
					any(ULong.class), eq(client)))
					.thenReturn(Mono.just(Boolean.TRUE));
			when(appService.addClientAccessAfterRegistration(eq(APP_CODE), any(ULong.class), eq(client)))
					.thenReturn(Mono.just(Boolean.TRUE));

			when(appRegistrationDAO.getFileAccessForRegistration(any(), any(), any(), anyString(), any(), any()))
					.thenReturn(Mono.just(List.of()));
			when(clientService.getClientLevelType(eq(NEW_CLIENT_ID), eq(APP_ID)))
					.thenReturn(Mono.just(ClientLevelType.CLIENT));

			when(userService.addDefaultProfiles(eq(APP_ID), eq(SYSTEM_CLIENT_ID), any(ULong.class),
					eq(client), eq(USER_ID)))
					.thenReturn(Mono.just(Boolean.TRUE));
			when(userService.addDefaultRoles(eq(APP_ID), eq(SYSTEM_CLIENT_ID), any(ULong.class),
					eq(client), eq(USER_ID)))
					.thenReturn(Mono.just(Boolean.TRUE));
			when(userService.addDesignation(eq(APP_ID), eq(SYSTEM_CLIENT_ID), any(ULong.class),
					eq(client), eq(USER_ID)))
					.thenReturn(Mono.just(Boolean.TRUE));

			StepVerifier.create(service.registerApp(APP_CODE, NEW_CLIENT_ID, USER_ID))
					.expectNext(Boolean.TRUE)
					.verifyComplete();

			verify(clientService).addClientRegistrationObjects(any(), any(), any(), any());
			verify(appService).addClientAccessAfterRegistration(anyString(), any(), any());
			verify(userService).addDefaultProfiles(any(), any(), any(), any(), any());
			verify(userService).addDefaultRoles(any(), any(), any(), any(), any());
			verify(userService).addDesignation(any(), any(), any(), any(), any());
		}
	}

	// =========================================================================
	// evokeRegistrationEvents() tests
	// =========================================================================

	@Nested
	@DisplayName("evokeRegistrationEvents()")
	class EvokeRegistrationEventsTests {

		@Test
		void evokeRegistrationEvents_PublishesEvents() {
			ContextAuthentication ca = createAuthenticatedContext();
			setupSecurityContext(ca);

			ClientRegistrationRequest req = createBasicRegistrationRequest();
			req.setUserId(USER_ID);

			ServerHttpRequest request = createMockRequest();
			ServerHttpResponse response = mock(ServerHttpResponse.class);

			User user = TestDataFactory.createActiveUser(USER_ID, NEW_CLIENT_ID);
			user.setPassword("StrongP@ss123");
			Client client = TestDataFactory.createBusinessClient(NEW_CLIENT_ID, "NEWCLIENT");

			when(userService.getUserForContext(APP_CODE, USER_ID)).thenReturn(Mono.just(user));
			when(clientService.getClientInfoById(NEW_CLIENT_ID)).thenReturn(Mono.just(client));

			AuthenticationResponse authResp = new AuthenticationResponse();
			authResp.setAccessToken("access-token");
			when(authenticationService.authenticate(any(), any(ServerHttpRequest.class),
					any(ServerHttpResponse.class)))
					.thenReturn(Mono.just(authResp));

			when(clientUrlService.getAppUrl(anyString(), anyString())).thenReturn(Mono.just(""));

			when(ecService.createEvent(any())).thenReturn(Mono.just(Boolean.TRUE));

			StepVerifier.create(service.evokeRegistrationEvents(req, request, response))
					.expectNext(Boolean.TRUE)
					.verifyComplete();

			verify(ecService, times(2)).createEvent(any());
		}
	}

	// =========================================================================
	// registerWSocial() tests
	// =========================================================================

	@Nested
	@DisplayName("registerWSocial()")
	class RegisterWSocialTests {

		@Test
		void registerWSocial_BlankState_ThrowsError() {
			ClientRegistrationRequest req = createBasicRegistrationRequest();
			req.setSocialRegisterState("");

			ServerHttpRequest request = createMockRequest();
			ServerHttpResponse response = mock(ServerHttpResponse.class);

			StepVerifier.create(service.registerWSocial(request, response, req))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}
	}

	/**
	 * Where the browser is sent after the provider verified the identity. The callback hangs
	 * the user's email, their name and a spendable state off this URL, so it has to be the
	 * calling app's own address and nowhere else.
	 */
	@Nested
	@DisplayName("socialCallbackDestination()")
	class SocialCallbackDestinationTests {

		private static final String BROKER_PREFIX = "https://dev.authzump.ai";
		private static final String APP_URL = "https://dev.sitezump.ai";

		private Mono<String> destination(Map<String, Object> requestParam) throws Exception {

			Method method = ClientRegistrationService.class.getDeclaredMethod(
					"socialCallbackDestination",
					com.fincity.security.dto.AppRegistrationIntegrationToken.class,
					com.fincity.security.dto.AppRegistrationIntegration.class,
					String.class);
			method.setAccessible(true);

			com.fincity.security.dto.AppRegistrationIntegrationToken token =
					new com.fincity.security.dto.AppRegistrationIntegrationToken();
			token.setRequestParam(requestParam);

			com.fincity.security.dto.AppRegistrationIntegration integration =
					new com.fincity.security.dto.AppRegistrationIntegration();
			integration.setLoginUri("/appLogin");
			integration.setSignupUri("/appSignUp");

			@SuppressWarnings("unchecked")
			Mono<String> result = (Mono<String>) method.invoke(service, token, integration, BROKER_PREFIX);
			return result;
		}

		private Map<String, Object> params(String redirectUrl) {
			return Map.of("appCode", "sitezump", "clientCode", "SYSTEM", "platform", "GOOGLE",
					"redirectUrl", redirectUrl);
		}

		/**
		 * The bug this whole change exists for. A page writes "/accountHome", and this used to
		 * be refused for having no scheme, sending the user to the OAuth broker's own login
		 * page on the broker's own domain, where the state means nothing.
		 */
		@Test
		@DisplayName("relative path resolves against the calling app's own URL")
		void relativePath_ResolvesAgainstTheApp() throws Exception {
			when(clientUrlService.getAppUrl("sitezump", "SYSTEM")).thenReturn(Mono.just(APP_URL));

			StepVerifier.create(destination(params("/accountHome")))
					.expectNext(APP_URL + "/accountHome")
					.verifyComplete();
		}

		@Test
		@DisplayName("relative path with no app URL on record falls back to the broker")
		void relativePath_NoAppUrl_FallsBack() throws Exception {
			when(clientUrlService.getAppUrl("sitezump", "SYSTEM")).thenReturn(Mono.just(""));

			StepVerifier.create(destination(params("/accountHome")))
					.expectNext(BROKER_PREFIX + "/appLogin")
					.verifyComplete();
		}

		@Test
		@DisplayName("absolute URL on one of the app's own hosts is honoured")
		void absoluteUrl_AppsOwnHost_Honoured() throws Exception {
			when(clientService.getClientPattern("https", "dev.sitezump.ai", ""))
					.thenReturn(Mono.just(new ClientUrlPattern("1", "SYSTEM", "dev.sitezump.ai", "sitezump")));

			StepVerifier.create(destination(params(APP_URL + "/accountHome")))
					.expectNext(APP_URL + "/accountHome")
					.verifyComplete();
		}

		@Test
		@DisplayName("absolute URL on a host belonging to another app is refused")
		void absoluteUrl_OtherAppsHost_Refused() throws Exception {
			when(clientService.getClientPattern("https", "dev.leadzump.ai", ""))
					.thenReturn(Mono.just(new ClientUrlPattern("2", "SYSTEM", "dev.leadzump.ai", "leadzump")));

			StepVerifier.create(destination(params("https://dev.leadzump.ai/steal")))
					.expectNext(BROKER_PREFIX + "/appLogin")
					.verifyComplete();
		}

		/** A host the platform has never heard of resolves to nothing, and nothing is refused. */
		@Test
		@DisplayName("absolute URL on an unknown host is refused")
		void absoluteUrl_UnknownHost_Refused() throws Exception {
			when(clientService.getClientPattern("https", "evil.example", ""))
					.thenReturn(Mono.empty());

			StepVerifier.create(destination(params("https://evil.example/harvest")))
					.expectNext(BROKER_PREFIX + "/appLogin")
					.verifyComplete();
		}

		/** "https://evil.example@dev.sitezump.ai" has host dev.sitezump.ai and lands elsewhere. */
		@Test
		@DisplayName("userinfo in the authority is refused without asking anyone")
		void absoluteUrl_WithUserInfo_Refused() throws Exception {
			StepVerifier.create(destination(params("https://evil.example@dev.sitezump.ai/x")))
					.expectNext(BROKER_PREFIX + "/appLogin")
					.verifyComplete();

			verify(clientService, never()).getClientPattern(anyString(), anyString(), anyString());
		}

		@Test
		@DisplayName("the mobile wrapper's own scheme still passes")
		void customScheme_MatchingTheApp_Honoured() throws Exception {
			String deepLink = "modlix.SYSTEM.sitezump://callback";

			StepVerifier.create(destination(params(deepLink)))
					.expectNext(deepLink)
					.verifyComplete();
		}

		@Test
		@DisplayName("another app's scheme does not")
		void customScheme_OtherApp_Refused() throws Exception {
			StepVerifier.create(destination(params("modlix.SYSTEM.leadzump://callback")))
					.expectNext(BROKER_PREFIX + "/appLogin")
					.verifyComplete();
		}

		@Test
		@DisplayName("no redirectUrl at all keeps the old behaviour")
		void noRedirectUrl_FallsBack() throws Exception {
			StepVerifier.create(destination(Map.of("appCode", "sitezump", "clientCode", "SYSTEM")))
					.expectNext(BROKER_PREFIX + "/appLogin")
					.verifyComplete();
		}

		@Test
		@DisplayName("a signup flow with no redirectUrl falls back to the signup page")
		void noRedirectUrl_Signup_FallsBackToSignupUri() throws Exception {
			StepVerifier.create(destination(
					Map.of("appCode", "sitezump", "clientCode", "SYSTEM", "signup", "true")))
					.expectNext(BROKER_PREFIX + "/appSignUp")
					.verifyComplete();
		}
	}
}
