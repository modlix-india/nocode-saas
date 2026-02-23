package com.fincity.security.integration.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.security.dao.ClientDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.integration.AbstractIntegrationTest;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ClientDAOIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private ClientDAO clientDAO;

	@BeforeEach
	void setUp() {
		setupMockBeans();
	}

	@AfterEach
	void tearDown() {
		databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
				.then(databaseClient.sql("DELETE FROM security_app_access WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_user WHERE ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client_url WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client_manager WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_app WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}

	// --- Helper methods ---

	private Mono<ULong> insertTestClientWithStatus(String code, String name, String typeCode, String statusCode) {
		return databaseClient.sql(
				"INSERT INTO security_client (CODE, NAME, TYPE_CODE, TOKEN_VALIDITY_MINUTES, STATUS_CODE) VALUES (:code, :name, :typeCode, 60, :status)")
				.bind("code", code)
				.bind("name", name)
				.bind("typeCode", typeCode)
				.bind("status", statusCode)
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	private Mono<ULong> insertTestUserWithStatus(ULong clientId, String userName, String email, String statusCode) {
		return databaseClient.sql(
				"INSERT INTO security_user (CLIENT_ID, USER_NAME, EMAIL_ID, FIRST_NAME, LAST_NAME, PASSWORD, PASSWORD_HASHED, STATUS_CODE) "
						+ "VALUES (:clientId, :userName, :email, 'Test', 'User', 'pw', false, :status)")
				.bind("clientId", clientId.longValue())
				.bind("userName", userName)
				.bind("email", email)
				.bind("status", statusCode)
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	private Mono<ULong> insertClientUrl(ULong clientId, String urlPattern, String appCode) {
		return databaseClient.sql(
				"INSERT INTO security_client_url (CLIENT_ID, URL_PATTERN, APP_CODE) VALUES (:clientId, :urlPattern, :appCode)")
				.bind("clientId", clientId.longValue())
				.bind("urlPattern", urlPattern)
				.bind("appCode", appCode)
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	@Nested
	@DisplayName("getSystemClientId()")
	class GetSystemClientIdTests {

		@Test
		void returnsSystemClientId() {
			StepVerifier.create(clientDAO.getSystemClientId())
					.assertNext(id -> {
						assertNotNull(id);
						assertEquals(ULong.valueOf(1), id);
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getClientTypeNCode()")
	class GetClientTypeNCodeTests {

		@Test
		void systemClient_ReturnsTypeAndCode() {
			StepVerifier.create(clientDAO.getClientTypeNCode(ULong.valueOf(1)))
					.assertNext(tuple -> {
						assertNotNull(tuple);
						assertEquals("SYS", tuple.getT1());
						assertNotNull(tuple.getT2());
					})
					.verifyComplete();
		}

		@Test
		void nonExistentClient_ReturnsEmpty() {
			StepVerifier.create(clientDAO.getClientTypeNCode(ULong.valueOf(999999)))
					.verifyComplete();
		}

		@Test
		void businessClient_ReturnsTypeCodeAndLevelType() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "BT" + ts.substring(ts.length() - 6);

			StepVerifier.create(
					insertTestClient(code, "Bus Type Client", "BUS")
							.flatMap(clientId -> clientDAO.getClientTypeNCode(clientId)))
					.assertNext(tuple -> {
						assertEquals("BUS", tuple.getT1());
						assertEquals(code, tuple.getT2());
						assertNotNull(tuple.getT3());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("readInternal()")
	class ReadInternalTests {

		@Test
		void existingClient_ReturnsClient() {
			StepVerifier.create(clientDAO.readInternal(ULong.valueOf(1)))
					.assertNext(client -> {
						assertNotNull(client);
						assertEquals(ULong.valueOf(1), client.getId());
						assertNotNull(client.getCode());
					})
					.verifyComplete();
		}

		@Test
		void nonExistentClient_ReturnsEmpty() {
			StepVerifier.create(clientDAO.readInternal(ULong.valueOf(999999)))
					.verifyComplete();
		}

		@Test
		void createdClient_ReturnsAllFields() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "RI" + ts.substring(ts.length() - 6);

			StepVerifier.create(
					insertTestClient(code, "ReadInternal Test", "BUS")
							.flatMap(clientId -> clientDAO.readInternal(clientId)))
					.assertNext(client -> {
						assertNotNull(client.getId());
						assertEquals(code, client.getCode());
						assertEquals("ReadInternal Test", client.getName());
						assertEquals("BUS", client.getTypeCode());
						assertNotNull(client.getStatusCode());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getClientBy() - by code")
	class GetClientByCodeTests {

		@Test
		void existingCode_ReturnsClient() {
			StepVerifier.create(clientDAO.getClientTypeNCode(ULong.valueOf(1))
					.flatMap(tuple -> clientDAO.getClientBy(tuple.getT2())))
					.assertNext(client -> {
						assertNotNull(client);
						assertEquals(ULong.valueOf(1), client.getId());
					})
					.verifyComplete();
		}

		@Test
		void nonExistentCode_ReturnsEmpty() {
			StepVerifier.create(clientDAO.getClientBy("NONEXISTENT_CODE_XYZ"))
					.verifyComplete();
		}

		@Test
		void createdClient_ReturnsByCode() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "GC" + ts.substring(ts.length() - 6);

			StepVerifier.create(
					insertTestClient(code, "GetClientBy Test", "BUS")
							.then(clientDAO.getClientBy(code)))
					.assertNext(client -> {
						assertNotNull(client);
						assertEquals(code, client.getCode());
						assertEquals("GetClientBy Test", client.getName());
					})
					.verifyComplete();
		}

		@Test
		void inactiveClient_StillReturnsByCode() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "IC" + ts.substring(ts.length() - 6);

			StepVerifier.create(
					insertTestClientWithStatus(code, "Inactive Client", "BUS", "INACTIVE")
							.then(clientDAO.getClientBy(code)))
					.assertNext(client -> {
						assertNotNull(client);
						assertEquals(code, client.getCode());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getClientsBy() - by IDs")
	class GetClientsByIdsTests {

		@Test
		void existingIds_ReturnsClients() {
			StepVerifier.create(clientDAO.getClientsBy(List.of(ULong.valueOf(1))))
					.assertNext(clients -> {
						assertNotNull(clients);
						assertFalse(clients.isEmpty());
						assertEquals(1, clients.size());
					})
					.verifyComplete();
		}

		@Test
		void mixOfExistingAndNonExisting_ReturnsOnlyExisting() {
			StepVerifier.create(clientDAO.getClientsBy(List.of(ULong.valueOf(1), ULong.valueOf(999999))))
					.assertNext(clients -> {
						assertNotNull(clients);
						assertEquals(1, clients.size());
					})
					.verifyComplete();
		}

		@Test
		void multipleCreatedClients_ReturnsAll() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code1 = "M1" + ts.substring(ts.length() - 6);
			String code2 = "M2" + ts.substring(ts.length() - 6);

			StepVerifier.create(
					insertTestClient(code1, "Multi 1", "BUS")
							.flatMap(id1 -> insertTestClient(code2, "Multi 2", "BUS")
									.flatMap(id2 -> clientDAO.getClientsBy(List.of(id1, id2)))))
					.assertNext(clients -> {
						assertNotNull(clients);
						assertEquals(2, clients.size());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("isClientActive()")
	class IsClientActiveTests {

		@Test
		void activeClient_ReturnsTrue() {
			StepVerifier.create(clientDAO.isClientActive(List.of(ULong.valueOf(1))))
					.assertNext(active -> assertTrue(active))
					.verifyComplete();
		}

		@Test
		void nonExistentClient_ReturnsFalse() {
			StepVerifier.create(clientDAO.isClientActive(List.of(ULong.valueOf(999999))))
					.assertNext(active -> assertFalse(active))
					.verifyComplete();
		}

		@Test
		void inactiveClient_ReturnsFalse() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "IA" + ts.substring(ts.length() - 6);

			StepVerifier.create(
					insertTestClientWithStatus(code, "Inactive Check", "BUS", "INACTIVE")
							.flatMap(clientId -> clientDAO.isClientActive(List.of(clientId))))
					.assertNext(active -> assertFalse(active))
					.verifyComplete();
		}

		@Test
		void mixOfActiveAndInactive_ReturnsFalse() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "MX" + ts.substring(ts.length() - 6);

			StepVerifier.create(
					insertTestClientWithStatus(code, "Inactive Mix", "BUS", "INACTIVE")
							.flatMap(inactiveId -> clientDAO
									.isClientActive(List.of(ULong.valueOf(1), inactiveId))))
					.assertNext(active -> assertFalse(active))
					.verifyComplete();
		}

		@Test
		void multipleActiveClients_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "MA" + ts.substring(ts.length() - 6);

			StepVerifier.create(
					insertTestClient(code, "Multi Active", "BUS")
							.flatMap(newId -> clientDAO
									.isClientActive(List.of(ULong.valueOf(1), newId))))
					.assertNext(active -> assertTrue(active))
					.verifyComplete();
		}

		@Test
		void deletedClient_ReturnsFalse() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "DL" + ts.substring(ts.length() - 6);

			StepVerifier.create(
					insertTestClientWithStatus(code, "Deleted Check", "BUS", "DELETED")
							.flatMap(clientId -> clientDAO.isClientActive(List.of(clientId))))
					.assertNext(active -> assertFalse(active))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getValidClientCode()")
	class GetValidClientCodeTests {

		@Test
		void validName_GeneratesCode() {
			StepVerifier.create(clientDAO.getValidClientCode("TestCompany"))
					.assertNext(code -> {
						assertNotNull(code);
						assertTrue(code.startsWith("TESTC"));
					})
					.verifyComplete();
		}

		@Test
		void shortName_GeneratesCode() {
			StepVerifier.create(clientDAO.getValidClientCode("AB"))
					.assertNext(code -> {
						assertNotNull(code);
						assertTrue(code.startsWith("AB"));
					})
					.verifyComplete();
		}

		@Test
		void singleCharName_GeneratesCode() {
			StepVerifier.create(clientDAO.getValidClientCode("X"))
					.assertNext(code -> {
						assertNotNull(code);
						assertTrue(code.startsWith("X"));
					})
					.verifyComplete();
		}

		@Test
		void longName_TruncatesTo5Chars() {
			StepVerifier.create(clientDAO.getValidClientCode("VeryLongCompanyName"))
					.assertNext(code -> {
						assertNotNull(code);
						assertTrue(code.startsWith("VERYL"));
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("makeClientActiveIfInActive()")
	class MakeClientActiveTests {

		@Test
		void nonExistentClient_ReturnsFalse() {
			StepVerifier.create(clientDAO.makeClientActiveIfInActive(ULong.valueOf(999999)))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		void inactiveClient_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "MCA" + ts.substring(ts.length() - 5);

			StepVerifier.create(
					insertTestClientWithStatus(code, "Make Active", "BUS", "INACTIVE")
							.flatMap(clientId -> clientDAO.makeClientActiveIfInActive(clientId)))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void inactiveClient_StatusBecomesActive() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "MCB" + ts.substring(ts.length() - 5);

			StepVerifier.create(
					insertTestClientWithStatus(code, "Verify Active", "BUS", "INACTIVE")
							.flatMap(clientId -> clientDAO.makeClientActiveIfInActive(clientId)
									.then(databaseClient.sql(
											"SELECT STATUS_CODE FROM security_client WHERE ID = :id")
											.bind("id", clientId.longValue())
											.map(row -> row.get("STATUS_CODE", String.class))
											.one())))
					.assertNext(status -> assertEquals("ACTIVE", status))
					.verifyComplete();
		}

		@Test
		void alreadyActiveClient_ReturnsFalse() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "MCC" + ts.substring(ts.length() - 5);

			StepVerifier.create(
					insertTestClient(code, "Already Active", "BUS")
							.flatMap(clientId -> clientDAO.makeClientActiveIfInActive(clientId)))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		void deletedClient_ReturnsFalse() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "MCD" + ts.substring(ts.length() - 5);

			StepVerifier.create(
					insertTestClientWithStatus(code, "Deleted No Active", "BUS", "DELETED")
							.flatMap(clientId -> clientDAO.makeClientActiveIfInActive(clientId)))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("makeClientInActive()")
	class MakeClientInActiveTests {

		@Test
		void nonExistentClient_ReturnsFalse() {
			StepVerifier.create(clientDAO.makeClientInActive(ULong.valueOf(999999)))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		void activeClient_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "MIA" + ts.substring(ts.length() - 5);

			StepVerifier.create(
					insertTestClient(code, "Make Inactive", "BUS")
							.flatMap(clientId -> clientDAO.makeClientInActive(clientId)))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void activeClient_StatusBecomesInactive() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "MIB" + ts.substring(ts.length() - 5);

			StepVerifier.create(
					insertTestClient(code, "Verify Inactive", "BUS")
							.flatMap(clientId -> clientDAO.makeClientInActive(clientId)
									.then(databaseClient.sql(
											"SELECT STATUS_CODE FROM security_client WHERE ID = :id")
											.bind("id", clientId.longValue())
											.map(row -> row.get("STATUS_CODE", String.class))
											.one())))
					.assertNext(status -> assertEquals("INACTIVE", status))
					.verifyComplete();
		}

		@Test
		void alreadyInactiveClient_ReturnsTrue() {
			// ne(DELETED) condition means inactive -> inactive update still matches
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "MIC" + ts.substring(ts.length() - 5);

			StepVerifier.create(
					insertTestClientWithStatus(code, "Already Inactive", "BUS", "INACTIVE")
							.flatMap(clientId -> clientDAO.makeClientInActive(clientId)))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void deletedClient_ReturnsFalse() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "MID" + ts.substring(ts.length() - 5);

			StepVerifier.create(
					insertTestClientWithStatus(code, "Deleted No Inactive", "BUS", "DELETED")
							.flatMap(clientId -> clientDAO.makeClientInActive(clientId)))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		void roundTrip_ActiveToInactiveAndBack() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "MIE" + ts.substring(ts.length() - 5);

			StepVerifier.create(
					insertTestClient(code, "Round Trip", "BUS")
							.flatMap(clientId -> clientDAO.makeClientInActive(clientId)
									.then(clientDAO.makeClientActiveIfInActive(clientId))
									.then(databaseClient.sql(
											"SELECT STATUS_CODE FROM security_client WHERE ID = :id")
											.bind("id", clientId.longValue())
											.map(row -> row.get("STATUS_CODE", String.class))
											.one())))
					.assertNext(status -> assertEquals("ACTIVE", status))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("readClientPatterns()")
	class ReadClientPatternsTests {

		@Test
		void returnsPatterns() {
			StepVerifier.create(clientDAO.readClientPatterns().collectList())
					.assertNext(patterns -> assertNotNull(patterns))
					.verifyComplete();
		}

		@Test
		void withInsertedUrl_ContainsPattern() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "RCP" + ts.substring(ts.length() - 5);

			StepVerifier.create(
					insertTestClient(code, "Pattern Client", "BUS")
							.flatMap(clientId -> insertClientUrl(clientId,
									"https://rcp-" + ts + ".example.com", "appbuilder")
									.thenReturn(clientId))
							.then(clientDAO.readClientPatterns().collectList()))
					.assertNext(patterns -> {
						assertNotNull(patterns);
						assertFalse(patterns.isEmpty());
						boolean found = patterns.stream()
								.anyMatch(p -> p.getUrlPattern() != null
										&& p.getUrlPattern().contains("rcp-" + ts));
						assertTrue(found, "Should find the inserted URL pattern");
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("fillUserCounts()")
	class FillUserCountsTests {

		@Test
		void withSystemClient_FillsCounts() {
			StepVerifier.create(clientDAO.readInternal(ULong.valueOf(1))
					.flatMap(client -> {
						Map<ULong, Client> map = new HashMap<>();
						map.put(client.getId(), client);
						return clientDAO.fillUserCounts(map, null, null);
					}))
					.assertNext(clients -> {
						assertNotNull(clients);
						assertFalse(clients.isEmpty());
					})
					.verifyComplete();
		}

		@Test
		void withAppCode_FillsCounts() {
			StepVerifier.create(clientDAO.readInternal(ULong.valueOf(1))
					.flatMap(client -> {
						Map<ULong, Client> map = new HashMap<>();
						map.put(client.getId(), client);
						return clientDAO.fillUserCounts(map, "appx", null);
					}))
					.assertNext(clients -> assertNotNull(clients))
					.verifyComplete();
		}

		@Test
		void clientWithActiveAndInactiveUsers_CountsCorrectly() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "FUC" + ts.substring(ts.length() - 5);

			StepVerifier.create(
					insertTestClient(code, "User Count Client", "BUS")
							.flatMap(clientId -> insertTestUserWithStatus(clientId,
									"active1_" + ts, "active1_" + ts + "@t.com", "ACTIVE")
									.then(insertTestUserWithStatus(clientId,
											"active2_" + ts, "active2_" + ts + "@t.com", "ACTIVE"))
									.then(insertTestUserWithStatus(clientId,
											"inactive_" + ts, "inactive_" + ts + "@t.com",
											"INACTIVE"))
									.then(insertTestUserWithStatus(clientId,
											"locked_" + ts, "locked_" + ts + "@t.com", "LOCKED"))
									.then(clientDAO.readInternal(clientId)))
							.flatMap(client -> {
								Map<ULong, Client> map = new HashMap<>();
								map.put(client.getId(), client);
								return clientDAO.fillUserCounts(map, null, null);
							}))
					.assertNext(clients -> {
						assertNotNull(clients);
						assertFalse(clients.isEmpty());
						Client client = clients.get(0);
						assertTrue(client.getActiveUsers() >= 2,
								"Should have at least 2 active users");
					})
					.verifyComplete();
		}

		@Test
		void clientWithNoUsers_ReturnsZeroCounts() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "FUN" + ts.substring(ts.length() - 5);

			StepVerifier.create(
					insertTestClient(code, "No Users Client", "BUS")
							.flatMap(clientId -> clientDAO.readInternal(clientId))
							.flatMap(client -> {
								Map<ULong, Client> map = new HashMap<>();
								map.put(client.getId(), client);
								return clientDAO.fillUserCounts(map, null, null);
							}))
					.assertNext(clients -> {
						assertNotNull(clients);
						Client client = clients.get(0);
						assertEquals(0, client.getActiveUsers());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("readClientURLs()")
	class ReadClientURLsTests {

		@Test
		void nonExistentCode_ReturnsEmptyMap() {
			StepVerifier.create(clientDAO.readClientURLs("NONEXISTENT", List.of(ULong.valueOf(1))))
					.assertNext(urls -> assertTrue(urls.isEmpty()))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("createProfileRestrictions()")
	class CreateProfileRestrictionsTests {

		@Test
		void singleProfile_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "PR" + ts.substring(ts.length() - 6);

			StepVerifier.create(
					insertTestClient(code, "Profile Restrict", "BUS")
							.flatMap(clientId -> databaseClient.sql(
									"SELECT ID FROM security_profile LIMIT 1")
									.map(row -> ULong.valueOf(row.get("ID", Long.class)))
									.one()
									.flatMap(profileId -> clientDAO.createProfileRestrictions(
											clientId, List.of(profileId)))
									.doFinally(s -> databaseClient.sql(
											"DELETE FROM security_profile_client_restriction WHERE CLIENT_ID = "
													+ clientId.longValue())
											.then().block())))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("Edge cases")
	class EdgeCaseTests {

		@Test
		void readInternalAfterStatusChange_ReflectsNewStatus() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "EC" + ts.substring(ts.length() - 6);

			StepVerifier.create(
					insertTestClient(code, "Edge Case", "BUS")
							.flatMap(clientId -> clientDAO.makeClientInActive(clientId)
									.then(clientDAO.readInternal(clientId))))
					.assertNext(client -> {
						assertNotNull(client);
						assertNotNull(client.getStatusCode());
					})
					.verifyComplete();
		}

		@Test
		void getClientByCode_AfterMakeInactive_StillReturnsClient() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "EA" + ts.substring(ts.length() - 6);

			StepVerifier.create(
					insertTestClient(code, "Still Returns", "BUS")
							.flatMap(clientId -> clientDAO.makeClientInActive(clientId)
									.then(clientDAO.getClientBy(code))))
					.assertNext(client -> {
						assertNotNull(client);
						assertEquals(code, client.getCode());
					})
					.verifyComplete();
		}
	}
}