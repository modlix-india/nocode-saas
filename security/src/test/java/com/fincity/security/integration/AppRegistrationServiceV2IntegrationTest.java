package com.fincity.security.integration;

import static org.junit.jupiter.api.Assertions.*;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.security.dto.appregistration.AbstractAppRegistration;
import com.fincity.security.dto.appregistration.AppRegistrationAccess;
import com.fincity.security.dto.appregistration.AppRegistrationDepartment;
import com.fincity.security.dto.appregistration.AppRegistrationFileAccess;
import com.fincity.security.enums.AppRegistrationObjectType;
import com.fincity.security.enums.ClientLevelType;
import com.fincity.security.service.appregistration.AppRegistrationServiceV2;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AppRegistrationServiceV2IntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private AppRegistrationServiceV2 appRegistrationServiceV2;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);

	private ContextAuthentication systemAuth;

	@BeforeEach
	void setUp() {
		setupMockBeans();
		systemAuth = TestDataFactory.createSystemAuth();
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
				.then(databaseClient
						.sql("DELETE FROM security_app_access WHERE APP_ID IN (SELECT ID FROM security_app WHERE APP_CODE LIKE 'arsv2%')")
						.then())
				.then(databaseClient.sql("DELETE FROM security_app WHERE APP_CODE LIKE 'arsv2%'").then())
				.then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}

	// ---- Helper methods ----

	private Mono<ULong> insertAppRegAccess(ULong clientId, ULong appId, ULong allowAppId,
			String clientType, String level, boolean writeAccess, boolean register) {
		return databaseClient.sql(
				"INSERT INTO security_app_reg_access (CLIENT_ID, APP_ID, ALLOW_APP_ID, CLIENT_TYPE, LEVEL, BUSINESS_TYPE, WRITE_ACCESS, REGISTER) "
						+ "VALUES (:clientId, :appId, :allowAppId, :clientType, :level, 'COMMON', :writeAccess, :register)")
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
						+ "VALUES (:clientId, :appId, :clientType, :level, 'COMMON', :resourceType, :accessName, :writeAccess, :path, :allowSubPathAccess)")
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

	private Mono<ULong> insertAppRegDepartment(ULong clientId, ULong appId, String name,
			String clientType, String level) {
		return databaseClient.sql(
				"INSERT INTO security_app_reg_department (CLIENT_ID, APP_ID, NAME, CLIENT_TYPE, LEVEL, BUSINESS_TYPE) "
						+ "VALUES (:clientId, :appId, :name, :clientType, :level, 'COMMON')")
				.bind("clientId", clientId.longValue())
				.bind("appId", appId.longValue())
				.bind("name", name)
				.bind("clientType", clientType)
				.bind("level", level)
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	// =======================================================================
	// create() - FILE_ACCESS type (no extraValues)
	// =======================================================================

	@Nested
	@DisplayName("create() FILE_ACCESS")
	class CreateFileAccessTests {

		@Test
		@DisplayName("system client creates FILE_ACCESS successfully")
		void systemClient_CreatesFileAccess() {
			Mono<AbstractAppRegistration> result = insertTestApp(SYSTEM_CLIENT_ID, "arsv2c1", "SvcV2 Create1")
					.flatMap(appId -> {
						AppRegistrationFileAccess entity = new AppRegistrationFileAccess();
						entity.setResourceType("STATIC");
						entity.setAccessName("images");
						entity.setPath("/images");
						entity.setWriteAccess(false);
						entity.setAllowSubPathAccess(true);
						entity.setClientType("BUS");
						entity.setLevel(ClientLevelType.CLIENT);
						entity.setBusinessType("COMMON");
						entity.setClientId(SYSTEM_CLIENT_ID);

						return appRegistrationServiceV2
								.create(AppRegistrationObjectType.FILE_ACCESS, "arsv2c1", entity)
								.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));
					});

			StepVerifier.create(result)
					.assertNext(created -> {
						assertNotNull(created.getId());
						assertEquals(SYSTEM_CLIENT_ID, created.getClientId());
						assertNotNull(created.getApp());
						assertNotNull(created.getClient());
						assertTrue(created instanceof AppRegistrationFileAccess);
						AppRegistrationFileAccess fa = (AppRegistrationFileAccess) created;
						assertEquals("STATIC", fa.getResourceType());
						assertEquals("/images", fa.getPath());
						assertTrue(fa.isAllowSubPathAccess());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("create sets clientId from context when not provided")
		void create_SetsClientIdFromContext_WhenNull() {
			Mono<AbstractAppRegistration> result = insertTestApp(SYSTEM_CLIENT_ID, "arsv2c2", "SvcV2 Create2")
					.flatMap(appId -> {
						AppRegistrationFileAccess entity = new AppRegistrationFileAccess();
						entity.setResourceType("SECURED");
						entity.setAccessName("docs");
						entity.setPath("/docs");
						entity.setWriteAccess(true);
						entity.setAllowSubPathAccess(false);
						entity.setClientType("BUS");
						entity.setLevel(ClientLevelType.CLIENT);
						entity.setBusinessType("COMMON");
						// clientId deliberately not set

						return appRegistrationServiceV2
								.create(AppRegistrationObjectType.FILE_ACCESS, "arsv2c2", entity)
								.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));
					});

			StepVerifier.create(result)
					.assertNext(created -> {
						assertNotNull(created.getId());
						assertEquals(SYSTEM_CLIENT_ID, created.getClientId());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("create for non-existent app code returns FORBIDDEN")
		void create_NonExistentAppCode_ThrowsForbidden() {
			AppRegistrationFileAccess entity = new AppRegistrationFileAccess();
			entity.setResourceType("STATIC");
			entity.setAccessName("test");
			entity.setPath("/test");

			StepVerifier.create(
					appRegistrationServiceV2
							.create(AppRegistrationObjectType.FILE_ACCESS, "NOEXIST", entity)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}

	// =======================================================================
	// create() - APPLICATION_ACCESS type (has extraValues for allowAppId)
	// =======================================================================

	@Nested
	@DisplayName("create() APPLICATION_ACCESS")
	class CreateAppAccessTests {

		@Test
		@DisplayName("system client creates APPLICATION_ACCESS with allowAppId")
		void systemClient_CreatesAppAccess() {
			Mono<AbstractAppRegistration> result = insertTestApp(SYSTEM_CLIENT_ID, "arsv2a1", "SvcV2 AppAcc1")
					.flatMap(appId -> insertTestApp(SYSTEM_CLIENT_ID, "arsv2a2", "SvcV2 Allow1")
							.flatMap(allowAppId -> {
								AppRegistrationAccess entity = new AppRegistrationAccess();
								entity.setAllowAppId(allowAppId);
								entity.setWriteAccess(true);
								entity.setRegister(false);
								entity.setClientType("BUS");
								entity.setLevel(ClientLevelType.CLIENT);
								entity.setBusinessType("COMMON");
								entity.setClientId(SYSTEM_CLIENT_ID);

								return appRegistrationServiceV2
										.create(AppRegistrationObjectType.APPLICATION_ACCESS, "arsv2a1",
												entity)
										.contextWrite(ReactiveSecurityContextHolder
												.withAuthentication(systemAuth));
							}));

			StepVerifier.create(result)
					.assertNext(created -> {
						assertNotNull(created.getId());
						assertTrue(created instanceof AppRegistrationAccess);
						AppRegistrationAccess access = (AppRegistrationAccess) created;
						assertTrue(access.isWriteAccess());
						assertNotNull(created.getApp());
						assertNotNull(created.getClient());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("APPLICATION_ACCESS with non-accessible allowAppId throws FORBIDDEN")
		void appAccess_NonAccessibleAllowApp_ThrowsForbidden() {
			// Create a business client that does not own the allowApp
			Mono<AbstractAppRegistration> result = insertTestClient("ARSV2BC", "SvcV2 BusCl", "BUS")
					.flatMap(busClientId -> insertClientHierarchy(busClientId, SYSTEM_CLIENT_ID, null, null,
							null)
							.then(insertTestApp(busClientId, "arsv2a3", "SvcV2 BusApp"))
							.flatMap(appId -> insertTestApp(SYSTEM_CLIENT_ID, "arsv2a4",
									"SvcV2 SysAllow")
									.flatMap(allowAppId -> {
										// Bus client creates access pointing to system-owned
										// allowApp
										// The bus client does NOT have write access to the
										// allowApp
										ContextAuthentication busAuth = TestDataFactory
												.createBusinessAuth(
														busClientId, "ARSV2BC",
														java.util.List.of(
																"Authorities.Logged_IN"));

										AppRegistrationAccess entity = new AppRegistrationAccess();
										entity.setAllowAppId(allowAppId);
										entity.setWriteAccess(false);
										entity.setRegister(false);
										entity.setClientType("BUS");
										entity.setLevel(ClientLevelType.CLIENT);
										entity.setBusinessType("COMMON");

										return appRegistrationServiceV2
												.create(AppRegistrationObjectType.APPLICATION_ACCESS,
														"arsv2a3", entity)
												.contextWrite(ReactiveSecurityContextHolder
														.withAuthentication(busAuth));
									})));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}

	// =======================================================================
	// create() - DEPARTMENT type (has extraValues for parentDepartmentId)
	// =======================================================================

	@Nested
	@DisplayName("create() DEPARTMENT")
	class CreateDepartmentTests {

		@Test
		@DisplayName("system client creates DEPARTMENT successfully")
		void systemClient_CreatesDepartment() {
			Mono<AbstractAppRegistration> result = insertTestApp(SYSTEM_CLIENT_ID, "arsv2d1", "SvcV2 Dept1")
					.flatMap(appId -> {
						AppRegistrationDepartment entity = new AppRegistrationDepartment();
						entity.setName("Engineering");
						entity.setDescription("Engineering department");
						entity.setClientType("BUS");
						entity.setLevel(ClientLevelType.CLIENT);
						entity.setBusinessType("COMMON");
						entity.setClientId(SYSTEM_CLIENT_ID);

						return appRegistrationServiceV2
								.create(AppRegistrationObjectType.DEPARTMENT, "arsv2d1", entity)
								.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));
					});

			StepVerifier.create(result)
					.assertNext(created -> {
						assertNotNull(created.getId());
						assertTrue(created instanceof AppRegistrationDepartment);
						AppRegistrationDepartment dept = (AppRegistrationDepartment) created;
						assertEquals("Engineering", dept.getName());
						assertNotNull(created.getApp());
						assertNotNull(created.getClient());
					})
					.verifyComplete();
		}
	}

	// =======================================================================
	// getById()
	// =======================================================================

	@Nested
	@DisplayName("getById()")
	class GetByIdTests {

		@Test
		@DisplayName("system client retrieves FILE_ACCESS by id with filled objects")
		void systemClient_GetsFileAccessById() {
			Mono<AbstractAppRegistration> result = insertTestApp(SYSTEM_CLIENT_ID, "arsv2g1", "SvcV2 GetById1")
					.flatMap(appId -> insertAppRegFileAccess(SYSTEM_CLIENT_ID, appId,
							"BUS", "CLIENT", "STATIC", "getById_test",
							false, "/get/by/id", true))
					.flatMap(id -> appRegistrationServiceV2
							.getById(AppRegistrationObjectType.FILE_ACCESS, id)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)));

			StepVerifier.create(result)
					.assertNext(entity -> {
						assertNotNull(entity);
						assertNotNull(entity.getId());
						assertNotNull(entity.getApp());
						assertNotNull(entity.getClient());
						assertTrue(entity instanceof AppRegistrationFileAccess);
						AppRegistrationFileAccess fa = (AppRegistrationFileAccess) entity;
						assertEquals("STATIC", fa.getResourceType());
						assertEquals("/get/by/id", fa.getPath());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("getById for APPLICATION_ACCESS fills allowApp object")
		void getById_AppAccess_FillsAllowApp() {
			Mono<AbstractAppRegistration> result = insertTestApp(SYSTEM_CLIENT_ID, "arsv2g2", "SvcV2 GetById2")
					.flatMap(appId -> insertTestApp(SYSTEM_CLIENT_ID, "arsv2g3", "SvcV2 AllowGet")
							.flatMap(allowAppId -> insertAppRegAccess(SYSTEM_CLIENT_ID, appId,
									allowAppId, "BUS", "CLIENT", true, false))
							.flatMap(id -> appRegistrationServiceV2
									.getById(AppRegistrationObjectType.APPLICATION_ACCESS, id)
									.contextWrite(ReactiveSecurityContextHolder
											.withAuthentication(systemAuth))));

			StepVerifier.create(result)
					.assertNext(entity -> {
						assertNotNull(entity);
						assertTrue(entity instanceof AppRegistrationAccess);
						AppRegistrationAccess access = (AppRegistrationAccess) entity;
						assertNotNull(access.getAllowApp());
						assertNotNull(entity.getApp());
						assertNotNull(entity.getClient());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("getById for non-existent id returns empty")
		void getById_NonExistent_ReturnsEmpty() {
			StepVerifier.create(
					appRegistrationServiceV2
							.getById(AppRegistrationObjectType.FILE_ACCESS, ULong.valueOf(999999))
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.verifyComplete();
		}
	}

	// =======================================================================
	// delete()
	// =======================================================================

	@Nested
	@DisplayName("delete()")
	class DeleteTests {

		@Test
		@DisplayName("system client deletes FILE_ACCESS successfully")
		void systemClient_DeletesFileAccess() {
			Mono<Boolean> result = insertTestApp(SYSTEM_CLIENT_ID, "arsv2x1", "SvcV2 Delete1")
					.flatMap(appId -> insertAppRegFileAccess(SYSTEM_CLIENT_ID, appId,
							"BUS", "CLIENT", "STATIC", "del_test",
							false, "/del/path", false))
					.flatMap(id -> appRegistrationServiceV2
							.delete(AppRegistrationObjectType.FILE_ACCESS, id)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)));

			StepVerifier.create(result)
					.assertNext(deleted -> assertTrue(deleted))
					.verifyComplete();
		}

		@Test
		@DisplayName("delete then getById returns empty")
		void delete_ThenGetById_ReturnsEmpty() {
			Mono<AbstractAppRegistration> result = insertTestApp(SYSTEM_CLIENT_ID, "arsv2x2", "SvcV2 Delete2")
					.flatMap(appId -> insertAppRegFileAccess(SYSTEM_CLIENT_ID, appId,
							"BUS", "CLIENT", "STATIC", "delget_test",
							false, "/delget/path", false))
					.flatMap(id -> appRegistrationServiceV2
							.delete(AppRegistrationObjectType.FILE_ACCESS, id)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))
							.then(appRegistrationServiceV2
									.getById(AppRegistrationObjectType.FILE_ACCESS, id)
									.contextWrite(ReactiveSecurityContextHolder
											.withAuthentication(systemAuth))));

			StepVerifier.create(result)
					.verifyComplete();
		}

		@Test
		@DisplayName("delete APPLICATION_ACCESS returns true")
		void systemClient_DeletesAppAccess() {
			Mono<Boolean> result = insertTestApp(SYSTEM_CLIENT_ID, "arsv2x3", "SvcV2 Delete3")
					.flatMap(appId -> insertTestApp(SYSTEM_CLIENT_ID, "arsv2x4", "SvcV2 AllowDel")
							.flatMap(allowAppId -> insertAppRegAccess(SYSTEM_CLIENT_ID, appId,
									allowAppId, "BUS", "CLIENT", false, false)))
					.flatMap(id -> appRegistrationServiceV2
							.delete(AppRegistrationObjectType.APPLICATION_ACCESS, id)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)));

			StepVerifier.create(result)
					.assertNext(deleted -> assertTrue(deleted))
					.verifyComplete();
		}

		@Test
		@DisplayName("delete non-existent id returns empty (getById fails)")
		void delete_NonExistent_ReturnsEmpty() {
			StepVerifier.create(
					appRegistrationServiceV2
							.delete(AppRegistrationObjectType.FILE_ACCESS, ULong.valueOf(999999))
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.verifyComplete();
		}
	}

	// =======================================================================
	// get() - paginated
	// =======================================================================

	@Nested
	@DisplayName("get() paginated")
	class GetPaginatedTests {

		@Test
		@DisplayName("system client gets FILE_ACCESS page for app")
		void systemClient_GetsFileAccessPage() {
			Mono<?> result = insertTestApp(SYSTEM_CLIENT_ID, "arsv2p1", "SvcV2 Page1")
					.flatMap(appId -> insertAppRegFileAccess(SYSTEM_CLIENT_ID, appId,
							"BUS", "CLIENT", "STATIC", "page_test_1",
							false, "/page/1", false)
							.then(insertAppRegFileAccess(SYSTEM_CLIENT_ID, appId,
									"BUS", "CLIENT", "SECURED", "page_test_2",
									true, "/page/2", true))
							.then(appRegistrationServiceV2
									.get(AppRegistrationObjectType.FILE_ACCESS, "arsv2p1",
											null, null, null, null, null,
											PageRequest.of(0, 10))
									.contextWrite(ReactiveSecurityContextHolder
											.withAuthentication(systemAuth))));

			StepVerifier.create(result)
					.assertNext(page -> {
						assertNotNull(page);
						var p = (org.springframework.data.domain.Page<?>) page;
						assertTrue(p.getTotalElements() >= 2);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("get returns empty page for app with no data")
		void get_NoData_ReturnsEmptyPage() {
			Mono<?> result = insertTestApp(SYSTEM_CLIENT_ID, "arsv2p2", "SvcV2 PageEmpty")
					.then(appRegistrationServiceV2
							.get(AppRegistrationObjectType.FILE_ACCESS, "arsv2p2",
									null, null, null, null, null,
									PageRequest.of(0, 10))
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)));

			StepVerifier.create(result)
					.assertNext(page -> {
						var p = (org.springframework.data.domain.Page<?>) page;
						assertEquals(0, p.getTotalElements());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("get with both clientCode and clientId returns BAD_REQUEST")
		void get_BothClientCodeAndId_ThrowsBadRequest() {
			StepVerifier.create(
					appRegistrationServiceV2
							.get(AppRegistrationObjectType.FILE_ACCESS, "anyapp",
									"SYSTEM", SYSTEM_CLIENT_ID, null, null, null,
									PageRequest.of(0, 10))
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		@DisplayName("get with clientCode resolves to clientId and returns data")
		void get_WithClientCode_ResolvesAndReturnsData() {
			Mono<?> result = insertTestApp(SYSTEM_CLIENT_ID, "arsv2p3", "SvcV2 PageCC")
					.flatMap(appId -> insertAppRegFileAccess(SYSTEM_CLIENT_ID, appId,
							"BUS", "CLIENT", "STATIC", "cc_test",
							false, "/cc/path", false))
					.then(appRegistrationServiceV2
							.get(AppRegistrationObjectType.FILE_ACCESS, "arsv2p3",
									"SYSTEM", null, null, null, null,
									PageRequest.of(0, 10))
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)));

			StepVerifier.create(result)
					.assertNext(page -> {
						var p = (org.springframework.data.domain.Page<?>) page;
						assertTrue(p.getTotalElements() >= 1);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("get with clientId filter returns matching data")
		void get_WithClientId_ReturnsMatchingData() {
			Mono<?> result = insertTestApp(SYSTEM_CLIENT_ID, "arsv2p4", "SvcV2 PageCI")
					.flatMap(appId -> insertAppRegFileAccess(SYSTEM_CLIENT_ID, appId,
							"BUS", "CLIENT", "STATIC", "ci_test",
							false, "/ci/path", false))
					.then(appRegistrationServiceV2
							.get(AppRegistrationObjectType.FILE_ACCESS, "arsv2p4",
									null, SYSTEM_CLIENT_ID, null, null, null,
									PageRequest.of(0, 10))
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)));

			StepVerifier.create(result)
					.assertNext(page -> {
						var p = (org.springframework.data.domain.Page<?>) page;
						assertTrue(p.getTotalElements() >= 1);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("get APPLICATION_ACCESS page returns data with allowApp filled")
		void get_AppAccess_ReturnsDataWithAllowApp() {
			Mono<?> result = insertTestApp(SYSTEM_CLIENT_ID, "arsv2p5", "SvcV2 PageAA")
					.flatMap(appId -> insertTestApp(SYSTEM_CLIENT_ID, "arsv2p6", "SvcV2 AllowPage")
							.flatMap(allowAppId -> insertAppRegAccess(SYSTEM_CLIENT_ID, appId,
									allowAppId, "BUS", "CLIENT", true, false))
							.then(appRegistrationServiceV2
									.get(AppRegistrationObjectType.APPLICATION_ACCESS, "arsv2p5",
											null, null, null, null, null,
											PageRequest.of(0, 10))
									.contextWrite(ReactiveSecurityContextHolder
											.withAuthentication(systemAuth))));

			StepVerifier.create(result)
					.assertNext(page -> {
						var p = (org.springframework.data.domain.Page<?>) page;
						assertTrue(p.getTotalElements() >= 1);
					})
					.verifyComplete();
		}
	}

	// =======================================================================
	// hasAccessTo()
	// =======================================================================

	@Nested
	@DisplayName("hasAccessTo()")
	class HasAccessToTests {

		@Test
		@DisplayName("returns true when entity clientId matches")
		void entityClientIdMatches_ReturnsTrue() {
			Mono<Boolean> result = insertTestApp(SYSTEM_CLIENT_ID, "arsv2h1", "SvcV2 HasAccess1")
					.flatMap(appId -> insertAppRegFileAccess(SYSTEM_CLIENT_ID, appId,
							"BUS", "CLIENT", "STATIC", "access_test",
							false, "/access/path", false))
					.flatMap(id -> appRegistrationServiceV2
							.hasAccessTo(id, SYSTEM_CLIENT_ID, AppRegistrationObjectType.FILE_ACCESS));

			StepVerifier.create(result)
					.assertNext(hasAccess -> assertTrue(hasAccess))
					.verifyComplete();
		}

		@Test
		@DisplayName("returns false when entity clientId does not match")
		void entityClientIdMismatch_ReturnsFalse() {
			Mono<Boolean> result = insertTestApp(SYSTEM_CLIENT_ID, "arsv2h2", "SvcV2 HasAccess2")
					.flatMap(appId -> insertAppRegFileAccess(SYSTEM_CLIENT_ID, appId,
							"BUS", "CLIENT", "STATIC", "noaccess_test",
							false, "/noaccess/path", false))
					.flatMap(id -> appRegistrationServiceV2
							.hasAccessTo(id, ULong.valueOf(99999), AppRegistrationObjectType.FILE_ACCESS));

			StepVerifier.create(result)
					.assertNext(hasAccess -> assertFalse(hasAccess))
					.verifyComplete();
		}
	}

	// =======================================================================
	// create/delete roundtrip for DEPARTMENT type
	// =======================================================================

	@Nested
	@DisplayName("DEPARTMENT roundtrip")
	class DepartmentRoundtripTests {

		@Test
		@DisplayName("create then delete DEPARTMENT completes roundtrip")
		void createThenDelete_Department() {
			Mono<Boolean> result = insertTestApp(SYSTEM_CLIENT_ID, "arsv2r1", "SvcV2 Round1")
					.flatMap(appId -> {
						AppRegistrationDepartment entity = new AppRegistrationDepartment();
						entity.setName("Roundtrip Dept");
						entity.setDescription("Roundtrip test department");
						entity.setClientType("BUS");
						entity.setLevel(ClientLevelType.CLIENT);
						entity.setBusinessType("COMMON");
						entity.setClientId(SYSTEM_CLIENT_ID);

						return appRegistrationServiceV2
								.create(AppRegistrationObjectType.DEPARTMENT, "arsv2r1", entity)
								.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));
					})
					.flatMap(created -> appRegistrationServiceV2
							.delete(AppRegistrationObjectType.DEPARTMENT, created.getId())
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)));

			StepVerifier.create(result)
					.assertNext(deleted -> assertTrue(deleted))
					.verifyComplete();
		}

		@Test
		@DisplayName("get DEPARTMENT page returns created entries")
		void getDepartmentPage_ReturnsCreatedEntries() {
			Mono<?> result = insertTestApp(SYSTEM_CLIENT_ID, "arsv2r2", "SvcV2 DeptPage")
					.flatMap(appId -> insertAppRegDepartment(SYSTEM_CLIENT_ID, appId,
							"Sales", "BUS", "CLIENT")
							.then(insertAppRegDepartment(SYSTEM_CLIENT_ID, appId,
									"Marketing", "BUS", "CLIENT")))
					.then(appRegistrationServiceV2
							.get(AppRegistrationObjectType.DEPARTMENT, "arsv2r2",
									null, null, null, null, null,
									PageRequest.of(0, 10))
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)));

			StepVerifier.create(result)
					.assertNext(page -> {
						var p = (org.springframework.data.domain.Page<?>) page;
						assertTrue(p.getTotalElements() >= 2);
					})
					.verifyComplete();
		}
	}

	// =======================================================================
	// Business client access tests
	// =======================================================================

	@Nested
	@DisplayName("Business client access")
	class BusinessClientAccessTests {

		@Test
		@DisplayName("business client can create FILE_ACCESS for own app")
		void businessClient_CreatesFileAccessForOwnApp() {
			Mono<AbstractAppRegistration> result = insertTestClient("ARSV2B1", "SvcV2 Bus1", "BUS")
					.flatMap(busClientId -> insertClientHierarchy(busClientId, SYSTEM_CLIENT_ID, null, null,
							null)
							.then(insertTestApp(busClientId, "arsv2b1", "SvcV2 BusApp1"))
							.flatMap(appId -> {
								ContextAuthentication busAuth = TestDataFactory.createBusinessAuth(
										busClientId, "ARSV2B1",
										java.util.List.of("Authorities.Logged_IN",
												"Authorities.Application_READ",
												"Authorities.Client_READ"));

								AppRegistrationFileAccess entity = new AppRegistrationFileAccess();
								entity.setResourceType("STATIC");
								entity.setAccessName("bus_files");
								entity.setPath("/bus/files");
								entity.setWriteAccess(false);
								entity.setAllowSubPathAccess(false);
								entity.setClientType("BUS");
								entity.setLevel(ClientLevelType.CLIENT);
								entity.setBusinessType("COMMON");

								return appRegistrationServiceV2
										.create(AppRegistrationObjectType.FILE_ACCESS, "arsv2b1",
												entity)
										.contextWrite(ReactiveSecurityContextHolder
												.withAuthentication(busAuth));
							}));

			StepVerifier.create(result)
					.assertNext(created -> {
						assertNotNull(created.getId());
						assertNotNull(created.getApp());
						assertNotNull(created.getClient());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("business client cannot create for non-managed client app")
		void businessClient_CannotCreateForNonManagedApp() {
			// Create a bus client that doesn't manage the app's client
			Mono<AbstractAppRegistration> result = insertTestClient("ARSV2B2", "SvcV2 Bus2", "BUS")
					.flatMap(busClientId -> insertClientHierarchy(busClientId, SYSTEM_CLIENT_ID, null, null,
							null)
							.then(insertTestApp(SYSTEM_CLIENT_ID, "arsv2b2", "SvcV2 SysApp2"))
							.flatMap(appId -> {
								ContextAuthentication busAuth = TestDataFactory.createBusinessAuth(
										busClientId, "ARSV2B2",
										java.util.List.of("Authorities.Logged_IN"));

								AppRegistrationFileAccess entity = new AppRegistrationFileAccess();
								entity.setResourceType("STATIC");
								entity.setAccessName("forbidden");
								entity.setPath("/forbidden");
								entity.setClientType("BUS");
								entity.setLevel(ClientLevelType.CLIENT);
								entity.setBusinessType("COMMON");

								return appRegistrationServiceV2
										.create(AppRegistrationObjectType.FILE_ACCESS, "arsv2b2",
												entity)
										.contextWrite(ReactiveSecurityContextHolder
												.withAuthentication(busAuth));
							}));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}

	// =======================================================================
	// readObject() (IAppRegistrationHelperService implementation)
	// =======================================================================

	@Nested
	@DisplayName("readObject()")
	class ReadObjectTests {

		@Test
		@DisplayName("readObject delegates to getById")
		void readObject_DelegatesToGetById() {
			Mono<?> result = insertTestApp(SYSTEM_CLIENT_ID, "arsv2o1", "SvcV2 ReadObj1")
					.flatMap(appId -> insertAppRegDepartment(SYSTEM_CLIENT_ID, appId,
							"ReadObj Dept", "BUS", "CLIENT"))
					.flatMap(id -> appRegistrationServiceV2
							.readObject(id, AppRegistrationObjectType.DEPARTMENT)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)));

			StepVerifier.create(result)
					.assertNext(entity -> {
						assertNotNull(entity);
						assertTrue(entity instanceof AppRegistrationDepartment);
					})
					.verifyComplete();
		}
	}

	// =======================================================================
	// get() with level and businessType filters
	// =======================================================================

	@Nested
	@DisplayName("get() with level and businessType filters")
	class GetWithFiltersTests {

		@Test
		@DisplayName("get with businessType filter returns matching entries")
		void get_WithLevelFilter() {
			Mono<?> result = insertTestApp(SYSTEM_CLIENT_ID, "arsv2f1", "SvcV2 Filter1")
					.flatMap(appId -> insertAppRegFileAccess(SYSTEM_CLIENT_ID, appId,
							"BUS", "CLIENT", "STATIC", "level_cl",
							false, "/lev/cl", false)
							.then(insertAppRegFileAccess(SYSTEM_CLIENT_ID, appId,
									"BUS", "CUSTOMER", "STATIC", "level_cust",
									false, "/lev/cust", false)))
					.then(appRegistrationServiceV2
							.get(AppRegistrationObjectType.FILE_ACCESS, "arsv2f1",
									null, null, null, null, "COMMON",
									PageRequest.of(0, 10))
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)));

			StepVerifier.create(result)
					.assertNext(page -> {
						var p = (org.springframework.data.domain.Page<?>) page;
						// Both entries have COMMON businessType so both should be returned
						assertTrue(p.getTotalElements() >= 2);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("get with clientType filter returns matching entries")
		void get_WithClientTypeFilter() {
			Mono<?> result = insertTestApp(SYSTEM_CLIENT_ID, "arsv2f2", "SvcV2 Filter2")
					.flatMap(appId -> insertAppRegFileAccess(SYSTEM_CLIENT_ID, appId,
							"BUS", "CLIENT", "STATIC", "ctype_bus",
							false, "/ct/bus", false)
							.then(insertAppRegFileAccess(SYSTEM_CLIENT_ID, appId,
									"INDV", "CLIENT", "STATIC", "ctype_indv",
									false, "/ct/indv", false)))
					.then(appRegistrationServiceV2
							.get(AppRegistrationObjectType.FILE_ACCESS, "arsv2f2",
									null, null, "BUS", null, null,
									PageRequest.of(0, 10))
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)));

			StepVerifier.create(result)
					.assertNext(page -> {
						var p = (org.springframework.data.domain.Page<?>) page;
						assertTrue(p.getTotalElements() >= 1);
					})
					.verifyComplete();
		}
	}
}
