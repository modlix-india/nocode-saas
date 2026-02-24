package com.fincity.security.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.security.dto.App;
import com.fincity.security.dto.AppProperty;
import com.fincity.security.jooq.enums.SecurityAppAppAccessType;
import com.fincity.security.jooq.enums.SecurityAppAppType;
import com.fincity.security.jooq.enums.SecurityAppStatus;
import com.fincity.security.model.AppDependency;
import com.fincity.security.model.PropertiesResponse;
import com.fincity.security.service.AppService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AppServiceIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private AppService appService;

	private ContextAuthentication systemAuth;

	@BeforeEach
	void setUp() {
		setupMockBeans();
		systemAuth = TestDataFactory.createSystemAuth();
	}

	@AfterEach
	void tearDown() {
		databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
				.then(databaseClient.sql("DELETE FROM security_app_dependency WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_app_property WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_app_access WHERE ID > 0").then())
				.then(databaseClient
						.sql("DELETE FROM security_app WHERE APP_CODE NOT IN ('appbuilder', 'nothing')").then())
				.then(databaseClient.sql("DELETE FROM security_client_manager WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_user WHERE ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}

	// -----------------------------------------------------------------------
	// getAppById
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("getAppById()")
	class GetAppByIdTests {

		@Test
		@DisplayName("null ID returns empty Mono")
		void nullId_ReturnsEmpty() {
			StepVerifier.create(appService.getAppById(null))
					.verifyComplete();
		}

		@Test
		@DisplayName("valid ID returns the app")
		void validId_ReturnsApp() {
			ULong appId = insertTestApp(ULong.valueOf(1), "byidtest", "By ID Test App").block();
			assertThat(appId).isNotNull();

			Mono<App> result = appService.getAppById(appId)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(app -> {
						assertThat(app).isNotNull();
						assertThat(app.getId()).isEqualTo(appId);
						assertThat(app.getAppCode()).isEqualTo("byidtest");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("non-existent ID throws NOT_FOUND error")
		void nonExistentId_ThrowsNotFound() {
			Mono<App> result = appService.getAppById(ULong.valueOf(999999))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.NOT_FOUND)
					.verify();
		}
	}

	// -----------------------------------------------------------------------
	// getAppByCodeCheckAccess
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("getAppByCodeCheckAccess()")
	class GetAppByCodeCheckAccessTests {

		@Test
		@DisplayName("system client always has access")
		void systemClient_HasAccess() {
			insertTestApp(ULong.valueOf(1), "chkacs", "Check Access App").block();

			Mono<App> result = appService.getAppByCodeCheckAccess("chkacs")
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(app -> {
						assertThat(app).isNotNull();
						assertThat(app.getAppCode()).isEqualTo("chkacs");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("business client without access returns empty")
		void businessClient_NoAccess_ReturnsEmpty() {
			ULong busClientId = insertTestClient("CHKBUS", "Check Access Bus", "BUS")
					.flatMap(id -> insertClientHierarchy(id, ULong.valueOf(1), null, null, null).thenReturn(id))
					.block();

			insertTestApp(ULong.valueOf(1), "chknoa", "Check No Access App").block();

			ContextAuthentication busAuth = TestDataFactory.createBusinessAuth(
					busClientId, "CHKBUS",
					List.of("Authorities.Application_READ", "Authorities.Logged_IN"));

			Mono<App> result = appService.getAppByCodeCheckAccess("chknoa")
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(busAuth));

			StepVerifier.create(result)
					.verifyComplete();
		}

		@Test
		@DisplayName("business client with read access returns app")
		void businessClient_WithAccess_ReturnsApp() {
			ULong busClientId = insertTestClient("CHKBA", "Check Access Bus2", "BUS")
					.flatMap(id -> insertClientHierarchy(id, ULong.valueOf(1), null, null, null).thenReturn(id))
					.block();

			ULong appId = insertTestApp(ULong.valueOf(1), "chkwth", "Check With Access App").block();

			// Grant read access
			appService.addClientAccess(appId, busClientId, false)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))
					.block();

			ContextAuthentication busAuth = TestDataFactory.createBusinessAuth(
					busClientId, "CHKBA",
					List.of("Authorities.Application_READ", "Authorities.Logged_IN"));

			Mono<App> result = appService.getAppByCodeCheckAccess("chkwth")
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(busAuth));

			StepVerifier.create(result)
					.assertNext(app -> {
						assertThat(app).isNotNull();
						assertThat(app.getAppCode()).isEqualTo("chkwth");
					})
					.verifyComplete();
		}
	}

	// -----------------------------------------------------------------------
	// hasWriteAccess / hasReadAccess by IDs
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("hasWriteAccess() and hasReadAccess() by IDs")
	class AccessByIdTests {

		@Test
		@DisplayName("owner client has write access via clientId match")
		void ownerClient_HasWriteAccess() {
			ULong appId = insertTestApp(ULong.valueOf(1), "waown", "WA Own App").block();

			Mono<Boolean> result = appService.hasWriteAccess(appId, ULong.valueOf(1));

			StepVerifier.create(result)
					.assertNext(hasAccess -> assertThat(hasAccess).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("unrelated client has no write access")
		void unrelatedClient_NoWriteAccess() {
			ULong busClientId = insertTestClient("NWACLI", "No Write Client", "BUS")
					.flatMap(id -> insertClientHierarchy(id, ULong.valueOf(1), null, null, null).thenReturn(id))
					.block();

			ULong appId = insertTestApp(ULong.valueOf(1), "nwaacc", "No Write App").block();

			Mono<Boolean> result = appService.hasWriteAccess(appId, busClientId);

			StepVerifier.create(result)
					.assertNext(hasAccess -> assertThat(hasAccess).isFalse())
					.verifyComplete();
		}

		@Test
		@DisplayName("granted read-only access is not write access")
		void readOnlyGrant_IsNotWriteAccess() {
			ULong busClientId = insertTestClient("ROACLI", "ReadOnly Client", "BUS")
					.flatMap(id -> insertClientHierarchy(id, ULong.valueOf(1), null, null, null).thenReturn(id))
					.block();

			ULong appId = insertTestApp(ULong.valueOf(1), "roaccs", "ReadOnly Access App").block();

			appService.addClientAccess(appId, busClientId, false)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))
					.block();

			StepVerifier.create(appService.hasWriteAccess(appId, busClientId))
					.assertNext(hasAccess -> assertThat(hasAccess).isFalse())
					.verifyComplete();

			StepVerifier.create(appService.hasReadAccess(appId, busClientId))
					.assertNext(hasAccess -> assertThat(hasAccess).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("granted write access is also read access")
		void writeGrant_IsAlsoReadAccess() {
			ULong busClientId = insertTestClient("WRCLI", "Write Client", "BUS")
					.flatMap(id -> insertClientHierarchy(id, ULong.valueOf(1), null, null, null).thenReturn(id))
					.block();

			ULong appId = insertTestApp(ULong.valueOf(1), "wraccs", "Write Access App").block();

			appService.addClientAccess(appId, busClientId, true)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))
					.block();

			StepVerifier.create(appService.hasWriteAccess(appId, busClientId))
					.assertNext(hasAccess -> assertThat(hasAccess).isTrue())
					.verifyComplete();

			StepVerifier.create(appService.hasReadAccess(appId, busClientId))
					.assertNext(hasAccess -> assertThat(hasAccess).isTrue())
					.verifyComplete();
		}
	}

	// -----------------------------------------------------------------------
	// hasWriteAccess / hasReadAccess by codes
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("hasWriteAccess() and hasReadAccess() by codes")
	class AccessByCodeTests {

		@Test
		@DisplayName("owner client code has write access")
		void ownerCode_HasWriteAccess() {
			insertTestApp(ULong.valueOf(1), "wacode", "WA Code App").block();

			Mono<Boolean> result = appService.hasWriteAccess("wacode", "SYSTEM");

			StepVerifier.create(result)
					.assertNext(hasAccess -> assertThat(hasAccess).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("unrelated client code has no write access")
		void unrelatedCode_NoWriteAccess() {
			insertTestClient("NWCODE", "No Write Code", "BUS")
					.flatMap(id -> insertClientHierarchy(id, ULong.valueOf(1), null, null, null).thenReturn(id))
					.block();
			insertTestApp(ULong.valueOf(1), "nwcode", "No Write Code App").block();

			Mono<Boolean> result = appService.hasWriteAccess("nwcode", "NWCODE");

			StepVerifier.create(result)
					.assertNext(hasAccess -> assertThat(hasAccess).isFalse())
					.verifyComplete();
		}
	}

	// -----------------------------------------------------------------------
	// hasReadAccess(String[])
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("hasReadAccess(String[])")
	class HasReadAccessArrayTests {

		@Test
		@DisplayName("system client has read access to all apps")
		void systemClient_HasAccessToAll() {
			insertTestApp(ULong.valueOf(1), "arraya", "Array A").block();
			insertTestApp(ULong.valueOf(1), "arrayb", "Array B").block();

			Mono<Map<String, Boolean>> result = appService
					.hasReadAccess(new String[] { "arraya", "arrayb" })
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(map -> {
						assertThat(map).containsKey("arraya");
						assertThat(map).containsKey("arrayb");
						assertThat(map.get("arraya")).isTrue();
						assertThat(map.get("arrayb")).isTrue();
					})
					.verifyComplete();
		}
	}

	// -----------------------------------------------------------------------
	// create() with EXPLICIT access type
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("create() with EXPLICIT access type")
	class CreateExplicitAppTests {

		@Test
		@DisplayName("creates EXPLICIT app and grants access to creator's client")
		void explicitApp_CreatesAndGrantsAccess() {
			App app = new App();
			app.setAppCode("expliap");
			app.setAppName("Explicit App");
			app.setAppType(SecurityAppAppType.APP);
			app.setAppAccessType(SecurityAppAppAccessType.EXPLICIT);

			Mono<App> result = appService.create(app)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(created -> {
						assertThat(created).isNotNull();
						assertThat(created.getAppCode()).isEqualTo("expliap");
						assertThat(created.getAppAccessType()).isEqualTo(SecurityAppAppAccessType.EXPLICIT);
					})
					.verifyComplete();
		}
	}

	// -----------------------------------------------------------------------
	// create() with null appCode (auto-generation)
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("create() with auto-generated appCode")
	class CreateAutoCodeTests {

		@Test
		@DisplayName("null appCode triggers auto-generation for OWN app")
		void nullCode_OwnApp_GeneratesCode() {
			App app = new App();
			app.setAppName("Auto Generated Code App");
			app.setAppType(SecurityAppAppType.APP);
			app.setAppAccessType(SecurityAppAppAccessType.OWN);

			Mono<App> result = appService.create(app)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(created -> {
						assertThat(created).isNotNull();
						assertThat(created.getAppCode()).isNotNull();
						assertThat(created.getAppCode()).isNotEmpty();
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("null appCode triggers auto-generation for EXPLICIT app")
		void nullCode_ExplicitApp_GeneratesCode() {
			App app = new App();
			app.setAppName("Auto Explicit Code App");
			app.setAppType(SecurityAppAppType.APP);
			app.setAppAccessType(SecurityAppAppAccessType.EXPLICIT);

			Mono<App> result = appService.create(app)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(created -> {
						assertThat(created).isNotNull();
						assertThat(created.getAppCode()).isNotNull();
						assertThat(created.getAppCode()).isNotEmpty();
					})
					.verifyComplete();
		}
	}

	// -----------------------------------------------------------------------
	// create() with invalid code
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("create() validation")
	class CreateValidationTests {

		@Test
		@DisplayName("special characters in appCode throws BAD_REQUEST")
		void specialCharsInCode_ThrowsBadRequest() {
			App app = new App();
			app.setAppCode("bad-code!");
			app.setAppName("Bad Code App");
			app.setAppType(SecurityAppAppType.APP);

			Mono<App> result = appService.create(app)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}
	}

	// -----------------------------------------------------------------------
	// update(ULong, Map) - partial update
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("update(ULong, Map) - partial update")
	class PartialUpdateTests {

		@Test
		@DisplayName("partial update of appName succeeds")
		void partialUpdate_AppName() {
			ULong appId = insertTestApp(ULong.valueOf(1), "pupdapp", "Partial Update App").block();

			Mono<App> result = appService.update(appId, Map.of("appName", "Partial Updated"))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(app -> {
						assertThat(app).isNotNull();
						assertThat(app.getAppName()).isEqualTo("Partial Updated");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("partial update of non-existent app throws NOT_FOUND")
		void partialUpdate_NonExistent_ThrowsNotFound() {
			Mono<App> result = appService.update(ULong.valueOf(999999), Map.of("appName", "Ghost"))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.NOT_FOUND)
					.verify();
		}
	}

	// -----------------------------------------------------------------------
	// delete() - soft then hard
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("delete()")
	class DeleteTests {

		@Test
		@DisplayName("deleting ACTIVE app archives it first")
		void deleteActive_ArchivesFirst() {
			App app = new App();
			app.setAppCode("delactv");
			app.setAppName("Delete Active App");
			app.setAppType(SecurityAppAppType.APP);

			App created = appService.create(app)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))
					.block();

			assertThat(created).isNotNull();

			Mono<Integer> result = appService.delete(created.getId())
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(count -> assertThat(count).isEqualTo(1))
					.verifyComplete();

			// Verify archived
			String status = databaseClient.sql("SELECT STATUS FROM security_app WHERE ID = :id")
					.bind("id", created.getId().longValue())
					.map(row -> row.get("STATUS", String.class))
					.one()
					.block();
			assertThat(status).isEqualTo("ARCHIVED");
		}

		@Test
		@DisplayName("deleting ARCHIVED app hard-deletes it")
		void deleteArchived_HardDeletes() {
			ULong appId = databaseClient.sql(
					"INSERT INTO security_app (CLIENT_ID, APP_NAME, APP_CODE, APP_TYPE, STATUS) VALUES (1, 'Archived App', 'delarch', 'APP', 'ARCHIVED')")
					.filter(s -> s.returnGeneratedValues("ID"))
					.map(row -> ULong.valueOf(row.get("ID", Long.class)))
					.one()
					.block();

			assertThat(appId).isNotNull();

			Mono<Integer> result = appService.delete(appId)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(count -> assertThat(count).isEqualTo(1))
					.verifyComplete();

			Long rowCount = databaseClient.sql("SELECT COUNT(*) AS cnt FROM security_app WHERE ID = :id")
					.bind("id", appId.longValue())
					.map(row -> row.get("cnt", Long.class))
					.one()
					.block();
			assertThat(rowCount).isZero();
		}

		@Test
		@DisplayName("deleting non-existent app throws NOT_FOUND")
		void deleteNonExistent_ThrowsNotFound() {
			Mono<Integer> result = appService.delete(ULong.valueOf(999999))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.NOT_FOUND)
					.verify();
		}
	}

	// -----------------------------------------------------------------------
	// deleteEverything()
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("deleteEverything()")
	class DeleteEverythingTests {

		@Test
		@DisplayName("force delete removes app and all related data")
		void forceDelete_RemovesEverything() {
			App app = new App();
			app.setAppCode("devryth");
			app.setAppName("Delete Everything App");
			app.setAppType(SecurityAppAppType.APP);

			App created = appService.create(app)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))
					.block();

			assertThat(created).isNotNull();

			Mono<Boolean> result = appService.deleteEverything(created.getId(), true)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(deleted -> assertThat(deleted).isNotNull())
					.verifyComplete();
		}

		@Test
		@DisplayName("non-force delete with no other users succeeds")
		void nonForceDelete_NoOtherUsers_Succeeds() {
			App app = new App();
			app.setAppCode("denvnf");
			app.setAppName("Non Force Delete App");
			app.setAppType(SecurityAppAppType.APP);

			App created = appService.create(app)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))
					.block();

			assertThat(created).isNotNull();

			Mono<Boolean> result = appService.deleteEverything(created.getId(), false)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(deleted -> assertThat(deleted).isTrue())
					.verifyComplete();
		}
	}

	// -----------------------------------------------------------------------
	// isNoneUsingTheAppOtherThan()
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("isNoneUsingTheAppOtherThan()")
	class IsNoneUsingTests {

		@Test
		@DisplayName("no other users returns true")
		void noOtherUsers_ReturnsTrue() {
			ULong appId = insertTestApp(ULong.valueOf(1), "nouser", "No User App").block();

			StepVerifier.create(appService.isNoneUsingTheAppOtherThan(appId, java.math.BigInteger.ONE))
					.assertNext(isNone -> assertThat(isNone).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("other users returns false")
		void otherUsers_ReturnsFalse() {
			ULong busClientId = insertTestClient("NUBCLI", "NUB Client", "BUS")
					.flatMap(id -> insertClientHierarchy(id, ULong.valueOf(1), null, null, null).thenReturn(id))
					.block();

			ULong appId = insertTestApp(ULong.valueOf(1), "hasuser", "Has User App").block();

			appService.addClientAccess(appId, busClientId, false)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))
					.block();

			StepVerifier.create(appService.isNoneUsingTheAppOtherThan(appId, java.math.BigInteger.ONE))
					.assertNext(isNone -> assertThat(isNone).isFalse())
					.verifyComplete();
		}
	}

	// -----------------------------------------------------------------------
	// App Properties CRUD
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("App Properties CRUD")
	class AppPropertyTests {

		@Test
		@DisplayName("getProperties with no appId and no appCode throws BAD_REQUEST")
		void getProperties_NoAppIdNoCode_ThrowsBadRequest() {
			Mono<Map<ULong, Map<String, AppProperty>>> result = appService
					.getProperties(null, null, null, null)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		@DisplayName("updateProperty with null appId throws BAD_REQUEST")
		void updateProperty_NullAppId_ThrowsBadRequest() {
			AppProperty prop = new AppProperty();
			prop.setName("testprop");
			prop.setValue("testval");

			Mono<Boolean> result = appService.updateProperty(prop)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		@DisplayName("updateProperty with blank name throws BAD_REQUEST")
		void updateProperty_BlankName_ThrowsBadRequest() {
			AppProperty prop = new AppProperty();
			prop.setAppId(ULong.valueOf(1));
			prop.setName("");
			prop.setValue("testval");

			Mono<Boolean> result = appService.updateProperty(prop)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		@DisplayName("full property lifecycle: create, read, delete")
		void propertyLifecycle_CreateReadDelete() {
			ULong appId = insertTestApp(ULong.valueOf(1), "prpcrud", "Prop CRUD App").block();

			// Create a property
			AppProperty prop = new AppProperty();
			prop.setAppId(appId);
			prop.setClientId(ULong.valueOf(1));
			prop.setName("REG_TYPE");
			prop.setValue("VERIFICATION");

			Boolean updated = appService.updateProperty(prop)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))
					.block();

			assertThat(updated).isTrue();

			// Read properties by appId
			Mono<Map<ULong, Map<String, AppProperty>>> readResult = appService
					.getProperties(null, appId, null, null)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(readResult)
					.assertNext(props -> {
						assertThat(props).isNotEmpty();
						assertThat(props).containsKey(ULong.valueOf(1));
						assertThat(props.get(ULong.valueOf(1))).containsKey("REG_TYPE");
						assertThat(props.get(ULong.valueOf(1)).get("REG_TYPE").getValue()).isEqualTo("VERIFICATION");
					})
					.verifyComplete();

			// Read properties by appCode
			Mono<Map<ULong, Map<String, AppProperty>>> readByCodeResult = appService
					.getProperties(null, null, "prpcrud", null)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(readByCodeResult)
					.assertNext(props -> {
						assertThat(props).isNotEmpty();
					})
					.verifyComplete();

			// Read with specific propName
			Mono<Map<ULong, Map<String, AppProperty>>> readByNameResult = appService
					.getProperties(null, appId, null, "REG_TYPE")
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(readByNameResult)
					.assertNext(props -> {
						assertThat(props).isNotEmpty();
						assertThat(props.values().iterator().next()).containsKey("REG_TYPE");
					})
					.verifyComplete();

			// Delete property by name
			Boolean deleted = appService.deleteProperty(ULong.valueOf(1), appId, "REG_TYPE")
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))
					.block();

			assertThat(deleted).isTrue();

			// Verify deleted
			Mono<Map<ULong, Map<String, AppProperty>>> afterDeleteResult = appService
					.getProperties(null, appId, null, null)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(afterDeleteResult)
					.assertNext(props -> assertThat(props).isEmpty())
					.verifyComplete();
		}

		@Test
		@DisplayName("deleteProperty with null appId throws BAD_REQUEST")
		void deleteProperty_NullAppId_ThrowsBadRequest() {
			Mono<Boolean> result = appService.deleteProperty(ULong.valueOf(1), null, "someName")
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		@DisplayName("deletePropertyById removes property and returns true")
		void deletePropertyById_RemovesProperty() {
			ULong appId = insertTestApp(ULong.valueOf(1), "delpbi", "Del Prop By ID App").block();

			// Insert property via raw SQL to get ID
			ULong propId = databaseClient.sql(
					"INSERT INTO security_app_property (APP_ID, CLIENT_ID, NAME, VALUE) VALUES (:appId, 1, 'TESTPROP', 'TESTVAL')")
					.bind("appId", appId.longValue())
					.filter(s -> s.returnGeneratedValues("ID"))
					.map(row -> ULong.valueOf(row.get("ID", Long.class)))
					.one()
					.block();

			assertThat(propId).isNotNull();

			Mono<Boolean> result = appService.deletePropertyById(propId)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(deleted -> assertThat(deleted).isTrue())
					.verifyComplete();
		}
	}

	// -----------------------------------------------------------------------
	// getPropertiesWithClients()
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("getPropertiesWithClients()")
	class GetPropertiesWithClientsTests {

		@Test
		@DisplayName("returns properties with client info")
		void returnsPropertiesWithClients() {
			ULong appId = insertTestApp(ULong.valueOf(1), "prpwcl", "Props With Clients App").block();

			// Insert a property
			databaseClient.sql(
					"INSERT INTO security_app_property (APP_ID, CLIENT_ID, NAME, VALUE) VALUES (:appId, 1, 'URL', 'https://test.com')")
					.bind("appId", appId.longValue())
					.then()
					.block();

			Mono<PropertiesResponse> result = appService
					.getPropertiesWithClients(null, appId, null, null)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(response -> {
						assertThat(response).isNotNull();
						assertThat(response.getProperties()).isNotNull();
						if (!response.getProperties().isEmpty()) {
							assertThat(response.getClients()).isNotNull();
						}
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("empty properties returns empty response")
		void emptyProperties_ReturnsEmptyResponse() {
			ULong appId = insertTestApp(ULong.valueOf(1), "prpwce", "Props With Clients Empty").block();

			Mono<PropertiesResponse> result = appService
					.getPropertiesWithClients(null, appId, null, null)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(response -> assertThat(response).isNotNull())
					.verifyComplete();
		}
	}

	// -----------------------------------------------------------------------
	// appInheritance()
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("appInheritance()")
	class AppInheritanceTests {

		@Test
		@DisplayName("same client code and url client code returns list with app owner")
		void sameClientAndUrl_ReturnsList() {
			insertTestApp(ULong.valueOf(1), "inhrts", "Inheritance Test").block();

			Mono<List<String>> result = appService.appInheritance("inhrts", "SYSTEM", "SYSTEM");

			StepVerifier.create(result)
					.assertNext(list -> {
						assertThat(list).isNotNull();
						assertThat(list).contains("SYSTEM");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("different client code and url client code returns list with all")
		void differentClientAndUrl_ReturnsList() {
			insertTestApp(ULong.valueOf(1), "inhrtd", "Inheritance Diff").block();

			insertTestClient("INHCLA", "Inh Client A", "BUS")
					.flatMap(id -> insertClientHierarchy(id, ULong.valueOf(1), null, null, null).thenReturn(id))
					.block();
			insertTestClient("INHCLB", "Inh Client B", "BUS")
					.flatMap(id -> insertClientHierarchy(id, ULong.valueOf(1), null, null, null).thenReturn(id))
					.block();

			Mono<List<String>> result = appService.appInheritance("inhrtd", "INHCLA", "INHCLB");

			StepVerifier.create(result)
					.assertNext(list -> {
						assertThat(list).isNotNull();
						assertThat(list).hasSize(3);
						assertThat(list).contains("SYSTEM", "INHCLA", "INHCLB");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("url client code same as client code returns two-element list")
		void sameUrlAndClientCode_ReturnsList() {
			insertTestApp(ULong.valueOf(1), "inhrts2", "Inheritance Same URL Client").block();

			insertTestClient("INHCLN", "Inh Same URL", "BUS")
					.flatMap(id -> insertClientHierarchy(id, ULong.valueOf(1), null, null, null).thenReturn(id))
					.block();

			Mono<List<String>> result = appService.appInheritance("inhrts2", "INHCLN", "INHCLN");

			StepVerifier.create(result)
					.assertNext(list -> {
						assertThat(list).isNotNull();
						// urlClientCode == clientCode but != appOwner(SYSTEM), so two elements
						assertThat(list).hasSize(2);
						assertThat(list).containsExactly("SYSTEM", "INHCLN");
					})
					.verifyComplete();
		}
	}

	// -----------------------------------------------------------------------
	// getAppExplicitInfoByCode()
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("getAppExplicitInfoByCode()")
	class GetAppExplicitInfoTests {

		@Test
		@DisplayName("OWN app returns info without explicit owner")
		void ownApp_ReturnsInfo() {
			insertTestApp(ULong.valueOf(1), "expinfo", "Explicit Info OWN").block();

			Mono<com.fincity.saas.commons.security.dto.App> result = appService
					.getAppExplicitInfoByCode("expinfo");

			StepVerifier.create(result)
					.assertNext(app -> {
						assertThat(app).isNotNull();
						assertThat(app.getClientCode()).isEqualTo("SYSTEM");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("EXPLICIT app returns info with explicit owner client code")
		void explicitApp_ReturnsInfoWithOwner() {
			ULong busClientId = insertTestClient("EXPCLI", "Explicit Owner", "BUS")
					.flatMap(id -> insertClientHierarchy(id, ULong.valueOf(1), null, null, null).thenReturn(id))
					.block();

			// Create EXPLICIT app owned by system, then give write access to busClient
			ULong appId = databaseClient.sql(
					"INSERT INTO security_app (CLIENT_ID, APP_NAME, APP_CODE, APP_TYPE, APP_ACCESS_TYPE) VALUES (1, 'Explicit Info EXPLICIT', 'explinf', 'APP', 'EXPLICIT')")
					.filter(s -> s.returnGeneratedValues("ID"))
					.map(row -> ULong.valueOf(row.get("ID", Long.class)))
					.one()
					.block();

			// Add write access for the bus client
			databaseClient.sql(
					"INSERT INTO security_app_access (APP_ID, CLIENT_ID, EDIT_ACCESS, CREATED_BY) VALUES (:appId, :clientId, 1, 1)")
					.bind("appId", appId.longValue())
					.bind("clientId", busClientId.longValue())
					.then()
					.block();

			Mono<com.fincity.saas.commons.security.dto.App> result = appService
					.getAppExplicitInfoByCode("explinf");

			StepVerifier.create(result)
					.assertNext(app -> {
						assertThat(app).isNotNull();
						assertThat(app.getClientCode()).isEqualTo("SYSTEM");
						assertThat(app.getExplicitOwnerClientCode()).isEqualTo("EXPCLI");
					})
					.verifyComplete();
		}
	}

	// -----------------------------------------------------------------------
	// getAppStatus()
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("getAppStatus()")
	class GetAppStatusTests {

		@Test
		@DisplayName("returns ACTIVE for active app")
		void activeApp_ReturnsActive() {
			insertTestApp(ULong.valueOf(1), "ststact", "Status Active App").block();

			Mono<String> result = appService.getAppStatus("ststact");

			StepVerifier.create(result)
					.assertNext(status -> assertThat(status).isEqualTo("ACTIVE"))
					.verifyComplete();
		}

		@Test
		@DisplayName("returns ARCHIVED for archived app")
		void archivedApp_ReturnsArchived() {
			databaseClient.sql(
					"INSERT INTO security_app (CLIENT_ID, APP_NAME, APP_CODE, APP_TYPE, STATUS) VALUES (1, 'Status Archived', 'ststarc', 'APP', 'ARCHIVED')")
					.then()
					.block();

			Mono<String> result = appService.getAppStatus("ststarc");

			StepVerifier.create(result)
					.assertNext(status -> assertThat(status).isEqualTo("ARCHIVED"))
					.verifyComplete();
		}
	}

	// -----------------------------------------------------------------------
	// getAppId()
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("getAppId()")
	class GetAppIdTests {

		@Test
		@DisplayName("returns the ID for an existing app code")
		void existingCode_ReturnsId() {
			ULong appId = insertTestApp(ULong.valueOf(1), "appidts", "App ID Test").block();

			Mono<ULong> result = appService.getAppId("appidts");

			StepVerifier.create(result)
					.assertNext(id -> assertThat(id).isEqualTo(appId))
					.verifyComplete();
		}

		@Test
		@DisplayName("non-existent code returns empty")
		void nonExistentCode_ReturnsEmpty() {
			StepVerifier.create(appService.getAppId("nxcoded"))
					.verifyComplete();
		}
	}

	// -----------------------------------------------------------------------
	// removeClient() and updateClientAccess()
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("removeClient() and updateClientAccess()")
	class ClientAccessManagementTests {

		@Test
		@DisplayName("system client can update client access to write")
		void systemClient_UpdatesAccess() {
			ULong busClientId = insertTestClient("UCACLI", "UCA Client", "BUS")
					.flatMap(id -> insertClientHierarchy(id, ULong.valueOf(1), null, null, null).thenReturn(id))
					.block();

			ULong appId = insertTestApp(ULong.valueOf(1), "ucaacc", "UCA Access App").block();

			// Add read-only access first
			appService.addClientAccess(appId, busClientId, false)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))
					.block();

			// Get the access ID
			ULong accessId = databaseClient.sql(
					"SELECT ID FROM security_app_access WHERE APP_ID = :appId AND CLIENT_ID = :clientId")
					.bind("appId", appId.longValue())
					.bind("clientId", busClientId.longValue())
					.map(row -> ULong.valueOf(row.get("ID", Long.class)))
					.one()
					.block();

			assertThat(accessId).isNotNull();

			// Update to write access
			Mono<Boolean> result = appService.updateClientAccess(accessId, true)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(updated -> assertThat(updated).isTrue())
					.verifyComplete();

			// Verify write access
			StepVerifier.create(appService.hasWriteAccess(appId, busClientId))
					.assertNext(hasWrite -> assertThat(hasWrite).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("system client can remove client access")
		void systemClient_RemovesAccess() {
			ULong busClientId = insertTestClient("RMACLI", "RMA Client", "BUS")
					.flatMap(id -> insertClientHierarchy(id, ULong.valueOf(1), null, null, null).thenReturn(id))
					.block();

			ULong appId = insertTestApp(ULong.valueOf(1), "rmaacc", "RMA Access App").block();

			appService.addClientAccess(appId, busClientId, false)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))
					.block();

			ULong accessId = databaseClient.sql(
					"SELECT ID FROM security_app_access WHERE APP_ID = :appId AND CLIENT_ID = :clientId")
					.bind("appId", appId.longValue())
					.bind("clientId", busClientId.longValue())
					.map(row -> ULong.valueOf(row.get("ID", Long.class)))
					.one()
					.block();

			assertThat(accessId).isNotNull();

			Mono<Boolean> result = appService.removeClient(appId, accessId)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(removed -> assertThat(removed).isTrue())
					.verifyComplete();

			// Verify no longer has access
			StepVerifier.create(appService.hasReadAccess(appId, busClientId))
					.assertNext(hasRead -> assertThat(hasRead).isFalse())
					.verifyComplete();
		}
	}

	// -----------------------------------------------------------------------
	// addAppDependency validation
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("addAppDependency() and removeAppDependency()")
	class AppDependencyTests {

		@Test
		@DisplayName("adding self-dependency throws BAD_REQUEST")
		void selfDependency_ThrowsBadRequest() {
			insertTestApp(ULong.valueOf(1), "selfdep", "Self Dep App").block();

			Mono<AppDependency> result = appService.addAppDependency("selfdep", "selfdep")
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		@DisplayName("adding dependency with blank appCode throws BAD_REQUEST")
		void blankAppCode_ThrowsBadRequest() {
			Mono<AppDependency> result = appService.addAppDependency("", "someapp")
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		@DisplayName("full dependency lifecycle: add, get, remove")
		void dependencyLifecycle() {
			insertTestApp(ULong.valueOf(1), "depmain", "Dep Main App").block();
			insertTestApp(ULong.valueOf(1), "depchld", "Dep Child App").block();

			// Add dependency
			Mono<AppDependency> addResult = appService.addAppDependency("depmain", "depchld")
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(addResult)
					.assertNext(dep -> {
						assertThat(dep).isNotNull();
						assertThat(dep.getAppId()).isNotNull();
						assertThat(dep.getDependentAppId()).isNotNull();
					})
					.verifyComplete();

			// Get dependencies
			Mono<List<AppDependency>> getResult = appService.getAppDependencies("depmain")
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(getResult)
					.assertNext(deps -> {
						assertThat(deps).hasSize(1);
						assertThat(deps.get(0).getDependentAppCode()).isEqualTo("depchld");
					})
					.verifyComplete();

			// Remove dependency
			Mono<Boolean> removeResult = appService.removeAppDependency("depmain", "depchld")
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(removeResult)
					.assertNext(removed -> assertThat(removed).isTrue())
					.verifyComplete();

			// Verify empty - DAO returns Mono.empty() when no dependencies exist
			Mono<List<AppDependency>> afterRemove = appService.getAppDependencies("depmain")
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(afterRemove)
					.verifyComplete();
		}
	}

	// -----------------------------------------------------------------------
	// hasDeleteAccess()
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("hasDeleteAccess()")
	class HasDeleteAccessTests {

		@Test
		@DisplayName("owner client with no other write access has delete access")
		void ownerWithNoOthers_HasDeleteAccess() {
			insertTestApp(ULong.valueOf(1), "hdaown", "HDA Own App").block();

			Mono<Boolean> result = appService.hasDeleteAccess("hdaown", "SYSTEM")
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(hasAccess -> assertThat(hasAccess).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("client without write access returns false")
		void noWriteAccess_ReturnsFalse() {
			insertTestClient("HDANCL", "HDA No Access", "BUS")
					.flatMap(id -> insertClientHierarchy(id, ULong.valueOf(1), null, null, null).thenReturn(id))
					.block();

			insertTestApp(ULong.valueOf(1), "hdanoa", "HDA No Access App").block();

			ContextAuthentication busAuth = TestDataFactory.createBusinessAuth(
					ULong.valueOf(2), "HDANCL",
					List.of("Authorities.Application_DELETE", "Authorities.Logged_IN"));

			Mono<Boolean> result = appService.hasDeleteAccess("hdanoa", "HDANCL")
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(busAuth));

			StepVerifier.create(result)
					.assertNext(hasAccess -> assertThat(hasAccess).isFalse())
					.verifyComplete();
		}
	}

	// -----------------------------------------------------------------------
	// findBaseClientCodeForOverride()
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("findBaseClientCodeForOverride()")
	class FindBaseClientCodeTests {

		@Test
		@DisplayName("same client as app owner returns empty")
		void sameClient_ReturnsEmpty() {
			insertTestApp(ULong.valueOf(1), "fbcown", "FBC Own App").block();

			Mono<reactor.util.function.Tuple2<String, Boolean>> result = appService
					.findBaseClientCodeForOverride("fbcown")
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			// When client is same as app owner, it should return empty (line 779 condition)
			StepVerifier.create(result)
					.verifyComplete();
		}

		@Test
		@DisplayName("different client on OWN app returns base client code without write flag")
		void differentClient_OwnApp_ReturnsBaseCode() {
			ULong busClientId = insertTestClient("FBCBUS", "FBC Bus Client", "BUS")
					.flatMap(id -> insertClientHierarchy(id, ULong.valueOf(1), null, null, null).thenReturn(id))
					.block();

			ULong appId = insertTestApp(ULong.valueOf(1), "fbcacc", "FBC Access App").block();

			appService.addClientAccess(appId, busClientId, true)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))
					.block();

			ContextAuthentication busAuth = TestDataFactory.createBusinessAuth(
					busClientId, "FBCBUS",
					List.of("Authorities.Application_READ", "Authorities.Logged_IN"));

			Mono<reactor.util.function.Tuple2<String, Boolean>> result = appService
					.findBaseClientCodeForOverride("fbcacc")
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(busAuth));

			StepVerifier.create(result)
					.assertNext(tuple -> {
						assertThat(tuple.getT1()).isEqualTo("SYSTEM");
						assertThat(tuple.getT2()).isFalse();
					})
					.verifyComplete();
		}
	}

	// -----------------------------------------------------------------------
	// createPropertiesFromTransport()
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("createPropertiesFromTransport()")
	class CreatePropertiesFromTransportTests {

		@Test
		@DisplayName("null properties list returns true")
		void nullList_ReturnsTrue() {
			StepVerifier.create(appService.createPropertiesFromTransport(ULong.valueOf(1), null))
					.assertNext(result -> assertThat(result).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("empty properties list returns true")
		void emptyList_ReturnsTrue() {
			StepVerifier.create(appService.createPropertiesFromTransport(ULong.valueOf(1), List.of()))
					.assertNext(result -> assertThat(result).isTrue())
					.verifyComplete();
		}
	}

	// -----------------------------------------------------------------------
	// Business client access control in create()
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("create() with business client access control")
	class CreateAccessControlTests {

		@Test
		@DisplayName("business client creating app for itself succeeds")
		void businessClient_CreatesOwnApp() {
			ULong busClientId = insertTestClient("CRTBUS", "Create Bus Client", "BUS")
					.flatMap(id -> insertClientHierarchy(id, ULong.valueOf(1), null, null, null).thenReturn(id))
					.block();

			ContextAuthentication busAuth = TestDataFactory.createBusinessAuth(
					busClientId, "CRTBUS",
					List.of("Authorities.Application_CREATE", "Authorities.Application_READ",
							"Authorities.Application_UPDATE", "Authorities.Application_DELETE",
							"Authorities.Logged_IN"));

			App app = new App();
			app.setAppCode("busown");
			app.setAppName("Business Own App");
			app.setAppType(SecurityAppAppType.APP);

			Mono<App> result = appService.create(app)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(busAuth));

			StepVerifier.create(result)
					.assertNext(created -> {
						assertThat(created).isNotNull();
						assertThat(created.getAppCode()).isEqualTo("busown");
						assertThat(created.getClientId()).isEqualTo(busClientId);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("business client creating app for unmanaged client is forbidden")
		void businessClient_UnmanagedClient_Forbidden() {
			ULong bus1Id = insertTestClient("CRTBA", "Create Bus A", "BUS")
					.flatMap(id -> insertClientHierarchy(id, ULong.valueOf(1), null, null, null).thenReturn(id))
					.block();

			ULong bus2Id = insertTestClient("CRTBB", "Create Bus B", "BUS")
					.flatMap(id -> insertClientHierarchy(id, ULong.valueOf(1), null, null, null).thenReturn(id))
					.block();

			ContextAuthentication busAuth = TestDataFactory.createBusinessAuth(
					bus1Id, "CRTBA",
					List.of("Authorities.Application_CREATE", "Authorities.Logged_IN"));

			App app = new App();
			app.setAppCode("busfail");
			app.setAppName("Business Fail App");
			app.setAppType(SecurityAppAppType.APP);
			app.setClientId(bus2Id);

			Mono<App> result = appService.create(app)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(busAuth));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}

	// -----------------------------------------------------------------------
	// update() full entity with non-system client
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("update() with non-system client")
	class UpdateWithNonSystemTests {

		@Test
		@DisplayName("business client updating own app succeeds")
		void businessClient_UpdatesOwnApp() {
			ULong busClientId = insertTestClient("UPDBUS", "Update Bus", "BUS")
					.flatMap(id -> insertClientHierarchy(id, ULong.valueOf(1), null, null, null).thenReturn(id))
					.block();

			// Create app owned by this business client
			ULong appId = databaseClient.sql(
					"INSERT INTO security_app (CLIENT_ID, APP_NAME, APP_CODE, APP_TYPE) VALUES (:clientId, 'Bus App', 'updbown', 'APP')")
					.bind("clientId", busClientId.longValue())
					.filter(s -> s.returnGeneratedValues("ID"))
					.map(row -> ULong.valueOf(row.get("ID", Long.class)))
					.one()
					.block();

			ContextAuthentication busAuth = TestDataFactory.createBusinessAuth(
					busClientId, "UPDBUS",
					List.of("Authorities.Application_CREATE", "Authorities.Application_READ",
							"Authorities.Application_UPDATE", "Authorities.Logged_IN"));

			// System client has to manage the bus client for updatableEntity to work.
			// Since busClientId hierarchy has level0 = 1 (SYSTEM), system manages it.
			// But the bus client calling update will use isUserClientManageClient, which
			// checks if busClientId manages its own client (itself) - it does.

			App updated = new App();
			updated.setId(appId);
			updated.setAppName("Updated Bus App Name");
			updated.setAppType(SecurityAppAppType.APP);
			updated.setStatus(SecurityAppStatus.ACTIVE);

			Mono<App> result = appService.update(updated)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(busAuth));

			StepVerifier.create(result)
					.assertNext(app -> {
						assertThat(app).isNotNull();
						assertThat(app.getAppName()).isEqualTo("Updated Bus App Name");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("update non-existent app throws NOT_FOUND")
		void updateNonExistent_ThrowsNotFound() {
			App app = new App();
			app.setId(ULong.valueOf(999999));
			app.setAppName("Ghost App");
			app.setAppType(SecurityAppAppType.APP);

			Mono<App> result = appService.update(app)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.NOT_FOUND)
					.verify();
		}
	}

	// -----------------------------------------------------------------------
	// Properties with non-system client
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("Properties with non-system client")
	class PropertiesNonSystemTests {

		@Test
		@DisplayName("business client can read properties of its own apps")
		void businessClient_ReadsOwnProperties() {
			ULong busClientId = insertTestClient("PRPBUS", "Prop Bus Client", "BUS")
					.flatMap(id -> insertClientHierarchy(id, ULong.valueOf(1), null, null, null).thenReturn(id))
					.block();

			ULong appId = databaseClient.sql(
					"INSERT INTO security_app (CLIENT_ID, APP_NAME, APP_CODE, APP_TYPE) VALUES (:clientId, 'Bus Prop App', 'prpbapp', 'APP')")
					.bind("clientId", busClientId.longValue())
					.filter(s -> s.returnGeneratedValues("ID"))
					.map(row -> ULong.valueOf(row.get("ID", Long.class)))
					.one()
					.block();

			// Insert property for bus client
			databaseClient.sql(
					"INSERT INTO security_app_property (APP_ID, CLIENT_ID, NAME, VALUE) VALUES (:appId, :clientId, 'MY_PROP', 'my_value')")
					.bind("appId", appId.longValue())
					.bind("clientId", busClientId.longValue())
					.then()
					.block();

			ContextAuthentication busAuth = TestDataFactory.createBusinessAuth(
					busClientId, "PRPBUS",
					List.of("Authorities.Application_READ", "Authorities.Logged_IN"));

			Mono<Map<ULong, Map<String, AppProperty>>> result = appService
					.getProperties(null, appId, null, null)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(busAuth));

			StepVerifier.create(result)
					.assertNext(props -> {
						assertThat(props).isNotNull();
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("updateProperty as system client with write access succeeds")
		void systemClient_UpdatesProperty_WithWriteAccess() {
			ULong appId = insertTestApp(ULong.valueOf(1), "updsys", "Update Sys Prop App").block();

			AppProperty prop = new AppProperty();
			prop.setAppId(appId);
			prop.setClientId(ULong.valueOf(1));
			prop.setName("SYS_PROP");
			prop.setValue("sys_value");

			Mono<Boolean> result = appService.updateProperty(prop)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(updated -> assertThat(updated).isTrue())
					.verifyComplete();
		}
	}

	// -----------------------------------------------------------------------
	// delete() with EXPLICIT access type app
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("delete() EXPLICIT access type")
	class DeleteExplicitTests {

		@Test
		@DisplayName("system client can delete EXPLICIT app")
		void systemClient_DeletesExplicit() {
			ULong appId = databaseClient.sql(
					"INSERT INTO security_app (CLIENT_ID, APP_NAME, APP_CODE, APP_TYPE, APP_ACCESS_TYPE) VALUES (1, 'Explicit Del', 'delexpl', 'APP', 'EXPLICIT')")
					.filter(s -> s.returnGeneratedValues("ID"))
					.map(row -> ULong.valueOf(row.get("ID", Long.class)))
					.one()
					.block();

			Mono<Integer> result = appService.delete(appId)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(count -> assertThat(count).isEqualTo(1))
					.verifyComplete();

			// Verify it was archived (ACTIVE -> ARCHIVED on first delete)
			String status = databaseClient.sql("SELECT STATUS FROM security_app WHERE ID = :id")
					.bind("id", appId.longValue())
					.map(row -> row.get("STATUS", String.class))
					.one()
					.block();
			assertThat(status).isEqualTo("ARCHIVED");
		}
	}

	// -----------------------------------------------------------------------
	// fillApps()
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("fillApps()")
	class FillAppsTests {

		@Test
		@DisplayName("fills apps for clients with owned apps")
		void fillsAppsForClients() {
			ULong busClientId = insertTestClient("FLACLI", "Fill Apps Client", "BUS")
					.flatMap(id -> insertClientHierarchy(id, ULong.valueOf(1), null, null, null).thenReturn(id))
					.block();

			ULong appId = databaseClient.sql(
					"INSERT INTO security_app (CLIENT_ID, APP_NAME, APP_CODE, APP_TYPE) VALUES (:clientId, 'Fill Apps App', 'fillaap', 'APP')")
					.bind("clientId", busClientId.longValue())
					.filter(s -> s.returnGeneratedValues("ID"))
					.map(row -> ULong.valueOf(row.get("ID", Long.class)))
					.one()
					.block();
			assertThat(appId).isNotNull();

			com.fincity.security.dto.Client busClient = new com.fincity.security.dto.Client();
			busClient.setId(busClientId);
			busClient.setCode("FLACLI");
			busClient.setName("Fill Apps Client");

			Mono<List<com.fincity.security.dto.Client>> result = appService
					.fillApps(Map.of(busClientId, busClient))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(clients -> {
						assertThat(clients).isNotNull();
						assertThat(clients).hasSize(1);
						assertThat(clients.get(0).getApps()).isNotNull();
						assertThat(clients.get(0).getApps()).isNotEmpty();
					})
					.verifyComplete();
		}
	}

	// -----------------------------------------------------------------------
	// addClientAccess with non-system client
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("addClientAccess() non-system client scenarios")
	class AddClientAccessNonSystemTests {

		@Test
		@DisplayName("system client addClientAccess for child client on system-owned app succeeds")
		void systemClient_AddsAccessForChild() {
			ULong childClientId = insertTestClient("ACNCHD", "ACN Child", "BUS")
					.flatMap(id -> insertClientHierarchy(id, ULong.valueOf(1), null, null, null).thenReturn(id))
					.block();

			// Create an OWN app owned by system
			ULong appId = insertTestApp(ULong.valueOf(1), "acnown", "ACN Own App").block();

			// System client grants read access for the child client
			Mono<Boolean> result = appService.addClientAccess(appId, childClientId, false)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(added -> assertThat(added).isTrue())
					.verifyComplete();

			// Verify access was granted
			StepVerifier.create(appService.hasReadAccess(appId, childClientId))
					.assertNext(hasRead -> assertThat(hasRead).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("system client addClientAccess for ANY app grants access")
		void systemClient_AnyApp_GrantsAccess() {
			ULong childClientId = insertTestClient("ACNACH", "ACN Any Child", "BUS")
					.flatMap(id -> insertClientHierarchy(id, ULong.valueOf(1), null, null, null).thenReturn(id))
					.block();

			// Create an ANY access type app owned by system
			ULong appId = databaseClient.sql(
					"INSERT INTO security_app (CLIENT_ID, APP_NAME, APP_CODE, APP_TYPE, APP_ACCESS_TYPE) VALUES (1, 'ANY App', 'acnanyap', 'APP', 'ANY')")
					.filter(s -> s.returnGeneratedValues("ID"))
					.map(row -> ULong.valueOf(row.get("ID", Long.class)))
					.one()
					.block();

			Mono<Boolean> result = appService.addClientAccess(appId, childClientId, true)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(added -> assertThat(added).isTrue())
					.verifyComplete();

			// Verify write access
			StepVerifier.create(appService.hasWriteAccess(appId, childClientId))
					.assertNext(hasWrite -> assertThat(hasWrite).isTrue())
					.verifyComplete();
		}
	}

	// -----------------------------------------------------------------------
	// evict()
	// -----------------------------------------------------------------------

	@Nested
	@DisplayName("evict()")
	class EvictTests {

		@Test
		@DisplayName("evict cache for app and client completes without error")
		void evictCache_Completes() {
			ULong busClientId = insertTestClient("EVCBUS", "Evict Bus", "BUS")
					.flatMap(id -> insertClientHierarchy(id, ULong.valueOf(1), null, null, null).thenReturn(id))
					.block();

			ULong appId = insertTestApp(ULong.valueOf(1), "evctst", "Evict Test App").block();

			Mono<Boolean> result = appService.evict(appId, busClientId)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(evicted -> assertThat(evicted).isNotNull())
					.verifyComplete();
		}
	}
}
