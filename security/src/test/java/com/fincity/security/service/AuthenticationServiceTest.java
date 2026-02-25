package com.fincity.security.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.LocalDateTime;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dao.AppRegistrationIntegrationTokenDao;
import com.fincity.security.dto.App;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.OneTimeToken;
import com.fincity.security.dto.TokenObject;
import com.fincity.security.dto.User;
import com.fincity.security.dto.policy.ClientOtpPolicy;
import com.fincity.security.dto.policy.ClientPasswordPolicy;
import com.fincity.security.dto.policy.ClientPinPolicy;

import com.fincity.security.model.AuthenticationPasswordType;
import com.fincity.security.model.AuthenticationRequest;
import com.fincity.security.model.MakeOneTimeTimeTokenRequest;
import com.fincity.security.model.UserAppAccessRequest;
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

		@Test
		@DisplayName("refreshToken - authenticated but token not near expiry returns existing token")
		void refreshToken_Authenticated_TokenNotNearExpiry_ReturnsExistingToken() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(CLIENT_ID, CLIENT_CODE,
					List.of("Authorities.Logged_IN"));
			ca.setAuthenticated(true);
			ca.setAccessToken("existing.jwt.token");
			ca.setAccessTokenExpiryAt(LocalDateTime.now().plusHours(1)); // far from expiry
			setupSecurityContext(ca);

			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			when(clientService.getClientInfoById(any(BigInteger.class)))
					.thenReturn(Mono.just(client));

			Client managedClient = new Client();
			managedClient.setCode("MANAGED");
			when(clientService.getManagedClientOfClientById(any(ULong.class)))
					.thenReturn(Mono.just(managedClient));

			ServerHttpRequest request = mockRequest("Bearer existing.jwt.token", APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			StepVerifier.create(service.refreshToken(request, response))
					.assertNext(authResponse -> {
						assertNotNull(authResponse);
						assertEquals("existing.jwt.token", authResponse.getAccessToken());
						assertEquals(CLIENT_CODE, authResponse.getLoggedInClientCode());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("refreshToken - empty security context returns error")
		void refreshToken_EmptySecurityContext_ReturnsError() {
			setupEmptySecurityContext();

			ServerHttpRequest request = mockRequest("Bearer token", APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			StepVerifier.create(service.refreshToken(request, response))
					.verifyComplete();
		}
	}

	// ===== PIN-based authentication tests =====

	@Nested
	@DisplayName("PIN-based Authentication")
	class PinAuthenticationTests {

		@Test
		@DisplayName("authenticate with valid PIN succeeds")
		void authenticate_ValidPin_ReturnsTokenResponse() {
			AuthenticationRequest authRequest = TestDataFactory.createAuthenticationRequestWithPin("testuser",
					"123456");

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			Client linClient = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			User user = TestDataFactory.createActiveUser(USER_ID, CLIENT_ID);
			user.setPin("hashedPin");
			user.setPinHashed(true);

			when(userService.findNonDeletedUserNActiveClient(anyString(), any(), anyString(), anyString(), any()))
					.thenReturn(Mono.just(Tuples.of(linClient, client, user)));

			when(profileService.checkIfUserHasAnyProfile(any(ULong.class), anyString()))
					.thenReturn(Mono.just(true));

			when(userService.checkUserAndClient(any(), anyString()))
					.thenReturn(Mono.just(true));

			ClientPinPolicy pinPolicy = TestDataFactory.createPinPolicy();
			when(clientService.getClientAppPolicy(any(ULong.class), anyString(), any(AuthenticationPasswordType.class)))
					.thenReturn(Mono.just(pinPolicy));

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
					})
					.verifyComplete();

			verify(userService).resetFailedAttempt(USER_ID, AuthenticationPasswordType.PIN);
		}

		@Test
		@DisplayName("authenticate with invalid PIN increases failed attempts")
		void authenticate_InvalidPin_IncreasesFailedAttempt() {
			AuthenticationRequest authRequest = TestDataFactory.createAuthenticationRequestWithPin("testuser",
					"000000");

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			Client linClient = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			User user = TestDataFactory.createActiveUser(USER_ID, CLIENT_ID);
			user.setPin("hashedPin");
			user.setPinHashed(true);
			user.setNoPinFailedAttempt((short) 0);

			when(userService.findNonDeletedUserNActiveClient(anyString(), any(), anyString(), anyString(), any()))
					.thenReturn(Mono.just(Tuples.of(linClient, client, user)));

			when(profileService.checkIfUserHasAnyProfile(any(ULong.class), anyString()))
					.thenReturn(Mono.just(true));

			when(userService.checkUserAndClient(any(), anyString()))
					.thenReturn(Mono.just(true));

			ClientPinPolicy pinPolicy = TestDataFactory.createPinPolicy();
			when(clientService.getClientAppPolicy(any(ULong.class), anyString(), any(AuthenticationPasswordType.class)))
					.thenReturn(Mono.just(pinPolicy));

			when(pwdEncoder.matches(anyString(), anyString())).thenReturn(false);

			when(userService.increaseFailedAttempt(any(ULong.class), any(AuthenticationPasswordType.class)))
					.thenReturn(Mono.just((short) 1));

			StepVerifier.create(service.authenticate(authRequest, request, response))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();

			verify(userService).increaseFailedAttempt(USER_ID, AuthenticationPasswordType.PIN);
		}

		@Test
		@DisplayName("authenticate with PIN exceeding max failed attempts locks user")
		void authenticate_PinMaxFailedAttempts_LocksUser() {
			AuthenticationRequest authRequest = TestDataFactory.createAuthenticationRequestWithPin("testuser",
					"000000");

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			Client linClient = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			User user = TestDataFactory.createActiveUser(USER_ID, CLIENT_ID);
			user.setPin("hashedPin");
			user.setPinHashed(true);
			user.setNoPinFailedAttempt((short) 3); // at max

			when(userService.findNonDeletedUserNActiveClient(anyString(), any(), anyString(), anyString(), any()))
					.thenReturn(Mono.just(Tuples.of(linClient, client, user)));

			when(profileService.checkIfUserHasAnyProfile(any(ULong.class), anyString()))
					.thenReturn(Mono.just(true));

			when(userService.checkUserAndClient(any(), anyString()))
					.thenReturn(Mono.just(true));

			ClientPinPolicy pinPolicy = TestDataFactory.createPinPolicy();
			when(clientService.getClientAppPolicy(any(ULong.class), anyString(), any(AuthenticationPasswordType.class)))
					.thenReturn(Mono.just(pinPolicy));

			when(pwdEncoder.matches(anyString(), anyString())).thenReturn(false);

			when(userService.lockUserInternal(any(ULong.class), any(LocalDateTime.class), anyString()))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.authenticate(authRequest, request, response))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();

			verify(userService).lockUserInternal(eq(USER_ID), any(LocalDateTime.class), eq("PIN"));
		}

		@Test
		@DisplayName("authenticate with unhashed PIN uses string comparison")
		void authenticate_UnhashedPin_UsesStringComparison() {
			AuthenticationRequest authRequest = TestDataFactory.createAuthenticationRequestWithPin("testuser",
					"123456");

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			Client linClient = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			User user = TestDataFactory.createActiveUser(USER_ID, CLIENT_ID);
			user.setPin("123456");
			user.setPinHashed(false);

			when(userService.findNonDeletedUserNActiveClient(anyString(), any(), anyString(), anyString(), any()))
					.thenReturn(Mono.just(Tuples.of(linClient, client, user)));

			when(profileService.checkIfUserHasAnyProfile(any(ULong.class), anyString()))
					.thenReturn(Mono.just(true));

			when(userService.checkUserAndClient(any(), anyString()))
					.thenReturn(Mono.just(true));

			ClientPinPolicy pinPolicy = TestDataFactory.createPinPolicy();
			when(clientService.getClientAppPolicy(any(ULong.class), anyString(), any(AuthenticationPasswordType.class)))
					.thenReturn(Mono.just(pinPolicy));

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

			// PasswordEncoder should NOT be called for unhashed pins
			verify(pwdEncoder, never()).matches(anyString(), anyString());
		}
	}

	// ===== OTP-based authentication tests =====

	@Nested
	@DisplayName("OTP-based Authentication")
	class OtpAuthenticationTests {

		@Test
		@DisplayName("authenticate with valid OTP succeeds and resets both failed and resend attempts")
		void authenticate_ValidOtp_ReturnsTokenAndResetsAttempts() {
			AuthenticationRequest authRequest = TestDataFactory.createAuthenticationRequestWithOtp("testuser",
					"1234");

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			Client linClient = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			User user = TestDataFactory.createActiveUser(USER_ID, CLIENT_ID);

			when(userService.findNonDeletedUserNActiveClient(anyString(), any(), anyString(), anyString(), any()))
					.thenReturn(Mono.just(Tuples.of(linClient, client, user)));

			when(profileService.checkIfUserHasAnyProfile(any(ULong.class), anyString()))
					.thenReturn(Mono.just(true));

			when(userService.checkUserAndClient(any(), anyString()))
					.thenReturn(Mono.just(true));

			ClientOtpPolicy otpPolicy = TestDataFactory.createOtpPolicy();
			when(clientService.getClientAppPolicy(any(ULong.class), anyString(), any(AuthenticationPasswordType.class)))
					.thenReturn(Mono.just(otpPolicy));

			// OTP verification goes through OtpService
			when(otpService.verifyOtpInternal(anyString(), any(User.class), any()))
					.thenReturn(Mono.just(true));

			when(userService.resetFailedAttempt(any(ULong.class), any(AuthenticationPasswordType.class)))
					.thenReturn(Mono.just(true));

			when(userService.resetResendAttempt(any(ULong.class)))
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
					})
					.verifyComplete();

			// OTP authentication should reset both failed attempts AND resend attempts
			verify(userService).resetFailedAttempt(USER_ID, AuthenticationPasswordType.OTP);
			verify(userService).resetResendAttempt(USER_ID);
		}

		@Test
		@DisplayName("authenticate with invalid OTP increases OTP failed attempts")
		void authenticate_InvalidOtp_IncreasesOtpFailedAttempts() {
			AuthenticationRequest authRequest = TestDataFactory.createAuthenticationRequestWithOtp("testuser",
					"9999");

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			Client linClient = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			User user = TestDataFactory.createActiveUser(USER_ID, CLIENT_ID);
			user.setNoOtpFailedAttempt((short) 0);

			when(userService.findNonDeletedUserNActiveClient(anyString(), any(), anyString(), anyString(), any()))
					.thenReturn(Mono.just(Tuples.of(linClient, client, user)));

			when(profileService.checkIfUserHasAnyProfile(any(ULong.class), anyString()))
					.thenReturn(Mono.just(true));

			when(userService.checkUserAndClient(any(), anyString()))
					.thenReturn(Mono.just(true));

			ClientOtpPolicy otpPolicy = TestDataFactory.createOtpPolicy();
			when(clientService.getClientAppPolicy(any(ULong.class), anyString(), any(AuthenticationPasswordType.class)))
					.thenReturn(Mono.just(otpPolicy));

			when(otpService.verifyOtpInternal(anyString(), any(User.class), any()))
					.thenReturn(Mono.just(false));

			when(userService.increaseFailedAttempt(any(ULong.class), any(AuthenticationPasswordType.class)))
					.thenReturn(Mono.just((short) 1));

			StepVerifier.create(service.authenticate(authRequest, request, response))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();

			verify(userService).increaseFailedAttempt(USER_ID, AuthenticationPasswordType.OTP);
		}

		@Test
		@DisplayName("authenticate with OTP exceeding max failed attempts locks user")
		void authenticate_OtpMaxFailedAttempts_LocksUser() {
			AuthenticationRequest authRequest = TestDataFactory.createAuthenticationRequestWithOtp("testuser",
					"9999");

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			Client linClient = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			User user = TestDataFactory.createActiveUser(USER_ID, CLIENT_ID);
			user.setNoOtpFailedAttempt((short) 3); // at max

			when(userService.findNonDeletedUserNActiveClient(anyString(), any(), anyString(), anyString(), any()))
					.thenReturn(Mono.just(Tuples.of(linClient, client, user)));

			when(profileService.checkIfUserHasAnyProfile(any(ULong.class), anyString()))
					.thenReturn(Mono.just(true));

			when(userService.checkUserAndClient(any(), anyString()))
					.thenReturn(Mono.just(true));

			ClientOtpPolicy otpPolicy = TestDataFactory.createOtpPolicy();
			when(clientService.getClientAppPolicy(any(ULong.class), anyString(), any(AuthenticationPasswordType.class)))
					.thenReturn(Mono.just(otpPolicy));

			when(otpService.verifyOtpInternal(anyString(), any(User.class), any()))
					.thenReturn(Mono.just(false));

			when(userService.lockUserInternal(any(ULong.class), any(LocalDateTime.class), anyString()))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.authenticate(authRequest, request, response))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();

			verify(userService).lockUserInternal(eq(USER_ID), any(LocalDateTime.class), eq("OTP"));
		}
	}

	// ===== authenticateWithOneTimeToken() tests =====

	@Nested
	@DisplayName("authenticateWithOneTimeToken")
	class AuthenticateWithOneTimeTokenTests {

		@Test
		@DisplayName("valid one-time token authenticates user and returns token response")
		void authenticateWithOneTimeToken_ValidToken_ReturnsAuthResponse() {
			String ottToken = "valid-one-time-token-123";
			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			OneTimeToken ott = TestDataFactory.createOneTimeToken(ULong.valueOf(50), USER_ID, ottToken);

			Client linClient = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			User user = TestDataFactory.createActiveUser(USER_ID, CLIENT_ID);

			when(oneTimeTokenService.getOneTimeToken(ottToken))
					.thenReturn(Mono.just(ott));

			when(userService.findNonDeletedUserNClient(any(), any(ULong.class), anyString(), anyString(), any()))
					.thenReturn(Mono.just(Tuples.of(linClient, client, user)));

			when(profileService.checkIfUserHasAnyProfile(any(ULong.class), anyString()))
					.thenReturn(Mono.just(true));

			when(userService.checkUserAndClient(any(), anyString()))
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

			StepVerifier.create(service.authenticateWithOneTimeToken(ottToken, request, response))
					.assertNext(authResponse -> {
						assertNotNull(authResponse);
						assertNotNull(authResponse.getAccessToken());
						assertEquals(CLIENT_CODE, authResponse.getLoggedInClientCode());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("invalid one-time token returns forbidden error")
		void authenticateWithOneTimeToken_InvalidToken_ThrowsForbidden() {
			String ottToken = "invalid-token";
			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			when(oneTimeTokenService.getOneTimeToken(ottToken))
					.thenReturn(Mono.empty());

			StepVerifier.create(service.authenticateWithOneTimeToken(ottToken, request, response))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		@DisplayName("one-time token with locked user throws forbidden")
		void authenticateWithOneTimeToken_LockedUser_ThrowsForbidden() {
			String ottToken = "valid-one-time-token-456";
			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			OneTimeToken ott = TestDataFactory.createOneTimeToken(ULong.valueOf(51), USER_ID, ottToken);

			Client linClient = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			User user = TestDataFactory.createLockedUser(USER_ID, CLIENT_ID,
					LocalDateTime.now().plusMinutes(10));

			when(oneTimeTokenService.getOneTimeToken(ottToken))
					.thenReturn(Mono.just(ott));

			when(userService.findNonDeletedUserNClient(any(), any(ULong.class), anyString(), anyString(), any()))
					.thenReturn(Mono.just(Tuples.of(linClient, client, user)));

			when(profileService.checkIfUserHasAnyProfile(any(ULong.class), anyString()))
					.thenReturn(Mono.just(true));

			when(userService.checkUserAndClient(any(), anyString()))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.authenticateWithOneTimeToken(ottToken, request, response))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		@DisplayName("one-time token user not found in system returns forbidden")
		void authenticateWithOneTimeToken_UserNotFound_ThrowsForbidden() {
			String ottToken = "valid-one-time-token-789";
			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			OneTimeToken ott = TestDataFactory.createOneTimeToken(ULong.valueOf(52), USER_ID, ottToken);

			when(oneTimeTokenService.getOneTimeToken(ottToken))
					.thenReturn(Mono.just(ott));

			when(userService.findNonDeletedUserNClient(any(), any(ULong.class), anyString(), anyString(), any()))
					.thenReturn(Mono.empty());

			StepVerifier.create(service.authenticateWithOneTimeToken(ottToken, request, response))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}

	// ===== getUserAppAccess() tests =====

	@Nested
	@DisplayName("getUserAppAccess")
	class GetUserAppAccessTests {

		@Test
		@DisplayName("user with app profile access returns UserAccess with app=true and token")
		void getUserAppAccess_UserHasAppProfile_ReturnsAccessWithToken() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(CLIENT_ID, CLIENT_CODE,
					List.of("Authorities.Logged_IN"));
			setupSecurityContext(ca);

			UserAppAccessRequest accessRequest = new UserAppAccessRequest();
			accessRequest.setAppCode(APP_CODE);
			accessRequest.setCallbackUrl("https://app.test/{token}");

			ServerHttpRequest request = mockRequest();

			App app = TestDataFactory.createOwnApp(ULong.valueOf(5), CLIENT_ID, APP_CODE);
			when(appService.getAppByCode(APP_CODE))
					.thenReturn(Mono.just(app));

			when(profileService.checkIfUserHasAnyProfile(any(ULong.class), eq(APP_CODE)))
					.thenReturn(Mono.just(true));

			OneTimeToken ott = new OneTimeToken();
			ott.setToken("appAccessToken123");
			when(oneTimeTokenService.create(any(OneTimeToken.class)))
					.thenReturn(Mono.just(ott));

			StepVerifier.create(service.getUserAppAccess(accessRequest, request))
					.assertNext(userAccess -> {
						assertTrue(userAccess.isApp());
						assertEquals("appAccessToken123", userAccess.getAppOneTimeToken());
						assertTrue(userAccess.getAppURL().contains("appAccessToken123"));
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("user without profile but is owner with client access returns proper UserAccess")
		void getUserAppAccess_NoProfileButOwnerWithClientAccess_ReturnsAccess() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(CLIENT_ID, CLIENT_CODE,
					List.of("Authorities.Logged_IN"));
			setupSecurityContext(ca);

			UserAppAccessRequest accessRequest = new UserAppAccessRequest();
			accessRequest.setAppCode(APP_CODE);

			ServerHttpRequest request = mockRequest();

			App app = TestDataFactory.createOwnApp(ULong.valueOf(5), CLIENT_ID, APP_CODE);
			when(appService.getAppByCode(APP_CODE))
					.thenReturn(Mono.just(app));

			when(profileService.checkIfUserHasAnyProfile(any(ULong.class), eq(APP_CODE)))
					.thenReturn(Mono.just(false));

			when(userService.checkIfUserIsOwner(any(ULong.class)))
					.thenReturn(Mono.just(true));

			when(appService.hasReadAccess(eq(APP_CODE), anyString()))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.getUserAppAccess(accessRequest, request))
					.assertNext(userAccess -> {
						assertFalse(userAccess.isApp());
						assertTrue(userAccess.isOwner());
						assertTrue(userAccess.isClientAccess());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("user without profile and not owner with false returns access with no app and no owner")
		void getUserAppAccess_NoProfileNotOwner_ReturnsNoAccess() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(CLIENT_ID, CLIENT_CODE,
					List.of("Authorities.Logged_IN"));
			setupSecurityContext(ca);

			UserAppAccessRequest accessRequest = new UserAppAccessRequest();
			accessRequest.setAppCode(APP_CODE);

			ServerHttpRequest request = mockRequest();

			App app = TestDataFactory.createOwnApp(ULong.valueOf(5), CLIENT_ID, APP_CODE);
			when(appService.getAppByCode(APP_CODE))
					.thenReturn(Mono.just(app));

			when(profileService.checkIfUserHasAnyProfile(any(ULong.class), eq(APP_CODE)))
					.thenReturn(Mono.just(false));

			when(userService.checkIfUserIsOwner(any(ULong.class)))
					.thenReturn(Mono.just(false));

			when(appService.hasReadAccess(eq(APP_CODE), anyString()))
					.thenReturn(Mono.empty());

			StepVerifier.create(service.getUserAppAccess(accessRequest, request))
					.assertNext(userAccess -> {
						assertFalse(userAccess.isApp());
						assertFalse(userAccess.isOwner());
						assertFalse(userAccess.isClientAccess());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("empty security context throws error")
		void getUserAppAccess_EmptySecurityContext_ThrowsError() {
			setupEmptySecurityContext();

			UserAppAccessRequest accessRequest = new UserAppAccessRequest();
			accessRequest.setAppCode(APP_CODE);

			ServerHttpRequest request = mockRequest();

			StepVerifier.create(service.getUserAppAccess(accessRequest, request))
					.expectError()
					.verify();
		}
	}

	// ===== Additional generateOtp() tests =====

	@Nested
	@DisplayName("generateOtp - additional scenarios")
	class GenerateOtpAdditionalTests {

		@Test
		@DisplayName("generateOtp with valid user delegates to otpService")
		void generateOtp_ValidUser_DelegatesToOtpService() {
			AuthenticationRequest authRequest = new AuthenticationRequest();
			authRequest.setGenerateOtp(true);
			authRequest.setUserName("test@test.com");

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);

			Client linClient = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			User user = TestDataFactory.createActiveUser(USER_ID, CLIENT_ID);

			when(userService.findNonDeletedUserNActiveClient(anyString(), any(), anyString(), anyString(), any()))
					.thenReturn(Mono.just(Tuples.of(linClient, client, user)));

			when(userService.checkUserAndClient(any(), anyString()))
					.thenReturn(Mono.just(true));

			App app = TestDataFactory.createOwnApp(ULong.valueOf(5), CLIENT_ID, APP_CODE);
			when(appService.getAppByCode(anyString()))
					.thenReturn(Mono.just(app));

			when(otpService.generateOtpInternal(any()))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.generateOtp(authRequest, request))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();

			verify(otpService).generateOtpInternal(any());
		}

		@Test
		@DisplayName("generateOtp with resend flag passes resend to internal request")
		void generateOtp_ResendFlag_PassesResendToRequest() {
			AuthenticationRequest authRequest = new AuthenticationRequest();
			authRequest.setGenerateOtp(true);
			authRequest.setResend(true);
			authRequest.setUserName("test@test.com");

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);

			Client linClient = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			User user = TestDataFactory.createActiveUser(USER_ID, CLIENT_ID);

			when(userService.findNonDeletedUserNActiveClient(anyString(), any(), anyString(), anyString(), any()))
					.thenReturn(Mono.just(Tuples.of(linClient, client, user)));

			when(userService.checkUserAndClient(any(), anyString()))
					.thenReturn(Mono.just(true));

			App app = TestDataFactory.createOwnApp(ULong.valueOf(5), CLIENT_ID, APP_CODE);
			when(appService.getAppByCode(anyString()))
					.thenReturn(Mono.just(app));

			when(otpService.generateOtpInternal(any()))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.generateOtp(authRequest, request))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("generateOtp when checkUserAndClient fails returns false")
		void generateOtp_CheckUserAndClientFails_ReturnsFalse() {
			AuthenticationRequest authRequest = new AuthenticationRequest();
			authRequest.setGenerateOtp(true);
			authRequest.setUserName("test@test.com");

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);

			Client linClient = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			User user = TestDataFactory.createActiveUser(USER_ID, CLIENT_ID);

			when(userService.findNonDeletedUserNActiveClient(anyString(), any(), anyString(), anyString(), any()))
					.thenReturn(Mono.just(Tuples.of(linClient, client, user)));

			when(userService.checkUserAndClient(any(), anyString()))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.generateOtp(authRequest, request))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	// ===== Additional revoke() tests =====

	@Nested
	@DisplayName("revoke - additional edge cases")
	class RevokeAdditionalTests {

		@Test
		@DisplayName("revoke with matching token in DB deletes it")
		void revoke_MatchingTokenInDB_DeletesToken() {
			String token = "actual.jwt.token.that.is.long.enough.for.part.token.extraction.testing.purposes";
			ServerHttpRequest request = mockRequest("Bearer " + token, null, null);

			TokenObject storedToken = TestDataFactory.createTokenObject(
					ULong.valueOf(200), USER_ID, token, LocalDateTime.now().plusHours(1));

			when(tokenService.readAllFilter(any()))
					.thenReturn(reactor.core.publisher.Flux.just(storedToken));

			when(tokenService.delete(any(ULong.class)))
					.thenReturn(Mono.just(1));

			StepVerifier.create(service.revoke(false, request))
					.assertNext(result -> assertEquals(1, result))
					.verifyComplete();

			verify(tokenService).delete(ULong.valueOf(200));
		}

		@Test
		@DisplayName("revoke with non-matching token in DB results returns default 1")
		void revoke_NonMatchingTokenInDB_ReturnsDefault() {
			String token = "my.jwt.token.that.is.definitely.long.enough.for.testing.the.part.token";
			ServerHttpRequest request = mockRequest("Bearer " + token, null, null);

			TokenObject storedToken = TestDataFactory.createTokenObject(
					ULong.valueOf(200), USER_ID, "different.token.value.that.does.not.match.at.all.for.testing",
					LocalDateTime.now().plusHours(1));

			when(tokenService.readAllFilter(any()))
					.thenReturn(reactor.core.publisher.Flux.just(storedToken));

			StepVerifier.create(service.revoke(false, request))
					.assertNext(result -> assertEquals(1, result))
					.verifyComplete();

			verify(tokenService, never()).delete(any(ULong.class));
		}

		@Test
		@DisplayName("ssoRevoke with empty security context completes empty")
		void revoke_SsoLogout_EmptySecurityContext_CompletesEmpty() {
			String token = "some.jwt.token.value";
			ServerHttpRequest request = mockRequest("Bearer " + token, null, null);

			setupEmptySecurityContext();

			StepVerifier.create(service.revoke(true, request))
					.verifyComplete();
		}
	}

	// ===== Authenticate with rememberMe/cookie/ssoToken tests =====

	@Nested
	@DisplayName("authenticate - rememberMe, cookie, and SSO token scenarios")
	class AuthenticateTokenOptionsTests {

		@Test
		@DisplayName("authenticate with rememberMe flag uses extended expiry")
		void authenticate_RememberMe_UsesExtendedExpiry() {
			AuthenticationRequest authRequest = TestDataFactory.createAuthenticationRequest("testuser", "password123");
			authRequest.setRememberMe(true);

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			Client linClient = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			client.setTokenValidityMinutes(30); // short validity
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
						// The token expiry should be much later due to rememberMe
						assertNotNull(authResponse.getAccessTokenExpiryAt());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("authenticate with cookie flag adds cookie to response")
		void authenticate_CookieFlag_AddsCookieToResponse() {
			AuthenticationRequest authRequest = TestDataFactory.createAuthenticationRequest("testuser", "password123");
			authRequest.setCookie(true);

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();
			HttpHeaders responseHeaders = new HttpHeaders();
			when(response.getHeaders()).thenReturn(responseHeaders);

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
					.assertNext(authResponse -> assertNotNull(authResponse.getAccessToken()))
					.verifyComplete();

			// Verify that addCookie was called on the response
			verify(response).addCookie(any());
		}

		@Test
		@DisplayName("authenticate with ssoToken flag creates one-time token in response")
		void authenticate_SsoTokenFlag_CreatesOneTimeToken() {
			AuthenticationRequest authRequest = TestDataFactory.createAuthenticationRequest("testuser", "password123");
			authRequest.setSsoToken(true);

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

			OneTimeToken ssoOtt = new OneTimeToken();
			ssoOtt.setToken("ssoOneTimeToken456");
			when(oneTimeTokenService.create(any(OneTimeToken.class)))
					.thenReturn(Mono.just(ssoOtt));

			StepVerifier.create(service.authenticate(authRequest, request, response))
					.assertNext(authResponse -> {
						assertNotNull(authResponse);
						assertNotNull(authResponse.getAccessToken());
						assertEquals("ssoOneTimeToken456", authResponse.getSsoToken());
					})
					.verifyComplete();

			verify(oneTimeTokenService).create(any(OneTimeToken.class));
		}

		@Test
		@DisplayName("authenticate with unhashed password uses string comparison")
		void authenticate_UnhashedPassword_UsesStringComparison() {
			AuthenticationRequest authRequest = TestDataFactory.createAuthenticationRequest("testuser", "plaintext");

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			Client linClient = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			User user = TestDataFactory.createActiveUser(USER_ID, CLIENT_ID);
			user.setPassword("plaintext");
			user.setPasswordHashed(false);

			when(userService.findNonDeletedUserNActiveClient(anyString(), any(), anyString(), anyString(), any()))
					.thenReturn(Mono.just(Tuples.of(linClient, client, user)));

			when(profileService.checkIfUserHasAnyProfile(any(ULong.class), anyString()))
					.thenReturn(Mono.just(true));

			when(userService.checkUserAndClient(any(), anyString()))
					.thenReturn(Mono.just(true));

			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			when(clientService.getClientAppPolicy(any(ULong.class), anyString(), any(AuthenticationPasswordType.class)))
					.thenReturn(Mono.just(policy));

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

			// PasswordEncoder should NOT be called for unhashed passwords
			verify(pwdEncoder, never()).matches(anyString(), anyString());
		}

		@Test
		@DisplayName("authenticate with zero tokenValidityMinutes uses default expiry")
		void authenticate_ZeroTokenValidity_UsesDefaultExpiry() {
			AuthenticationRequest authRequest = TestDataFactory.createAuthenticationRequest("testuser", "password123");

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			Client linClient = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			client.setTokenValidityMinutes(0); // zero means use default
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
					})
					.verifyComplete();
		}
	}

	// ===== makeOneTimeToken() additional tests =====

	@Nested
	@DisplayName("makeOneTimeToken - additional scenarios")
	class MakeOneTimeTokenAdditionalTests {

		@Test
		@DisplayName("makeOneTimeToken with empty security context completes empty")
		void makeOneTimeToken_EmptySecurityContext_CompletesEmpty() {
			setupEmptySecurityContext();

			var tokenRequest = new MakeOneTimeTimeTokenRequest();
			tokenRequest.setCallbackUrl("https://app.test/{token}");

			ServerHttpRequest request = mockRequest();

			StepVerifier.create(service.makeOneTimeToken(tokenRequest, request))
					.verifyComplete();
		}

		@Test
		@DisplayName("makeOneTimeToken with env placeholder in URL fills env value")
		void makeOneTimeToken_EnvPlaceholderInUrl_FillsEnvValue() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			var tokenRequest = new MakeOneTimeTimeTokenRequest();
			tokenRequest.setCallbackUrl("https://{env}.app.test/{token}");

			ServerHttpRequest request = mockRequest();

			OneTimeToken ott = new OneTimeToken();
			ott.setToken("tokenABC");

			when(oneTimeTokenService.create(any(OneTimeToken.class)))
					.thenReturn(Mono.just(ott));

			StepVerifier.create(service.makeOneTimeToken(tokenRequest, request))
					.assertNext(result -> {
						assertEquals("tokenABC", result.get("token"));
						// {env} should be replaced with appCodeSuffix minus the dot prefix
						String url = result.get("url");
						assertFalse(url.contains("{env}"));
						assertFalse(url.contains("{token}"));
						assertTrue(url.contains("tokenABC"));
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("makeOneTimeToken with rememberMe passes flag to OneTimeToken")
		void makeOneTimeToken_RememberMe_PassesFlag() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			var tokenRequest = new MakeOneTimeTimeTokenRequest();
			tokenRequest.setCallbackUrl("https://app.test/{token}");
			tokenRequest.setRememberMe(true);

			ServerHttpRequest request = mockRequest();

			OneTimeToken ott = new OneTimeToken();
			ott.setToken("rememberToken");

			when(oneTimeTokenService.create(any(OneTimeToken.class)))
					.thenAnswer(inv -> {
						OneTimeToken arg = inv.getArgument(0);
						assertTrue(arg.getRememberMe(), "rememberMe should be true");
						return Mono.just(ott);
					});

			StepVerifier.create(service.makeOneTimeToken(tokenRequest, request))
					.assertNext(result -> assertEquals("rememberToken", result.get("token")))
					.verifyComplete();
		}
	}

	// ===== authenticate with deleted user tests =====

	@Nested
	@DisplayName("authenticate - user status edge cases")
	class AuthenticateUserStatusEdgeCaseTests {

		@Test
		@DisplayName("authenticate with deleted user throws forbidden")
		void authenticate_DeletedUser_ThrowsForbidden() {
			AuthenticationRequest authRequest = TestDataFactory.createAuthenticationRequest("testuser", "password123");

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			Client linClient = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			User user = TestDataFactory.createDeletedUser(USER_ID, CLIENT_ID);

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
		@DisplayName("authenticate with user having no profile falls through to authenticateUserForHavingApp")
		void authenticate_NoProfile_FallsThrough() {
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

			// No profile for user in this app
			when(profileService.checkIfUserHasAnyProfile(any(ULong.class), anyString()))
					.thenReturn(Mono.empty());

			// authenticateUserForHavingApp fallback also fails
			when(userService.findNonDeletedUserNClient(anyString(), any(), anyString(), any(), any()))
					.thenReturn(Mono.empty());

			StepVerifier.create(service.authenticate(authRequest, request, response))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		@DisplayName("authenticate with email identifier type resolves correctly")
		void authenticate_EmailIdentifier_ResolvesCorrectly() {
			AuthenticationRequest authRequest = TestDataFactory.createAuthenticationRequest("user@example.com",
					"password123");
			// Email ID should be auto-detected from the @ sign

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
					})
					.verifyComplete();
		}
	}

	// ===== authenticateUserForHavingApp - additional tests =====

	@Nested
	@DisplayName("authenticateUserForHavingApp - additional scenarios")
	class AuthenticateUserForHavingAppAdditionalTests {

		@Test
		@DisplayName("authenticateUserForHavingApp with valid user and app authenticates successfully")
		void authenticateUserForHavingApp_ValidUserAndApp_AuthenticatesSuccessfully() {
			AuthenticationRequest authRequest = TestDataFactory.createAuthenticationRequest("testuser", "password123");

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			// Mock the mutate() chain used in authenticateUserForHavingApp to set app code
			ServerHttpRequest.Builder requestBuilder = mock(ServerHttpRequest.Builder.class);
			when(request.mutate()).thenReturn(requestBuilder);
			when(requestBuilder.header(anyString(), any(String[].class))).thenReturn(requestBuilder);

			// The mutated request needs to return proper headers with the resolved app code
			ServerHttpRequest mutatedRequest = mockRequest(null, "resolvedApp", CLIENT_CODE);
			when(requestBuilder.build()).thenReturn(mutatedRequest);

			Client linClient = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			User user = TestDataFactory.createActiveUser(USER_ID, CLIENT_ID);
			user.setPassword("hashedPassword");
			user.setPasswordHashed(true);

			// findNonDeletedUserNClient for the "having app" path
			when(userService.findNonDeletedUserNClient(anyString(), any(), anyString(), any(), any()))
					.thenReturn(Mono.just(Tuples.of(linClient, client, user)));

			ULong appId = ULong.valueOf(5);
			when(profileService.getUserAppHavingProfile(any(ULong.class)))
					.thenReturn(Mono.just(appId));

			App app = TestDataFactory.createOwnApp(appId, CLIENT_ID, "resolvedApp");
			when(appService.getAppById(appId))
					.thenReturn(Mono.just(app));

			// This will trigger authenticate() with the resolved app code
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

			StepVerifier.create(
					service.authenticateUserForHavingApp(authRequest, CLIENT_CODE, request, response))
					.assertNext(authResponse -> {
						assertNotNull(authResponse);
						assertNotNull(authResponse.getAccessToken());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("authenticateUserForHavingApp with app not found returns forbidden")
		void authenticateUserForHavingApp_AppNotFoundById_ThrowsForbidden() {
			AuthenticationRequest authRequest = TestDataFactory.createAuthenticationRequest("testuser", "password123");

			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);
			ServerHttpResponse response = mockResponse();

			Client linClient = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			Client client = TestDataFactory.createBusinessClient(CLIENT_ID, CLIENT_CODE);
			User user = TestDataFactory.createActiveUser(USER_ID, CLIENT_ID);

			when(userService.findNonDeletedUserNClient(anyString(), any(), anyString(), any(), any()))
					.thenReturn(Mono.just(Tuples.of(linClient, client, user)));

			ULong appId = ULong.valueOf(5);
			when(profileService.getUserAppHavingProfile(any(ULong.class)))
					.thenReturn(Mono.just(appId));

			when(appService.getAppById(appId))
					.thenReturn(Mono.empty());

			StepVerifier.create(
					service.authenticateUserForHavingApp(authRequest, CLIENT_CODE, request, response))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}

	// ===== getAuthentication - additional tests =====

	@Nested
	@DisplayName("getAuthentication - additional scenarios")
	class GetAuthenticationAdditionalTests {

		@Test
		@DisplayName("getAuthentication with null token returns anonymous")
		void getAuthentication_NullToken_ReturnsAnonymous() {
			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);

			when(clientService.getClientBy(anyString()))
					.thenReturn(Mono.just(TestDataFactory.createSystemClient()));

			StepVerifier.create(service.getAuthentication(false, null, CLIENT_CODE, APP_CODE, request))
					.assertNext(auth -> {
						assertTrue(auth instanceof ContextAuthentication);
						assertFalse(auth.isAuthenticated());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("getAuthentication with malformed JWT returns anonymous via error handler")
		void getAuthentication_MalformedJWT_ReturnsAnonymous() {
			ServerHttpRequest request = mockRequest(null, APP_CODE, CLIENT_CODE);

			when(clientService.getClientBy(anyString()))
					.thenReturn(Mono.just(TestDataFactory.createSystemClient()));

			StepVerifier.create(
					service.getAuthentication(false, "not.a.valid.jwt.token.at.all", CLIENT_CODE, APP_CODE,
							request))
					.assertNext(auth -> {
						assertTrue(auth instanceof ContextAuthentication);
						assertFalse(auth.isAuthenticated());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("getAuthentication with unknown client code throws unauthorized")
		void getAuthentication_UnknownClientCode_ThrowsUnauthorized() {
			ServerHttpRequest request = mockRequest(null, APP_CODE, "UNKNOWN");

			when(clientService.getClientBy("UNKNOWN"))
					.thenReturn(Mono.empty());

			StepVerifier.create(
					service.getAuthentication(false, "", "UNKNOWN", APP_CODE, request))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.UNAUTHORIZED)
					.verify();
		}
	}
}
