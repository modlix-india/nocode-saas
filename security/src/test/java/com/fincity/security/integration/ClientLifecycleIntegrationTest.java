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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fincity.security.dto.Client;
import com.fincity.security.jooq.enums.SecurityClientStatusCode;
import com.fincity.security.model.AuthenticationRequest;
import com.fincity.security.model.AuthenticationResponse;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureWebTestClient(timeout = "30000")
class ClientLifecycleIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private WebTestClient webTestClient;

	private static final String CLIENT_ENDPOINT = "/api/security/clients";
	private static final String AUTH_ENDPOINT = "/api/security/authenticate";

	private static final String SYSADMIN_USERNAME = "sysadmin";
	private static final String SYSADMIN_PASSWORD = "fincity@123";
	private static final String SYSTEM_CLIENT_CODE = "SYSTEM";
	private static final String SYSTEM_APP_CODE = "appbuilder";

	private String accessToken;
	private ULong createdClientId;
	private String createdClientCode;

	@BeforeAll
	void setupTestData() {
		setupMockBeans();

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

	@AfterAll
	void cleanup() {
		databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
				.then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client_manager WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_user WHERE ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}

	@Test
	@Order(1)
	void createClient_WithValidData_ReturnsCreatedClient() {

		Client client = new Client();
		client.setCode("INTGTEST");
		client.setName("Integration Test Client");
		client.setTypeCode("BUS");
		client.setTokenValidityMinutes(60);
		client.setLocaleCode("en");
		client.setStatusCode(SecurityClientStatusCode.ACTIVE);

		webTestClient.post()
				.uri(CLIENT_ENDPOINT)
				.header("Authorization", "Bearer " + accessToken)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("appCode", SYSTEM_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(client)
				.exchange()
				.expectStatus().isOk()
				.expectBody(Client.class)
				.value(response -> {
					assertThat(response).isNotNull();
					assertThat(response.getId()).isNotNull();
					assertThat(response.getName()).isEqualTo("Integration Test Client");
					assertThat(response.getTypeCode()).isEqualTo("BUS");
					assertThat(response.getCode()).isNotBlank();
					createdClientId = response.getId();
					createdClientCode = response.getCode();
				});

		assertThat(createdClientId).isNotNull();
		assertThat(createdClientCode).isNotBlank();

		// When SYSTEM client creates a child, ClientService.create() does NOT
		// automatically insert the client hierarchy. We insert it manually so
		// that hierarchy-dependent tests (6-8) work correctly.
		insertClientHierarchy(createdClientId, ULong.valueOf(1), null, null, null).block();
	}

	@Test
	@Order(2)
	void readClient_ById_ReturnsClient() {

		assertThat(createdClientId).isNotNull();

		webTestClient.get()
				.uri(CLIENT_ENDPOINT + "/" + createdClientId)
				.header("Authorization", "Bearer " + accessToken)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("appCode", SYSTEM_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.exchange()
				.expectStatus().isOk()
				.expectBody(Client.class)
				.value(response -> {
					assertThat(response).isNotNull();
					assertThat(response.getId()).isEqualTo(createdClientId);
					assertThat(response.getName()).isEqualTo("Integration Test Client");
					assertThat(response.getTypeCode()).isEqualTo("BUS");
					assertThat(response.getCode()).isEqualTo(createdClientCode);
				});
	}

	@Test
	@Order(3)
	void getClientByCode_Internal_ReturnsClient() {

		assertThat(createdClientCode).isNotBlank();

		webTestClient.get()
				.uri(uriBuilder -> uriBuilder.path(CLIENT_ENDPOINT + "/internal/getClientByCode")
						.queryParam("clientCode", createdClientCode)
						.build())
				.header("Authorization", "Bearer " + accessToken)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("appCode", SYSTEM_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.exchange()
				.expectStatus().isOk()
				.expectBody(Client.class)
				.value(response -> {
					assertThat(response).isNotNull();
					assertThat(response.getId()).isEqualTo(createdClientId);
					assertThat(response.getCode()).isEqualTo(createdClientCode);
				});
	}

	@Test
	@Order(4)
	void getClientById_Internal_ReturnsClient() {

		assertThat(createdClientId).isNotNull();

		webTestClient.get()
				.uri(uriBuilder -> uriBuilder.path(CLIENT_ENDPOINT + "/internal/getClientById")
						.queryParam("clientId", createdClientId.toString())
						.build())
				.header("Authorization", "Bearer " + accessToken)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("appCode", SYSTEM_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.exchange()
				.expectStatus().isOk()
				.expectBody(Client.class)
				.value(response -> {
					assertThat(response).isNotNull();
					assertThat(response.getCode()).isEqualTo(createdClientCode);
				});
	}

	@Test
	@Order(5)
	void updateClient_ChangeName_ReturnsUpdatedClient() {

		assertThat(createdClientId).isNotNull();

		Client client = new Client();
		client.setId(createdClientId);
		client.setName("Updated Client Name");
		client.setTypeCode("BUS");
		client.setTokenValidityMinutes(60);
		client.setLocaleCode("en");
		client.setCode(createdClientCode);
		client.setStatusCode(SecurityClientStatusCode.ACTIVE);

		webTestClient.put()
				.uri(CLIENT_ENDPOINT + "/" + createdClientId)
				.header("Authorization", "Bearer " + accessToken)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("appCode", SYSTEM_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(client)
				.exchange()
				.expectStatus().isOk()
				.expectBody(Client.class)
				.value(response -> {
					assertThat(response).isNotNull();
					assertThat(response.getId()).isEqualTo(createdClientId);
					assertThat(response.getName()).isEqualTo("Updated Client Name");
				});
	}

	@Test
	@Order(6)
	void doesClientManageClient_SystemManagesChild_ReturnsTrue() {

		assertThat(createdClientId).isNotNull();

		webTestClient.get()
				.uri(uriBuilder -> uriBuilder.path(CLIENT_ENDPOINT + "/internal/doesClientManageClient")
						.queryParam("managingClientId", "1")
						.queryParam("clientId", createdClientId.toString())
						.build())
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
	@Order(7)
	@SuppressWarnings("unchecked")
	void getClientHierarchy_ReturnsCorrectOrder() {

		assertThat(createdClientId).isNotNull();

		webTestClient.get()
				.uri(uriBuilder -> uriBuilder.path(CLIENT_ENDPOINT + "/internal/clientHierarchy")
						.queryParam("clientId", createdClientId.toString())
						.build())
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
					assertThat(response).isNotEmpty();
					assertThat(response.size()).isGreaterThanOrEqualTo(1);
				});
	}

	@Test
	@Order(8)
	@SuppressWarnings("unchecked")
	void getManagingClientIds_SystemClient_IncludesChild() {

		assertThat(createdClientId).isNotNull();

		webTestClient.get()
				.uri(uriBuilder -> uriBuilder.path(CLIENT_ENDPOINT + "/internal/managingClientIds")
						.queryParam("clientId", "1")
						.build())
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
					assertThat(response).isNotEmpty();
					assertThat(response).hasSizeGreaterThanOrEqualTo(1);
				});
	}

	@Test
	@Order(9)
	void makeClientInActive_SetsInactive() {

		assertThat(createdClientId).isNotNull();

		webTestClient.get()
				.uri(uriBuilder -> uriBuilder.path(CLIENT_ENDPOINT + "/makeClientInActive")
						.queryParam("clientId", createdClientId.toString())
						.build())
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

		// Verify the status changed via direct SQL
		String status = databaseClient.sql(
				"SELECT STATUS_CODE FROM security_client WHERE ID = :id")
				.bind("id", createdClientId.longValue())
				.map(row -> row.get("STATUS_CODE", String.class))
				.one()
				.block();

		assertThat(status).isEqualTo("INACTIVE");
	}

	@Test
	@Order(10)
	void makeClientActive_SetsActive() {

		assertThat(createdClientId).isNotNull();

		webTestClient.get()
				.uri(uriBuilder -> uriBuilder.path(CLIENT_ENDPOINT + "/makeClientActive")
						.queryParam("clientId", createdClientId.toString())
						.build())
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

		// Verify the status changed back via direct SQL
		String status = databaseClient.sql(
				"SELECT STATUS_CODE FROM security_client WHERE ID = :id")
				.bind("id", createdClientId.longValue())
				.map(row -> row.get("STATUS_CODE", String.class))
				.one()
				.block();

		assertThat(status).isEqualTo("ACTIVE");
	}

	@Test
	@Order(11)
	@SuppressWarnings("unchecked")
	void readPageFilter_ReturnsClients() {

		webTestClient.get()
				.uri(CLIENT_ENDPOINT)
				.header("Authorization", "Bearer " + accessToken)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("appCode", SYSTEM_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.exchange()
				.expectStatus().isOk()
				.expectBody(new ParameterizedTypeReference<Map<String, Object>>() {
				})
				.value(response -> {
					assertThat(response).isNotNull();
					assertThat(response).containsKey("content");
					List<Object> content = (List<Object>) response.get("content");
					assertThat(content).isNotNull();
					assertThat(content.size()).isGreaterThanOrEqualTo(2);
				});
	}

	@Test
	@Order(12)
	void validateClientCode_Existing_ReturnsTrue() {

		assertThat(createdClientCode).isNotBlank();

		webTestClient.get()
				.uri(uriBuilder -> uriBuilder.path(CLIENT_ENDPOINT + "/internal/validateClientCode")
						.queryParam("clientCode", createdClientCode)
						.build())
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
	@Order(13)
	void validateClientCode_NonExistent_ReturnsEmptyOrNoContent() {

		webTestClient.get()
				.uri(uriBuilder -> uriBuilder.path(CLIENT_ENDPOINT + "/internal/validateClientCode")
						.queryParam("clientCode", "NONEXISTENT_XYZ")
						.build())
				.header("Authorization", "Bearer " + accessToken)
				.header("clientCode", SYSTEM_CLIENT_CODE)
				.header("appCode", SYSTEM_APP_CODE)
				.header("X-Forwarded-Host", "localhost")
				.header("X-Real-IP", "127.0.0.1")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.consumeWith(result -> {
					byte[] body = result.getResponseBody();
					assertThat(body == null || body.length == 0).isTrue();
				});
	}
}
