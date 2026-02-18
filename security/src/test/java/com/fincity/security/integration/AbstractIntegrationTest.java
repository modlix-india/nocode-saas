package com.fincity.security.integration;

import org.jooq.types.ULong;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;

import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.security.feign.IFeignFilesService;

import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

	static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
			.withDatabaseName("security")
			.withUsername("test")
			.withPassword("test")
			.withReuse(true);

	static {
		mysql.start();
	}

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {

		String host = mysql.getHost();
		Integer port = mysql.getFirstMappedPort();

		registry.add("spring.r2dbc.url",
				() -> "r2dbc:mysql://" + host + ":" + port + "/security");
		registry.add("spring.r2dbc.username", () -> "test");
		registry.add("spring.r2dbc.password", () -> "test");

		registry.add("spring.flyway.url",
				() -> "jdbc:mysql://" + host + ":" + port + "/security");
		registry.add("spring.flyway.user", () -> "test");
		registry.add("spring.flyway.password", () -> "test");
	}

	@MockitoBean
	protected CachingConnectionFactory cachingConnectionFactory;

	@MockitoBean
	protected EventCreationService eventCreationService;

	@MockitoBean
	protected IFeignFilesService feignFilesService;

	@Autowired
	protected DatabaseClient databaseClient;

	protected void setupMockBeans() {
		lenient().when(eventCreationService.createEvent(any()))
				.thenReturn(Mono.just(true));
		lenient().when(feignFilesService.createInternalAccessPath(any()))
				.thenReturn(Mono.just(new IFeignFilesService.FilesAccessPath()));
	}

	protected Mono<Void> cleanupTestData() {
		return databaseClient.sql("DELETE FROM security_user_token WHERE USER_ID > 1").then()
				.then(databaseClient.sql("DELETE FROM security_client_manager WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_user WHERE ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then());
	}

	protected Mono<ULong> insertTestClient(String code, String name, String typeCode) {
		return databaseClient.sql(
				"INSERT INTO security_client (CODE, NAME, TYPE_CODE, TOKEN_VALIDITY_MINUTES, STATUS_CODE) VALUES (:code, :name, :typeCode, 60, 'ACTIVE')")
				.bind("code", code)
				.bind("name", name)
				.bind("typeCode", typeCode)
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	protected Mono<ULong> insertTestUser(ULong clientId, String userName, String email, String password) {
		return databaseClient.sql(
				"INSERT INTO security_user (CLIENT_ID, USER_NAME, EMAIL_ID, FIRST_NAME, LAST_NAME, PASSWORD, PASSWORD_HASHED, STATUS_CODE) VALUES (:clientId, :userName, :email, 'Test', 'User', :password, false, 'ACTIVE')")
				.bind("clientId", clientId.longValue())
				.bind("userName", userName)
				.bind("email", email)
				.bind("password", password)
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	protected Mono<Void> insertClientHierarchy(ULong clientId, ULong level0, ULong level1, ULong level2,
			ULong level3) {
		var spec = databaseClient.sql(
				"INSERT INTO security_client_hierarchy (CLIENT_ID, MANAGE_CLIENT_LEVEL_0, MANAGE_CLIENT_LEVEL_1, MANAGE_CLIENT_LEVEL_2, MANAGE_CLIENT_LEVEL_3) VALUES (:clientId, :level0, :level1, :level2, :level3)")
				.bind("clientId", clientId.longValue());

		spec = level0 != null ? spec.bind("level0", level0.longValue()) : spec.bindNull("level0", Long.class);
		spec = level1 != null ? spec.bind("level1", level1.longValue()) : spec.bindNull("level1", Long.class);
		spec = level2 != null ? spec.bind("level2", level2.longValue()) : spec.bindNull("level2", Long.class);
		spec = level3 != null ? spec.bind("level3", level3.longValue()) : spec.bindNull("level3", Long.class);

		return spec.then();
	}

	protected Mono<Void> insertClientManager(ULong clientId, ULong managerId) {
		return databaseClient.sql(
				"INSERT INTO security_client_manager (CLIENT_ID, MANAGER_ID) VALUES (:clientId, :managerId)")
				.bind("clientId", clientId.longValue())
				.bind("managerId", managerId.longValue())
				.then();
	}

	protected Mono<ULong> insertTestApp(ULong clientId, String appCode, String appName) {
		return databaseClient.sql(
				"INSERT INTO security_app (CLIENT_ID, APP_NAME, APP_CODE, APP_TYPE) VALUES (:clientId, :appName, :appCode, 'APP')")
				.bind("clientId", clientId.longValue())
				.bind("appName", appName)
				.bind("appCode", appCode)
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}
}
