package com.fincity.security.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
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
}
