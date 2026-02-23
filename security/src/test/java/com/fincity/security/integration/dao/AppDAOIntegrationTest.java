package com.fincity.security.integration.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.security.dao.AppDAO;
import com.fincity.security.dto.AppProperty;
import com.fincity.security.integration.AbstractIntegrationTest;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AppDAOIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private AppDAO appDAO;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);

	@BeforeEach
	void setUp() {
		setupMockBeans();
	}

	@AfterEach
	void tearDown() {
		databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
				.then(databaseClient.sql("DELETE FROM security_app_dependency WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_app_property WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_app_access WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_app WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}

	private Mono<ULong> insertAppAccess(ULong appId, ULong clientId, boolean writeAccess) {
		return databaseClient.sql(
				"INSERT INTO security_app_access (APP_ID, CLIENT_ID, EDIT_ACCESS) VALUES (:appId, :clientId, :editAccess)")
				.bind("appId", appId.longValue())
				.bind("clientId", clientId.longValue())
				.bind("editAccess", writeAccess ? 1 : 0)
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	private Mono<Void> insertAppProperty(ULong appId, ULong clientId, String name, String value) {
		return databaseClient.sql(
				"INSERT INTO security_app_property (APP_ID, CLIENT_ID, NAME, VALUE) VALUES (:appId, :clientId, :name, :value)")
				.bind("appId", appId.longValue())
				.bind("clientId", clientId.longValue())
				.bind("name", name)
				.bind("value", value)
				.then();
	}

	private Mono<Void> insertAppDependency(ULong appId, ULong depAppId) {
		return databaseClient.sql(
				"INSERT INTO security_app_dependency (APP_ID, DEP_APP_ID) VALUES (:appId, :depAppId)")
				.bind("appId", appId.longValue())
				.bind("depAppId", depAppId.longValue())
				.then();
	}

	@Nested
	@DisplayName("hasReadAccess() - by IDs")
	class HasReadAccessByIdTests {

		@Test
		void ownerClient_HasReadAccess() {
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, "testracc_" + System.currentTimeMillis(), "Test Read Access App")
							.flatMap(appId -> appDAO.hasReadAccess(appId, SYSTEM_CLIENT_ID)))
					.assertNext(hasAccess -> assertTrue(hasAccess))
					.verifyComplete();
		}

		@Test
		void nonExistentApp_NoAccess() {
			StepVerifier.create(appDAO.hasReadAccess(ULong.valueOf(999999), SYSTEM_CLIENT_ID))
					.assertNext(hasAccess -> assertFalse(hasAccess))
					.verifyComplete();
		}

		@Test
		void nonExistentClient_NoAccess() {
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, "testrac2_" + System.currentTimeMillis(), "Test App")
							.flatMap(appId -> appDAO.hasReadAccess(appId, ULong.valueOf(999999))))
					.assertNext(hasAccess -> assertFalse(hasAccess))
					.verifyComplete();
		}

		@Test
		void clientWithReadAccessGrant_HasAccess() {
			StepVerifier.create(
					insertTestClient("RDACC", "Read Access Client", "BUS")
							.flatMap(busClientId -> insertTestApp(SYSTEM_CLIENT_ID,
									"rdacc_" + System.currentTimeMillis(), "Read App")
									.flatMap(appId -> insertAppAccess(appId, busClientId, false)
											.then(appDAO.hasReadAccess(appId, busClientId)))))
					.assertNext(hasAccess -> assertTrue(hasAccess))
					.verifyComplete();
		}

		@Test
		void clientWithWriteAccessGrant_AlsoHasReadAccess() {
			StepVerifier.create(
					insertTestClient("WRACC", "Write Access Client", "BUS")
							.flatMap(busClientId -> insertTestApp(SYSTEM_CLIENT_ID,
									"wracc_" + System.currentTimeMillis(), "Write App")
									.flatMap(appId -> insertAppAccess(appId, busClientId, true)
											.then(appDAO.hasReadAccess(appId, busClientId)))))
					.assertNext(hasAccess -> assertTrue(hasAccess))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("hasWriteAccess() - by IDs")
	class HasWriteAccessByIdTests {

		@Test
		void ownerClient_HasWriteAccess() {
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, "testwracc_" + System.currentTimeMillis(), "Test Write App")
							.flatMap(appId -> appDAO.hasWriteAccess(appId, SYSTEM_CLIENT_ID)))
					.assertNext(hasAccess -> assertTrue(hasAccess))
					.verifyComplete();
		}

		@Test
		void nonExistentApp_NoWriteAccess() {
			StepVerifier.create(appDAO.hasWriteAccess(ULong.valueOf(999999), SYSTEM_CLIENT_ID))
					.assertNext(hasAccess -> assertFalse(hasAccess))
					.verifyComplete();
		}

		@Test
		void clientWithWriteAccessGrant_HasAccess() {
			StepVerifier.create(
					insertTestClient("WRGNT", "Write Grant Client", "BUS")
							.flatMap(busClientId -> insertTestApp(SYSTEM_CLIENT_ID,
									"wrgnt_" + System.currentTimeMillis(), "Write Grant App")
									.flatMap(appId -> insertAppAccess(appId, busClientId, true)
											.then(appDAO.hasWriteAccess(appId, busClientId)))))
					.assertNext(hasAccess -> assertTrue(hasAccess))
					.verifyComplete();
		}

		@Test
		void clientWithReadOnlyAccessGrant_NoWriteAccess() {
			StepVerifier.create(
					insertTestClient("RONL", "ReadOnly Client", "BUS")
							.flatMap(busClientId -> insertTestApp(SYSTEM_CLIENT_ID,
									"ronl_" + System.currentTimeMillis(), "ReadOnly App")
									.flatMap(appId -> insertAppAccess(appId, busClientId, false)
											.then(appDAO.hasWriteAccess(appId, busClientId)))))
					.assertNext(hasAccess -> assertFalse(hasAccess))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("hasReadAccess() - by codes")
	class HasReadAccessByCodeTests {

		@Test
		void nonExistentAppCode_NoAccess() {
			StepVerifier.create(appDAO.hasReadAccess("NONEXISTENT_APP_CODE", "SYSTEM"))
					.assertNext(hasAccess -> assertFalse(hasAccess))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("hasWriteAccess() - by codes")
	class HasWriteAccessByCodeTests {

		@Test
		void nonExistentAppCode_NoAccess() {
			StepVerifier.create(appDAO.hasWriteAccess("NONEXISTENT_APP_CODE", "SYSTEM"))
					.assertNext(hasAccess -> assertFalse(hasAccess))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("addClientAccess() - via SQL insert + DAO verification")
	class AddClientAccessTests {

		@Test
		void addReadAccess_GrantsReadButNotWrite() {
			StepVerifier.create(
					insertTestClient("ADRD", "Add Read Client", "BUS")
							.flatMap(busClientId -> insertTestApp(SYSTEM_CLIENT_ID,
									"adrd_" + System.currentTimeMillis(), "Add Read App")
									.flatMap(appId -> insertAppAccess(appId, busClientId, false)
											.then(appDAO.hasReadAccess(appId, busClientId)
													.zipWith(appDAO.hasWriteAccess(appId, busClientId))))))
					.assertNext(tuple -> {
						assertTrue(tuple.getT1());
						assertFalse(tuple.getT2());
					})
					.verifyComplete();
		}

		@Test
		void addWriteAccess_GrantsBothReadAndWrite() {
			StepVerifier.create(
					insertTestClient("ADWR", "Add Write Client", "BUS")
							.flatMap(busClientId -> insertTestApp(SYSTEM_CLIENT_ID,
									"adwr_" + System.currentTimeMillis(), "Add Write App")
									.flatMap(appId -> insertAppAccess(appId, busClientId, true)
											.then(appDAO.hasReadAccess(appId, busClientId)
													.zipWith(appDAO.hasWriteAccess(appId, busClientId))))))
					.assertNext(tuple -> {
						assertTrue(tuple.getT1());
						assertTrue(tuple.getT2());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("removeClientAccess()")
	class RemoveClientAccessTests {

		@Test
		void removeAccess_RevokesAccess() {
			StepVerifier.create(
					insertTestClient("RMAC", "Remove Access Client", "BUS")
							.flatMap(busClientId -> insertTestApp(SYSTEM_CLIENT_ID,
									"rmac_" + System.currentTimeMillis(), "Remove Access App")
									.flatMap(appId -> insertAppAccess(appId, busClientId, true)
											.flatMap(accessId -> appDAO.removeClientAccess(appId, accessId)
													.then(appDAO.hasReadAccess(appId, busClientId))))))
					.assertNext(hasAccess -> assertFalse(hasAccess))
					.verifyComplete();
		}

		@Test
		void removeNonExistentAccess_ReturnsFalse() {
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, "rmnon_" + System.currentTimeMillis(), "Non Access App")
							.flatMap(appId -> appDAO.removeClientAccess(appId, ULong.valueOf(999999))))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("updateClientAccess()")
	class UpdateClientAccessTests {

		@Test
		void upgradeReadToWrite_Success() {
			StepVerifier.create(
					insertTestClient("UPGR", "Upgrade Client", "BUS")
							.flatMap(busClientId -> insertTestApp(SYSTEM_CLIENT_ID,
									"upgr_" + System.currentTimeMillis(), "Upgrade App")
									.flatMap(appId -> insertAppAccess(appId, busClientId, false)
											.flatMap(accessId -> appDAO.updateClientAccess(accessId, true)
													.then(appDAO.hasWriteAccess(appId, busClientId))))))
					.assertNext(hasAccess -> assertTrue(hasAccess))
					.verifyComplete();
		}

		@Test
		void downgradeWriteToRead_Success() {
			StepVerifier.create(
					insertTestClient("DNGR", "Downgrade Client", "BUS")
							.flatMap(busClientId -> insertTestApp(SYSTEM_CLIENT_ID,
									"dngr_" + System.currentTimeMillis(), "Downgrade App")
									.flatMap(appId -> insertAppAccess(appId, busClientId, true)
											.flatMap(accessId -> appDAO.updateClientAccess(accessId, false)
													.then(appDAO.hasWriteAccess(appId, busClientId))))))
					.assertNext(hasAccess -> assertFalse(hasAccess))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getByAppCode()")
	class GetByAppCodeTests {

		@Test
		void existingApp_ReturnsApp() {
			String appCode = "getbycode_" + System.currentTimeMillis();
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, appCode, "Get By Code App")
							.then(appDAO.getByAppCode(appCode)))
					.assertNext(app -> {
						assertNotNull(app);
						assertEquals(appCode, app.getAppCode());
						assertEquals("Get By Code App", app.getAppName());
						assertEquals(SYSTEM_CLIENT_ID, app.getClientId());
					})
					.verifyComplete();
		}

		@Test
		void nonExistentAppCode_ReturnsEmpty() {
			StepVerifier.create(appDAO.getByAppCode("NONEXISTENT_CODE_XYZ"))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("generateAppCode()")
	class GenerateAppCodeTests {

		@Test
		void generatesUniqueCode() {
			com.fincity.security.dto.App app = new com.fincity.security.dto.App();
			app.setAppName("TestApp");
			StepVerifier.create(appDAO.generateAppCode(app))
					.assertNext(code -> {
						assertNotNull(code);
						assertFalse(code.isEmpty());
					})
					.verifyComplete();
		}

		@Test
		void nullAppName_GeneratesDefaultCode() {
			com.fincity.security.dto.App app = new com.fincity.security.dto.App();
			StepVerifier.create(appDAO.generateAppCode(app))
					.assertNext(code -> {
						assertNotNull(code);
						assertFalse(code.isEmpty());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("updateProperty()")
	class UpdatePropertyTests {

		@Test
		void createNewProperty_Success() {
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, "prpnew_" + System.currentTimeMillis(), "Property App")
							.flatMap(appId -> {
								AppProperty property = new AppProperty();
								property.setAppId(appId);
								property.setClientId(SYSTEM_CLIENT_ID);
								property.setName("TEST_PROP");
								property.setValue("test_value");
								return appDAO.updateProperty(property);
							}))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void upsertExistingProperty_UpdatesValue() {
			String appCode = "prpupd_" + System.currentTimeMillis();
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, appCode, "Property Update App")
							.flatMap(appId -> insertAppProperty(appId, SYSTEM_CLIENT_ID, "UPD_PROP",
									"old_value")
									.then(Mono.defer(() -> {
										AppProperty property = new AppProperty();
										property.setAppId(appId);
										property.setClientId(SYSTEM_CLIENT_ID);
										property.setName("UPD_PROP");
										property.setValue("new_value");
										return appDAO.updateProperty(property);
									}))
									.then(databaseClient.sql(
											"SELECT VALUE FROM security_app_property WHERE APP_ID = :appId AND NAME = 'UPD_PROP'")
											.bind("appId", appId.longValue())
											.map(row -> row.get("VALUE", String.class))
											.one())))
					.assertNext(value -> assertEquals("new_value", value))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("deleteProperty()")
	class DeletePropertyTests {

		@Test
		void deleteExistingProperty_ReturnsTrue() {
			String appCode = "prpdel_" + System.currentTimeMillis();
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, appCode, "Property Delete App")
							.flatMap(appId -> insertAppProperty(appId, SYSTEM_CLIENT_ID, "DEL_PROP",
									"to_delete")
									.then(appDAO.deleteProperty(SYSTEM_CLIENT_ID, appId, "DEL_PROP"))))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void deleteNonExistentProperty_ReturnsFalse() {
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, "prpndl_" + System.currentTimeMillis(), "No Delete App")
							.flatMap(appId -> appDAO.deleteProperty(SYSTEM_CLIENT_ID, appId, "NONEXISTENT")))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("Properties via SQL verification")
	class PropertiesVerificationTests {

		@Test
		void multipleProperties_AllStoredCorrectly() {
			String appCode = "prpmul_" + System.currentTimeMillis();
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, appCode, "Multi Prop App")
							.flatMap(appId -> insertAppProperty(appId, SYSTEM_CLIENT_ID, "PROP_A", "value_a")
									.then(insertAppProperty(appId, SYSTEM_CLIENT_ID, "PROP_B", "value_b"))
									.then(databaseClient.sql(
											"SELECT COUNT(*) as cnt FROM security_app_property WHERE APP_ID = :appId")
											.bind("appId", appId.longValue())
											.map(row -> row.get("cnt", Long.class))
											.one())))
					.assertNext(count -> assertEquals(2L, count))
					.verifyComplete();
		}

		@Test
		void propertyValues_StoredCorrectly() {
			String appCode = "prpval_" + System.currentTimeMillis();
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, appCode, "Value Prop App")
							.flatMap(appId -> insertAppProperty(appId, SYSTEM_CLIENT_ID, "CHECK_PROP",
									"check_value")
									.then(databaseClient.sql(
											"SELECT VALUE FROM security_app_property WHERE APP_ID = :appId AND NAME = 'CHECK_PROP'")
											.bind("appId", appId.longValue())
											.map(row -> row.get("VALUE", String.class))
											.one())))
					.assertNext(value -> assertEquals("check_value", value))
					.verifyComplete();
		}

		@Test
		void deleteProperty_VerifiesRemoval() {
			String appCode = "prpdelv_" + System.currentTimeMillis();
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, appCode, "Delete Verify App")
							.flatMap(appId -> insertAppProperty(appId, SYSTEM_CLIENT_ID, "TO_DELETE",
									"will_be_gone")
									.then(appDAO.deleteProperty(SYSTEM_CLIENT_ID, appId, "TO_DELETE"))
									.then(databaseClient.sql(
											"SELECT COUNT(*) as cnt FROM security_app_property WHERE APP_ID = :appId AND NAME = 'TO_DELETE'")
											.bind("appId", appId.longValue())
											.map(row -> row.get("cnt", Long.class))
											.one())))
					.assertNext(count -> assertEquals(0L, count))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("addAppDependency() and getAppDependencies()")
	class AppDependencyTests {

		@Test
		void addDependency_ReturnsDependencyObject() {
			String ts = String.valueOf(System.currentTimeMillis());
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, "depapp_" + ts, "Main App")
							.flatMap(mainAppId -> insertTestApp(SYSTEM_CLIENT_ID, "deplib_" + ts, "Lib App")
									.flatMap(libAppId -> appDAO.addAppDependency(mainAppId, libAppId))))
					.assertNext(dep -> {
						assertNotNull(dep);
						assertNotNull(dep.getAppId());
						assertNotNull(dep.getDependentAppId());
					})
					.verifyComplete();
		}

		@Test
		void getDependencies_ReturnsAllDeps() {
			String ts = String.valueOf(System.currentTimeMillis());
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, "gmain_" + ts, "Main App")
							.flatMap(mainAppId -> insertTestApp(SYSTEM_CLIENT_ID, "gdep1_" + ts, "Dep 1")
									.flatMap(dep1Id -> insertTestApp(SYSTEM_CLIENT_ID, "gdep2_" + ts, "Dep 2")
											.flatMap(dep2Id -> insertAppDependency(mainAppId, dep1Id)
													.then(insertAppDependency(mainAppId, dep2Id))
													.then(appDAO.getAppDependencies(mainAppId))))))
					.assertNext(deps -> {
						assertNotNull(deps);
						assertEquals(2, deps.size());
					})
					.verifyComplete();
		}

		@Test
		void getNoDependencies_ReturnsEmpty() {
			StepVerifier.create(appDAO.getAppDependencies(ULong.valueOf(999999)))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("removeAppDependency()")
	class RemoveAppDependencyTests {

		@Test
		void removeExisting_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, "rmdep_" + ts, "Main App")
							.flatMap(mainAppId -> insertTestApp(SYSTEM_CLIENT_ID, "rmlib_" + ts, "Lib App")
									.flatMap(libAppId -> insertAppDependency(mainAppId, libAppId)
											.then(appDAO.removeAppDependency(mainAppId, libAppId)))))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void removeNonExistent_ReturnsFalse() {
			StepVerifier.create(appDAO.removeAppDependency(ULong.valueOf(999999), ULong.valueOf(888888)))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("appInheritance()")
	class AppInheritanceTests {

		@Test
		void sameClientAsOwner_ReturnsSingleEntry() {
			String appCode = "inher_" + System.currentTimeMillis();
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, appCode, "Inheritance App")
							.then(databaseClient.sql("SELECT CODE FROM security_client WHERE ID = 1")
									.map(row -> row.get("CODE", String.class))
									.one())
							.flatMap(sysCode -> appDAO.appInheritance(appCode, null, sysCode)))
					.assertNext(codes -> {
						assertNotNull(codes);
						assertEquals(1, codes.size());
					})
					.verifyComplete();
		}

		@Test
		void differentClient_ReturnsTwoEntries() {
			String appCode = "inhdf_" + System.currentTimeMillis();
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, appCode, "Inheritance Diff App")
							.then(appDAO.appInheritance(appCode, null, "DIFFERENT_CLIENT")))
					.assertNext(codes -> {
						assertNotNull(codes);
						assertEquals(2, codes.size());
					})
					.verifyComplete();
		}

		@Test
		void urlClientDifferentFromClient_ReturnsThreeEntries() {
			String appCode = "inh3_" + System.currentTimeMillis();
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, appCode, "Inheritance Three App")
							.then(appDAO.appInheritance(appCode, "URL_CLIENT", "DIFF_CLIENT")))
					.assertNext(codes -> {
						assertNotNull(codes);
						assertEquals(3, codes.size());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getAppsIDsPerClient()")
	class GetAppsIDsPerClientTests {

		@Test
		void systemClient_ReturnsApps() {
			StepVerifier.create(appDAO.getAppsIDsPerClient(List.of(SYSTEM_CLIENT_ID)))
					.assertNext(appsMap -> assertNotNull(appsMap))
					.verifyComplete();
		}

		@Test
		void nonExistentClient_ReturnsEmptyMap() {
			StepVerifier.create(appDAO.getAppsIDsPerClient(List.of(ULong.valueOf(999999))))
					.assertNext(appsMap -> {
						assertNotNull(appsMap);
						assertTrue(appsMap.isEmpty() || !appsMap.containsKey(ULong.valueOf(999999)));
					})
					.verifyComplete();
		}

		@Test
		void clientWithAppAccess_IncludesAccessedApps() {
			String ts = String.valueOf(System.currentTimeMillis());
			StepVerifier.create(
					insertTestClient("GAID", "Get App IDs Client", "BUS")
							.flatMap(busClientId -> insertTestApp(SYSTEM_CLIENT_ID, "gaid_" + ts, "GAID App")
									.flatMap(appId -> insertAppAccess(appId, busClientId, false)
											.then(appDAO.getAppsIDsPerClient(List.of(busClientId))))))
					.assertNext(appsMap -> {
						assertNotNull(appsMap);
						assertFalse(appsMap.isEmpty());
					})
					.verifyComplete();
		}

		@Test
		void nullOrEmptyList_ReturnsEmptyMap() {
			StepVerifier.create(appDAO.getAppsIDsPerClient(List.of()))
					.assertNext(appsMap -> assertTrue(appsMap.isEmpty()))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("isNoneUsingTheAppOtherThan()")
	class IsNoneUsingTests {

		@Test
		void nonExistentApp_ReturnsTrue() {
			StepVerifier.create(appDAO.isNoneUsingTheAppOtherThan(ULong.valueOf(999999), BigInteger.ONE))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void appWithNoOtherClients_ReturnsTrue() {
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, "noother_" + System.currentTimeMillis(), "No Other App")
							.flatMap(appId -> appDAO.isNoneUsingTheAppOtherThan(appId, BigInteger.ONE)))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void appWithOtherClients_ReturnsFalse() {
			String ts = String.valueOf(System.currentTimeMillis());
			StepVerifier.create(
					insertTestClient("OTHCL", "Other Client", "BUS")
							.flatMap(busClientId -> insertTestApp(SYSTEM_CLIENT_ID, "other_" + ts, "Other App")
									.flatMap(appId -> insertAppAccess(appId, busClientId, false)
											.then(appDAO.isNoneUsingTheAppOtherThan(appId, BigInteger.ONE)))))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("noOneHasWriteAccessExcept()")
	class NoOneHasWriteAccessExceptTests {

		@Test
		void noOtherWriteAccess_ReturnsTrue() {
			String appCode = "nowr_" + System.currentTimeMillis();
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, appCode, "No Write App")
							.then(databaseClient.sql("SELECT CODE FROM security_client WHERE ID = 1")
									.map(row -> row.get("CODE", String.class))
									.one())
							.flatMap(sysCode -> appDAO.noOneHasWriteAccessExcept(appCode, sysCode)))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("deletePropertyById()")
	class DeletePropertyByIdTests {

		@Test
		void deleteExisting_ReturnsTrue() {
			String appCode = "dpid_" + System.currentTimeMillis();
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, appCode, "Delete Prop App")
							.flatMap(appId -> databaseClient.sql(
									"INSERT INTO security_app_property (APP_ID, CLIENT_ID, NAME, VALUE) VALUES (:appId, :clientId, :name, :value)")
									.bind("appId", appId.longValue())
									.bind("clientId", SYSTEM_CLIENT_ID.longValue())
									.bind("name", "DEL_BY_ID")
									.bind("value", "del_value")
									.filter(s -> s.returnGeneratedValues("ID"))
									.map(row -> ULong.valueOf(row.get("ID", Long.class)))
									.one())
							.flatMap(propId -> appDAO.deletePropertyById(propId)))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void deleteNonExistent_ReturnsFalse() {
			StepVerifier.create(appDAO.deletePropertyById(ULong.valueOf(999999)))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getPropertyById()")
	class GetPropertyByIdTests {

		@Test
		void existingProperty_ReturnsProperty() {
			String appCode = "gpid_" + System.currentTimeMillis();
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, appCode, "Get Prop App")
							.flatMap(appId -> databaseClient.sql(
									"INSERT INTO security_app_property (APP_ID, CLIENT_ID, NAME, VALUE) VALUES (:appId, :clientId, :name, :value)")
									.bind("appId", appId.longValue())
									.bind("clientId", SYSTEM_CLIENT_ID.longValue())
									.bind("name", "GET_PROP")
									.bind("value", "get_value")
									.filter(s -> s.returnGeneratedValues("ID"))
									.map(row -> ULong.valueOf(row.get("ID", Long.class)))
									.one())
							.flatMap(propId -> appDAO.getPropertyById(propId)))
					.assertNext(prop -> {
						assertNotNull(prop);
						assertEquals("GET_PROP", prop.getName());
						assertEquals("get_value", prop.getValue());
					})
					.verifyComplete();
		}

		@Test
		void nonExistentProperty_ReturnsEmpty() {
			StepVerifier.create(appDAO.getPropertyById(ULong.valueOf(999999)))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("createPropertyFromTransport()")
	class CreatePropertyFromTransportTests {

		@Test
		void createNew_ReturnsTrue() {
			String appCode = "trprt_" + System.currentTimeMillis();
			StepVerifier.create(
					insertTestApp(SYSTEM_CLIENT_ID, appCode, "Transport App")
							.flatMap(appId -> appDAO.createPropertyFromTransport(appId, SYSTEM_CLIENT_ID,
									"TRANSPORT_PROP", "transport_value")))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}
	}
}
