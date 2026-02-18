package com.fincity.security.service.appregistration;

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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
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
import com.fincity.security.service.ClientService;
import com.fincity.security.service.ClientUrlService;
import com.fincity.security.service.OtpService;
import com.fincity.security.service.SecurityMessageResourceService;
import com.fincity.security.service.UserService;
import com.fincity.security.service.plansnbilling.PlanService;
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

	@Mock
	private PlanService planService;

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
				clientService, clientHierarchyService, ecService, clientUrlService,
				appRegistrationDAO, filesService, appRegistrationIntegrationService,
				appRegistrationIntegrationTokenService, securityMessageResourceService,
				planService);

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
}
