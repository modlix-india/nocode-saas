package com.fincity.security.integration.dao;

import static org.junit.jupiter.api.Assertions.*;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import com.fincity.security.dao.appregistration.AppRegistrationV2DAO;
import com.fincity.security.dto.appregistration.AppRegistrationAccess;
import com.fincity.security.dto.appregistration.AppRegistrationDepartment;
import com.fincity.security.dto.appregistration.AppRegistrationFileAccess;
import com.fincity.security.enums.AppRegistrationObjectType;
import com.fincity.security.enums.ClientLevelType;
import com.fincity.security.integration.AbstractIntegrationTest;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.function.Tuple2;

class AppRegistrationV2DAOIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private AppRegistrationV2DAO appRegistrationV2DAO;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);

	@BeforeEach
	void setUp() {
		setupMockBeans();
	}

	@AfterEach
	void tearDown() {
		databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
				.then(databaseClient.sql("DELETE FROM security_app_reg_user_designation WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_app_reg_designation WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_app_reg_department WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_app_reg_profile_restriction WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_app_reg_user_profile WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_app_reg_user_role_v2 WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_app_reg_file_access WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_app_reg_access WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_app WHERE APP_CODE NOT IN ('appbuilder', 'nothing')").then())
				.then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}

	// ---- Helper methods for raw SQL insertion ----

	private Mono<ULong> insertAppRegAccess(ULong clientId, ULong appId, ULong allowAppId,
			String clientType, String level, boolean writeAccess, boolean register) {
		return databaseClient.sql(
				"INSERT INTO security_app_reg_access (CLIENT_ID, APP_ID, ALLOW_APP_ID, CLIENT_TYPE, LEVEL, BUSINESS_TYPE, WRITE_ACCESS, REGISTER) "
						+
						"VALUES (:clientId, :appId, :allowAppId, :clientType, :level, 'COMMON', :writeAccess, :register)")
				.bind("clientId", clientId.longValue())
				.bind("appId", appId.longValue())
				.bind("allowAppId", allowAppId.longValue())
				.bind("clientType", clientType)
				.bind("level", level)
				.bind("writeAccess", writeAccess ? 1 : 0)
				.bind("register", register ? 1 : 0)
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	private Mono<ULong> insertAppRegFileAccess(ULong clientId, ULong appId,
			String clientType, String level, String resourceType, String accessName,
			boolean writeAccess, String path, boolean allowSubPathAccess) {
		return databaseClient.sql(
				"INSERT INTO security_app_reg_file_access (CLIENT_ID, APP_ID, CLIENT_TYPE, LEVEL, BUSINESS_TYPE, RESOURCE_TYPE, ACCESS_NAME, WRITE_ACCESS, PATH, ALLOW_SUB_PATH_ACCESS) "
						+
						"VALUES (:clientId, :appId, :clientType, :level, 'COMMON', :resourceType, :accessName, :writeAccess, :path, :allowSubPathAccess)")
				.bind("clientId", clientId.longValue())
				.bind("appId", appId.longValue())
				.bind("clientType", clientType)
				.bind("level", level)
				.bind("resourceType", resourceType)
				.bind("accessName", accessName)
				.bind("writeAccess", writeAccess ? 1 : 0)
				.bind("path", path)
				.bind("allowSubPathAccess", allowSubPathAccess ? 1 : 0)
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	private Mono<ULong> insertAppRegUserRole(ULong clientId, ULong appId, ULong roleId,
			String clientType, String level) {
		return databaseClient.sql(
				"INSERT INTO security_app_reg_user_role_v2 (CLIENT_ID, APP_ID, ROLE_ID, CLIENT_TYPE, LEVEL, BUSINESS_TYPE) "
						+
						"VALUES (:clientId, :appId, :roleId, :clientType, :level, 'COMMON')")
				.bind("clientId", clientId.longValue())
				.bind("appId", appId.longValue())
				.bind("roleId", roleId.longValue())
				.bind("clientType", clientType)
				.bind("level", level)
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	private Mono<ULong> insertAppRegUserProfile(ULong clientId, ULong appId, ULong profileId,
			String clientType, String level) {
		return databaseClient.sql(
				"INSERT INTO security_app_reg_user_profile (CLIENT_ID, APP_ID, PROFILE_ID, CLIENT_TYPE, LEVEL, BUSINESS_TYPE) "
						+
						"VALUES (:clientId, :appId, :profileId, :clientType, :level, 'COMMON')")
				.bind("clientId", clientId.longValue())
				.bind("appId", appId.longValue())
				.bind("profileId", profileId.longValue())
				.bind("clientType", clientType)
				.bind("level", level)
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	private Mono<ULong> insertAppRegDepartment(ULong clientId, ULong appId, String name,
			String clientType, String level) {
		return databaseClient.sql(
				"INSERT INTO security_app_reg_department (CLIENT_ID, APP_ID, NAME, CLIENT_TYPE, LEVEL, BUSINESS_TYPE) "
						+
						"VALUES (:clientId, :appId, :name, :clientType, :level, 'COMMON')")
				.bind("clientId", clientId.longValue())
				.bind("appId", appId.longValue())
				.bind("name", name)
				.bind("clientType", clientType)
				.bind("level", level)
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	private Mono<ULong> insertAppRegProfileRestriction(ULong clientId, ULong appId, ULong profileId,
			String clientType, String level) {
		return databaseClient.sql(
				"INSERT INTO security_app_reg_profile_restriction (CLIENT_ID, APP_ID, PROFILE_ID, CLIENT_TYPE, LEVEL, BUSINESS_TYPE) "
						+
						"VALUES (:clientId, :appId, :profileId, :clientType, :level, 'COMMON')")
				.bind("clientId", clientId.longValue())
				.bind("appId", appId.longValue())
				.bind("profileId", profileId.longValue())
				.bind("clientType", clientType)
				.bind("level", level)
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	private Mono<ULong> getAnyRoleId() {
		return databaseClient.sql("SELECT ID FROM security_v2_role LIMIT 1")
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	private Mono<ULong> getAnyProfileId() {
		return databaseClient.sql("SELECT ID FROM security_profile LIMIT 1")
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	// ---- CRUD Tests ----

	@Nested
	@DisplayName("create() and getById()")
	class CreateAndGetByIdTests {

		@Test
		@DisplayName("APPLICATION_ACCESS: create and retrieve roundtrip")
		void createAppAccess_ThenGetById_ReturnsCreatedEntity() {
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, "CRUDAPP", "CRUD App")
							.flatMap(appId -> insertTestApp(SYSTEM_CLIENT_ID, "ALLOWAP", "Allow App")
									.flatMap(allowAppId -> {
										AppRegistrationAccess access = new AppRegistrationAccess();
										access.setClientId(SYSTEM_CLIENT_ID);
										access.setAppId(appId);
										access.setAllowAppId(allowAppId);
										access.setClientType("BUS");
										access.setLevel(ClientLevelType.CLIENT);
										access.setBusinessType("COMMON");
										access.setWriteAccess(true);
										access.setRegister(false);

										return appRegistrationV2DAO
												.create(AppRegistrationObjectType.APPLICATION_ACCESS, access);
									})))
					.assertNext(created -> {
						assertNotNull(created);
						assertNotNull(created.getId());
						assertEquals(SYSTEM_CLIENT_ID, created.getClientId());
						assertEquals("BUS", created.getClientType());
						assertEquals(ClientLevelType.CLIENT, created.getLevel());
						assertTrue(created instanceof AppRegistrationAccess);
						assertTrue(((AppRegistrationAccess) created).isWriteAccess());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("DEPARTMENT: create and retrieve roundtrip")
		void createDepartment_ThenGetById_ReturnsCreatedEntity() {
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, "DEPTAPP", "Dept App")
							.flatMap(appId -> {
								AppRegistrationDepartment dept = new AppRegistrationDepartment();
								dept.setClientId(SYSTEM_CLIENT_ID);
								dept.setAppId(appId);
								dept.setName("Engineering");
								dept.setClientType("BUS");
								dept.setLevel(ClientLevelType.CLIENT);
								dept.setBusinessType("COMMON");

								return appRegistrationV2DAO
										.create(AppRegistrationObjectType.DEPARTMENT, dept);
							}))
					.assertNext(created -> {
						assertNotNull(created);
						assertNotNull(created.getId());
						assertTrue(created instanceof AppRegistrationDepartment);
						assertEquals("Engineering", ((AppRegistrationDepartment) created).getName());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("getById for non-existent ID returns empty")
		void getByIdNonExistent_ReturnsEmpty() {
			StepVerifier.create(
					appRegistrationV2DAO.getById(AppRegistrationObjectType.APPLICATION_ACCESS,
							ULong.valueOf(999999)))
					.verifyComplete();
		}
	}

	// ---- Delete Tests ----

	@Nested
	@DisplayName("delete()")
	class DeleteTests {

		@Test
		@DisplayName("delete existing entry returns true")
		void deleteExistingEntry_ReturnsTrue() {
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, "DELAPP", "Del App")
							.flatMap(appId -> insertTestApp(SYSTEM_CLIENT_ID, "DELALLW", "Del Allow")
									.flatMap(allowAppId -> insertAppRegAccess(SYSTEM_CLIENT_ID, appId,
											allowAppId, "BUS", "CLIENT", false, false)))
							.flatMap(id -> appRegistrationV2DAO.delete(
									AppRegistrationObjectType.APPLICATION_ACCESS, id)))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("delete non-existent entry returns false")
		void deleteNonExistent_ReturnsFalse() {
			StepVerifier.create(
					appRegistrationV2DAO.delete(AppRegistrationObjectType.APPLICATION_ACCESS,
							ULong.valueOf(999999)))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("delete then getById returns empty")
		void deleteThenGetById_ReturnsEmpty() {
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, "DLGTAPP", "DelGet App")
							.flatMap(appId -> insertTestApp(SYSTEM_CLIENT_ID, "DLGTALW", "DelGet Allow")
									.flatMap(allowAppId -> insertAppRegAccess(SYSTEM_CLIENT_ID, appId,
											allowAppId, "BUS", "CLIENT", false, false)))
							.flatMap(id -> appRegistrationV2DAO
									.delete(AppRegistrationObjectType.APPLICATION_ACCESS, id)
									.then(appRegistrationV2DAO.getById(
											AppRegistrationObjectType.APPLICATION_ACCESS, id))))
					.verifyComplete();
		}
	}

	// ---- Paginated get() Tests ----

	@Nested
	@DisplayName("get() paginated query")
	class GetPaginatedTests {

		@Test
		@DisplayName("returns all entries when no filters applied")
		void noFilters_ReturnsAllEntries() {
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, "PGALL1", "PgAll App1")
							.flatMap(appId -> insertTestApp(SYSTEM_CLIENT_ID, "PGALL2", "PgAll App2")
									.flatMap(allowAppId -> insertAppRegAccess(SYSTEM_CLIENT_ID, appId,
											allowAppId, "BUS", "CLIENT", false, false)
											.then(insertAppRegAccess(SYSTEM_CLIENT_ID, appId, appId,
													"BUS", "CUSTOMER", true, false))))
							.then(appRegistrationV2DAO.get(
									AppRegistrationObjectType.APPLICATION_ACCESS,
									null, null, null, null, null,
									PageRequest.of(0, 10))))
					.assertNext(page -> {
						assertNotNull(page);
						assertTrue(page.getTotalElements() >= 2);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("filter by appId returns matching entries only")
		void filterByAppId_ReturnsMatchingEntries() {
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, "PGFLT1", "PgFlt App1")
							.flatMap(appId1 -> insertTestApp(SYSTEM_CLIENT_ID, "PGFLT2", "PgFlt App2")
									.flatMap(appId2 -> insertAppRegAccess(SYSTEM_CLIENT_ID, appId1,
											appId2, "BUS", "CLIENT", false, false)
											.thenReturn(appId1)))
							.flatMap(appId1 -> appRegistrationV2DAO.get(
									AppRegistrationObjectType.APPLICATION_ACCESS,
									appId1, null, null, null, null,
									PageRequest.of(0, 10))))
					.assertNext(page -> {
						assertNotNull(page);
						assertEquals(1, page.getTotalElements());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("filter by clientId and appId returns matching entries")
		void filterByClientIdAndAppId_ReturnsMatchingEntries() {
			StepVerifier.create(
					insertTestClient("PGCLNT", "PgClnt", "BUS")
							.flatMap(clientId -> insertTestApp(clientId, "PGCA1", "PgCa App1")
									.flatMap(appId -> insertTestApp(clientId, "PGCA2", "PgCa App2")
											.flatMap(allowAppId -> insertAppRegAccess(clientId, appId,
													allowAppId, "BUS", "CLIENT", false, false)
													.thenReturn(appId))
											.flatMap(appId2 -> appRegistrationV2DAO.get(
													AppRegistrationObjectType.APPLICATION_ACCESS,
													appId2, clientId, null, null, null,
													PageRequest.of(0, 10))))))
					.assertNext(page -> {
						assertNotNull(page);
						assertEquals(1, page.getTotalElements());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("empty results return empty page")
		void noMatchingEntries_ReturnsEmptyPage() {
			StepVerifier.create(
					appRegistrationV2DAO.get(
							AppRegistrationObjectType.APPLICATION_ACCESS,
							ULong.valueOf(999999), null, null, null, null,
							PageRequest.of(0, 10)))
					.assertNext(page -> {
						assertNotNull(page);
						assertEquals(0, page.getTotalElements());
						assertTrue(page.getContent().isEmpty());
					})
					.verifyComplete();
		}
	}

	// ---- Client Preference / Registration Tests ----

	@Nested
	@DisplayName("getAppIdsForRegistration() client preference logic")
	class GetAppIdsForRegistrationTests {

		@Test
		@DisplayName("no data for either client returns default list with appId")
		void noData_ReturnsDefaultWithAppId() {
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, "NODATA", "No Data App")
							.flatMap(appId -> appRegistrationV2DAO.getAppIdsForRegistration(
									appId, SYSTEM_CLIENT_ID, ULong.valueOf(999),
									"BUS", ClientLevelType.CLIENT, "COMMON")))
					.assertNext(list -> {
						assertEquals(1, list.size());
						Tuple2<ULong, Boolean> first = list.get(0);
						assertFalse(first.getT2());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("only appClientId has data returns appClientId results")
		void onlyAppClientHasData_ReturnsAppClientResults() {
			StepVerifier.create(
					insertTestClient("APPCLR", "AppCl Reg", "BUS")
							.flatMap(appClientId -> insertTestApp(SYSTEM_CLIENT_ID, "ACREGA", "ACReg App")
									.flatMap(appId -> insertTestApp(SYSTEM_CLIENT_ID, "ACREGB", "ACReg Allow")
											.flatMap(allowAppId -> insertAppRegAccess(appClientId, appId,
													allowAppId, "BUS", "CLIENT", true, false)
													.then(appRegistrationV2DAO.getAppIdsForRegistration(
															appId, appClientId, ULong.valueOf(999999),
															"BUS", ClientLevelType.CLIENT, "COMMON"))))))
					.assertNext(list -> {
						assertFalse(list.isEmpty());
						// First should be the appId itself (reordered to front)
						assertNotNull(list.get(0).getT1());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("both clients have data returns urlClientId results (preferred)")
		void bothClientsHaveData_PrefersUrlClient() {
			StepVerifier.create(
					insertTestClient("URLPRF", "Url Pref", "BUS")
							.flatMap(urlClientId -> insertTestClient("APPPRF", "App Pref", "BUS")
									.flatMap(appClientId -> insertTestApp(SYSTEM_CLIENT_ID, "PRFAPP", "Prf App")
											.flatMap(appId -> insertTestApp(SYSTEM_CLIENT_ID, "PRFAL1",
													"Prf Allow1")
													.flatMap(allowApp1 -> insertTestApp(SYSTEM_CLIENT_ID, "PRFAL2",
															"Prf Allow2")
															.flatMap(allowApp2 ->
															// Insert for appClient with writeAccess=false
															insertAppRegAccess(appClientId, appId, allowApp1,
																	"BUS", "CLIENT", false, false)
																	// Insert for urlClient with writeAccess=true
																	.then(insertAppRegAccess(urlClientId, appId,
																			allowApp2, "BUS", "CLIENT", true, false))
																	.then(appRegistrationV2DAO
																			.getAppIdsForRegistration(
																					appId, appClientId, urlClientId,
																					"BUS", ClientLevelType.CLIENT,
																					"COMMON"))))))))
					.assertNext(list -> {
						assertFalse(list.isEmpty());
						// urlClient's results are preferred; at least one entry should have
						// writeAccess=true
						boolean hasWriteAccess = list.stream().anyMatch(Tuple2::getT2);
						assertTrue(hasWriteAccess);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("appId is always first in the returned list")
		void appIdAlwaysFirst() {
			StepVerifier.create(
					insertTestClient("FRSTRA", "First App", "BUS")
							.flatMap(clientId -> insertTestApp(SYSTEM_CLIENT_ID, "FRSTAP", "First App")
									.flatMap(appId -> insertTestApp(SYSTEM_CLIENT_ID, "FRSTA2", "First Allow")
											.flatMap(allowAppId ->
											// Insert allowApp first (not the appId itself)
											insertAppRegAccess(clientId, appId, allowAppId,
													"BUS", "CLIENT", false, false)
													// Then insert the appId entry
													.then(insertAppRegAccess(clientId, appId, appId,
															"BUS", "CLIENT", true, false))
													.then(appRegistrationV2DAO.getAppIdsForRegistration(
															appId, clientId, clientId,
															"BUS", ClientLevelType.CLIENT, "COMMON"))))))
					.assertNext(list -> {
						assertTrue(list.size() >= 2);
						// The appId should be reordered to the first position
						assertNotNull(list.get(0).getT1());
					})
					.verifyComplete();
		}
	}

	// ---- Additional Registration Tests ----

	@Nested
	@DisplayName("getAppIdsForRegistrationForAdditionalRegistration()")
	class GetAppIdsForAdditionalRegistrationTests {

		@Test
		@DisplayName("no data returns default list with appId")
		void noData_ReturnsDefaultWithAppId() {
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, "ADNODA", "AdNoData App")
							.flatMap(appId -> appRegistrationV2DAO
									.getAppIdsForRegistrationForAdditionalRegistration(
											appId, SYSTEM_CLIENT_ID, ULong.valueOf(999),
											"BUS", ClientLevelType.CLIENT, "COMMON")))
					.assertNext(list -> {
						assertEquals(1, list.size());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("only REGISTER=1 entries are considered")
		void onlyRegisterEntriesConsidered() {
			StepVerifier.create(
					insertTestClient("ADREGC", "AdReg Cl", "BUS")
							.flatMap(clientId -> insertTestApp(SYSTEM_CLIENT_ID, "ADREG1", "AdReg App")
									.flatMap(appId -> insertTestApp(SYSTEM_CLIENT_ID, "ADREG2", "AdReg Allow")
											.flatMap(allowAppId ->
											// Insert with register=false - should NOT be included
											insertAppRegAccess(clientId, appId, allowAppId,
													"BUS", "CLIENT", false, false)
													.then(appRegistrationV2DAO
															.getAppIdsForRegistrationForAdditionalRegistration(
																	appId, clientId, clientId,
																	"BUS", ClientLevelType.CLIENT, "COMMON"))))))
					.assertNext(list -> {
						// register=false entries are excluded, so default result with just appId
						assertEquals(1, list.size());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("REGISTER=1 entries are returned with client preference")
		void registerEntriesReturned_WithClientPreference() {
			StepVerifier.create(
					insertTestClient("ADRGRC", "AdRgReg Cl", "BUS")
							.flatMap(clientId -> insertTestApp(SYSTEM_CLIENT_ID, "ADRGR1", "AdRgR App")
									.flatMap(appId -> insertTestApp(SYSTEM_CLIENT_ID, "ADRGR2", "AdRgR Allow")
											.flatMap(allowAppId ->
											// Insert with register=true
											insertAppRegAccess(clientId, appId, allowAppId,
													"BUS", "CLIENT", false, true)
													.then(appRegistrationV2DAO
															.getAppIdsForRegistrationForAdditionalRegistration(
																	appId, clientId, clientId,
																	"BUS", ClientLevelType.CLIENT, "COMMON"))))))
					.assertNext(list -> {
						assertTrue(list.size() >= 1);
						// appId should be first
						assertNotNull(list.get(0));
					})
					.verifyComplete();
		}
	}

	// ---- File Access Registration Tests ----

	@Nested
	@DisplayName("getFileAccessForRegistration()")
	class GetFileAccessForRegistrationTests {

		@Test
		@DisplayName("no data returns empty list")
		void noData_ReturnsEmpty() {
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, "FANODA", "FaNoData App")
							.flatMap(appId -> appRegistrationV2DAO.getFileAccessForRegistration(
									appId, SYSTEM_CLIENT_ID, ULong.valueOf(999),
									"BUS", ClientLevelType.CLIENT, "COMMON")))
					.assertNext(list -> assertTrue(list.isEmpty()))
					.verifyComplete();
		}

		@Test
		@DisplayName("returns file access entries for appClient when only appClient has data")
		void onlyAppClient_ReturnsData() {
			StepVerifier.create(
					insertTestClient("FACLA", "FaCl App", "BUS")
							.flatMap(clientId -> insertTestApp(SYSTEM_CLIENT_ID, "FACL1A", "FaCl App1")
									.flatMap(appId -> insertAppRegFileAccess(clientId, appId,
											"BUS", "CLIENT", "STATIC", "hasRole('Manager')",
											false, "/files/images", true)
											.then(appRegistrationV2DAO.getFileAccessForRegistration(
													appId, clientId, ULong.valueOf(999999),
													"BUS", ClientLevelType.CLIENT, "COMMON")))))
					.assertNext(list -> {
						assertFalse(list.isEmpty());
						AppRegistrationFileAccess fa = list.get(0);
						assertEquals("STATIC", fa.getResourceType());
						assertEquals("/files/images", fa.getPath());
						assertTrue(fa.isAllowSubPathAccess());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("prefers urlClient data over appClient data")
		void prefersUrlClient() {
			StepVerifier.create(
					insertTestClient("FAURLC", "FaUrl Cl", "BUS")
							.flatMap(urlClientId -> insertTestClient("FAAPPC", "FaApp Cl", "BUS")
									.flatMap(appClientId -> insertTestApp(SYSTEM_CLIENT_ID, "FAPREF", "FaPref App")
											.flatMap(appId -> insertAppRegFileAccess(appClientId, appId,
													"BUS", "CLIENT", "STATIC", "appClientAccess",
													false, "/app/path", false)
													.then(insertAppRegFileAccess(urlClientId, appId,
															"BUS", "CLIENT", "SECURED", "urlClientAccess",
															true, "/url/path", true))
													.then(appRegistrationV2DAO.getFileAccessForRegistration(
															appId, appClientId, urlClientId,
															"BUS", ClientLevelType.CLIENT, "COMMON"))))))
					.assertNext(list -> {
						assertFalse(list.isEmpty());
						// All entries should be from urlClient
						AppRegistrationFileAccess fa = list.get(0);
						assertEquals("urlClientAccess", fa.getAccessName());
						assertEquals("SECURED", fa.getResourceType());
					})
					.verifyComplete();
		}
	}

	// ---- Role IDs Registration Tests ----

	@Nested
	@DisplayName("getRoleIdsForUserRegistration()")
	class GetRoleIdsForUserRegistrationTests {

		@Test
		@DisplayName("no data returns empty list")
		void noData_ReturnsEmpty() {
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, "RLNODA", "RlNoData App")
							.flatMap(appId -> appRegistrationV2DAO.getRoleIdsForUserRegistration(
									appId, SYSTEM_CLIENT_ID, ULong.valueOf(999),
									"BUS", ClientLevelType.CLIENT, "COMMON")))
					.assertNext(list -> assertTrue(list.isEmpty()))
					.verifyComplete();
		}

		@Test
		@DisplayName("returns role IDs with client preference")
		void withData_ReturnsRoleIds() {
			StepVerifier.create(
					getAnyRoleId()
							.flatMap(roleId -> insertTestClient("RLCLA", "Rl Cl A", "BUS")
									.flatMap(clientId -> insertTestApp(SYSTEM_CLIENT_ID, "RLREGA", "RlReg App")
											.flatMap(appId -> insertAppRegUserRole(clientId, appId, roleId,
													"BUS", "CLIENT")
													.then(appRegistrationV2DAO.getRoleIdsForUserRegistration(
															appId, clientId, clientId,
															"BUS", ClientLevelType.CLIENT, "COMMON"))))))
					.assertNext(list -> {
						assertFalse(list.isEmpty());
						assertEquals(1, list.size());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("prefers urlClient role data over appClient")
		void prefersUrlClientRoles() {
			StepVerifier.create(
					getAnyRoleId()
							.flatMap(roleId -> insertTestClient("RLURLC", "RlUrl Cl", "BUS")
									.flatMap(urlClientId -> insertTestClient("RLAPPC", "RlApp Cl", "BUS")
											.flatMap(appClientId -> insertTestApp(SYSTEM_CLIENT_ID, "RLPREF",
													"RlPref App")
													.flatMap(appId ->
													// appClient has the role
													insertAppRegUserRole(appClientId, appId, roleId,
															"BUS", "CLIENT")
															// urlClient also has the same role
															.then(insertAppRegUserRole(urlClientId, appId, roleId,
																	"BUS", "CLIENT"))
															.then(appRegistrationV2DAO
																	.getRoleIdsForUserRegistration(
																			appId, appClientId, urlClientId,
																			"BUS", ClientLevelType.CLIENT,
																			"COMMON")))))))
					.assertNext(list -> {
						// urlClient data should be preferred
						assertFalse(list.isEmpty());
					})
					.verifyComplete();
		}
	}

	// ---- Profile IDs Registration Tests ----

	@Nested
	@DisplayName("getProfileIdsForUserRegistration()")
	class GetProfileIdsForUserRegistrationTests {

		@Test
		@DisplayName("no data returns empty list")
		void noData_ReturnsEmpty() {
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, "PFNODA", "PfNoData App")
							.flatMap(appId -> appRegistrationV2DAO.getProfileIdsForUserRegistration(
									appId, SYSTEM_CLIENT_ID, ULong.valueOf(999),
									"BUS", ClientLevelType.CLIENT, "COMMON")))
					.assertNext(list -> assertTrue(list.isEmpty()))
					.verifyComplete();
		}

		@Test
		@DisplayName("returns profile IDs for appClient")
		void withData_ReturnsProfileIds() {
			StepVerifier.create(
					getAnyProfileId()
							.flatMap(profileId -> insertTestClient("PFCLDA", "Pf Cl Data", "BUS")
									.flatMap(clientId -> insertTestApp(SYSTEM_CLIENT_ID, "PFREGA", "PfReg App")
											.flatMap(appId -> insertAppRegUserProfile(clientId, appId, profileId,
													"BUS", "CLIENT")
													.then(appRegistrationV2DAO.getProfileIdsForUserRegistration(
															appId, clientId, clientId,
															"BUS", ClientLevelType.CLIENT, "COMMON"))))))
					.assertNext(list -> {
						assertFalse(list.isEmpty());
						assertEquals(1, list.size());
					})
					.verifyComplete();
		}
	}

	// ---- Profile Restriction Registration Tests ----

	@Nested
	@DisplayName("getProfileRestrictionIdsForRegistration()")
	class GetProfileRestrictionIdsTests {

		@Test
		@DisplayName("no data returns empty list")
		void noData_ReturnsEmpty() {
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, "PRNODA", "PrNoData App")
							.flatMap(appId -> appRegistrationV2DAO.getProfileRestrictionIdsForRegistration(
									appId, SYSTEM_CLIENT_ID, ULong.valueOf(999),
									"BUS", ClientLevelType.CLIENT, "COMMON")))
					.assertNext(list -> assertTrue(list.isEmpty()))
					.verifyComplete();
		}

		@Test
		@DisplayName("returns profile restriction IDs for appClient")
		void withData_ReturnsIds() {
			StepVerifier.create(
					getAnyProfileId()
							.flatMap(profileId -> insertTestClient("PRCLDA", "Pr Cl Data", "BUS")
									.flatMap(clientId -> insertTestApp(SYSTEM_CLIENT_ID, "PRREGA", "PrReg App")
											.flatMap(appId -> insertAppRegProfileRestriction(clientId, appId,
													profileId, "BUS", "CLIENT")
													.then(appRegistrationV2DAO
															.getProfileRestrictionIdsForRegistration(
																	appId, clientId, clientId,
																	"BUS", ClientLevelType.CLIENT, "COMMON"))))))
					.assertNext(list -> {
						assertFalse(list.isEmpty());
						assertEquals(1, list.size());
					})
					.verifyComplete();
		}
	}

	// ---- Department Registration Tests ----

	@Nested
	@DisplayName("getDepartmentsForRegistration()")
	class GetDepartmentsForRegistrationTests {

		@Test
		@DisplayName("no data returns empty list")
		void noData_ReturnsEmpty() {
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, "DPNODA", "DpNoData App")
							.flatMap(appId -> appRegistrationV2DAO.getDepartmentsForRegistration(
									appId, SYSTEM_CLIENT_ID, ULong.valueOf(999),
									"BUS", ClientLevelType.CLIENT, "COMMON")))
					.assertNext(list -> assertTrue(list.isEmpty()))
					.verifyComplete();
		}

		@Test
		@DisplayName("returns departments for appClient")
		void withData_ReturnsDepartments() {
			StepVerifier.create(
					insertTestClient("DPCLDA", "Dp Cl Data", "BUS")
							.flatMap(clientId -> insertTestApp(SYSTEM_CLIENT_ID, "DPREGA", "DpReg App")
									.flatMap(appId -> insertAppRegDepartment(clientId, appId, "Sales Dept",
											"BUS", "CLIENT")
											.then(appRegistrationV2DAO.getDepartmentsForRegistration(
													appId, clientId, clientId,
													"BUS", ClientLevelType.CLIENT, "COMMON")))))
					.assertNext(list -> {
						assertFalse(list.isEmpty());
						assertEquals("Sales Dept", list.get(0).getName());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("prefers urlClient departments over appClient")
		void prefersUrlClient() {
			StepVerifier.create(
					insertTestClient("DPURLC", "DpUrl Cl", "BUS")
							.flatMap(urlClientId -> insertTestClient("DPAPPC", "DpApp Cl", "BUS")
									.flatMap(appClientId -> insertTestApp(SYSTEM_CLIENT_ID, "DPPREF",
											"DpPref App")
											.flatMap(appId -> insertAppRegDepartment(appClientId, appId,
													"AppClient Dept", "BUS", "CLIENT")
													.then(insertAppRegDepartment(urlClientId, appId,
															"UrlClient Dept", "BUS", "CLIENT"))
													.then(appRegistrationV2DAO.getDepartmentsForRegistration(
															appId, appClientId, urlClientId,
															"BUS", ClientLevelType.CLIENT, "COMMON"))))))
					.assertNext(list -> {
						assertFalse(list.isEmpty());
						// All returned departments should belong to urlClient
						assertEquals("UrlClient Dept", list.get(0).getName());
					})
					.verifyComplete();
		}
	}

	// ---- Designation Registration Tests ----

	@Nested
	@DisplayName("getDesignationsForRegistration()")
	class GetDesignationsForRegistrationTests {

		@Test
		@DisplayName("no data returns empty list")
		void noData_ReturnsEmpty() {
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, "DSNODA", "DsNoData App")
							.flatMap(appId -> appRegistrationV2DAO.getDesignationsForRegistration(
									appId, SYSTEM_CLIENT_ID, ULong.valueOf(999),
									"BUS", ClientLevelType.CLIENT, "COMMON")))
					.assertNext(list -> assertTrue(list.isEmpty()))
					.verifyComplete();
		}

		@Test
		@DisplayName("returns designations for appClient with department reference")
		void withData_ReturnsDesignations() {
			StepVerifier.create(
					insertTestClient("DSCLDA", "Ds Cl Data", "BUS")
							.flatMap(clientId -> insertTestApp(SYSTEM_CLIENT_ID, "DSREGA", "DsReg App")
									.flatMap(appId -> insertAppRegDepartment(clientId, appId,
											"Eng Dept", "BUS", "CLIENT")
											.flatMap(deptId -> databaseClient.sql(
													"INSERT INTO security_app_reg_designation (CLIENT_ID, APP_ID, NAME, CLIENT_TYPE, LEVEL, BUSINESS_TYPE, DEPARTMENT_ID) "
															+
															"VALUES (:clientId, :appId, 'Senior Engineer', :clientType, :level, 'COMMON', :deptId)")
													.bind("clientId", clientId.longValue())
													.bind("appId", appId.longValue())
													.bind("clientType", "BUS")
													.bind("level", "CLIENT")
													.bind("deptId", deptId.longValue())
													.then()
													.then(appRegistrationV2DAO.getDesignationsForRegistration(
															appId, clientId, clientId,
															"BUS", ClientLevelType.CLIENT, "COMMON"))))))
					.assertNext(list -> {
						assertFalse(list.isEmpty());
						assertEquals("Senior Engineer", list.get(0).getName());
					})
					.verifyComplete();
		}
	}

	// ---- User Designation Registration Tests ----

	@Nested
	@DisplayName("getUserDesignationsForRegistration()")
	class GetUserDesignationsForRegistrationTests {

		@Test
		@DisplayName("no data returns empty list")
		void noData_ReturnsEmpty() {
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, "UDNODA", "UdNoData App")
							.flatMap(appId -> appRegistrationV2DAO.getUserDesignationsForRegistration(
									appId, SYSTEM_CLIENT_ID, ULong.valueOf(999),
									"BUS", ClientLevelType.CLIENT, "COMMON")))
					.assertNext(list -> assertTrue(list.isEmpty()))
					.verifyComplete();
		}

		@Test
		@DisplayName("returns user designations with designation reference")
		void withData_ReturnsUserDesignations() {
			StepVerifier.create(
					insertTestClient("UDCLDA", "Ud Cl Data", "BUS")
							.flatMap(clientId -> insertTestApp(SYSTEM_CLIENT_ID, "UDREGA", "UdReg App")
									.flatMap(appId -> insertAppRegDepartment(clientId, appId,
											"UD Dept", "BUS", "CLIENT")
											.flatMap(deptId -> databaseClient.sql(
													"INSERT INTO security_app_reg_designation (CLIENT_ID, APP_ID, NAME, CLIENT_TYPE, LEVEL, BUSINESS_TYPE, DEPARTMENT_ID) "
															+
															"VALUES (:clientId, :appId, 'UD Designation', :clientType, :level, 'COMMON', :deptId)")
													.bind("clientId", clientId.longValue())
													.bind("appId", appId.longValue())
													.bind("clientType", "BUS")
													.bind("level", "CLIENT")
													.bind("deptId", deptId.longValue())
													.filter(s -> s.returnGeneratedValues("ID"))
													.map(row -> ULong.valueOf(row.get("ID", Long.class)))
													.one())
											.flatMap(desgId -> databaseClient.sql(
													"INSERT INTO security_app_reg_user_designation (CLIENT_ID, APP_ID, DESIGNATION_ID, CLIENT_TYPE, LEVEL, BUSINESS_TYPE) "
															+
															"VALUES (:clientId, :appId, :desgId, :clientType, :level, 'COMMON')")
													.bind("clientId", clientId.longValue())
													.bind("appId", appId.longValue())
													.bind("desgId", desgId.longValue())
													.bind("clientType", "BUS")
													.bind("level", "CLIENT")
													.then()
													.then(appRegistrationV2DAO
															.getUserDesignationsForRegistration(
																	appId, clientId, clientId,
																	"BUS", ClientLevelType.CLIENT, "COMMON"))))))
					.assertNext(list -> {
						assertFalse(list.isEmpty());
						assertNotNull(list.get(0).getDesignationId());
					})
					.verifyComplete();
		}
	}

	// ---- Business Type Default Tests ----

	@Nested
	@DisplayName("businessType defaults to COMMON")
	class BusinessTypeDefaultTests {

		@Test
		@DisplayName("null businessType defaults to COMMON in getAppIdsForRegistration")
		void nullBusinessType_DefaultsToCOMMON() {
			StepVerifier.create(
					insertTestClient("BTZNCL", "Btz Null Cl", "BUS")
							.flatMap(clientId -> insertTestApp(SYSTEM_CLIENT_ID, "BTZNAP", "Btz Null App")
									.flatMap(appId -> insertTestApp(SYSTEM_CLIENT_ID, "BTZNAL", "Btz Null Allow")
											.flatMap(allowAppId -> insertAppRegAccess(clientId, appId,
													allowAppId, "BUS", "CLIENT", false, false)
													.then(appRegistrationV2DAO.getAppIdsForRegistration(
															appId, clientId, clientId,
															"BUS", ClientLevelType.CLIENT, null))))))
					.assertNext(list -> {
						// Should find the entry because null defaults to "COMMON"
						assertFalse(list.isEmpty());
					})
					.verifyComplete();
		}
	}

	// ---- Level Filter Tests ----

	@Nested
	@DisplayName("level filtering")
	class LevelFilterTests {

		@Test
		@DisplayName("CUSTOMER level data not returned when querying CLIENT level")
		void customerLevelData_NotReturnedForClientQuery() {
			StepVerifier.create(
					insertTestClient("LVLCLA", "Lvl Cl A", "BUS")
							.flatMap(clientId -> insertTestApp(SYSTEM_CLIENT_ID, "LVLA1A", "LvlA App")
									.flatMap(appId -> insertTestApp(SYSTEM_CLIENT_ID, "LVLA2A", "LvlA Allow")
											.flatMap(allowAppId ->
											// Insert at CUSTOMER level
											insertAppRegAccess(clientId, appId, allowAppId,
													"BUS", "CUSTOMER", false, false)
													.then(appRegistrationV2DAO.getAppIdsForRegistration(
															appId, clientId, clientId,
															"BUS", ClientLevelType.CLIENT, "COMMON"))))))
					.assertNext(list -> {
						// Should return default (appId) since no CLIENT level data exists
						assertEquals(1, list.size());
					})
					.verifyComplete();
		}
	}
}
