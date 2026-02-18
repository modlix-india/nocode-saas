package com.fincity.security.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;


import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dao.AppRegistrationIntegrationTokenDao;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.OneTimeToken;
import com.fincity.security.dto.TokenObject;
import com.fincity.security.dto.User;
import com.fincity.security.dto.policy.ClientPasswordPolicy;

import com.fincity.security.model.AuthenticationPasswordType;
import com.fincity.security.model.AuthenticationRequest;
import com.fincity.security.model.MakeOneTimeTimeTokenRequest;
import com.fincity.security.service.appregistration.AppRegistrationIntegrationTokenService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.function.Tuples;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest extends AbstractServiceUnitTest {

	@Mock
	private UserService userService;
	@Mock
	private ClientService clientService;
	@Mock
	private AppService appService;
	@Mock
	private TokenService tokenService;
	@Mock
	private OtpService otpService;
	@Mock
	private SecurityMessageResourceService resourceService;
	@Mock
	private SoxLogService soxLogService;
	@Mock
	private PasswordEncoder pwdEncoder;
	@Mock
	private CacheService cacheService;
	@Mock
	private ProfileService profileService;
	@Mock
	private AppRegistrationIntegrationTokenDao integrationTokenDao;
	@Mock
	private AppRegistrationIntegrationTokenService appRegistrationIntegrationTokenService;
	@Mock
	private OneTimeTokenService oneTimeTokenService;

	private AuthenticationService service;

	private static final ULong USER_ID = ULong.valueOf(10);
	private static final ULong CLIENT_ID = ULong.valueOf(2);
	private static final String APP_CODE = "testApp";
	private static final String CLIENT_CODE = "TESTCLIENT";

	@BeforeEach
	void setUp() {
		service = new AuthenticationService(
				userService, clientService, appService, tokenService, otpService,
				resourceService, soxLogService, pwdEncoder, cacheService,
				integrationTokenDao, appRegistrationIntegrationTokenService,
				profileService, oneTimeTokenService);

		setupMessageResourceService(resourceService);
		setupCacheService(cacheService);
		setupSoxLogService(soxLogService);

		// Inject @Value fields via reflection
		setField(service, "tokenKey", "testSecretKeyForJWTTokenGenerationThatIsLongEnoughForHS256Algorithm1234567890");
		setField(service, "rememberMeExpiryInMinutes", 1440);
		setField(service, "defaultExpiryInMinutes", 60);
		setField(service, "defaultRefreshInMinutes", 10);
		setField(service, "appCodeSuffix", ".test");
	}

	private void setField(Object target, String fieldName, Object value) {
		try {
			var field = target.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			field.set(target, value);
		} catch (Exception e) {
			throw new RuntimeException("Failed to set field: " + fieldName, e);
		}
	}

	private ServerHttpRequest mockRequest() {
		return mockRequest(null, null, null);
	}

	private ServerHttpRequest mockRequest(String authHeader, String appCode, String clientCode) {
		ServerHttpRequest request = mock(ServerHttpRequest.class);
		HttpHeaders headers = new HttpHeaders();
		if (authHeader != null)
			headers.add(HttpHeaders.AUTHORIZATION, authHeader);
		if (appCode != null)
			headers.add("appCode", appCode);
		if (clientCode != null)
			headers.add("clientCode", clientCode);

		when(request.getHeaders()).thenReturn(headers);
		MultiValueMap<String, org.springframework.http.HttpCookie> cookies = new LinkedMultiValueMap<>();
		lenient().when(request.getCookies()).thenReturn(cookies);
		lenient().when(request.getURI()).thenReturn(URI.create("https://test.local:443"));
		lenient().when(request.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));
		return request;
	}

	private ServerHttpResponse mockResponse() {
		return mock(ServerHttpResponse.class);
	}

	// ===== revoke() tests =====

	@Nested
	class RevokeTests {

		@Test
		void revoke_NullToken_Returns1() {
			ServerHttpRequest request = mockRequest(null, null, null);

			StepVerifier.create(service.revoke(false, request))
					.assertNext(result -> assertEquals(1, result))
					.verifyComplete();
		}

		@Test
		void revoke_BearerToken_EvictsCacheAndDeletesFromDB() {
			String token = "some.jwt.token.that.is.long.enough.for.part.token.extraction.test";
			ServerHttpRequest request = mockRequest("Bearer " + token, null, null);

			when(tokenService.readAllFilter(any()))
					.thenReturn(reactor.core.publisher.Flux.empty());

			StepVerifier.create(service.revoke(false, request))
					.assertNext(result -> assertEquals(1, result))
					.verifyComplete();

			verify(cacheService).evict(anyString(), eq(token));
		}

		@Test
		void revoke_BasicAuthPrefix_StripsPrefix() {
			String token = "dXNlcjpwYXNz"; // base64 of user:pass
			ServerHttpRequest request = mockRequest("Basic " + token, null, null);

			when(tokenService.readAllFilter(any()))
					.thenReturn(reactor.core.publisher.Flux.empty());

			StepVerifier.create(service.revoke(false, request))
					.assertNext(result -> assertEquals(1, result))
					.verifyComplete();

			verify(cacheService).evict(anyString(), eq(token));
		}

		@Test
		void revoke_SsoLogout_True_CallsSsoRevoke() {
			String token = "some.jwt.token.value";
			ServerHttpRequest request = mockRequest("Bearer " + token, null, null);

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			when(tokenService.deleteTokens(any(BigInteger.class)))
					.thenReturn(Mono.just(1));

			StepVerifier.create(service.revoke(true, request))
					.assertNext(result -> assertEquals(1, result))
					.verifyComplete();
		}

		@Test
		void revoke_CookieToken_ExtractsFromCookie() {
			ServerHttpRequest request = mock(ServerHttpRequest.class);
			HttpHeaders headers = new HttpHeaders();
			when(request.getHeaders()).thenReturn(headers);

			MultiValueMap<String, org.springframework.http.HttpCookie> cookies = new LinkedMultiValueMap<>();
			cookies.add(HttpHeaders.AUTHORIZATION, new org.springframework.http.HttpCookie(
					HttpHeaders.AUTHORIZATION, "cookie.jwt.token.value.that.is.long.enough.for.test"));
			when(request.getCookies()).thenReturn(cookies);

			when(tokenService.readAllFilter(any()))
					.thenReturn(reactor.core.publisher.Flux.empty());

			StepVerifier.create(service.revoke(false, request))
					.assertNext(result -> assertEquals(1, result))
					.verifyComplete();
		}
	}

	// ===== generateOtp() tests =====

	@Nested
	class GenerateOtpTests {

		@Test
		void generateOtp_NotGenerateFlag_ReturnsFalse() {
			AuthenticationRequest authRequest = new AuthenticationRequest();
			authRequest.setGenerateOtp(false);

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);

			StepVerifier.create(service.generateOtp(authRequest, request))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		void generateOtp_UserNotFound_ReturnsFalse() {
			AuthenticationRequest authRequest = new AuthenticationRequest();
			authRequest.setGenerateOtp(true);
			authRequest.setUserName("unknown@test.com");

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);

			when(userService.findNonDeletedUserNActiveClient(anyString(), any(), anyString(), anyString(), any()))
					.thenReturn(Mono.empty());

			StepVerifier.create(service.generateOtp(authRequest, request))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	// ===== authenticate() tests =====

	@Nested
	class AuthenticateTests {

		@Test
		void authenticate_NullPasswordType_ThrowsBadRequest() {
			AuthenticationRequest authRequest = new AuthenticationRequest();
			authRequest.setUserName("testuser");
			// No password, pin, or otp set → passwordType will be null

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			StepVerifier.create(service.authenticate(authRequest, request, response))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void authenticate_HappyPath_Password_ReturnsTokenResponse() {
			AuthenticationRequest authRequest = TestDataFactory.createAuthenticationRequest("testuser", "password123");

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			Client linClient = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			User user = TestDataFactory.createActiveUser(USER_ID, CLIENT_ID);
			user.setPassword("hashedPassword");
			user.setPasswordHashed(true);

			when(userService.findNonDeletedUserNActiveClient(anyString(), any(), anyString(), anyString(), any()))
					.thenReturn(Mono.just(Tuples.of(linClient, client, user)));

			when(profileService.checkIfUserHasAnyProfile(any(ULong.class), anyString()))
					.thenReturn(Mono.just(true));

			when(userService.checkUserAndClient(any(), anyString()))
					.thenReturn(Mono.just(true));

			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			when(clientService.getClientAppPolicy(any(ULong.class), anyString(), any(AuthenticationPasswordType.class)))
					.thenReturn(Mono.just(policy));

			when(pwdEncoder.matches(anyString(), anyString())).thenReturn(true);

			when(userService.resetFailedAttempt(any(ULong.class), any(AuthenticationPasswordType.class)))
					.thenReturn(Mono.just(true));

			when(tokenService.create(any(TokenObject.class)))
					.thenAnswer(inv -> {
						TokenObject t = inv.getArgument(0);
						t.setId(ULong.valueOf(100));
						return Mono.just(t);
					});

			Client managedClient = new Client();
			managedClient.setCode("MANAGED");
			when(clientService.getManagedClientOfClientById(any(ULong.class)))
					.thenReturn(Mono.just(managedClient));

			StepVerifier.create(service.authenticate(authRequest, request, response))
					.assertNext(authResponse -> {
						assertNotNull(authResponse);
						assertNotNull(authResponse.getAccessToken());
						assertEquals(CLIENT_CODE, authResponse.getLoggedInClientCode());
					})
					.verifyComplete();
		}

		@Test
		void authenticate_InvalidPassword_IncreasesFailedAttempt() {
			AuthenticationRequest authRequest = TestDataFactory.createAuthenticationRequest("testuser", "wrongpassword");

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			Client linClient = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			User user = TestDataFactory.createActiveUser(USER_ID, CLIENT_ID);
			user.setPassword("hashedPassword");
			user.setPasswordHashed(true);
			user.setNoFailedAttempt((short) 0);

			when(userService.findNonDeletedUserNActiveClient(anyString(), any(), anyString(), anyString(), any()))
					.thenReturn(Mono.just(Tuples.of(linClient, client, user)));

			when(profileService.checkIfUserHasAnyProfile(any(ULong.class), anyString()))
					.thenReturn(Mono.just(true));

			when(userService.checkUserAndClient(any(), anyString()))
					.thenReturn(Mono.just(true));

			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			when(clientService.getClientAppPolicy(any(ULong.class), anyString(), any(AuthenticationPasswordType.class)))
					.thenReturn(Mono.just(policy));

			when(pwdEncoder.matches(anyString(), anyString())).thenReturn(false);

			when(userService.increaseFailedAttempt(any(ULong.class), any(AuthenticationPasswordType.class)))
					.thenReturn(Mono.just((short) 1));

			StepVerifier.create(service.authenticate(authRequest, request, response))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void authenticate_ExceedMaxFailedAttempts_LocksUser() {
			AuthenticationRequest authRequest = TestDataFactory.createAuthenticationRequest("testuser", "wrongpassword");

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			Client linClient = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			User user = TestDataFactory.createActiveUser(USER_ID, CLIENT_ID);
			user.setPassword("hashedPassword");
			user.setPasswordHashed(true);
			user.setNoFailedAttempt((short) 3); // already at max

			when(userService.findNonDeletedUserNActiveClient(anyString(), any(), anyString(), anyString(), any()))
					.thenReturn(Mono.just(Tuples.of(linClient, client, user)));

			when(profileService.checkIfUserHasAnyProfile(any(ULong.class), anyString()))
					.thenReturn(Mono.just(true));

			when(userService.checkUserAndClient(any(), anyString()))
					.thenReturn(Mono.just(true));

			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			when(clientService.getClientAppPolicy(any(ULong.class), anyString(), any(AuthenticationPasswordType.class)))
					.thenReturn(Mono.just(policy));

			when(pwdEncoder.matches(anyString(), anyString())).thenReturn(false);

			when(userService.lockUserInternal(any(ULong.class), any(LocalDateTime.class), anyString()))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.authenticate(authRequest, request, response))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void authenticate_LockedUser_ThrowsForbidden() {
			AuthenticationRequest authRequest = TestDataFactory.createAuthenticationRequest("testuser", "password123");

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			Client linClient = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			User user = TestDataFactory.createLockedUser(USER_ID, CLIENT_ID,
					LocalDateTime.now().plusMinutes(10));

			when(userService.findNonDeletedUserNActiveClient(anyString(), any(), anyString(), anyString(), any()))
					.thenReturn(Mono.just(Tuples.of(linClient, client, user)));

			when(profileService.checkIfUserHasAnyProfile(any(ULong.class), anyString()))
					.thenReturn(Mono.just(true));

			when(userService.checkUserAndClient(any(), anyString()))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.authenticate(authRequest, request, response))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void authenticate_LockedUserLockExpired_UnlocksAndAuthenticates() {
			AuthenticationRequest authRequest = TestDataFactory.createAuthenticationRequest("testuser", "password123");

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			Client linClient = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			User user = TestDataFactory.createLockedUser(USER_ID, CLIENT_ID,
					LocalDateTime.now().minusMinutes(5)); // lock expired
			user.setPassword("hashedPassword");
			user.setPasswordHashed(true);

			when(userService.findNonDeletedUserNActiveClient(anyString(), any(), anyString(), anyString(), any()))
					.thenReturn(Mono.just(Tuples.of(linClient, client, user)));

			when(profileService.checkIfUserHasAnyProfile(any(ULong.class), anyString()))
					.thenReturn(Mono.just(true));

			when(userService.checkUserAndClient(any(), anyString()))
					.thenReturn(Mono.just(true));

			when(userService.unlockUserInternal(any(ULong.class)))
					.thenReturn(Mono.just(true));

			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			when(clientService.getClientAppPolicy(any(ULong.class), anyString(), any(AuthenticationPasswordType.class)))
					.thenReturn(Mono.just(policy));

			when(pwdEncoder.matches(anyString(), anyString())).thenReturn(true);

			when(userService.resetFailedAttempt(any(ULong.class), any(AuthenticationPasswordType.class)))
					.thenReturn(Mono.just(true));

			when(tokenService.create(any(TokenObject.class)))
					.thenAnswer(inv -> {
						TokenObject t = inv.getArgument(0);
						t.setId(ULong.valueOf(100));
						return Mono.just(t);
					});

			Client managedClient = new Client();
			managedClient.setCode("MANAGED");
			when(clientService.getManagedClientOfClientById(any(ULong.class)))
					.thenReturn(Mono.just(managedClient));

			StepVerifier.create(service.authenticate(authRequest, request, response))
					.assertNext(authResponse -> assertNotNull(authResponse.getAccessToken()))
					.verifyComplete();

			verify(userService).unlockUserInternal(USER_ID);
		}

		@Test
		void authenticate_InactiveUser_ThrowsForbidden() {
			AuthenticationRequest authRequest = TestDataFactory.createAuthenticationRequest("testuser", "password123");

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			Client linClient = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			User user = TestDataFactory.createInactiveUser(USER_ID, CLIENT_ID);

			when(userService.findNonDeletedUserNActiveClient(anyString(), any(), anyString(), anyString(), any()))
					.thenReturn(Mono.just(Tuples.of(linClient, client, user)));

			when(profileService.checkIfUserHasAnyProfile(any(ULong.class), anyString()))
					.thenReturn(Mono.just(true));

			when(userService.checkUserAndClient(any(), anyString()))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.authenticate(authRequest, request, response))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void authenticate_PasswordExpiredUser_ThrowsForbidden() {
			AuthenticationRequest authRequest = TestDataFactory.createAuthenticationRequest("testuser", "password123");

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			Client linClient = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			User user = TestDataFactory.createPasswordExpiredUser(USER_ID, CLIENT_ID);

			when(userService.findNonDeletedUserNActiveClient(anyString(), any(), anyString(), anyString(), any()))
					.thenReturn(Mono.just(Tuples.of(linClient, client, user)));

			when(profileService.checkIfUserHasAnyProfile(any(ULong.class), anyString()))
					.thenReturn(Mono.just(true));

			when(userService.checkUserAndClient(any(), anyString()))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.authenticate(authRequest, request, response))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void authenticate_UserNotFound_FallsBackToAuthenticateForHavingApp() {
			AuthenticationRequest authRequest = TestDataFactory.createAuthenticationRequest("testuser", "password123");

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			// First call returns empty (user not found in this app)
			when(userService.findNonDeletedUserNActiveClient(anyString(), any(), anyString(), anyString(), any()))
					.thenReturn(Mono.empty());

			// authenticateUserForHavingApp path
			when(userService.findNonDeletedUserNClient(anyString(), any(), anyString(), any(), any()))
					.thenReturn(Mono.empty());

			StepVerifier.create(service.authenticate(authRequest, request, response))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void authenticate_ResetsFailedAttemptOnSuccess() {
			AuthenticationRequest authRequest = TestDataFactory.createAuthenticationRequest("testuser", "password123");

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			Client linClient = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			User user = TestDataFactory.createActiveUser(USER_ID, CLIENT_ID);
			user.setPassword("hashedPassword");
			user.setPasswordHashed(true);
			user.setNoFailedAttempt((short) 2); // had some failures

			when(userService.findNonDeletedUserNActiveClient(anyString(), any(), anyString(), anyString(), any()))
					.thenReturn(Mono.just(Tuples.of(linClient, client, user)));

			when(profileService.checkIfUserHasAnyProfile(any(ULong.class), anyString()))
					.thenReturn(Mono.just(true));

			when(userService.checkUserAndClient(any(), anyString()))
					.thenReturn(Mono.just(true));

			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			when(clientService.getClientAppPolicy(any(ULong.class), anyString(), any(AuthenticationPasswordType.class)))
					.thenReturn(Mono.just(policy));

			when(pwdEncoder.matches(anyString(), anyString())).thenReturn(true);

			when(userService.resetFailedAttempt(any(ULong.class), any(AuthenticationPasswordType.class)))
					.thenReturn(Mono.just(true));

			when(tokenService.create(any(TokenObject.class)))
					.thenAnswer(inv -> {
						TokenObject t = inv.getArgument(0);
						t.setId(ULong.valueOf(100));
						return Mono.just(t);
					});

			Client managedClient = new Client();
			managedClient.setCode("MANAGED");
			when(clientService.getManagedClientOfClientById(any(ULong.class)))
					.thenReturn(Mono.just(managedClient));

			StepVerifier.create(service.authenticate(authRequest, request, response))
					.assertNext(authResponse -> assertNotNull(authResponse))
					.verifyComplete();

			verify(userService).resetFailedAttempt(USER_ID, AuthenticationPasswordType.PASSWORD);
		}
	}

	// ===== authenticateWSocial() tests =====

	@Nested
	class AuthenticateWSocialTests {

		@Test
		void authenticateWSocial_BlankState_ThrowsBadRequest() {
			AuthenticationRequest authRequest = new AuthenticationRequest();
			authRequest.setSocialRegisterState("");

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			StepVerifier.create(service.authenticateWSocial(authRequest, request, response))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void authenticateWSocial_UserNotFound_FallsBack() {
			AuthenticationRequest authRequest = new AuthenticationRequest();
			authRequest.setUserName("social@test.com");
			authRequest.setSocialRegisterState("validState123");

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			when(userService.findNonDeletedUserNClient(anyString(), any(), anyString(), any(), any()))
					.thenReturn(Mono.empty());

			StepVerifier.create(service.authenticateWSocial(authRequest, request, response))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}

	// ===== authenticateUserForHavingApp() tests =====

	@Nested
	class AuthenticateUserForHavingAppTests {

		@Test
		void authenticateUserForHavingApp_NoApp_ThrowsForbidden() {
			AuthenticationRequest authRequest = TestDataFactory.createAuthenticationRequest("testuser", "password123");

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			Client linClient = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			User user = TestDataFactory.createActiveUser(USER_ID, CLIENT_ID);

			when(userService.findNonDeletedUserNClient(anyString(), any(), anyString(), any(), any()))
					.thenReturn(Mono.just(Tuples.of(linClient, client, user)));

			when(profileService.getUserAppHavingProfile(any(ULong.class)))
					.thenReturn(Mono.empty());

			StepVerifier.create(service.authenticateUserForHavingApp(authRequest, CLIENT_CODE, request, response))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void authenticateUserForHavingApp_UserNotFound_ThrowsForbidden() {
			AuthenticationRequest authRequest = TestDataFactory.createAuthenticationRequest("testuser", "password123");

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			when(userService.findNonDeletedUserNClient(anyString(), any(), anyString(), any(), any()))
					.thenReturn(Mono.empty());

			StepVerifier.create(service.authenticateUserForHavingApp(authRequest, CLIENT_CODE, request, response))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}

	// ===== getAuthentication() tests =====

	@Nested
	class GetAuthenticationTests {

		@Test
		void getAuthentication_BlankToken_ReturnsAnonymous() {
			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);

			when(clientService.getClientBy(anyString()))
					.thenReturn(Mono.just(TestDataFactory.createSystemClient()));

			StepVerifier.create(service.getAuthentication(false, "", CLIENT_CODE, APP_CODE, request))
					.assertNext(auth -> {
						assertTrue(auth instanceof ContextAuthentication);
						assertFalse(auth.isAuthenticated());
					})
					.verifyComplete();
		}

		@Test
		void getAuthentication_ExpiredToken_ReturnsAnonymous() {
			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);

			when(clientService.getClientBy(anyString()))
					.thenReturn(Mono.just(TestDataFactory.createSystemClient()));

			StepVerifier.create(
					service.getAuthentication(false, "invalid.expired.token", CLIENT_CODE, APP_CODE, request))
					.assertNext(auth -> {
						assertTrue(auth instanceof ContextAuthentication);
						assertFalse(auth.isAuthenticated());
					})
					.verifyComplete();
		}
	}

	// ===== makeOneTimeToken() tests =====

	@Nested
	class MakeOneTimeTokenTests {

		@Test
		void makeOneTimeToken_HappyPath_ReturnsTokenAndUrl() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			var tokenRequest = new MakeOneTimeTimeTokenRequest();
			tokenRequest.setCallbackUrl("https://app.test/{token}");

			ServerHttpRequest request = mockRequest();

			OneTimeToken ott = new OneTimeToken();
			ott.setToken("generatedToken123");

			when(oneTimeTokenService.create(any(OneTimeToken.class)))
					.thenReturn(Mono.just(ott));

			StepVerifier.create(service.makeOneTimeToken(tokenRequest, request))
					.assertNext(result -> {
						assertEquals("generatedToken123", result.get("token"));
						assertTrue(result.get("url").contains("generatedToken123"));
					})
					.verifyComplete();
		}
	}

	// ===== refreshToken() tests =====

	@Nested
	class RefreshTokenTests {

		@Test
		void refreshToken_NotAuthenticated_ThrowsUnauthorized() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(CLIENT_ID, CLIENT_CODE,
					List.of("Authorities.Logged_IN"));
			ca.setAuthenticated(false);
			setupSecurityContext(ca);

			ServerHttpRequest request = mockRequest("Bearer token", APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			StepVerifier.create(service.refreshToken(request, response))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.UNAUTHORIZED)
					.verify();
		}
	}
}
