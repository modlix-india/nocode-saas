package com.fincity.security.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.security.dto.App;
import com.fincity.security.jooq.enums.SecurityAppAppType;
import com.fincity.security.model.AppDependency;
import com.fincity.security.service.AppService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppLifecycleIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private AppService appService;

	private ContextAuthentication systemAuth;
	private ULong createdAppId;
	private ULong secondAppId;
	private ULong businessClientId;

	@BeforeAll
	void setupTestData() {
		setupMockBeans();
		systemAuth = TestDataFactory.createSystemAuth();

		// Insert a business client for access control tests
		businessClientId = insertTestClient("TSBUSAP", "Test Business Client For App", "BUS")
				.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
						.thenReturn(clientId))
				.block();
	}

	@BeforeEach
	void setUp() {
		setupMockBeans();
	}

	@Test
	@Order(1)
	void createApp_WithValidData_ReturnsCreatedApp() {

		App app = new App();
		app.setAppCode("testcrud");
		app.setAppName("Test CRUD App");
		app.setAppType(SecurityAppAppType.APP);

		Mono<App> result = appService.create(app)
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

		StepVerifier.create(result)
				.assertNext(response -> {
					assertThat(response).isNotNull();
					assertThat(response.getId()).isNotNull();
					assertThat(response.getAppCode()).isEqualTo("testcrud");
					assertThat(response.getAppName()).isEqualTo("Test CRUD App");
					assertThat(response.getAppType()).isEqualTo(SecurityAppAppType.APP);
					createdAppId = response.getId();
				})
				.verifyComplete();

		assertThat(createdAppId).isNotNull();
	}

	@Test
	@Order(2)
	void readApp_ById_ReturnsApp() {

		assertThat(createdAppId).isNotNull();

		Mono<App> result = appService.read(createdAppId)
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

		StepVerifier.create(result)
				.assertNext(response -> {
					assertThat(response).isNotNull();
					assertThat(response.getId()).isEqualTo(createdAppId);
					assertThat(response.getAppCode()).isEqualTo("testcrud");
					assertThat(response.getAppName()).isEqualTo("Test CRUD App");
					assertThat(response.getAppType()).isEqualTo(SecurityAppAppType.APP);
				})
				.verifyComplete();
	}

	@Test
	@Order(3)
	void getAppByCode_ReturnsApp() {

		Mono<App> result = appService.getAppByCode("testcrud")
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

		StepVerifier.create(result)
				.assertNext(response -> {
					assertThat(response).isNotNull();
					assertThat(response.getAppCode()).isEqualTo("testcrud");
					assertThat(response.getAppName()).isEqualTo("Test CRUD App");
				})
				.verifyComplete();
	}

	@Test
	@Order(4)
	void updateApp_ChangeName_ReturnsUpdatedApp() {

		assertThat(createdAppId).isNotNull();

		App app = new App();
		app.setId(createdAppId);
		app.setAppCode("testcrud");
		app.setAppName("Test CRUD App Updated");
		app.setAppType(SecurityAppAppType.APP);

		Mono<App> result = appService.update(app)
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

		StepVerifier.create(result)
				.assertNext(response -> {
					assertThat(response).isNotNull();
					assertThat(response.getId()).isEqualTo(createdAppId);
					assertThat(response.getAppName()).isEqualTo("Test CRUD App Updated");
				})
				.verifyComplete();
	}

	@Test
	@Order(5)
	void addClientAccess_GrantsAccess() {

		assertThat(createdAppId).isNotNull();
		assertThat(businessClientId).isNotNull();

		Mono<Boolean> result = appService.addClientAccess(createdAppId, businessClientId, false)
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

		StepVerifier.create(result)
				.assertNext(response -> assertThat(response).isTrue())
				.verifyComplete();
	}

	@Test
	@Order(6)
	void hasReadAccess_AfterGrant_ReturnsTrue() {

		Mono<Boolean> result = appService.hasReadAccess("testcrud", "TSBUSAP")
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

		StepVerifier.create(result)
				.assertNext(response -> assertThat(response).isTrue())
				.verifyComplete();
	}

	@Test
	@Order(7)
	void createSecondApp_ForDependencyTest() {

		secondAppId = insertTestApp(ULong.valueOf(1), "testdep", "Test Dependency App").block();
		assertThat(secondAppId).isNotNull();
	}

	@Test
	@Order(8)
	void addAppDependency_ReturnsCreatedDependency() {

		Mono<AppDependency> result = appService.addAppDependency("testcrud", "testdep")
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

		StepVerifier.create(result)
				.assertNext(response -> {
					assertThat(response).isNotNull();
					assertThat(response.getAppId()).isNotNull();
					assertThat(response.getDependentAppId()).isNotNull();
				})
				.verifyComplete();
	}

	@Test
	@Order(9)
	void getAppDependencies_ReturnsAll() {

		Mono<List<AppDependency>> result = appService.getAppDependencies("testcrud")
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

		StepVerifier.create(result)
				.assertNext(response -> {
					assertThat(response).isNotNull();
					assertThat(response).isNotEmpty();
					assertThat(response.get(0).getDependentAppCode()).isEqualTo("testdep");
				})
				.verifyComplete();
	}

	@Test
	@Order(10)
	void removeAppDependency_ReturnsTrue() {

		Mono<Boolean> result = appService.removeAppDependency("testcrud", "testdep")
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

		StepVerifier.create(result)
				.assertNext(response -> assertThat(response).isTrue())
				.verifyComplete();
	}

	@Test
	@Order(11)
	void deleteApp_SoftDeletes_ArchivesFirst() {

		assertThat(createdAppId).isNotNull();

		Mono<Integer> result = appService.delete(createdAppId)
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

		StepVerifier.create(result)
				.assertNext(response -> assertThat(response).isEqualTo(1))
				.verifyComplete();

		// Verify app is archived via direct SQL (service read may be cached)
		String status = databaseClient.sql("SELECT STATUS FROM security_app WHERE ID = :id")
				.bind("id", createdAppId.longValue())
				.map(row -> row.get("STATUS", String.class))
				.one()
				.block();

		assertThat(status).isEqualTo("ARCHIVED");
	}

	@Test
	@Order(12)
	void deleteApp_HardDelete_RemovesArchivedApp() {

		// Remove client access entries that reference this app (FK constraint)
		databaseClient.sql("DELETE FROM security_app_access WHERE APP_ID = :appId")
				.bind("appId", createdAppId.longValue())
				.then()
				.block();

		assertThat(createdAppId).isNotNull();

		// Second delete on archived app should hard-delete
		Mono<Integer> result = appService.delete(createdAppId)
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

		StepVerifier.create(result)
				.assertNext(response -> assertThat(response).isEqualTo(1))
				.verifyComplete();

		// Verify the app no longer exists
		Long count = databaseClient.sql("SELECT COUNT(*) AS cnt FROM security_app WHERE ID = :id")
				.bind("id", createdAppId.longValue())
				.map(row -> row.get("cnt", Long.class))
				.one()
				.block();

		assertThat(count).isZero();
	}

	@Test
	@Order(13)
	void createAppWithInvalidCode_ThrowsError() {

		App app = new App();
		app.setAppCode("test-invalid!");
		app.setAppName("Invalid App");
		app.setAppType(SecurityAppAppType.APP);

		Mono<App> result = appService.create(app)
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

		StepVerifier.create(result)
				.expectError()
				.verify();
	}

	@Test
	@Order(14)
	void addDependency_SameApp_ThrowsError() {

		App app = new App();
		app.setAppCode("selfdepcr");
		app.setAppName("Self Dep App");
		app.setAppType(SecurityAppAppType.APP);

		// Create app first
		App created = appService.create(app)
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))
				.block();

		assertThat(created).isNotNull();

		// Try to add self-dependency
		Mono<AppDependency> result = appService.addAppDependency("selfdepcr", "selfdepcr")
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

		StepVerifier.create(result)
				.expectError()
				.verify();
	}

	@Test
	@Order(15)
	@DisplayName("a new app is given a draft URL, and a hard delete takes it away again")
	void createApp_MintsDraftUrl_AndHardDeleteClearsIt() {

		App app = new App();
		app.setAppCode("draftapp");
		app.setAppName("Draft URL App");
		app.setAppType(SecurityAppAppType.APP);

		App created = appService.create(app)
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))
				.block();

		assertThat(created).isNotNull();

		// Read through SQL rather than the service: the point is that the row is
		// really there, written by the create itself and not by anyone asking.
		Map<String, Object> draft = databaseClient
				.sql("SELECT URL_PATTERN, URL_TYPE, CLIENT_ID FROM security_client_url WHERE APP_CODE = :appCode")
				.bind("appCode", "draftapp")
				.fetch().one().block();

		assertThat(draft).isNotNull();
		assertThat(draft.get("URL_TYPE")).hasToString("DRAFT");
		assertThat(draft.get("URL_PATTERN").toString()).matches("^d[0-9a-f]{32}.*\\.modlix\\.com$");
		assertThat(((Number) draft.get("CLIENT_ID")).longValue()).isEqualTo(created.getClientId().longValue());

		// Archive, then hard delete. FK1_CLIENT_URL_APP_CODE is ON DELETE RESTRICT,
		// so the second call only succeeds because the delete path clears the draft
		// row first. Before it did, giving every app a draft URL made every app
		// undeletable.
		appService.delete(created.getId())
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)).block();
		appService.delete(created.getId())
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)).block();

		Long remaining = databaseClient
				.sql("SELECT COUNT(*) AS cnt FROM security_client_url WHERE APP_CODE = :appCode")
				.bind("appCode", "draftapp")
				.map(row -> row.get("cnt", Long.class)).one().block();

		assertThat(remaining).isZero();
	}

	@AfterAll
	void cleanup() {
		databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
				// Every app created above was given one of these, and the container
				// database is shared with every other integration test class.
				.then(databaseClient.sql("DELETE FROM security_client_url WHERE URL_TYPE = 'DRAFT'").then())
				.then(databaseClient.sql("DELETE FROM security_app_dependency WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_app_access WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_app_property WHERE ID > 0").then())
				.then(databaseClient
						.sql("DELETE FROM security_app WHERE APP_CODE NOT IN ('appbuilder', 'nothing')").then())
				.then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client_activity WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}
}
