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

import com.fincity.security.model.AuthenticationRequest;
import com.fincity.security.model.AuthenticationResponse;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureWebTestClient(timeout = "30000")
class AuthenticationIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private WebTestClient webTestClient;

	private static final String AUTH_ENDPOINT = "/api/security/authenticate";
	private static final String VERIFY_TOKEN_ENDPOINT = "/api/security/verifyToken";
	private static final String REFRESH_TOKEN_ENDPOINT = "/api/security/refreshToken";
	private static final String REVOKE_ENDPOINT = "/api/security/revoke";

	private static final String SYSADMIN_USERNAME = "sysadmin";
	private static final String SYSADMIN_PASSWORD = "fincity@123";
	private static final String SYSTEM_CLIENT_CODE = "SYSTEM";

	private ULong businessClientId;

	@BeforeAll
	void setupTestData() {
		// Insert a business client and user for tests that need a non-system client.
		// The SYSTEM client (ID=1) and sysadmin user (ID=1) come from Flyway V1.
		businessClientId = insertTestClient("TESTBUS1", "Test Business Client", "BUS")
				.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
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
				.exchange()
				.expectStatus().isOk();
	}

	@Test
	@Order(6)
	void verifyToken_WithNoToken_ReturnsForbidden() {

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
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.exchange()
				.expectStatus().isOk()
				.expectBody(AuthenticationResponse.class)
				.returnResult()
				.getResponseBody();

		assertThat(authResponse).isNotNull();
		String accessToken = authResponse.getAccessToken();

		// Revoke the token
		webTestClient.get()
				.uri(uriBuilder -> uriBuilder.path(REVOKE_ENDPOINT)
						.queryParam("ssoLogout", false)
						.build())
				.header("Authorization", "Bearer " + accessToken)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.exchange()
				.expectStatus().isOk();

		// Verify the revoked token no longer works for verifyToken.
		// After revocation, the token is deleted from the DB and evicted from cache.
		// The verifyToken endpoint should return 401 since the token exists in JWT
		// form but is no longer in the token store.
		webTestClient.get()
				.uri(VERIFY_TOKEN_ENDPOINT)
				.header("Authorization", "Bearer " + accessToken)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	@Order(10)
	void multipleAuthentications_GenerateUniqueTokens() {

		AuthenticationRequest request = new AuthenticationRequest()
				.setUserName(SYSADMIN_USERNAME)
				.setPassword(SYSADMIN_PASSWORD);

		// First authentication
		AuthenticationResponse firstResponse = webTestClient.post()
				.uri(AUTH_ENDPOINT)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.exchange()
				.expectStatus().isOk()
				.expectBody(AuthenticationResponse.class)
				.returnResult()
				.getResponseBody();

		// Second authentication
		AuthenticationResponse secondResponse = webTestClient.post()
				.uri(AUTH_ENDPOINT)
				.header("clientCode", SYSTEM_CLIENT_CODE)
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
}
