package com.fincity.security.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterAll;
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

import com.fincity.security.dto.Profile;
import com.fincity.security.dto.RoleV2;
import com.fincity.security.model.AuthenticationRequest;
import com.fincity.security.model.AuthenticationResponse;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureWebTestClient(timeout = "30000")
class RoleProfileIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private WebTestClient webTestClient;

	private static final String ROLE_ENDPOINT = "/api/security/rolev2";
	private static final String PROFILE_ENDPOINT = "/api/security/app/profiles";
	private static final String AUTH_ENDPOINT = "/api/security/authenticate";

	private static final String SYSADMIN_USERNAME = "sysadmin";
	private static final String SYSADMIN_PASSWORD = "fincity@123";
	private static final String SYSTEM_CLIENT_CODE = "SYSTEM";
	private static final String SYSTEM_APP_CODE = "appbuilder";

	private String accessToken;
	private ULong testAppId;
	private ULong roleId;
	private ULong secondRoleId;
	private ULong profileId;

	@BeforeAll
	void setupTestData() {
		setupMockBeans();

		// Create a test app for role and profile scoping
		testAppId = insertTestApp(ULong.valueOf(1), "testroleapp", "Test Role App")
				.block();
		assertThat(testAppId).isNotNull();

		// Authenticate as sysadmin to get an access token for all subsequent requests
		AuthenticationRequest request = new AuthenticationRequest()
				.setUserName(SYSADMIN_USERNAME)
				.setPassword(SYSADMIN_PASSWORD);

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
		accessToken = authResponse.getAccessToken();
		assertThat(accessToken).isNotBlank();
	}

	@BeforeEach
	void setUp() {
		setupMockBeans();
	}

	@Test
	@Order(1)
	void setup_VerifyTestAppExists() {
		assertThat(testAppId).isNotNull();
		assertThat(accessToken).isNotBlank();
	}

	@Test
	@Order(2)
	void createRole_WithValidData_ReturnsCreatedRole() {

		RoleV2 role = new RoleV2();
		role.setName("Test Role");
		role.setShortName("TESTROLE");
		role.setDescription("A test role");
		role.setAppId(testAppId);
		role.setClientId(ULong.valueOf(1));

		webTestClient.post()
				.uri(ROLE_ENDPOINT)
				.header("Authorization", "Bearer " + accessToken)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("appCode", SYSTEM_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(role)
				.exchange()
				.expectStatus().isOk()
				.expectBody(RoleV2.class)
				.value(response -> {
					assertThat(response).isNotNull();
					assertThat(response.getId()).isNotNull();
					assertThat(response.getName()).isEqualTo("Test Role");
					assertThat(response.getShortName()).isEqualTo("TESTROLE");
					assertThat(response.getDescription()).isEqualTo("A test role");
					roleId = response.getId();
				});

		assertThat(roleId).isNotNull();
	}

	@Test
	@Order(3)
	void readRole_ById_ReturnsRole() {

		assertThat(roleId).isNotNull();

		webTestClient.get()
				.uri(ROLE_ENDPOINT + "/" + roleId)
				.header("Authorization", "Bearer " + accessToken)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("appCode", SYSTEM_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.exchange()
				.expectStatus().isOk()
				.expectBody(RoleV2.class)
				.value(response -> {
					assertThat(response).isNotNull();
					assertThat(response.getId()).isEqualTo(roleId);
					assertThat(response.getName()).isEqualTo("Test Role");
					assertThat(response.getShortName()).isEqualTo("TESTROLE");
				});
	}

	@Test
	@Order(4)
	void updateRole_ChangeName_ReturnsUpdatedRole() {

		assertThat(roleId).isNotNull();

		RoleV2 role = new RoleV2();
		role.setId(roleId);
		role.setName("Updated Test Role");
		role.setShortName("TESTROLE");
		role.setDescription("An updated test role");
		role.setAppId(testAppId);
		role.setClientId(ULong.valueOf(1));

		webTestClient.put()
				.uri(ROLE_ENDPOINT + "/" + roleId)
				.header("Authorization", "Bearer " + accessToken)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("appCode", SYSTEM_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(role)
				.exchange()
				.expectStatus().isOk()
				.expectBody(RoleV2.class)
				.value(response -> {
					assertThat(response).isNotNull();
					assertThat(response.getId()).isEqualTo(roleId);
					assertThat(response.getName()).isEqualTo("Updated Test Role");
					assertThat(response.getDescription()).isEqualTo("An updated test role");
				});
	}

	@Test
	@Order(5)
	void createSecondRole_ForProfileTest() {

		RoleV2 role = new RoleV2();
		role.setName("Profile Role");
		role.setShortName("PROFROLE");
		role.setDescription("A role for profile testing");
		role.setAppId(testAppId);
		role.setClientId(ULong.valueOf(1));

		webTestClient.post()
				.uri(ROLE_ENDPOINT)
				.header("Authorization", "Bearer " + accessToken)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("appCode", SYSTEM_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(role)
				.exchange()
				.expectStatus().isOk()
				.expectBody(RoleV2.class)
				.value(response -> {
					assertThat(response).isNotNull();
					assertThat(response.getId()).isNotNull();
					assertThat(response.getName()).isEqualTo("Profile Role");
					assertThat(response.getShortName()).isEqualTo("PROFROLE");
					secondRoleId = response.getId();
				});

		assertThat(secondRoleId).isNotNull();
	}

	@Test
	@Order(6)
	void createProfile_WithRole_ReturnsCreatedProfile() {

		assertThat(roleId).isNotNull();
		assertThat(testAppId).isNotNull();

		// The Profile entity uses an arrangement map to define roles.
		// Each entry in the arrangement map represents a role assignment with a roleId.
		Profile profile = new Profile();
		profile.setName("Test Profile");
		profile.setTitle("Test Profile Title");
		profile.setDescription("A test profile");
		profile.setAppId(testAppId);
		profile.setClientId(ULong.valueOf(1));
		profile.setArrangement(Map.of(
				"role1", Map.of("roleId", roleId.toString(), "assignable", true)));

		webTestClient.post()
				.uri(PROFILE_ENDPOINT)
				.header("Authorization", "Bearer " + accessToken)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("appCode", SYSTEM_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(profile)
				.exchange()
				.expectStatus().isOk()
				.expectBody(Profile.class)
				.value(response -> {
					assertThat(response).isNotNull();
					assertThat(response.getId()).isNotNull();
					assertThat(response.getName()).isEqualTo("Test Profile");
					assertThat(response.getTitle()).isEqualTo("Test Profile Title");
					assertThat(response.getAppId()).isEqualTo(testAppId);
					profileId = response.getId();
				});

		assertThat(profileId).isNotNull();
	}

	@Test
	@Order(7)
	void readProfile_ById_ReturnsProfile() {

		assertThat(profileId).isNotNull();

		webTestClient.get()
				.uri(PROFILE_ENDPOINT + "/" + profileId)
				.header("Authorization", "Bearer " + accessToken)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("appCode", SYSTEM_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.exchange()
				.expectStatus().isOk()
				.expectBody(Profile.class)
				.value(response -> {
					assertThat(response).isNotNull();
					assertThat(response.getId()).isEqualTo(profileId);
					assertThat(response.getName()).isEqualTo("Test Profile");
					assertThat(response.getTitle()).isEqualTo("Test Profile Title");
				});
	}

	@Test
	@Order(8)
	void deleteProfile_ReturnsTrue() {

		assertThat(profileId).isNotNull();

		webTestClient.delete()
				.uri(PROFILE_ENDPOINT + "/" + profileId)
				.header("Authorization", "Bearer " + accessToken)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("appCode", SYSTEM_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.exchange()
				.expectStatus().isOk()
				.expectBody(Boolean.class)
				.value(response -> {
					assertThat(response).isTrue();
				});
	}

	@Test
	@Order(9)
	void deleteRole_ReturnsCount() {

		assertThat(roleId).isNotNull();

		webTestClient.delete()
				.uri(ROLE_ENDPOINT + "/" + roleId)
				.header("Authorization", "Bearer " + accessToken)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("appCode", SYSTEM_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.exchange()
				.expectStatus().isNoContent()
				.expectBody(Integer.class)
				.value(response -> {
					assertThat(response).isNotNull();
					assertThat(response).isGreaterThan(0);
				});
	}

	@Test
	@Order(10)
	void deleteSecondRole_Cleanup() {

		assertThat(secondRoleId).isNotNull();

		webTestClient.delete()
				.uri(ROLE_ENDPOINT + "/" + secondRoleId)
				.header("Authorization", "Bearer " + accessToken)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("appCode", SYSTEM_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.exchange()
				.expectStatus().isNoContent()
				.expectBody(Integer.class)
				.value(response -> {
					assertThat(response).isNotNull();
					assertThat(response).isGreaterThan(0);
				});
	}

	@Test
	@Order(11)
	void readRole_Deleted_ReturnsNotFound() {

		assertThat(roleId).isNotNull();

		webTestClient.get()
				.uri(ROLE_ENDPOINT + "/" + roleId)
				.header("Authorization", "Bearer " + accessToken)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("appCode", SYSTEM_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.exchange()
				.expectStatus().isNotFound();
	}

	@Test
	@Order(12)
	@SuppressWarnings("unchecked")
	void getAssignableRoles_ForApp_ReturnsList() {

		webTestClient.get()
				.uri(ROLE_ENDPOINT + "/assignable/testroleapp")
				.header("Authorization", "Bearer " + accessToken)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("appCode", SYSTEM_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.exchange()
				.expectStatus().isOk()
				.expectBody(List.class)
				.value(response -> {
					assertThat(response).isNotNull();
					// After deletion, the list may be empty but should still be a valid list
					assertThat(response).isInstanceOf(List.class);
				});
	}

	@AfterAll
	void cleanup() {
		databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
				.then(databaseClient.sql("DELETE FROM security_profile_role WHERE ROLE_ID IN (SELECT ID FROM security_v2_role WHERE CLIENT_ID > 1)").then())
				.then(databaseClient.sql("DELETE FROM security_v2_role_role WHERE ROLE_ID IN (SELECT ID FROM security_v2_role WHERE CLIENT_ID > 1) OR SUB_ROLE_ID IN (SELECT ID FROM security_v2_role WHERE CLIENT_ID > 1)").then())
				.then(databaseClient.sql("DELETE FROM security_v2_role WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_profile WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_app WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}
}
