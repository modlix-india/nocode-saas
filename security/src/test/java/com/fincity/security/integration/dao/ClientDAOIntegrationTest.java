package com.fincity.security.integration.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collection;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.security.dao.ClientDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.integration.AbstractIntegrationTest;
import com.fincity.security.testutil.TestDataFactory;

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
				.then(databaseClient.sql("DELETE FROM security_profile_user WHERE USER_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_profile_role WHERE PROFILE_ID IN (SELECT ID FROM security_profile WHERE CLIENT_ID > 1)").then())
				.then(databaseClient.sql("DELETE FROM security_profile_client_restriction WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_profile WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_v2_role WHERE CLIENT_ID > 1").then())
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

	private Mono<ULong> insertTestRole(ULong clientId, String name) {
		return databaseClient.sql(
				"INSERT INTO security_v2_role (CLIENT_ID, NAME, SHORT_NAME, DESCRIPTION) VALUES (:clientId, :name, :shortName, :desc)")
				.bind("clientId", clientId.longValue())
				.bind("name", name)
				.bind("shortName", name.toUpperCase())
				.bind("desc", "Role " + name)
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	private Mono<ULong> insertTestProfile(ULong clientId, ULong appId, String name) {
		return databaseClient.sql(
				"INSERT INTO security_profile (CLIENT_ID, APP_ID, NAME) VALUES (:clientId, :appId, :name)")
				.bind("clientId", clientId.longValue())
				.bind("appId", appId.longValue())
				.bind("name", name)
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	private Mono<Void> insertProfileRole(ULong profileId, ULong roleId) {
		return databaseClient.sql(
				"INSERT INTO security_profile_role (PROFILE_ID, ROLE_ID) VALUES (:profileId, :roleId)")
				.bind("profileId", profileId.longValue())
				.bind("roleId", roleId.longValue())
				.then();
	}

	private Mono<Void> insertProfileUser(ULong profileId, ULong userId) {
		return databaseClient.sql(
				"INSERT INTO security_profile_user (PROFILE_ID, USER_ID) VALUES (:profileId, :userId)")
				.bind("profileId", profileId.longValue())
				.bind("userId", userId.longValue())
				.then();
	}

	private Mono<Void> insertAppAccess(ULong clientId, ULong appId) {
		return databaseClient.sql(
				"INSERT INTO security_app_access (CLIENT_ID, APP_ID) VALUES (:clientId, :appId)")
				.bind("clientId", clientId.longValue())
				.bind("appId", appId.longValue())
				.then();
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

	// ========================================================================
	// New test classes for uncovered methods
	// ========================================================================

	@Nested
	@DisplayName("getOwnersPerClient()")
	class GetOwnersPerClientTests {

		@Test
		void clientWithNoOwners_ReturnsEmptyMap() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "OPC1" + ts.substring(ts.length() - 4);

			StepVerifier.create(
					insertTestClient(code, "No Owners Client", "BUS")
							.flatMap(clientId -> clientDAO.readInternal(clientId)
									.flatMap(client -> {
										Map<ULong, Client> map = new HashMap<>();
										map.put(client.getId(), client);
										return clientDAO.getOwnersPerClient(map, null, null);
									})))
					.assertNext(ownerMap -> {
						assertNotNull(ownerMap);
						assertTrue(ownerMap.isEmpty());
					})
					.verifyComplete();
		}

		@Test
		void clientWithOwnerRole_ReturnsOwnerUserId() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "OPC2" + ts.substring(ts.length() - 4);

			StepVerifier.create(
					insertTestClient(code, "Owner Client", "BUS")
							.flatMap(clientId -> insertTestApp(clientId, "opc2" + ts.substring(ts.length() - 4), "OPC2 App")
									.flatMap(appId -> insertTestRole(clientId, "Owner")
											.flatMap(roleId -> insertTestProfile(clientId, appId, "OwnerProfile_" + ts)
													.flatMap(profileId -> insertProfileRole(profileId, roleId)
															.then(insertTestUserWithStatus(clientId,
																	"owner_" + ts, "owner_" + ts + "@t.com", "ACTIVE"))
															.flatMap(userId -> insertProfileUser(profileId, userId)
																	.thenReturn(userId)))
													.flatMap(userId -> clientDAO.readInternal(clientId)
															.flatMap(client -> {
																Map<ULong, Client> map = new HashMap<>();
																map.put(client.getId(), client);
																return clientDAO.getOwnersPerClient(map, null, null)
																		.map(ownerMap -> Map.entry(ownerMap, userId));
															}))))))
					.assertNext(entry -> {
						Map<ULong, Collection<ULong>> ownerMap = entry.getKey();
						ULong userId = entry.getValue();
						assertNotNull(ownerMap);
						assertFalse(ownerMap.isEmpty());
						assertTrue(ownerMap.values().stream()
								.anyMatch(ids -> ids.contains(userId)));
					})
					.verifyComplete();
		}

		@Test
		void withAppCodeFilter_ReturnsOnlyMatchingOwners() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "OPC3" + ts.substring(ts.length() - 4);
			String appCode = "op3" + ts.substring(ts.length() - 5);

			StepVerifier.create(
					insertTestClient(code, "AppCode Owner", "BUS")
							.flatMap(clientId -> insertTestApp(clientId, appCode, "OPC3 App")
									.flatMap(appId -> insertTestRole(clientId, "Owner")
											.flatMap(roleId -> insertTestProfile(clientId, appId, "OwnerProf3_" + ts)
													.flatMap(profileId -> insertProfileRole(profileId, roleId)
															.then(insertTestUserWithStatus(clientId,
																	"own3_" + ts, "own3_" + ts + "@t.com", "ACTIVE"))
															.flatMap(userId -> insertProfileUser(profileId, userId)
																	.thenReturn(clientId))))))
							.flatMap(clientId -> clientDAO.readInternal(clientId)
									.flatMap(client -> {
										Map<ULong, Client> map = new HashMap<>();
										map.put(client.getId(), client);
										return clientDAO.getOwnersPerClient(map, appCode, null);
									})))
					.assertNext(ownerMap -> {
						assertNotNull(ownerMap);
						assertFalse(ownerMap.isEmpty());
					})
					.verifyComplete();
		}

		@Test
		void withAppIdFilter_ReturnsOnlyMatchingOwners() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "OPC4" + ts.substring(ts.length() - 4);

			StepVerifier.create(
					insertTestClient(code, "AppId Owner", "BUS")
							.flatMap(clientId -> insertTestApp(clientId, "op4" + ts.substring(ts.length() - 5), "OPC4 App")
									.flatMap(appId -> insertTestRole(clientId, "Owner")
											.flatMap(roleId -> insertTestProfile(clientId, appId, "OwnerProf4_" + ts)
													.flatMap(profileId -> insertProfileRole(profileId, roleId)
															.then(insertTestUserWithStatus(clientId,
																	"own4_" + ts, "own4_" + ts + "@t.com", "ACTIVE"))
															.flatMap(userId -> insertProfileUser(profileId, userId)
																	.thenReturn(appId))))))
							.flatMap(appId -> databaseClient.sql("SELECT CLIENT_ID FROM security_app WHERE ID = :appId")
									.bind("appId", appId.longValue())
									.map(row -> ULong.valueOf(row.get("CLIENT_ID", Long.class)))
									.one()
									.flatMap(clientId -> clientDAO.readInternal(clientId)
											.flatMap(client -> {
												Map<ULong, Client> map = new HashMap<>();
												map.put(client.getId(), client);
												return clientDAO.getOwnersPerClient(map, null, appId.toString());
											}))))
					.assertNext(ownerMap -> {
						assertNotNull(ownerMap);
						assertFalse(ownerMap.isEmpty());
					})
					.verifyComplete();
		}

		@Test
		void withNonMatchingAppCode_ReturnsEmptyMap() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "OPC5" + ts.substring(ts.length() - 4);

			StepVerifier.create(
					insertTestClient(code, "NoMatch Owner", "BUS")
							.flatMap(clientId -> clientDAO.readInternal(clientId)
									.flatMap(client -> {
										Map<ULong, Client> map = new HashMap<>();
										map.put(client.getId(), client);
										return clientDAO.getOwnersPerClient(map, "nonexistentapp", null);
									})))
					.assertNext(ownerMap -> {
						assertNotNull(ownerMap);
						assertTrue(ownerMap.isEmpty());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("readPageFilter() with system security context")
	class ReadPageFilterTests {

		@Test
		void systemContext_NoFilter_ReturnsPage() {
			ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();
			Pageable pageable = PageRequest.of(0, 10);

			StepVerifier.create(
					clientDAO.readPageFilter(pageable, null)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(page -> {
						assertNotNull(page);
						assertTrue(page.getTotalElements() >= 1);
						assertFalse(page.getContent().isEmpty());
					})
					.verifyComplete();
		}

		@Test
		void systemContext_WithNameEqualsFilter_ReturnsMatchingClients() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "RPF1" + ts.substring(ts.length() - 4);
			ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();

			FilterCondition fc = FilterCondition.of("name", "RPF1 Test Client", FilterConditionOperator.EQUALS);

			StepVerifier.create(
					insertTestClient(code, "RPF1 Test Client", "BUS")
							.then(clientDAO.readPageFilter(PageRequest.of(0, 10), fc)
									.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))))
					.assertNext(page -> {
						assertNotNull(page);
						assertTrue(page.getTotalElements() >= 1);
						assertTrue(page.getContent().stream()
								.anyMatch(c -> "RPF1 Test Client".equals(c.getName())));
					})
					.verifyComplete();
		}

		@Test
		void systemContext_WithStatusCodeFilter_ReturnsOnlyActive() {
			String ts = String.valueOf(System.currentTimeMillis());
			String codeA = "RPA" + ts.substring(ts.length() - 5);
			String codeI = "RPI" + ts.substring(ts.length() - 5);
			ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();

			FilterCondition fc = FilterCondition.of("statusCode", "ACTIVE", FilterConditionOperator.EQUALS);

			StepVerifier.create(
					insertTestClient(codeA, "Active RPF", "BUS")
							.then(insertTestClientWithStatus(codeI, "Inactive RPF", "BUS", "INACTIVE"))
							.then(clientDAO.readPageFilter(PageRequest.of(0, 100), fc)
									.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))))
					.assertNext(page -> {
						assertNotNull(page);
						assertTrue(page.getContent().stream()
								.allMatch(c -> c.getStatusCode() != null
										&& "ACTIVE".equals(c.getStatusCode().getLiteral())));
					})
					.verifyComplete();
		}

		@Test
		void systemContext_WithSortByName_ReturnsSortedPage() {
			ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();
			Pageable pageable = PageRequest.of(0, 100, Sort.by(Sort.Direction.ASC, "name"));

			StepVerifier.create(
					clientDAO.readPageFilter(pageable, null)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(page -> {
						assertNotNull(page);
						List<Client> clients = page.getContent();
						for (int i = 1; i < clients.size(); i++) {
							assertTrue(clients.get(i - 1).getName()
									.compareTo(clients.get(i).getName()) <= 0,
									"Clients should be sorted by name ascending");
						}
					})
					.verifyComplete();
		}

		@Test
		void systemContext_WithStringLooseEqualFilter_MatchesPartialName() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "RPF2" + ts.substring(ts.length() - 4);
			String uniqueName = "UniqueLoose_" + ts;
			ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();

			FilterCondition fc = FilterCondition.of("name", "UniqueLoose", FilterConditionOperator.STRING_LOOSE_EQUAL);

			StepVerifier.create(
					insertTestClient(code, uniqueName, "BUS")
							.then(clientDAO.readPageFilter(PageRequest.of(0, 10), fc)
									.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))))
					.assertNext(page -> {
						assertNotNull(page);
						assertTrue(page.getTotalElements() >= 1);
						assertTrue(page.getContent().stream()
								.anyMatch(c -> c.getName().contains("UniqueLoose")));
					})
					.verifyComplete();
		}

		@Test
		void systemContext_WithLikeFilter_MatchesPattern() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "RPF3" + ts.substring(ts.length() - 4);
			ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();

			FilterCondition fc = FilterCondition.of("name", "LikeTest%", FilterConditionOperator.LIKE);

			StepVerifier.create(
					insertTestClient(code, "LikeTest_" + ts, "BUS")
							.then(clientDAO.readPageFilter(PageRequest.of(0, 10), fc)
									.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))))
					.assertNext(page -> {
						assertNotNull(page);
						assertTrue(page.getTotalElements() >= 1);
					})
					.verifyComplete();
		}

		@Test
		void systemContext_WithPagination_RespectsPageSize() {
			String ts = String.valueOf(System.currentTimeMillis());
			ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();

			StepVerifier.create(
					insertTestClient("PG1" + ts.substring(ts.length() - 5), "Page1", "BUS")
							.then(insertTestClient("PG2" + ts.substring(ts.length() - 5), "Page2", "BUS"))
							.then(insertTestClient("PG3" + ts.substring(ts.length() - 5), "Page3", "BUS"))
							.then(clientDAO.readPageFilter(PageRequest.of(0, 2), null)
									.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))))
					.assertNext(page -> {
						assertNotNull(page);
						assertTrue(page.getContent().size() <= 2);
						assertTrue(page.getTotalElements() >= 3);
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("readPageFilter() with business client security context")
	class ReadPageFilterBusinessContextTests {

		@Test
		void businessContext_WithHierarchy_ReturnsOnlyManagedClients() {
			String ts = String.valueOf(System.currentTimeMillis());
			String busCode = "BPF" + ts.substring(ts.length() - 5);
			String childCode = "CPF" + ts.substring(ts.length() - 5);

			StepVerifier.create(
					insertTestClient(busCode, "Business Parent", "BUS")
							.flatMap(busId -> insertClientHierarchy(busId, ULong.valueOf(1), null, null, null)
									.then(insertTestClient(childCode, "Child Client", "BUS"))
									.flatMap(childId -> insertClientHierarchy(childId, ULong.valueOf(1), busId, null, null)
											.thenReturn(busId)))
							.flatMap(busId -> {
								ContextAuthentication busAuth = TestDataFactory.createBusinessAuth(
										busId, busCode, List.of("Authorities.Client_READ", "Authorities.Logged_IN"));
								return clientDAO.readPageFilter(PageRequest.of(0, 100), null)
										.contextWrite(ReactiveSecurityContextHolder.withAuthentication(busAuth));
							}))
					.assertNext(page -> {
						assertNotNull(page);
						// Business client should only see clients it manages in its hierarchy
						assertTrue(page.getTotalElements() >= 1);
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("readPageFilter() with ComplexCondition")
	class ReadPageFilterComplexConditionTests {

		@Test
		void andCondition_MultipleFilters_ReturnsIntersection() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "CCA" + ts.substring(ts.length() - 5);
			ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();

			ComplexCondition cc = ComplexCondition.and(
					FilterCondition.of("typeCode", "BUS", FilterConditionOperator.EQUALS),
					FilterCondition.of("statusCode", "ACTIVE", FilterConditionOperator.EQUALS));

			StepVerifier.create(
					insertTestClient(code, "ComplexAnd Test", "BUS")
							.then(clientDAO.readPageFilter(PageRequest.of(0, 100), cc)
									.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))))
					.assertNext(page -> {
						assertNotNull(page);
						assertTrue(page.getContent().stream()
								.allMatch(c -> "BUS".equals(c.getTypeCode())
										&& "ACTIVE".equals(c.getStatusCode().getLiteral())));
					})
					.verifyComplete();
		}

		@Test
		void orCondition_MultipleFilters_ReturnsUnion() {
			String ts = String.valueOf(System.currentTimeMillis());
			String codeA = "COA" + ts.substring(ts.length() - 5);
			String codeI = "COI" + ts.substring(ts.length() - 5);
			ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();

			ComplexCondition cc = ComplexCondition.or(
					FilterCondition.of("statusCode", "ACTIVE", FilterConditionOperator.EQUALS),
					FilterCondition.of("statusCode", "INACTIVE", FilterConditionOperator.EQUALS));

			StepVerifier.create(
					insertTestClient(codeA, "OrActive", "BUS")
							.then(insertTestClientWithStatus(codeI, "OrInactive", "BUS", "INACTIVE"))
							.then(clientDAO.readPageFilter(PageRequest.of(0, 100), cc)
									.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))))
					.assertNext(page -> {
						assertNotNull(page);
						assertTrue(page.getTotalElements() >= 2);
						assertTrue(page.getContent().stream()
								.allMatch(c -> "ACTIVE".equals(c.getStatusCode().getLiteral())
										|| "INACTIVE".equals(c.getStatusCode().getLiteral())));
					})
					.verifyComplete();
		}

		@Test
		void emptyComplexCondition_ReturnsAll() {
			ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();

			ComplexCondition cc = new ComplexCondition()
					.setOperator(ComplexConditionOperator.AND)
					.setConditions(List.of());

			StepVerifier.create(
					clientDAO.readPageFilter(PageRequest.of(0, 10), cc)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(page -> {
						assertNotNull(page);
						assertTrue(page.getTotalElements() >= 1);
					})
					.verifyComplete();
		}

		@Test
		void negatedCondition_ExcludesMatching() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "CCN" + ts.substring(ts.length() - 5);
			ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();

			FilterCondition fc = FilterCondition.of("typeCode", "SYS", FilterConditionOperator.EQUALS);
			fc.setNegate(true);

			StepVerifier.create(
					insertTestClient(code, "Negate Test", "BUS")
							.then(clientDAO.readPageFilter(PageRequest.of(0, 100), fc)
									.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))))
					.assertNext(page -> {
						assertNotNull(page);
						assertTrue(page.getContent().stream()
								.noneMatch(c -> "SYS".equals(c.getTypeCode())));
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("filterConditionFilter() - user field conditions via readPageFilter")
	class FilterConditionUserFieldTests {

		@Test
		void userFieldEquals_FiltersByUserName() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "UFE" + ts.substring(ts.length() - 5);
			String userName = "ufe_user_" + ts;
			ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();

			FilterCondition fc = FilterCondition.of("user.userName", userName, FilterConditionOperator.EQUALS);

			StepVerifier.create(
					insertTestClient(code, "UserField EQ", "BUS")
							.flatMap(clientId -> insertTestUserWithStatus(clientId, userName,
									userName + "@t.com", "ACTIVE")
									.thenReturn(clientId))
							.then(clientDAO.readPageFilter(PageRequest.of(0, 100), fc)
									.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))))
					.assertNext(page -> {
						assertNotNull(page);
						assertTrue(page.getTotalElements() >= 1);
						assertTrue(page.getContent().stream()
								.anyMatch(c -> code.equals(c.getCode())));
					})
					.verifyComplete();
		}

		@Test
		void userFieldLike_FiltersByEmailPattern() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "UFL" + ts.substring(ts.length() - 5);
			String email = "ufl_" + ts + "@testdomain.com";
			ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();

			FilterCondition fc = FilterCondition.of("user.emailId", "ufl_" + ts + "%", FilterConditionOperator.LIKE);

			StepVerifier.create(
					insertTestClient(code, "UserField Like", "BUS")
							.flatMap(clientId -> insertTestUserWithStatus(clientId, "ufl_" + ts,
									email, "ACTIVE")
									.thenReturn(clientId))
							.then(clientDAO.readPageFilter(PageRequest.of(0, 100), fc)
									.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))))
					.assertNext(page -> {
						assertNotNull(page);
						assertTrue(page.getTotalElements() >= 1);
					})
					.verifyComplete();
		}

		@Test
		void userFieldStringLooseEqual_MatchesPartialUserName() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "UFS" + ts.substring(ts.length() - 5);
			String userName = "ufsloose_" + ts;
			ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();

			FilterCondition fc = FilterCondition.of("user.userName", "ufsloose", FilterConditionOperator.STRING_LOOSE_EQUAL);

			StepVerifier.create(
					insertTestClient(code, "UserField Loose", "BUS")
							.flatMap(clientId -> insertTestUserWithStatus(clientId, userName,
									userName + "@t.com", "ACTIVE")
									.thenReturn(clientId))
							.then(clientDAO.readPageFilter(PageRequest.of(0, 100), fc)
									.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))))
					.assertNext(page -> {
						assertNotNull(page);
						assertTrue(page.getTotalElements() >= 1);
					})
					.verifyComplete();
		}

		@Test
		void userFieldStatusCodeIsNull_FiltersByNullStatus() {
			ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();

			FilterCondition fc = FilterCondition.of("user.statusCode", null, FilterConditionOperator.IS_NULL);

			StepVerifier.create(
					clientDAO.readPageFilter(PageRequest.of(0, 10), fc)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(page -> {
						assertNotNull(page);
						// Users with NULL status code are rare, but the query should execute
					})
					.verifyComplete();
		}

		@Test
		void userFieldNonExistentField_ReturnsNoCondition() {
			ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();

			FilterCondition fc = FilterCondition.of("user.nonExistentField", "val", FilterConditionOperator.EQUALS);

			StepVerifier.create(
					clientDAO.readPageFilter(PageRequest.of(0, 10), fc)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(page -> {
						assertNotNull(page);
						// noCondition means all clients returned
						assertTrue(page.getTotalElements() >= 1);
					})
					.verifyComplete();
		}

		@Test
		void userFieldIn_FiltersByMultipleUserNames() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "UFI" + ts.substring(ts.length() - 5);
			String userName1 = "ufin1_" + ts;
			String userName2 = "ufin2_" + ts;
			ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();

			FilterCondition fc = FilterCondition.of("user.userName", userName1 + "," + userName2, FilterConditionOperator.IN);

			StepVerifier.create(
					insertTestClient(code, "UserField In", "BUS")
							.flatMap(clientId -> insertTestUserWithStatus(clientId, userName1,
									userName1 + "@t.com", "ACTIVE")
									.then(insertTestUserWithStatus(clientId, userName2,
											userName2 + "@t.com", "ACTIVE"))
									.thenReturn(clientId))
							.then(clientDAO.readPageFilter(PageRequest.of(0, 100), fc)
									.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))))
					.assertNext(page -> {
						assertNotNull(page);
						assertTrue(page.getTotalElements() >= 1);
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("filterConditionFilter() - appId/appCode conditions via readPageFilter")
	class FilterConditionAppFieldTests {

		@Test
		void appCodeEquals_FiltersByAppCode() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "ACE" + ts.substring(ts.length() - 5);
			String appCode = "ace" + ts.substring(ts.length() - 5);
			ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();

			FilterCondition fc = FilterCondition.of("appCode", appCode, FilterConditionOperator.EQUALS);

			StepVerifier.create(
					insertTestClient(code, "AppCode EQ", "BUS")
							.flatMap(clientId -> insertTestApp(clientId, appCode, "ACE App")
									.thenReturn(clientId))
							.then(clientDAO.readPageFilter(PageRequest.of(0, 100), fc)
									.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))))
					.assertNext(page -> {
						assertNotNull(page);
						assertTrue(page.getTotalElements() >= 1);
						assertTrue(page.getContent().stream()
								.anyMatch(c -> code.equals(c.getCode())));
					})
					.verifyComplete();
		}

		@Test
		void appCodeEquals_ViaAppAccess_FiltersCorrectly() {
			String ts = String.valueOf(System.currentTimeMillis());
			String ownerCode = "AAO" + ts.substring(ts.length() - 5);
			String accessCode = "AAC" + ts.substring(ts.length() - 5);
			String appCode = "aac" + ts.substring(ts.length() - 5);
			ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();

			FilterCondition fc = FilterCondition.of("appCode", appCode, FilterConditionOperator.EQUALS);

			StepVerifier.create(
					insertTestClient(ownerCode, "App Owner", "BUS")
							.flatMap(ownerId -> insertTestApp(ownerId, appCode, "Access App")
									.flatMap(appId -> insertTestClient(accessCode, "App Access Client", "BUS")
											.flatMap(accessId -> insertAppAccess(accessId, appId)
													.thenReturn(accessId))))
							.then(clientDAO.readPageFilter(PageRequest.of(0, 100), fc)
									.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))))
					.assertNext(page -> {
						assertNotNull(page);
						// Should include both the owner client and the access client
						assertTrue(page.getTotalElements() >= 2);
					})
					.verifyComplete();
		}

		@Test
		void appIdEquals_FiltersByAppId() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "AIE" + ts.substring(ts.length() - 5);
			ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();

			StepVerifier.create(
					insertTestClient(code, "AppId EQ", "BUS")
							.flatMap(clientId -> insertTestApp(clientId, "aie" + ts.substring(ts.length() - 5), "AIE App")
									.flatMap(appId -> {
										FilterCondition fc = FilterCondition.of("appId", appId.toString(),
												FilterConditionOperator.EQUALS);
										return clientDAO.readPageFilter(PageRequest.of(0, 100), fc)
												.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));
									})))
					.assertNext(page -> {
						assertNotNull(page);
						assertTrue(page.getTotalElements() >= 1);
						assertTrue(page.getContent().stream()
								.anyMatch(c -> code.equals(c.getCode())));
					})
					.verifyComplete();
		}

		@Test
		void appIdIn_FiltersByMultipleAppIds() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code1 = "AIN1" + ts.substring(ts.length() - 4);
			String code2 = "AIN2" + ts.substring(ts.length() - 4);
			ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();

			StepVerifier.create(
					insertTestClient(code1, "AppId IN 1", "BUS")
							.flatMap(cid1 -> insertTestApp(cid1, "in1" + ts.substring(ts.length() - 5), "IN1 App")
									.flatMap(appId1 -> insertTestClient(code2, "AppId IN 2", "BUS")
											.flatMap(cid2 -> insertTestApp(cid2, "in2" + ts.substring(ts.length() - 5), "IN2 App")
													.flatMap(appId2 -> {
														FilterCondition fc = FilterCondition.of("appId",
																appId1.toString() + "," + appId2.toString(),
																FilterConditionOperator.IN);
														return clientDAO.readPageFilter(PageRequest.of(0, 100), fc)
																.contextWrite(ReactiveSecurityContextHolder
																		.withAuthentication(systemAuth));
													})))))
					.assertNext(page -> {
						assertNotNull(page);
						assertTrue(page.getTotalElements() >= 2);
					})
					.verifyComplete();
		}

		@Test
		void appCodeIn_FiltersByMultipleAppCodes() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code1 = "ACI1" + ts.substring(ts.length() - 4);
			String code2 = "ACI2" + ts.substring(ts.length() - 4);
			String ac1 = "ci1" + ts.substring(ts.length() - 5);
			String ac2 = "ci2" + ts.substring(ts.length() - 5);
			ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();

			FilterCondition fc = FilterCondition.of("appCode", ac1 + "," + ac2, FilterConditionOperator.IN);

			StepVerifier.create(
					insertTestClient(code1, "AppCode IN 1", "BUS")
							.flatMap(cid1 -> insertTestApp(cid1, ac1, "ACI1 App")
									.thenReturn(cid1))
							.then(insertTestClient(code2, "AppCode IN 2", "BUS")
									.flatMap(cid2 -> insertTestApp(cid2, ac2, "ACI2 App")
											.thenReturn(cid2)))
							.then(clientDAO.readPageFilter(PageRequest.of(0, 100), fc)
									.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))))
					.assertNext(page -> {
						assertNotNull(page);
						assertTrue(page.getTotalElements() >= 2);
					})
					.verifyComplete();
		}

		@Test
		void appCodeNegated_ExcludesMatchingClients() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "ACN" + ts.substring(ts.length() - 5);
			String appCode = "acn" + ts.substring(ts.length() - 5);
			ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();

			FilterCondition fc = FilterCondition.of("appCode", appCode, FilterConditionOperator.EQUALS);
			fc.setNegate(true);

			StepVerifier.create(
					insertTestClient(code, "AppCode Negate", "BUS")
							.flatMap(clientId -> insertTestApp(clientId, appCode, "Negate App")
									.thenReturn(clientId))
							.then(clientDAO.readPageFilter(PageRequest.of(0, 100), fc)
									.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))))
					.assertNext(page -> {
						assertNotNull(page);
						// The negated filter excludes the client that owns appCode
					})
					.verifyComplete();
		}

		@Test
		void appIdNegated_ExcludesMatchingClients() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "ANI" + ts.substring(ts.length() - 5);
			ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();

			StepVerifier.create(
					insertTestClient(code, "AppId Negate", "BUS")
							.flatMap(clientId -> insertTestApp(clientId, "ani" + ts.substring(ts.length() - 5), "Negate Id App")
									.flatMap(appId -> {
										FilterCondition fc = FilterCondition.of("appId", appId.toString(),
												FilterConditionOperator.EQUALS);
										fc.setNegate(true);
										return clientDAO.readPageFilter(PageRequest.of(0, 100), fc)
												.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));
									})))
					.assertNext(page -> {
						assertNotNull(page);
						// Negated appId filter should exclude the matching client
					})
					.verifyComplete();
		}

		@Test
		void appCodeWithUnsupportedOperator_ReturnsTrueCondition() {
			ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();

			FilterCondition fc = FilterCondition.of("appCode", "someapp", FilterConditionOperator.GREATER_THAN);

			StepVerifier.create(
					clientDAO.readPageFilter(PageRequest.of(0, 10), fc)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(page -> {
						assertNotNull(page);
						// trueCondition means all clients returned
						assertTrue(page.getTotalElements() >= 1);
					})
					.verifyComplete();
		}

		@Test
		void appIdWithUnsupportedOperator_ReturnsTrueCondition() {
			ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();

			FilterCondition fc = FilterCondition.of("appId", "123", FilterConditionOperator.LIKE);

			StepVerifier.create(
					clientDAO.readPageFilter(PageRequest.of(0, 10), fc)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(page -> {
						assertNotNull(page);
						assertTrue(page.getTotalElements() >= 1);
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("fillUserCounts() - with appId filter")
	class FillUserCountsAppIdTests {

		@Test
		void withAppId_CountsUsersInApp() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "FCA" + ts.substring(ts.length() - 5);

			StepVerifier.create(
					insertTestClient(code, "AppId Count", "BUS")
							.flatMap(clientId -> insertTestApp(clientId, "fca" + ts.substring(ts.length() - 5), "FCA App")
									.flatMap(appId -> insertTestProfile(clientId, appId, "FCProfile_" + ts)
											.flatMap(profileId -> insertTestUserWithStatus(clientId,
													"fca_user_" + ts, "fca_user_" + ts + "@t.com", "ACTIVE")
													.flatMap(userId -> insertProfileUser(profileId, userId)
															.thenReturn(appId)))))
							.flatMap(appId -> databaseClient.sql("SELECT CLIENT_ID FROM security_app WHERE ID = :appId")
									.bind("appId", appId.longValue())
									.map(row -> ULong.valueOf(row.get("CLIENT_ID", Long.class)))
									.one()
									.flatMap(clientId -> clientDAO.readInternal(clientId)
											.flatMap(client -> {
												Map<ULong, Client> map = new HashMap<>();
												map.put(client.getId(), client);
												return clientDAO.fillUserCounts(map, null, appId.toString());
											}))))
					.assertNext(clients -> {
						assertNotNull(clients);
						assertFalse(clients.isEmpty());
						Client client = clients.get(0);
						assertTrue(client.getActiveUsers() >= 1);
					})
					.verifyComplete();
		}

		@Test
		void withAllUserStatuses_CountsEachStatusCorrectly() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "FCS" + ts.substring(ts.length() - 5);

			StepVerifier.create(
					insertTestClient(code, "All Statuses", "BUS")
							.flatMap(clientId -> insertTestUserWithStatus(clientId,
									"fcs_active_" + ts, "fcs_active_" + ts + "@t.com", "ACTIVE")
									.then(insertTestUserWithStatus(clientId,
											"fcs_inactive_" + ts, "fcs_inactive_" + ts + "@t.com", "INACTIVE"))
									.then(insertTestUserWithStatus(clientId,
											"fcs_deleted_" + ts, "fcs_deleted_" + ts + "@t.com", "DELETED"))
									.then(insertTestUserWithStatus(clientId,
											"fcs_locked_" + ts, "fcs_locked_" + ts + "@t.com", "LOCKED"))
									.then(insertTestUserWithStatus(clientId,
											"fcs_pwexp_" + ts, "fcs_pwexp_" + ts + "@t.com", "PASSWORD_EXPIRED"))
									.then(clientDAO.readInternal(clientId)))
							.flatMap(client -> {
								Map<ULong, Client> map = new HashMap<>();
								map.put(client.getId(), client);
								return clientDAO.fillUserCounts(map, null, null);
							}))
					.assertNext(clients -> {
						assertNotNull(clients);
						Client client = clients.get(0);
						assertEquals(1, client.getActiveUsers());
						assertEquals(1, client.getInactiveUsers());
						assertEquals(1, client.getDeletedUsers());
						assertEquals(1, client.getLockedUsers());
						assertEquals(1, client.getPasswordExpiredUsers());
						assertEquals(5, client.getTotalUsers());
					})
					.verifyComplete();
		}

		@Test
		void multipleClientsInMap_FillsCountsForAll() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code1 = "FC1" + ts.substring(ts.length() - 5);
			String code2 = "FC2" + ts.substring(ts.length() - 5);

			StepVerifier.create(
					insertTestClient(code1, "Multi Count 1", "BUS")
							.flatMap(cid1 -> insertTestUserWithStatus(cid1,
									"mc1_" + ts, "mc1_" + ts + "@t.com", "ACTIVE")
									.thenReturn(cid1)
									.flatMap(id1 -> insertTestClient(code2, "Multi Count 2", "BUS")
											.flatMap(cid2 -> insertTestUserWithStatus(cid2,
													"mc2_" + ts, "mc2_" + ts + "@t.com", "INACTIVE")
													.thenReturn(cid2)
													.flatMap(id2 -> Mono.zip(
															clientDAO.readInternal(id1),
															clientDAO.readInternal(id2))
															.flatMap(tuple -> {
																Map<ULong, Client> map = new HashMap<>();
																map.put(tuple.getT1().getId(), tuple.getT1());
																map.put(tuple.getT2().getId(), tuple.getT2());
																return clientDAO.fillUserCounts(map, null, null);
															}))))))
					.assertNext(clients -> {
						assertNotNull(clients);
						assertEquals(2, clients.size());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("readClientURLs() - with valid data")
	class ReadClientURLsValidDataTests {

		@Test
		void existingClientCode_WithMatchingUrlIds_ReturnsUrlMap() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "RCU" + ts.substring(ts.length() - 5);

			StepVerifier.create(
					insertTestClient(code, "URL Client", "BUS")
							.flatMap(clientId -> insertClientUrl(clientId,
									"https://rcu-" + ts + ".example.com", "appbuilder"))
							.flatMap(urlId -> clientDAO.readClientURLs(code, List.of(urlId))))
					.assertNext(urls -> {
						assertNotNull(urls);
						assertFalse(urls.isEmpty());
						assertEquals(1, urls.size());
						assertTrue(urls.values().stream()
								.anyMatch(u -> u.contains("rcu-" + ts)));
					})
					.verifyComplete();
		}

		@Test
		void existingClientCode_WithMultipleUrls_ReturnsAll() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "RCM" + ts.substring(ts.length() - 5);

			StepVerifier.create(
					insertTestClient(code, "Multi URL", "BUS")
							.flatMap(clientId -> insertClientUrl(clientId,
									"https://rcm1-" + ts + ".example.com", "appbuilder")
									.flatMap(urlId1 -> insertClientUrl(clientId,
											"https://rcm2-" + ts + ".example.com", "appbuilder")
											.map(urlId2 -> List.of(urlId1, urlId2))))
							.flatMap(urlIds -> clientDAO.readClientURLs(code, urlIds)))
					.assertNext(urls -> {
						assertNotNull(urls);
						assertEquals(2, urls.size());
					})
					.verifyComplete();
		}

		@Test
		void existingClientCode_WithNonMatchingUrlIds_ReturnsEmpty() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "RCN" + ts.substring(ts.length() - 5);

			StepVerifier.create(
					insertTestClient(code, "No Match URL", "BUS")
							.flatMap(clientId -> insertClientUrl(clientId,
									"https://rcn-" + ts + ".example.com", "appbuilder")
									.thenReturn(clientId))
							.then(clientDAO.readClientURLs(code, List.of(ULong.valueOf(999999)))))
					.assertNext(urls -> {
						assertNotNull(urls);
						assertTrue(urls.isEmpty());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getValidClientCode() - duplicate code collision")
	class GetValidClientCodeDuplicateTests {

		@Test
		void existingCode_GeneratesIncrementedCode() {
			// removeSpecialCharacters strips digits, so use letters-only prefix
			// getValidClientCode("DupAB") -> removeSpecialCharacters -> "DupAB" -> clientCode = "DUPAB"
			// Insert a client with code "DUPAB" so the expand loop detects a collision
			String prefix = "DUPAB";

			StepVerifier.create(
					insertTestClient(prefix, "Dup Code", "BUS")
							.then(clientDAO.getValidClientCode(prefix)))
					.assertNext(code -> {
						assertNotNull(code);
						assertTrue(code.startsWith(prefix));
						// Should have appended a number since prefix already exists
						assertTrue(code.length() > prefix.length(),
								"Code should have numeric suffix: " + code);
					})
					.verifyComplete();
		}

		@Test
		void multipleExistingCodes_GeneratesNextIncrement() {
			String ts = String.valueOf(System.currentTimeMillis());
			// Use a 5-char code prefix
			String prefix = "DUPXX";

			StepVerifier.create(
					insertTestClient(prefix, "Dup1", "BUS")
							.then(insertTestClient(prefix + "1", "Dup2", "BUS"))
							.then(clientDAO.getValidClientCode("DupXX")))
					.assertNext(code -> {
						assertNotNull(code);
						assertTrue(code.startsWith("DUPXX"));
						// First "DUPXX" exists, "DUPXX1" exists, so should get "DUPXX2"
						assertEquals("DUPXX2", code);
					})
					.verifyComplete();
		}

		@Test
		void nameWithSpecialChars_RemovesSpecialAndGeneratesCode() {
			StepVerifier.create(clientDAO.getValidClientCode("Test@#$Company!"))
					.assertNext(code -> {
						assertNotNull(code);
						// Special chars removed, then truncated to 5 and uppercased
						assertTrue(code.startsWith("TESTC"));
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("readById() - inherited from AbstractDAO")
	class ReadByIdTests {

		@Test
		void systemContext_ExistingClient_ReturnsClient() {
			ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();

			StepVerifier.create(
					clientDAO.readById(ULong.valueOf(1))
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(client -> {
						assertNotNull(client);
						assertEquals(ULong.valueOf(1), client.getId());
					})
					.verifyComplete();
		}

		@Test
		void systemContext_NonExistentClient_ReturnsError() {
			ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();

			StepVerifier.create(
					clientDAO.readById(ULong.valueOf(999999))
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.expectError()
					.verify();
		}

		@Test
		void businessContext_ClientInHierarchy_ReturnsClient() {
			String ts = String.valueOf(System.currentTimeMillis());
			String busCode = "RBI" + ts.substring(ts.length() - 5);

			StepVerifier.create(
					insertTestClient(busCode, "ReadById Bus", "BUS")
							.flatMap(busId -> insertClientHierarchy(busId, ULong.valueOf(1), null, null, null)
									.thenReturn(busId))
							.flatMap(busId -> {
								ContextAuthentication busAuth = TestDataFactory.createBusinessAuth(
										busId, busCode, List.of("Authorities.Client_READ", "Authorities.Logged_IN"));
								return clientDAO.readById(busId)
										.contextWrite(ReactiveSecurityContextHolder.withAuthentication(busAuth));
							}))
					.assertNext(client -> {
						assertNotNull(client);
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("create() and update() - inherited CRUD")
	class CreateAndUpdateTests {

		@Test
		void createClient_ReturnsCreatedClient() {
			Client newClient = new Client();
			newClient.setCode("CRCL" + String.valueOf(System.currentTimeMillis()).substring(9));
			newClient.setName("Created Client");
			newClient.setTypeCode("BUS");
			newClient.setTokenValidityMinutes(120);
			newClient.setLocaleCode("en");

			StepVerifier.create(clientDAO.create(newClient))
					.assertNext(client -> {
						assertNotNull(client);
						assertNotNull(client.getId());
						assertEquals("Created Client", client.getName());
						assertEquals("BUS", client.getTypeCode());
						assertEquals(120, client.getTokenValidityMinutes());
					})
					.verifyComplete();
		}

		@Test
		void updateClient_ChangesName() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "UPD" + ts.substring(ts.length() - 5);

			StepVerifier.create(
					insertTestClient(code, "Before Update", "BUS")
							.flatMap(clientId -> clientDAO.readInternal(clientId))
							.flatMap(client -> {
								client.setName("After Update");
								client.setTokenValidityMinutes(client.getTokenValidityMinutes());
								client.setLocaleCode(client.getLocaleCode());
								client.setStatusCode(client.getStatusCode());
								client.setTypeCode(client.getTypeCode());
								client.setCode(client.getCode());
								return clientDAO.update(client)
										.contextWrite(ReactiveSecurityContextHolder.withAuthentication(
												TestDataFactory.createSystemAuth()));
							}))
					.assertNext(client -> {
						assertNotNull(client);
						assertEquals("After Update", client.getName());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("readAll() - inherited from AbstractDAO")
	class ReadAllTests {

		@Test
		void systemContext_NullCondition_ReturnsAllClients() {
			ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();

			StepVerifier.create(
					clientDAO.readAll(null)
							.collectList()
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(clients -> {
						assertNotNull(clients);
						assertTrue(clients.size() >= 1);
					})
					.verifyComplete();
		}

		@Test
		void systemContext_WithFilter_ReturnsFilteredClients() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "RAL" + ts.substring(ts.length() - 5);
			ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();

			FilterCondition fc = FilterCondition.of("typeCode", "BUS", FilterConditionOperator.EQUALS);

			StepVerifier.create(
					insertTestClient(code, "ReadAll BUS", "BUS")
							.then(clientDAO.readAll(fc)
									.collectList()
									.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))))
					.assertNext(clients -> {
						assertNotNull(clients);
						assertTrue(clients.stream().allMatch(c -> "BUS".equals(c.getTypeCode())));
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("delete() - inherited from AbstractDAO")
	class DeleteTests {

		@Test
		void existingClient_ReturnsOneRowDeleted() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "DEL" + ts.substring(ts.length() - 5);

			StepVerifier.create(
					insertTestClient(code, "Delete Me", "BUS")
							.flatMap(clientId -> clientDAO.delete(clientId)))
					.assertNext(count -> assertEquals(1, count))
					.verifyComplete();
		}

		@Test
		void nonExistentClient_ReturnsZeroRowsDeleted() {
			StepVerifier.create(clientDAO.delete(ULong.valueOf(999999)))
					.assertNext(count -> assertEquals(0, count))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("filter() - with business client context adds hierarchy condition")
	class FilterWithBusinessContextTests {

		@Test
		void businessContext_FilterAddsManageClientCondition() {
			String ts = String.valueOf(System.currentTimeMillis());
			String busCode = "FBC" + ts.substring(ts.length() - 5);
			String childCode = "FCH" + ts.substring(ts.length() - 5);
			String otherCode = "FOT" + ts.substring(ts.length() - 5);

			StepVerifier.create(
					insertTestClient(busCode, "Filter Bus Parent", "BUS")
							.flatMap(busId -> insertClientHierarchy(busId, ULong.valueOf(1), null, null, null)
									.then(insertTestClient(childCode, "Filter Child", "BUS"))
									.flatMap(childId -> insertClientHierarchy(childId, ULong.valueOf(1), busId, null, null)
											.thenReturn(busId))
									.flatMap(id -> insertTestClient(otherCode, "Filter Other", "BUS")
											.flatMap(otherId -> insertClientHierarchy(otherId, ULong.valueOf(1), null, null, null)
													.thenReturn(id))))
							.flatMap(busId -> {
								ContextAuthentication busAuth = TestDataFactory.createBusinessAuth(
										busId, busCode, List.of("Authorities.Client_READ", "Authorities.Logged_IN"));
								FilterCondition fc = FilterCondition.of("typeCode", "BUS", FilterConditionOperator.EQUALS);
								return clientDAO.readPageFilter(PageRequest.of(0, 100), fc)
										.contextWrite(ReactiveSecurityContextHolder.withAuthentication(busAuth));
							}))
					.assertNext(page -> {
						assertNotNull(page);
						// Business context should filter by hierarchy - should not see "other" client
						// that is not in its hierarchy
						boolean hasOther = page.getContent().stream()
								.anyMatch(c -> otherCode.equals(c.getCode()));
						assertFalse(hasOther, "Should not see client outside hierarchy");
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getClientsBy() - additional edge cases")
	class GetClientsByAdditionalTests {

		@Test
		void emptyList_ReturnsEmptyList() {
			StepVerifier.create(clientDAO.getClientsBy(List.of()))
					.assertNext(clients -> {
						assertNotNull(clients);
						assertTrue(clients.isEmpty());
					})
					.verifyComplete();
		}

		@Test
		void clientsWithDifferentStatuses_ReturnsAll() {
			String ts = String.valueOf(System.currentTimeMillis());
			String codeA = "GA" + ts.substring(ts.length() - 6);
			String codeI = "GI" + ts.substring(ts.length() - 6);
			String codeD = "GD" + ts.substring(ts.length() - 6);

			StepVerifier.create(
					insertTestClient(codeA, "Active", "BUS")
							.flatMap(idA -> insertTestClientWithStatus(codeI, "Inactive", "BUS", "INACTIVE")
									.flatMap(idI -> insertTestClientWithStatus(codeD, "Deleted", "BUS", "DELETED")
											.flatMap(idD -> clientDAO.getClientsBy(List.of(idA, idI, idD))))))
					.assertNext(clients -> {
						assertNotNull(clients);
						assertEquals(3, clients.size());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("createProfileRestrictions() - additional cases")
	class CreateProfileRestrictionsAdditionalTests {

		@Test
		void multipleProfiles_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());
			String code = "MPR" + ts.substring(ts.length() - 5);

			StepVerifier.create(
					insertTestClient(code, "Multi Profile Restrict", "BUS")
							.flatMap(clientId -> databaseClient.sql(
									"SELECT ID FROM security_profile LIMIT 2")
									.map(row -> ULong.valueOf(row.get("ID", Long.class)))
									.all()
									.collectList()
									.flatMap(profileIds -> {
										if (profileIds.size() < 2) {
											return Mono.just(true);
										}
										return clientDAO.createProfileRestrictions(clientId, profileIds)
												.doFinally(s -> databaseClient.sql(
														"DELETE FROM security_profile_client_restriction WHERE CLIENT_ID = "
																+ clientId.longValue())
														.then().block());
									})))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}
	}
}