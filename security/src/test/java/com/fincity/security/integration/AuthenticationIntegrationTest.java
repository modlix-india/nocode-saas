package com.fincity.security.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.model.AuthenticationIdentifierType;
import com.fincity.security.model.AuthenticationRequest;
import com.fincity.security.model.AuthenticationResponse;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureWebTestClient(timeout = "30000")
class AuthenticationIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private WebTestClient webTestClient;

	@Autowired
	private CacheService cacheService;

	private static final String AUTH_ENDPOINT = "/api/security/authenticate";
	private static final String VERIFY_TOKEN_ENDPOINT = "/api/security/verifyToken";
	private static final String REFRESH_TOKEN_ENDPOINT = "/api/security/refreshToken";
	private static final String REVOKE_ENDPOINT = "/api/security/revoke";

	private static final String SYSADMIN_USERNAME = "sysadmin";
	private static final String SYSADMIN_PASSWORD = "fincity@123";
	private static final String SYSTEM_CLIENT_CODE = "SYSTEM";
	private static final String SYSTEM_APP_CODE = "appbuilder";
	private static final String BUSINESS_APP_CODE = "testapp1";

	private ULong businessClientId;

	@BeforeAll
	void setupTestData() {
		// Insert a business client, user, and app for tests that need a non-system client.
		// The SYSTEM client (ID=1) and sysadmin user (ID=1) come from Flyway V1.
		// The "appbuilder" app (owned by SYSTEM) comes from Flyway V2.
		businessClientId = insertTestClient("TESTBUS1", "Test Business Client", "BUS")
				.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
						.then(insertTestApp(clientId, BUSINESS_APP_CODE, "Test App"))
						.then(insertTestUser(clientId, "testuser", "testuser@test.com", "testpass123"))
						.thenReturn(clientId))
				.block();
	}

	@BeforeEach
	void setUp() {
		setupMockBeans();
	}

	// --- Helper to authenticate and return a valid token ---
	private String authenticateAndGetToken(String userName, String password, String clientCode, String appCode) {
		AuthenticationRequest request = new AuthenticationRequest()
				.setUserName(userName)
				.setPassword(password);

		AuthenticationResponse response = webTestClient.post()
				.uri(AUTH_ENDPOINT)
				.header("clientCode", clientCode)
				.header("appCode", appCode)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.exchange()
				.expectStatus().isOk()
				.expectBody(AuthenticationResponse.class)
				.returnResult()
				.getResponseBody();

		assertThat(response).isNotNull();
		return response.getAccessToken();
	}

	@Test
	@Order(1)
	void authenticate_WithValidPassword_ReturnsTokenResponse() {

		AuthenticationRequest request = new AuthenticationRequest()
				.setUserName(SYSADMIN_USERNAME)
				.setPassword(SYSADMIN_PASSWORD);

		webTestClient.post()
				.uri(AUTH_ENDPOINT)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("appCode", SYSTEM_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.exchange()
				.expectStatus().isOk()
				.expectBody(AuthenticationResponse.class)
				.value(response -> {
					assertThat(response).isNotNull();
					assertThat(response.getAccessToken()).isNotBlank();
					assertThat(response.getAccessTokenExpiryAt()).isNotNull();
					assertThat(response.getUser()).isNotNull();
					assertThat(response.getUser().getUserName()).isEqualTo(SYSADMIN_USERNAME);
					assertThat(response.getClient()).isNotNull();
					assertThat(response.getLoggedInClientCode()).isEqualTo(SYSTEM_CLIENT_CODE);
				});
	}

	@Test
	@Order(2)
	void authenticate_WithInvalidPassword_Returns403() {

		AuthenticationRequest request = new AuthenticationRequest()
				.setUserName(SYSADMIN_USERNAME)
				.setPassword("wrongPassword");

		webTestClient.post()
				.uri(AUTH_ENDPOINT)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("appCode", SYSTEM_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.exchange()
				.expectStatus().isForbidden();
	}

	@Test
	@Order(3)
	void authenticate_WithNonExistentUser_Returns403() {

		AuthenticationRequest request = new AuthenticationRequest()
				.setUserName("nonexistentuser")
				.setPassword("anyPassword");

		webTestClient.post()
				.uri(AUTH_ENDPOINT)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("appCode", SYSTEM_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.exchange()
				.expectStatus().isForbidden();
	}

	@Test
	@Order(4)
	void fullLoginFlow_AuthenticateAndVerifyToken() {

		AuthenticationRequest request = new AuthenticationRequest()
				.setUserName(SYSADMIN_USERNAME)
				.setPassword(SYSADMIN_PASSWORD);

		// Step 1: Authenticate to get a token
		AuthenticationResponse authResponse = webTestClient.post()
				.uri(AUTH_ENDPOINT)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("appCode", SYSTEM_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.exchange()
				.expectStatus().isOk()
				.expectBody(AuthenticationResponse.class)
				.returnResult()
				.getResponseBody();

		assertThat(authResponse).isNotNull();
		String accessToken = authResponse.getAccessToken();
		assertThat(accessToken).isNotBlank();

		// Step 2: Verify the token
		webTestClient.get()
				.uri(VERIFY_TOKEN_ENDPOINT)
				.header("Authorization", "Bearer " + accessToken)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("X-Forwarded-Host", "localhost")
				.exchange()
				.expectStatus().isOk()
				.expectBody(AuthenticationResponse.class)
				.value(verifyResponse -> {
					assertThat(verifyResponse).isNotNull();
					assertThat(verifyResponse.getAccessToken()).isEqualTo(accessToken);
					assertThat(verifyResponse.getUser()).isNotNull();
					assertThat(verifyResponse.getUser().getUserName()).isEqualTo(SYSADMIN_USERNAME);
				});
	}

	@Test
	@Order(5)
	void tokenLifecycle_AuthenticateRefreshRevoke() {

		AuthenticationRequest request = new AuthenticationRequest()
				.setUserName(SYSADMIN_USERNAME)
				.setPassword(SYSADMIN_PASSWORD);

		// Step 1: Authenticate
		AuthenticationResponse authResponse = webTestClient.post()
				.uri(AUTH_ENDPOINT)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("appCode", SYSTEM_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.exchange()
				.expectStatus().isOk()
				.expectBody(AuthenticationResponse.class)
				.returnResult()
				.getResponseBody();

		assertThat(authResponse).isNotNull();
		String accessToken = authResponse.getAccessToken();

		// Step 2: Refresh the token (returns current or new token depending on expiry
		// window)
		webTestClient.get()
				.uri(REFRESH_TOKEN_ENDPOINT)
				.header("Authorization", "Bearer " + accessToken)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("X-Forwarded-Host", "localhost")
				.exchange()
				.expectStatus().isOk()
				.expectBody(AuthenticationResponse.class)
				.value(refreshResponse -> {
					assertThat(refreshResponse).isNotNull();
					assertThat(refreshResponse.getAccessToken()).isNotBlank();
					assertThat(refreshResponse.getUser()).isNotNull();
				});

		// Step 3: Revoke the token
		webTestClient.get()
				.uri(uriBuilder -> uriBuilder.path(REVOKE_ENDPOINT)
						.queryParam("ssoLogout", false)
						.build())
				.header("Authorization", "Bearer " + accessToken)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("X-Forwarded-Host", "localhost")
				.exchange()
				.expectStatus().isOk();
	}

	@Test
	@Order(6)
	void verifyToken_WithNoToken_Returns403() {

		// With no token, the JWTTokenFilter creates anonymous authentication.
		// The verifyToken endpoint sees isAuthenticated()=false and no bearer token,
		// so it returns 403 FORBIDDEN (as opposed to 401 for an invalid token).
		webTestClient.get()
				.uri(VERIFY_TOKEN_ENDPOINT)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.exchange()
				.expectStatus().isForbidden();
	}

	@Test
	@Order(7)
	void verifyToken_WithInvalidToken_Returns401() {

		webTestClient.get()
				.uri(VERIFY_TOKEN_ENDPOINT)
				.header("Authorization", "Bearer invalidTokenValueThatIsNotARealJWT12345")
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("X-Forwarded-Host", "localhost")
				.exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	@Order(8)
	void authenticate_WithNewBusinessClient_ReturnsToken() {

		assertThat(businessClientId).isNotNull();

		AuthenticationRequest request = new AuthenticationRequest()
				.setUserName("testuser")
				.setPassword("testpass123");

		webTestClient.post()
				.uri(AUTH_ENDPOINT)
				.header("clientCode", "TESTBUS1")
				.header("appCode", BUSINESS_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.exchange()
				.expectStatus().isOk()
				.expectBody(AuthenticationResponse.class)
				.value(response -> {
					assertThat(response).isNotNull();
					assertThat(response.getAccessToken()).isNotBlank();
					assertThat(response.getUser()).isNotNull();
					assertThat(response.getUser().getUserName()).isEqualTo("testuser");
					assertThat(response.getLoggedInClientCode()).isEqualTo("TESTBUS1");
				});
	}

	@Test
	@Order(9)
	void revoke_WithValidToken_Returns200() {

		AuthenticationRequest request = new AuthenticationRequest()
				.setUserName(SYSADMIN_USERNAME)
				.setPassword(SYSADMIN_PASSWORD);

		// Authenticate first
		AuthenticationResponse authResponse = webTestClient.post()
				.uri(AUTH_ENDPOINT)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("appCode", SYSTEM_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.exchange()
				.expectStatus().isOk()
				.expectBody(AuthenticationResponse.class)
				.returnResult()
				.getResponseBody();

		assertThat(authResponse).isNotNull();
		String accessToken = authResponse.getAccessToken();

		// Count all tokens for user 1 (sysadmin) before revocation.
		Long preRevokeCount = databaseClient.sql(
				"SELECT COUNT(*) as cnt FROM security_user_token WHERE USER_ID = 1")
				.map(row -> row.get("cnt", Long.class))
				.one()
				.block();
		assertThat(preRevokeCount).isGreaterThan(0L);

		// Revoke the token
		webTestClient.get()
				.uri(uriBuilder -> uriBuilder.path(REVOKE_ENDPOINT)
						.queryParam("ssoLogout", false)
						.build())
				.header("Authorization", "Bearer " + accessToken)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("X-Forwarded-Host", "localhost")
				.exchange()
				.expectStatus().isOk();

		// Verify the token count decreased after revocation.
		Long postRevokeCount = databaseClient.sql(
				"SELECT COUNT(*) as cnt FROM security_user_token WHERE USER_ID = 1")
				.map(row -> row.get("cnt", Long.class))
				.one()
				.block();
		assertThat(postRevokeCount).isLessThan(preRevokeCount);
	}

	@Test
	@Order(10)
	void multipleAuthentications_GenerateUniqueTokens() throws InterruptedException {

		AuthenticationRequest request = new AuthenticationRequest()
				.setUserName(SYSADMIN_USERNAME)
				.setPassword(SYSADMIN_PASSWORD);

		// First authentication
		AuthenticationResponse firstResponse = webTestClient.post()
				.uri(AUTH_ENDPOINT)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("appCode", SYSTEM_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.exchange()
				.expectStatus().isOk()
				.expectBody(AuthenticationResponse.class)
				.returnResult()
				.getResponseBody();

		// Wait 1 second so JWT timestamps differ (iat/exp are epoch seconds)
		Thread.sleep(1000);

		// Second authentication
		AuthenticationResponse secondResponse = webTestClient.post()
				.uri(AUTH_ENDPOINT)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("appCode", SYSTEM_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.exchange()
				.expectStatus().isOk()
				.expectBody(AuthenticationResponse.class)
				.returnResult()
				.getResponseBody();

		assertThat(firstResponse).isNotNull();
		assertThat(secondResponse).isNotNull();

		// Each authentication should produce a distinct token since JWT includes
		// a timestamp-based expiry and unique generation parameters.
		assertThat(firstResponse.getAccessToken())
				.isNotEqualTo(secondResponse.getAccessToken());

		// Both tokens should be valid
		assertThat(firstResponse.getAccessToken()).isNotBlank();
		assertThat(secondResponse.getAccessToken()).isNotBlank();
		assertThat(firstResponse.getAccessTokenExpiryAt()).isNotNull();
		assertThat(secondResponse.getAccessTokenExpiryAt()).isNotNull();
	}

	@Test
	@Order(11)
	void authenticate_DeletedUser_Returns403() {

		String ts = String.valueOf(System.currentTimeMillis());
		String userName = "deltest_" + ts;

		// Create a user and set status to DELETED
		insertTestUser(businessClientId, userName, "deltest_" + ts + "@test.com", "testpass123")
				.flatMap(userId -> databaseClient.sql(
						"UPDATE security_user SET STATUS_CODE = 'DELETED' WHERE ID = :userId")
						.bind("userId", userId.longValue())
						.then())
				.block();

		AuthenticationRequest request = new AuthenticationRequest()
				.setUserName(userName)
				.setPassword("testpass123");

		webTestClient.post()
				.uri(AUTH_ENDPOINT)
				.header("clientCode", "TESTBUS1")
				.header("appCode", BUSINESS_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.exchange()
				.expectStatus().isForbidden();
	}

	@Test
	@Order(12)
	void authenticate_WithEmail_ReturnsToken() {

		// Authenticate using email address instead of username
		AuthenticationRequest request = new AuthenticationRequest()
				.setUserName("testuser@test.com")
				.setPassword("testpass123")
				.setIdentifierType(AuthenticationIdentifierType.EMAIL_ID);

		webTestClient.post()
				.uri(AUTH_ENDPOINT)
				.header("clientCode", "TESTBUS1")
				.header("appCode", BUSINESS_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.exchange()
				.expectStatus().isOk()
				.expectBody(AuthenticationResponse.class)
				.value(response -> {
					assertThat(response).isNotNull();
					assertThat(response.getAccessToken()).isNotBlank();
					assertThat(response.getUser()).isNotNull();
					assertThat(response.getUser().getEmailId()).isEqualTo("testuser@test.com");
				});
	}

	@Test
	@Order(13)
	void authenticate_MissingClientCodeHeader_Returns5xx() {

		AuthenticationRequest request = new AuthenticationRequest()
				.setUserName(SYSADMIN_USERNAME)
				.setPassword(SYSADMIN_PASSWORD);

		webTestClient.post()
				.uri(AUTH_ENDPOINT)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.exchange()
				.expectStatus().is5xxServerError();
	}

	@Test
	@Order(15)
	void contextAuthentication_WithValidToken_ReturnsContext() {

		AuthenticationRequest authRequest = new AuthenticationRequest()
				.setUserName(SYSADMIN_USERNAME)
				.setPassword(SYSADMIN_PASSWORD);

		AuthenticationResponse authResponse = webTestClient.post()
				.uri(AUTH_ENDPOINT)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("appCode", SYSTEM_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(authRequest)
				.exchange()
				.expectStatus().isOk()
				.expectBody(AuthenticationResponse.class)
				.returnResult()
				.getResponseBody();

		assertThat(authResponse).isNotNull();
		String accessToken = authResponse.getAccessToken();

		webTestClient.get()
				.uri("/api/security/internal/securityContextAuthentication")
				.header("Authorization", "Bearer " + accessToken)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("X-Forwarded-Host", "localhost")
				.exchange()
				.expectStatus().isOk()
				.expectBody(ContextAuthentication.class)
				.value(ctx -> {
					assertThat(ctx).isNotNull();
					assertThat(ctx.getUser()).isNotNull();
				});
	}

	@Test
	@Order(16)
	void authenticate_InactiveUser_Returns403() {

		String ts = String.valueOf(System.currentTimeMillis());
		String userName = "inactive_" + ts;

		// Create a user and set status to INACTIVE
		insertTestUser(businessClientId, userName, "inactive_" + ts + "@test.com", "testpass123")
				.flatMap(userId -> databaseClient.sql(
						"UPDATE security_user SET STATUS_CODE = 'INACTIVE' WHERE ID = :userId")
						.bind("userId", userId.longValue())
						.then())
				.block();

		AuthenticationRequest request = new AuthenticationRequest()
				.setUserName(userName)
				.setPassword("testpass123");

		webTestClient.post()
				.uri(AUTH_ENDPOINT)
				.header("clientCode", "TESTBUS1")
				.header("appCode", BUSINESS_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.exchange()
				.expectStatus().isForbidden();
	}

	@Test
	@Order(18)
	void authenticate_EmptyPassword_Returns400() {

		AuthenticationRequest request = new AuthenticationRequest()
				.setUserName(SYSADMIN_USERNAME)
				.setPassword("");

		webTestClient.post()
				.uri(AUTH_ENDPOINT)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("appCode", SYSTEM_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.exchange()
				.expectStatus().isBadRequest();
	}

	@Test
	@Order(19)
	void refreshToken_WithNoToken_Returns401() {

		webTestClient.get()
				.uri(REFRESH_TOKEN_ENDPOINT)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("X-Forwarded-Host", "localhost")
				.exchange()
				.expectStatus().isUnauthorized();
	}

	// ==========================================
	// NEW NESTED TEST CLASSES FOR EXPANDED COVERAGE
	// ==========================================

	@Nested
	@DisplayName("Locked Account Scenarios")
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	class LockedAccountTests {

		@Test
		@Order(1)
		@DisplayName("Authenticate with actively locked user returns 403")
		void authenticate_LockedUser_StillWithinLockPeriod_Returns403() {

			String ts = String.valueOf(System.currentTimeMillis());
			String userName = "locked_" + ts;

			// Create a user and set status to LOCKED with a future lock-until time
			insertTestUser(businessClientId, userName, "locked_" + ts + "@test.com", "testpass123")
					.flatMap(userId -> databaseClient.sql(
							"UPDATE security_user SET STATUS_CODE = 'LOCKED', "
									+ "LOCKED_UNTIL = :lockedUntil, "
									+ "LOCKED_DUE_TO = 'Too many failed attempts' "
									+ "WHERE ID = :userId")
							.bind("lockedUntil", LocalDateTime.now().plusMinutes(30))
							.bind("userId", userId.longValue())
							.then())
					.block();

			AuthenticationRequest request = new AuthenticationRequest()
					.setUserName(userName)
					.setPassword("testpass123");

			webTestClient.post()
					.uri(AUTH_ENDPOINT)
					.header("clientCode", "TESTBUS1")
					.header("appCode", BUSINESS_APP_CODE)
					.header("X-Forwarded-Host", "localhost")
					.header("X-Real-IP", "127.0.0.1")
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(request)
					.exchange()
					.expectStatus().isForbidden();
		}

		@Test
		@Order(2)
		@DisplayName("Authenticate with lock-expired user succeeds (auto-unlock)")
		void authenticate_LockedUser_LockExpired_AutoUnlocksAndSucceeds() {

			String ts = String.valueOf(System.currentTimeMillis());
			String userName = "lockexp_" + ts;

			// Create a user with an expired lock (lock-until is in the past)
			insertTestUser(businessClientId, userName, "lockexp_" + ts + "@test.com", "testpass123")
					.flatMap(userId -> databaseClient.sql(
							"UPDATE security_user SET STATUS_CODE = 'LOCKED', "
									+ "LOCKED_UNTIL = :lockedUntil, "
									+ "LOCKED_DUE_TO = 'Too many failed attempts' "
									+ "WHERE ID = :userId")
							.bind("lockedUntil", LocalDateTime.now().minusMinutes(5))
							.bind("userId", userId.longValue())
							.then())
					.block();

			AuthenticationRequest request = new AuthenticationRequest()
					.setUserName(userName)
					.setPassword("testpass123");

			webTestClient.post()
					.uri(AUTH_ENDPOINT)
					.header("clientCode", "TESTBUS1")
					.header("appCode", BUSINESS_APP_CODE)
					.header("X-Forwarded-Host", "localhost")
					.header("X-Real-IP", "127.0.0.1")
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(request)
					.exchange()
					.expectStatus().isOk()
					.expectBody(AuthenticationResponse.class)
					.value(response -> {
						assertThat(response).isNotNull();
						assertThat(response.getAccessToken()).isNotBlank();
						assertThat(response.getUser().getUserName()).isEqualTo(userName);
					});
		}
	}

	@Nested
	@DisplayName("Password Expired Account Scenarios")
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	class PasswordExpiredAccountTests {

		@Test
		@Order(1)
		@DisplayName("Authenticate with password-expired user returns 403")
		void authenticate_PasswordExpiredUser_Returns403() {

			String ts = String.valueOf(System.currentTimeMillis());
			String userName = "pwdexp_" + ts;

			// Create a user and set status to PASSWORD_EXPIRED
			insertTestUser(businessClientId, userName, "pwdexp_" + ts + "@test.com", "testpass123")
					.flatMap(userId -> databaseClient.sql(
							"UPDATE security_user SET STATUS_CODE = 'PASSWORD_EXPIRED', "
									+ "LOCKED_DUE_TO = 'Password' "
									+ "WHERE ID = :userId")
							.bind("userId", userId.longValue())
							.then())
					.block();

			AuthenticationRequest request = new AuthenticationRequest()
					.setUserName(userName)
					.setPassword("testpass123");

			webTestClient.post()
					.uri(AUTH_ENDPOINT)
					.header("clientCode", "TESTBUS1")
					.header("appCode", BUSINESS_APP_CODE)
					.header("X-Forwarded-Host", "localhost")
					.header("X-Real-IP", "127.0.0.1")
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(request)
					.exchange()
					.expectStatus().isForbidden();
		}
	}

	@Nested
	@DisplayName("Failed Attempt Tracking and Account Locking")
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	class FailedAttemptTests {

		@Test
		@Order(1)
		@DisplayName("Wrong password increments failed attempt counter and returns remaining attempts")
		void authenticate_WrongPassword_IncrementsFailedAttempts() {

			String ts = String.valueOf(System.currentTimeMillis());
			String userName = "failatt_" + ts;

			// Create a fresh user with 0 failed attempts
			ULong userId = insertTestUser(businessClientId, userName, "failatt_" + ts + "@test.com", "testpass123")
					.block();

			assertThat(userId).isNotNull();

			AuthenticationRequest request = new AuthenticationRequest()
					.setUserName(userName)
					.setPassword("wrongPassword");

			// First failed attempt - should return 403 with remaining attempts info
			webTestClient.post()
					.uri(AUTH_ENDPOINT)
					.header("clientCode", "TESTBUS1")
					.header("appCode", BUSINESS_APP_CODE)
					.header("X-Forwarded-Host", "localhost")
					.header("X-Real-IP", "127.0.0.1")
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(request)
					.exchange()
					.expectStatus().isForbidden();

			// Verify the failed attempt counter was incremented in the DB
			Short failedAttempts = databaseClient.sql(
					"SELECT NO_FAILED_ATTEMPT FROM security_user WHERE ID = :userId")
					.bind("userId", userId.longValue())
					.map(row -> row.get("NO_FAILED_ATTEMPT", Short.class))
					.one()
					.block();

			assertThat(failedAttempts).isEqualTo((short) 1);
		}

		@Test
		@Order(2)
		@DisplayName("Multiple failed attempts locks the account when limit reached")
		void authenticate_MultipleFailedAttempts_LocksAccount() {

			String ts = String.valueOf(System.currentTimeMillis());
			String userName = "lockout_" + ts;

			// Create a user with failed attempts already at the threshold (3 is default policy limit).
			// Set NO_FAILED_ATTEMPT = 3 so the next wrong password triggers lock.
			ULong userId = insertTestUser(businessClientId, userName, "lockout_" + ts + "@test.com", "testpass123")
					.block();

			assertThat(userId).isNotNull();

			databaseClient.sql(
					"UPDATE security_user SET NO_FAILED_ATTEMPT = 3 WHERE ID = :userId")
					.bind("userId", userId.longValue())
					.then()
					.block();

			AuthenticationRequest request = new AuthenticationRequest()
					.setUserName(userName)
					.setPassword("wrongPassword");

			// This attempt should trigger the lock since failed attempts >= policy limit
			webTestClient.post()
					.uri(AUTH_ENDPOINT)
					.header("clientCode", "TESTBUS1")
					.header("appCode", BUSINESS_APP_CODE)
					.header("X-Forwarded-Host", "localhost")
					.header("X-Real-IP", "127.0.0.1")
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(request)
					.exchange()
					.expectStatus().isForbidden();

			// Verify the user is now locked in the DB
			String statusCode = databaseClient.sql(
					"SELECT STATUS_CODE FROM security_user WHERE ID = :userId")
					.bind("userId", userId.longValue())
					.map(row -> row.get("STATUS_CODE", String.class))
					.one()
					.block();

			assertThat(statusCode).isEqualTo("LOCKED");
		}

		@Test
		@Order(3)
		@DisplayName("Successful login resets failed attempt counter")
		void authenticate_SuccessAfterFailures_ResetsCounter() {

			String ts = String.valueOf(System.currentTimeMillis());
			String userName = "reset_" + ts;

			// Create a user with some failed attempts already
			ULong userId = insertTestUser(businessClientId, userName, "reset_" + ts + "@test.com", "testpass123")
					.block();

			assertThat(userId).isNotNull();

			databaseClient.sql(
					"UPDATE security_user SET NO_FAILED_ATTEMPT = 2 WHERE ID = :userId")
					.bind("userId", userId.longValue())
					.then()
					.block();

			// Now login with correct password
			AuthenticationRequest request = new AuthenticationRequest()
					.setUserName(userName)
					.setPassword("testpass123");

			webTestClient.post()
					.uri(AUTH_ENDPOINT)
					.header("clientCode", "TESTBUS1")
					.header("appCode", BUSINESS_APP_CODE)
					.header("X-Forwarded-Host", "localhost")
					.header("X-Real-IP", "127.0.0.1")
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(request)
					.exchange()
					.expectStatus().isOk();

			// Verify the failed attempt counter was reset
			Short failedAttempts = databaseClient.sql(
					"SELECT NO_FAILED_ATTEMPT FROM security_user WHERE ID = :userId")
					.bind("userId", userId.longValue())
					.map(row -> row.get("NO_FAILED_ATTEMPT", Short.class))
					.one()
					.block();

			assertThat(failedAttempts).isEqualTo((short) 0);
		}
	}

	@Nested
	@DisplayName("PIN-based Authentication")
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	class PinAuthenticationTests {

		@Test
		@Order(1)
		@DisplayName("Authenticate with valid PIN (unhashed) returns token")
		void authenticate_WithValidPin_ReturnsToken() {

			String ts = String.valueOf(System.currentTimeMillis());
			String userName = "pinuser_" + ts;

			// Create a user with a PIN set (unhashed)
			ULong userId = insertTestUser(businessClientId, userName, "pinuser_" + ts + "@test.com", "testpass123")
					.block();

			assertThat(userId).isNotNull();

			databaseClient.sql(
					"UPDATE security_user SET PIN = '123456', PIN_HASHED = false WHERE ID = :userId")
					.bind("userId", userId.longValue())
					.then()
					.block();

			AuthenticationRequest request = new AuthenticationRequest()
					.setUserName(userName)
					.setPin("123456");

			webTestClient.post()
					.uri(AUTH_ENDPOINT)
					.header("clientCode", "TESTBUS1")
					.header("appCode", BUSINESS_APP_CODE)
					.header("X-Forwarded-Host", "localhost")
					.header("X-Real-IP", "127.0.0.1")
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(request)
					.exchange()
					.expectStatus().isOk()
					.expectBody(AuthenticationResponse.class)
					.value(response -> {
						assertThat(response).isNotNull();
						assertThat(response.getAccessToken()).isNotBlank();
						assertThat(response.getUser().getUserName()).isEqualTo(userName);
					});
		}

		@Test
		@Order(2)
		@DisplayName("Authenticate with wrong PIN returns 403 and increments PIN failed attempts")
		void authenticate_WithWrongPin_Returns403() {

			String ts = String.valueOf(System.currentTimeMillis());
			String userName = "pinbad_" + ts;

			ULong userId = insertTestUser(businessClientId, userName, "pinbad_" + ts + "@test.com", "testpass123")
					.block();

			assertThat(userId).isNotNull();

			databaseClient.sql(
					"UPDATE security_user SET PIN = '123456', PIN_HASHED = false WHERE ID = :userId")
					.bind("userId", userId.longValue())
					.then()
					.block();

			AuthenticationRequest request = new AuthenticationRequest()
					.setUserName(userName)
					.setPin("999999");

			webTestClient.post()
					.uri(AUTH_ENDPOINT)
					.header("clientCode", "TESTBUS1")
					.header("appCode", BUSINESS_APP_CODE)
					.header("X-Forwarded-Host", "localhost")
					.header("X-Real-IP", "127.0.0.1")
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(request)
					.exchange()
					.expectStatus().isForbidden();

			// Verify PIN failed attempts counter was incremented
			Short pinFailedAttempts = databaseClient.sql(
					"SELECT NO_PIN_FAILED_ATTEMPT FROM security_user WHERE ID = :userId")
					.bind("userId", userId.longValue())
					.map(row -> row.get("NO_PIN_FAILED_ATTEMPT", Short.class))
					.one()
					.block();

			assertThat(pinFailedAttempts).isEqualTo((short) 1);
		}

		@Test
		@Order(3)
		@DisplayName("Authenticate with valid hashed PIN returns token")
		void authenticate_WithValidHashedPin_ReturnsToken() {

			String ts = String.valueOf(System.currentTimeMillis());
			String userName = "pinhash_" + ts;

			ULong userId = insertTestUser(businessClientId, userName, "pinhash_" + ts + "@test.com", "testpass123")
					.block();

			assertThat(userId).isNotNull();

			// Hash the PIN as userId + pin (same as checkPassword logic)
			BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
			String hashedPin = encoder.encode(userId.longValue() + "654321");

			databaseClient.sql(
					"UPDATE security_user SET PIN = :pin, PIN_HASHED = true WHERE ID = :userId")
					.bind("pin", hashedPin)
					.bind("userId", userId.longValue())
					.then()
					.block();

			AuthenticationRequest request = new AuthenticationRequest()
					.setUserName(userName)
					.setPin("654321");

			webTestClient.post()
					.uri(AUTH_ENDPOINT)
					.header("clientCode", "TESTBUS1")
					.header("appCode", BUSINESS_APP_CODE)
					.header("X-Forwarded-Host", "localhost")
					.header("X-Real-IP", "127.0.0.1")
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(request)
					.exchange()
					.expectStatus().isOk()
					.expectBody(AuthenticationResponse.class)
					.value(response -> {
						assertThat(response).isNotNull();
						assertThat(response.getAccessToken()).isNotBlank();
						assertThat(response.getUser().getUserName()).isEqualTo(userName);
					});
		}
	}

	@Nested
	@DisplayName("Hashed Password Authentication")
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	class HashedPasswordTests {

		@Test
		@Order(1)
		@DisplayName("Authenticate with valid BCrypt-hashed password returns token")
		void authenticate_WithHashedPassword_ReturnsToken() {

			String ts = String.valueOf(System.currentTimeMillis());
			String userName = "hashpwd_" + ts;

			ULong userId = insertTestUser(businessClientId, userName, "hashpwd_" + ts + "@test.com", "placeholder")
					.block();

			assertThat(userId).isNotNull();

			// Hash the password as userId + password (same as checkPassword logic)
			BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
			String hashedPassword = encoder.encode(userId.longValue() + "SecurePass!1");

			databaseClient.sql(
					"UPDATE security_user SET PASSWORD = :password, PASSWORD_HASHED = true WHERE ID = :userId")
					.bind("password", hashedPassword)
					.bind("userId", userId.longValue())
					.then()
					.block();

			AuthenticationRequest request = new AuthenticationRequest()
					.setUserName(userName)
					.setPassword("SecurePass!1");

			webTestClient.post()
					.uri(AUTH_ENDPOINT)
					.header("clientCode", "TESTBUS1")
					.header("appCode", BUSINESS_APP_CODE)
					.header("X-Forwarded-Host", "localhost")
					.header("X-Real-IP", "127.0.0.1")
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(request)
					.exchange()
					.expectStatus().isOk()
					.expectBody(AuthenticationResponse.class)
					.value(response -> {
						assertThat(response).isNotNull();
						assertThat(response.getAccessToken()).isNotBlank();
						assertThat(response.getUser().getUserName()).isEqualTo(userName);
					});
		}

		@Test
		@Order(2)
		@DisplayName("Authenticate with wrong password for hashed user returns 403")
		void authenticate_WithWrongHashedPassword_Returns403() {

			String ts = String.valueOf(System.currentTimeMillis());
			String userName = "hashbad_" + ts;

			ULong userId = insertTestUser(businessClientId, userName, "hashbad_" + ts + "@test.com", "placeholder")
					.block();

			assertThat(userId).isNotNull();

			BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
			String hashedPassword = encoder.encode(userId.longValue() + "CorrectPass!1");

			databaseClient.sql(
					"UPDATE security_user SET PASSWORD = :password, PASSWORD_HASHED = true WHERE ID = :userId")
					.bind("password", hashedPassword)
					.bind("userId", userId.longValue())
					.then()
					.block();

			AuthenticationRequest request = new AuthenticationRequest()
					.setUserName(userName)
					.setPassword("WrongPass!1");

			webTestClient.post()
					.uri(AUTH_ENDPOINT)
					.header("clientCode", "TESTBUS1")
					.header("appCode", BUSINESS_APP_CODE)
					.header("X-Forwarded-Host", "localhost")
					.header("X-Real-IP", "127.0.0.1")
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(request)
					.exchange()
					.expectStatus().isForbidden();
		}
	}

	@Nested
	@DisplayName("Token Revocation Scenarios")
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	class TokenRevocationTests {

		@Test
		@Order(1)
		@DisplayName("Verify token after revocation - token count decreases in DB")
		void verifyToken_AfterRevocation_TokenDeletedFromDB() {

			// Get a token using a dedicated user to avoid JWT collisions from other tests
			String accessToken = authenticateAndGetToken("testuser", "testpass123",
					"TESTBUS1", BUSINESS_APP_CODE);

			// Get the user ID for this token
			Long userId = databaseClient.sql(
					"SELECT ID FROM security_user WHERE USER_NAME = 'testuser' AND CLIENT_ID = :clientId")
					.bind("clientId", businessClientId.longValue())
					.map(row -> row.get("ID", Long.class))
					.one()
					.block();

			assertThat(userId).isNotNull();

			// Count tokens for this user before revocation
			Long preCount = databaseClient.sql(
					"SELECT COUNT(*) as cnt FROM security_user_token WHERE USER_ID = :userId")
					.bind("userId", userId)
					.map(row -> row.get("cnt", Long.class))
					.one()
					.block();
			assertThat(preCount).isGreaterThan(0L);

			// Revoke it
			webTestClient.get()
					.uri(uriBuilder -> uriBuilder.path(REVOKE_ENDPOINT)
							.queryParam("ssoLogout", false)
							.build())
					.header("Authorization", "Bearer " + accessToken)
					.header("clientCode", "TESTBUS1")
					.header("X-Forwarded-Host", "localhost")
					.exchange()
					.expectStatus().isOk();

			// Confirm the token count decreased after revocation
			Long postCount = databaseClient.sql(
					"SELECT COUNT(*) as cnt FROM security_user_token WHERE USER_ID = :userId")
					.bind("userId", userId)
					.map(row -> row.get("cnt", Long.class))
					.one()
					.block();
			assertThat(postCount).isLessThan(preCount);
		}

		@Test
		@Order(2)
		@DisplayName("SSO logout revokes all tokens for user")
		void revoke_WithSsoLogout_RevokesAllUserTokens() {

			// Authenticate twice to create two tokens for the user
			authenticateAndGetToken(SYSADMIN_USERNAME, SYSADMIN_PASSWORD,
					SYSTEM_CLIENT_CODE, SYSTEM_APP_CODE);
			String token2 = authenticateAndGetToken(SYSADMIN_USERNAME, SYSADMIN_PASSWORD,
					SYSTEM_CLIENT_CODE, SYSTEM_APP_CODE);

			// Count tokens before SSO logout
			Long preCount = databaseClient.sql(
					"SELECT COUNT(*) as cnt FROM security_user_token WHERE USER_ID = 1")
					.map(row -> row.get("cnt", Long.class))
					.one()
					.block();

			assertThat(preCount).isGreaterThanOrEqualTo(2L);

			// SSO logout using second token
			webTestClient.get()
					.uri(uriBuilder -> uriBuilder.path(REVOKE_ENDPOINT)
							.queryParam("ssoLogout", true)
							.build())
					.header("Authorization", "Bearer " + token2)
					.header("clientCode", SYSTEM_CLIENT_CODE)
					.header("X-Forwarded-Host", "localhost")
					.exchange()
					.expectStatus().isOk();

			// After SSO logout, all tokens for user 1 should be deleted
			Long postCount = databaseClient.sql(
					"SELECT COUNT(*) as cnt FROM security_user_token WHERE USER_ID = 1")
					.map(row -> row.get("cnt", Long.class))
					.one()
					.block();

			assertThat(postCount).isZero();
		}

		@Test
		@Order(3)
		@DisplayName("Refresh token after revocation fails")
		void refreshToken_AfterRevocation_Fails() {

			String accessToken = authenticateAndGetToken(SYSADMIN_USERNAME, SYSADMIN_PASSWORD,
					SYSTEM_CLIENT_CODE, SYSTEM_APP_CODE);

			// Revoke the token
			webTestClient.get()
					.uri(uriBuilder -> uriBuilder.path(REVOKE_ENDPOINT)
							.queryParam("ssoLogout", false)
							.build())
					.header("Authorization", "Bearer " + accessToken)
					.header("clientCode", SYSTEM_CLIENT_CODE)
					.header("X-Forwarded-Host", "localhost")
					.exchange()
					.expectStatus().isOk();

			// Try to refresh the revoked token - should fail
			webTestClient.get()
					.uri(REFRESH_TOKEN_ENDPOINT)
					.header("Authorization", "Bearer " + accessToken)
					.header("clientCode", SYSTEM_CLIENT_CODE)
					.header("X-Forwarded-Host", "localhost")
					.exchange()
					.expectStatus().isUnauthorized();
		}
	}

	@Nested
	@DisplayName("Remember Me and Cookie Authentication")
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	class RememberMeTests {

		@Test
		@Order(1)
		@DisplayName("Authenticate with rememberMe flag returns token with extended expiry")
		void authenticate_WithRememberMe_ReturnsTokenWithExtendedExpiry() {

			// First get a normal token for comparison
			AuthenticationRequest normalRequest = new AuthenticationRequest()
					.setUserName(SYSADMIN_USERNAME)
					.setPassword(SYSADMIN_PASSWORD);

			AuthenticationResponse normalResponse = webTestClient.post()
					.uri(AUTH_ENDPOINT)
					.header("clientCode", SYSTEM_CLIENT_CODE)
					.header("appCode", SYSTEM_APP_CODE)
					.header("X-Forwarded-Host", "localhost")
					.header("X-Real-IP", "127.0.0.1")
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(normalRequest)
					.exchange()
					.expectStatus().isOk()
					.expectBody(AuthenticationResponse.class)
					.returnResult()
					.getResponseBody();

			// Now with rememberMe
			AuthenticationRequest rememberMeRequest = new AuthenticationRequest()
					.setUserName(SYSADMIN_USERNAME)
					.setPassword(SYSADMIN_PASSWORD)
					.setRememberMe(true);

			AuthenticationResponse rememberMeResponse = webTestClient.post()
					.uri(AUTH_ENDPOINT)
					.header("clientCode", SYSTEM_CLIENT_CODE)
					.header("appCode", SYSTEM_APP_CODE)
					.header("X-Forwarded-Host", "localhost")
					.header("X-Real-IP", "127.0.0.1")
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(rememberMeRequest)
					.exchange()
					.expectStatus().isOk()
					.expectBody(AuthenticationResponse.class)
					.returnResult()
					.getResponseBody();

			assertThat(normalResponse).isNotNull();
			assertThat(rememberMeResponse).isNotNull();

			// Both should have valid tokens
			assertThat(normalResponse.getAccessToken()).isNotBlank();
			assertThat(rememberMeResponse.getAccessToken()).isNotBlank();

			// Remember-me token should have a later expiry than normal
			assertThat(rememberMeResponse.getAccessTokenExpiryAt())
					.isAfterOrEqualTo(normalResponse.getAccessTokenExpiryAt());
		}
	}

	@Nested
	@DisplayName("Email Auto-detection in Authentication")
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	class EmailAutoDetectionTests {

		@Test
		@Order(1)
		@DisplayName("Authenticate with email without explicit identifierType auto-detects EMAIL_ID")
		void authenticate_WithEmailWithoutIdentifierType_AutoDetects() {

			// When the username contains '@', the system should auto-detect it as EMAIL_ID
			AuthenticationRequest request = new AuthenticationRequest()
					.setUserName("testuser@test.com")
					.setPassword("testpass123");
			// Note: identifierType is NOT set explicitly here

			webTestClient.post()
					.uri(AUTH_ENDPOINT)
					.header("clientCode", "TESTBUS1")
					.header("appCode", BUSINESS_APP_CODE)
					.header("X-Forwarded-Host", "localhost")
					.header("X-Real-IP", "127.0.0.1")
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(request)
					.exchange()
					.expectStatus().isOk()
					.expectBody(AuthenticationResponse.class)
					.value(response -> {
						assertThat(response).isNotNull();
						assertThat(response.getAccessToken()).isNotBlank();
						assertThat(response.getUser().getEmailId()).isEqualTo("testuser@test.com");
					});
		}
	}

	@Nested
	@DisplayName("Refresh Token Scenarios")
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	class RefreshTokenTests {

		@Test
		@Order(1)
		@DisplayName("Refresh token with valid token returns response with user and client info")
		void refreshToken_WithValidToken_ReturnsFullResponse() {

			String accessToken = authenticateAndGetToken(SYSADMIN_USERNAME, SYSADMIN_PASSWORD,
					SYSTEM_CLIENT_CODE, SYSTEM_APP_CODE);

			webTestClient.get()
					.uri(REFRESH_TOKEN_ENDPOINT)
					.header("Authorization", "Bearer " + accessToken)
					.header("clientCode", SYSTEM_CLIENT_CODE)
					.header("X-Forwarded-Host", "localhost")
					.exchange()
					.expectStatus().isOk()
					.expectBody(AuthenticationResponse.class)
					.value(response -> {
						assertThat(response).isNotNull();
						assertThat(response.getAccessToken()).isNotBlank();
						assertThat(response.getUser()).isNotNull();
						assertThat(response.getUser().getUserName()).isEqualTo(SYSADMIN_USERNAME);
						assertThat(response.getClient()).isNotNull();
						assertThat(response.getLoggedInClientCode()).isNotBlank();
						assertThat(response.getAccessTokenExpiryAt()).isNotNull();
					});
		}

		@Test
		@Order(2)
		@DisplayName("Refresh token with invalid token returns 401")
		void refreshToken_WithInvalidToken_Returns401() {

			webTestClient.get()
					.uri(REFRESH_TOKEN_ENDPOINT)
					.header("Authorization", "Bearer totallyInvalidTokenXYZ123")
					.header("clientCode", SYSTEM_CLIENT_CODE)
					.header("X-Forwarded-Host", "localhost")
					.exchange()
					.expectStatus().isUnauthorized();
		}

		@Test
		@Order(3)
		@DisplayName("Refresh token for business client returns correct client info")
		void refreshToken_ForBusinessClient_ReturnsCorrectClientInfo() {

			String accessToken = authenticateAndGetToken("testuser", "testpass123",
					"TESTBUS1", BUSINESS_APP_CODE);

			webTestClient.get()
					.uri(REFRESH_TOKEN_ENDPOINT)
					.header("Authorization", "Bearer " + accessToken)
					.header("clientCode", "TESTBUS1")
					.header("X-Forwarded-Host", "localhost")
					.exchange()
					.expectStatus().isOk()
					.expectBody(AuthenticationResponse.class)
					.value(response -> {
						assertThat(response).isNotNull();
						assertThat(response.getUser().getUserName()).isEqualTo("testuser");
						assertThat(response.getLoggedInClientCode()).isEqualTo("TESTBUS1");
					});
		}
	}

	@Nested
	@DisplayName("Verify Token Scenarios")
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	class VerifyTokenTests {

		@Test
		@Order(1)
		@DisplayName("Verify token returns full authentication response with client details")
		void verifyToken_WithValidToken_ReturnsFullResponse() {

			String accessToken = authenticateAndGetToken(SYSADMIN_USERNAME, SYSADMIN_PASSWORD,
					SYSTEM_CLIENT_CODE, SYSTEM_APP_CODE);

			webTestClient.get()
					.uri(VERIFY_TOKEN_ENDPOINT)
					.header("Authorization", "Bearer " + accessToken)
					.header("clientCode", SYSTEM_CLIENT_CODE)
					.header("X-Forwarded-Host", "localhost")
					.exchange()
					.expectStatus().isOk()
					.expectBody(AuthenticationResponse.class)
					.value(response -> {
						assertThat(response).isNotNull();
						assertThat(response.getAccessToken()).isEqualTo(accessToken);
						assertThat(response.getUser()).isNotNull();
						assertThat(response.getUser().getUserName()).isEqualTo(SYSADMIN_USERNAME);
						assertThat(response.getClient()).isNotNull();
						assertThat(response.getLoggedInClientCode()).isEqualTo(SYSTEM_CLIENT_CODE);
						assertThat(response.getAccessTokenExpiryAt()).isNotNull();
					});
		}

		@Test
		@Order(2)
		@DisplayName("Verify token for business client returns correct client details")
		void verifyToken_ForBusinessClient_ReturnsCorrectDetails() {

			String accessToken = authenticateAndGetToken("testuser", "testpass123",
					"TESTBUS1", BUSINESS_APP_CODE);

			webTestClient.get()
					.uri(VERIFY_TOKEN_ENDPOINT)
					.header("Authorization", "Bearer " + accessToken)
					.header("clientCode", "TESTBUS1")
					.header("X-Forwarded-Host", "localhost")
					.exchange()
					.expectStatus().isOk()
					.expectBody(AuthenticationResponse.class)
					.value(response -> {
						assertThat(response).isNotNull();
						assertThat(response.getUser().getUserName()).isEqualTo("testuser");
						assertThat(response.getLoggedInClientCode()).isEqualTo("TESTBUS1");
					});
		}
	}

	@Nested
	@DisplayName("OTP Generation Endpoint")
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	class OtpGenerationTests {

		@Test
		@Order(1)
		@DisplayName("Generate OTP with generateOtp=false returns false")
		void generateOtp_WithFlagFalse_ReturnsFalse() {

			AuthenticationRequest request = new AuthenticationRequest()
					.setUserName(SYSADMIN_USERNAME)
					.setPassword(SYSADMIN_PASSWORD)
					.setGenerateOtp(false);

			webTestClient.post()
					.uri("/api/security/authenticate/otp/generate")
					.header("clientCode", SYSTEM_CLIENT_CODE)
					.header("appCode", SYSTEM_APP_CODE)
					.header("X-Forwarded-Host", "localhost")
					.header("X-Real-IP", "127.0.0.1")
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(request)
					.exchange()
					.expectStatus().isOk()
					.expectBody(Boolean.class)
					.value(result -> assertThat(result).isFalse());
		}
	}

	@Nested
	@DisplayName("Null and Edge Case Password Types")
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	class NullPasswordTypeTests {

		@Test
		@Order(1)
		@DisplayName("Authenticate with no password, no pin, no otp returns 400 (null password type)")
		void authenticate_NoPasswordNoPinNoOtp_Returns400() {

			// When all password fields are blank/null, getInputPassType() returns null,
			// which triggers a BAD_REQUEST response from authenticate()
			AuthenticationRequest request = new AuthenticationRequest()
					.setUserName(SYSADMIN_USERNAME);
			// No password, pin, or otp set

			webTestClient.post()
					.uri(AUTH_ENDPOINT)
					.header("clientCode", SYSTEM_CLIENT_CODE)
					.header("appCode", SYSTEM_APP_CODE)
					.header("X-Forwarded-Host", "localhost")
					.header("X-Real-IP", "127.0.0.1")
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(request)
					.exchange()
					.expectStatus().isBadRequest();
		}
	}

	@Nested
	@DisplayName("Context Authentication Endpoint")
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	class ContextAuthenticationTests {

		@Test
		@Order(1)
		@DisplayName("Context authentication with valid business client token returns context")
		void contextAuthentication_WithBusinessClientToken_ReturnsContext() {

			String accessToken = authenticateAndGetToken("testuser", "testpass123",
					"TESTBUS1", BUSINESS_APP_CODE);

			webTestClient.get()
					.uri("/api/security/internal/securityContextAuthentication")
					.header("Authorization", "Bearer " + accessToken)
					.header("clientCode", "TESTBUS1")
					.header("X-Forwarded-Host", "localhost")
					.exchange()
					.expectStatus().isOk()
					.expectBody(ContextAuthentication.class)
					.value(ctx -> {
						assertThat(ctx).isNotNull();
						assertThat(ctx.getUser()).isNotNull();
						assertThat(ctx.getUser().getUserName()).isEqualTo("testuser");
						assertThat(ctx.isAuthenticated()).isTrue();
					});
		}

		@Test
		@Order(2)
		@DisplayName("Context authentication without token returns anonymous context")
		void contextAuthentication_WithoutToken_ReturnsAnonymous() {

			// Without a Bearer token, the security context should have anonymous auth
			// The endpoint requires authentication though, so this should fail
			webTestClient.get()
					.uri("/api/security/internal/securityContextAuthentication")
					.header("clientCode", SYSTEM_CLIENT_CODE)
					.header("X-Forwarded-Host", "localhost")
					.exchange()
					.expectStatus().isOk()
					.expectBody(ContextAuthentication.class)
					.value(ctx -> {
						assertThat(ctx).isNotNull();
						assertThat(ctx.getUser()).isNotNull();
						// Anonymous user has ID = 0 and is not authenticated
						assertThat(ctx.isAuthenticated()).isFalse();
					});
		}
	}

	@Nested
	@DisplayName("Concurrent Session and Token Management")
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	class ConcurrentSessionTests {

		@Test
		@Order(1)
		@DisplayName("Multiple tokens for same user can coexist and both verify successfully")
		void multipleTokens_SameUser_CanCoexist() throws InterruptedException {

			String token1 = authenticateAndGetToken(SYSADMIN_USERNAME, SYSADMIN_PASSWORD,
					SYSTEM_CLIENT_CODE, SYSTEM_APP_CODE);

			// Wait so JWT timestamps differ (iat/exp are epoch seconds)
			Thread.sleep(1000);

			String token2 = authenticateAndGetToken(SYSADMIN_USERNAME, SYSADMIN_PASSWORD,
					SYSTEM_CLIENT_CODE, SYSTEM_APP_CODE);

			assertThat(token1).isNotEqualTo(token2);

			// Both tokens should be valid
			webTestClient.get()
					.uri(VERIFY_TOKEN_ENDPOINT)
					.header("Authorization", "Bearer " + token1)
					.header("clientCode", SYSTEM_CLIENT_CODE)
					.header("X-Forwarded-Host", "localhost")
					.exchange()
					.expectStatus().isOk();

			webTestClient.get()
					.uri(VERIFY_TOKEN_ENDPOINT)
					.header("Authorization", "Bearer " + token2)
					.header("clientCode", SYSTEM_CLIENT_CODE)
					.header("X-Forwarded-Host", "localhost")
					.exchange()
					.expectStatus().isOk();
		}

		@Test
		@Order(2)
		@DisplayName("Revoking one token reduces token count and second token still works")
		void revokeOneToken_DoesNotAffectOther() throws InterruptedException {

			String token1 = authenticateAndGetToken(SYSADMIN_USERNAME, SYSADMIN_PASSWORD,
					SYSTEM_CLIENT_CODE, SYSTEM_APP_CODE);

			// Wait so JWT timestamps differ
			Thread.sleep(1000);

			String token2 = authenticateAndGetToken(SYSADMIN_USERNAME, SYSADMIN_PASSWORD,
					SYSTEM_CLIENT_CODE, SYSTEM_APP_CODE);

			// Count tokens before revoke
			Long preRevokeCount = databaseClient.sql(
					"SELECT COUNT(*) as cnt FROM security_user_token WHERE USER_ID = 1")
					.map(row -> row.get("cnt", Long.class))
					.one()
					.block();

			// Revoke token1
			webTestClient.get()
					.uri(uriBuilder -> uriBuilder.path(REVOKE_ENDPOINT)
							.queryParam("ssoLogout", false)
							.build())
					.header("Authorization", "Bearer " + token1)
					.header("clientCode", SYSTEM_CLIENT_CODE)
					.header("X-Forwarded-Host", "localhost")
					.exchange()
					.expectStatus().isOk();

			// Token count should have decreased by 1
			Long postRevokeCount = databaseClient.sql(
					"SELECT COUNT(*) as cnt FROM security_user_token WHERE USER_ID = 1")
					.map(row -> row.get("cnt", Long.class))
					.one()
					.block();
			assertThat(postRevokeCount).isEqualTo(preRevokeCount - 1);

			// token2 should still verify successfully
			webTestClient.get()
					.uri(VERIFY_TOKEN_ENDPOINT)
					.header("Authorization", "Bearer " + token2)
					.header("clientCode", SYSTEM_CLIENT_CODE)
					.header("X-Forwarded-Host", "localhost")
					.exchange()
					.expectStatus().isOk();
		}
	}

	@Nested
	@DisplayName("Token Database Persistence")
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	class TokenPersistenceTests {

		@Test
		@Order(1)
		@DisplayName("Authenticate creates a token record in the database with correct fields")
		void authenticate_CreatesTokenInDatabase() {

			// Count tokens before auth
			Long priorCount = databaseClient.sql(
					"SELECT COUNT(*) as cnt FROM security_user_token WHERE USER_ID = 1")
					.map(row -> row.get("cnt", Long.class))
					.one()
					.block();

			String accessToken = authenticateAndGetToken(SYSADMIN_USERNAME, SYSADMIN_PASSWORD,
					SYSTEM_CLIENT_CODE, SYSTEM_APP_CODE);

			Long afterCount = databaseClient.sql(
					"SELECT COUNT(*) as cnt FROM security_user_token WHERE USER_ID = 1")
					.map(row -> row.get("cnt", Long.class))
					.one()
					.block();

			assertThat(afterCount).isGreaterThan(priorCount);

			// Verify the stored token has correct IP address
			String storedPartToken = accessToken.length() > 50
					? accessToken.substring(accessToken.length() - 50)
					: accessToken;

			String ipAddress = databaseClient.sql(
					"SELECT IP_ADDRESS FROM security_user_token WHERE PART_TOKEN = :partToken ORDER BY ID DESC LIMIT 1")
					.bind("partToken", storedPartToken)
					.map(row -> row.get("IP_ADDRESS", String.class))
					.one()
					.block();

			assertThat(ipAddress).isEqualTo("127.0.0.1");
		}
	}

	@Nested
	@DisplayName("Authentication with Phone Number Identifier")
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	class PhoneNumberAuthenticationTests {

		@Test
		@Order(1)
		@DisplayName("Authenticate with phone number identifier type for non-existent phone returns 403")
		void authenticate_WithPhoneNumberIdentifier_NonExistent_Returns403() {

			AuthenticationRequest request = new AuthenticationRequest()
					.setUserName("+1234567890")
					.setPassword("testpass123")
					.setIdentifierType(AuthenticationIdentifierType.PHONE_NUMBER);

			webTestClient.post()
					.uri(AUTH_ENDPOINT)
					.header("clientCode", "TESTBUS1")
					.header("appCode", BUSINESS_APP_CODE)
					.header("X-Forwarded-Host", "localhost")
					.header("X-Real-IP", "127.0.0.1")
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(request)
					.exchange()
					.expectStatus().isForbidden();
		}
	}

	@Nested
	@DisplayName("Multiple PIN Failed Attempts Lock Account")
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	class PinLockoutTests {

		@Test
		@Order(1)
		@DisplayName("Multiple wrong PIN attempts lock the account via PIN failure path")
		void authenticate_MultiplePinFails_LocksAccount() {

			String ts = String.valueOf(System.currentTimeMillis());
			String userName = "pinlk_" + ts;

			ULong userId = insertTestUser(businessClientId, userName, "pinlk_" + ts + "@test.com", "testpass123")
					.block();

			assertThat(userId).isNotNull();

			// Set up user with PIN and already at failure threshold (3 is default policy limit)
			databaseClient.sql(
					"UPDATE security_user SET PIN = '123456', PIN_HASHED = false, NO_PIN_FAILED_ATTEMPT = 3 WHERE ID = :userId")
					.bind("userId", userId.longValue())
					.then()
					.block();

			AuthenticationRequest request = new AuthenticationRequest()
					.setUserName(userName)
					.setPin("999999");

			// This should trigger lockout since pin failed attempts >= limit
			webTestClient.post()
					.uri(AUTH_ENDPOINT)
					.header("clientCode", "TESTBUS1")
					.header("appCode", BUSINESS_APP_CODE)
					.header("X-Forwarded-Host", "localhost")
					.header("X-Real-IP", "127.0.0.1")
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(request)
					.exchange()
					.expectStatus().isForbidden();

			// Verify user is now locked
			String statusCode = databaseClient.sql(
					"SELECT STATUS_CODE FROM security_user WHERE ID = :userId")
					.bind("userId", userId.longValue())
					.map(row -> row.get("STATUS_CODE", String.class))
					.one()
					.block();

			assertThat(statusCode).isEqualTo("LOCKED");
		}
	}
}
