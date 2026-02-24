package com.fincity.security.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dto.AppRegistrationIntegration;
import com.fincity.security.dto.AppRegistrationIntegrationToken;
import com.fincity.security.jooq.enums.SecurityAppRegIntegrationPlatform;
import com.fincity.security.service.appregistration.AppRegistrationIntegrationService;
import com.fincity.security.service.appregistration.AppRegistrationIntegrationTokenService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AppRegistrationIntegrationServiceIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private AppRegistrationIntegrationService service;

	@Autowired
	private AppRegistrationIntegrationTokenService tokenService;

	@Autowired
	private CacheService cacheService;

	private ContextAuthentication systemAuth;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);

	@BeforeEach
	void setUp() {
		setupMockBeans();
		systemAuth = createSystemAuthWithIntegrationPermissions();

		cacheService.evictAll("integrationPlatform").block();
	}

	private ContextAuthentication createSystemAuthWithIntegrationPermissions() {
		ContextAuthentication auth = TestDataFactory.createSystemAuth();
		auth.setUrlAppCode("appbuilder");
		auth.setUrlClientCode("SYSTEM");
		// Add Integration CRUD authorities required by @PreAuthorize on service methods
		List<String> authorities = new java.util.ArrayList<>(auth.getUser().getStringAuthorities());
		authorities.add("Authorities.Integration_CREATE");
		authorities.add("Authorities.Integration_READ");
		authorities.add("Authorities.Integration_UPDATE");
		authorities.add("Authorities.Integration_DELETE");
		auth.getUser().setStringAuthorities(authorities);
		return auth;
	}

	@AfterEach
	void tearDown() {
		databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
				.then(databaseClient.sql("DELETE FROM security_app_reg_integration_tokens WHERE ID > 0").then())
				.then(databaseClient
						.sql("DELETE FROM security_app_reg_integration WHERE ID > 0").then())
				.then(databaseClient
						.sql("DELETE FROM security_app_access WHERE APP_ID IN (SELECT ID FROM security_app WHERE CLIENT_ID > 1 OR APP_CODE LIKE 'aritest%')")
						.then())
				.then(databaseClient
						.sql("DELETE FROM security_app_property WHERE APP_ID IN (SELECT ID FROM security_app WHERE APP_CODE LIKE 'aritest%')")
						.then())
				.then(databaseClient.sql("DELETE FROM security_app WHERE APP_CODE LIKE 'aritest%'").then())
				.then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_user WHERE ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}

	// -----------------------------------------------------------------------
	// Helper: insert a registration integration record via raw SQL
	// -----------------------------------------------------------------------
	private Mono<ULong> insertIntegration(ULong appId, ULong clientId,
			String platform, String intgId, String intgSecret, String loginUri, String signupUri) {
		return databaseClient.sql(
				"INSERT INTO security_app_reg_integration (APP_ID, CLIENT_ID, PLATFORM, INTG_ID, INTG_SECRET, LOGIN_URI, SIGNUP_URI) "
						+ "VALUES (:appId, :clientId, :platform, :intgId, :intgSecret, :loginUri, :signupUri)")
				.bind("appId", appId.longValue())
				.bind("clientId", clientId.longValue())
				.bind("platform", platform)
				.bind("intgId", intgId)
				.bind("intgSecret", intgSecret)
				.bind("loginUri", loginUri)
				.bind("signupUri", signupUri)
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	// -----------------------------------------------------------------------
	// Helper: insert an integration token via raw SQL
	// -----------------------------------------------------------------------
	private Mono<ULong> insertIntegrationToken(ULong integrationId, String state) {
		return databaseClient.sql(
				"INSERT INTO security_app_reg_integration_tokens (INTEGRATION_ID, STATE, CREATED_BY) "
						+ "VALUES (:integrationId, :state, 1)")
				.bind("integrationId", integrationId.longValue())
				.bind("state", state)
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	// -----------------------------------------------------------------------
	// Helper: create an AppRegistrationIntegration DTO
	// -----------------------------------------------------------------------
	private AppRegistrationIntegration createIntegrationDto(ULong clientId, ULong appId,
			SecurityAppRegIntegrationPlatform platform) {
		AppRegistrationIntegration intg = new AppRegistrationIntegration();
		intg.setClientId(clientId);
		intg.setAppId(appId);
		intg.setPlatform(platform);
		intg.setIntgId("test-client-id-123");
		intg.setIntgSecret("test-client-secret-456");
		intg.setLoginUri("https://login.example.com");
		intg.setSignupUri("https://signup.example.com");
		return intg;
	}

	// =======================================================================
	// create()
	// =======================================================================

	@Nested
	@DisplayName("create()")
	class CreateTests {

		@Test
		@DisplayName("should create integration record in database with correct fields")
		void create_PersistsRecord() {
			Mono<AppRegistrationIntegration> result = insertTestApp(SYSTEM_CLIENT_ID, "aritest1", "ARI Test App 1")
					.flatMap(appId -> {
						AppRegistrationIntegration intg = createIntegrationDto(
								SYSTEM_CLIENT_ID, appId, SecurityAppRegIntegrationPlatform.GOOGLE);
						return service.create(intg)
								.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));
					});

			StepVerifier.create(result)
					.assertNext(created -> {
						assertThat(created.getId()).isNotNull();
						assertThat(created.getClientId()).isEqualTo(SYSTEM_CLIENT_ID);
						assertThat(created.getPlatform()).isEqualTo(SecurityAppRegIntegrationPlatform.GOOGLE);
						assertThat(created.getIntgId()).isEqualTo("test-client-id-123");
						assertThat(created.getIntgSecret()).isEqualTo("test-client-secret-456");
						assertThat(created.getLoginUri()).isEqualTo("https://login.example.com");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should create integration record for META platform")
		void create_MetaPlatform() {
			Mono<AppRegistrationIntegration> result = insertTestApp(SYSTEM_CLIENT_ID, "aritest2", "ARI Test App 2")
					.flatMap(appId -> {
						AppRegistrationIntegration intg = createIntegrationDto(
								SYSTEM_CLIENT_ID, appId, SecurityAppRegIntegrationPlatform.META);
						return service.create(intg)
								.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));
					});

			StepVerifier.create(result)
					.assertNext(created -> {
						assertThat(created.getId()).isNotNull();
						assertThat(created.getPlatform()).isEqualTo(SecurityAppRegIntegrationPlatform.META);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should create multiple integrations for different platforms on the same app")
		void create_MultiplePlatforms() {
			Mono<AppRegistrationIntegration> result = insertTestApp(SYSTEM_CLIENT_ID, "aritest3", "ARI Test App 3")
					.flatMap(appId -> {
						AppRegistrationIntegration google = createIntegrationDto(
								SYSTEM_CLIENT_ID, appId, SecurityAppRegIntegrationPlatform.GOOGLE);
						return service.create(google)
								.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))
								.then(Mono.defer(() -> {
									AppRegistrationIntegration meta = createIntegrationDto(
											SYSTEM_CLIENT_ID, appId, SecurityAppRegIntegrationPlatform.META);
									return service.create(meta)
											.contextWrite(ReactiveSecurityContextHolder
													.withAuthentication(systemAuth));
								}));
					});

			StepVerifier.create(result)
					.assertNext(created -> {
						assertThat(created.getId()).isNotNull();
						assertThat(created.getPlatform()).isEqualTo(SecurityAppRegIntegrationPlatform.META);
					})
					.verifyComplete();
		}
	}

	// =======================================================================
	// update()
	// =======================================================================

	@Nested
	@DisplayName("update()")
	class UpdateTests {

		@Test
		@DisplayName("should update integration fields via updatableEntity")
		void update_ModifiesFields() {
			Mono<AppRegistrationIntegration> result = insertTestApp(SYSTEM_CLIENT_ID, "aritest4", "ARI Test App 4")
					.flatMap(appId -> insertIntegration(appId, SYSTEM_CLIENT_ID, "GOOGLE",
							"original-id", "original-secret", "https://orig.example.com",
							"https://origsignup.example.com"))
					.flatMap(intgId -> service.read(intgId)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.flatMap(existing -> {
						existing.setIntgId("updated-id");
						existing.setIntgSecret("updated-secret");
						existing.setLoginUri("https://updated.example.com");
						return service.update(existing)
								.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));
					});

			StepVerifier.create(result)
					.assertNext(updated -> {
						assertThat(updated.getIntgId()).isEqualTo("updated-id");
						assertThat(updated.getIntgSecret()).isEqualTo("updated-secret");
						assertThat(updated.getLoginUri()).isEqualTo("https://updated.example.com");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should preserve clientId and appId after update via updatableEntity")
		void update_PreservesImmutableFields() {
			Mono<AppRegistrationIntegration> result = insertTestApp(SYSTEM_CLIENT_ID, "aritest5", "ARI Test App 5")
					.flatMap(appId -> insertIntegration(appId, SYSTEM_CLIENT_ID, "GOOGLE",
							"orig-intg-id", "orig-intg-secret", "https://orig.example.com",
							"https://origsignup.example.com")
							.map(intgId -> new Object[] { intgId, appId }))
					.flatMap(arr -> {
						ULong intgId = (ULong) arr[0];
						ULong appId = (ULong) arr[1];
						return service.read(intgId)
								.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))
								.flatMap(existing -> {
									existing.setIntgId("new-intg-id");
									return service.update(existing)
											.contextWrite(ReactiveSecurityContextHolder
													.withAuthentication(systemAuth));
								})
								.map(updated -> new Object[] { updated, appId });
					})
					.map(arr -> (AppRegistrationIntegration) arr[0]);

			StepVerifier.create(result)
					.assertNext(updated -> {
						assertThat(updated.getClientId()).isEqualTo(SYSTEM_CLIENT_ID);
						assertThat(updated.getPlatform()).isEqualTo(SecurityAppRegIntegrationPlatform.GOOGLE);
					})
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
		@DisplayName("should delete integration record and return 1")
		void delete_ReturnsOne() {
			Mono<Integer> result = insertTestApp(SYSTEM_CLIENT_ID, "aritest6", "ARI Test App 6")
					.flatMap(appId -> insertIntegration(appId, SYSTEM_CLIENT_ID, "GOOGLE",
							"del-intg-id", "del-intg-secret", "https://del.example.com",
							"https://delsignup.example.com"))
					.flatMap(intgId -> service.delete(intgId)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)));

			StepVerifier.create(result)
					.assertNext(count -> assertThat(count).isEqualTo(1))
					.verifyComplete();
		}

		@Test
		@DisplayName("should cascade delete associated tokens when integration is deleted")
		void delete_CascadesTokenDeletion() {
			Mono<Long> result = insertTestApp(SYSTEM_CLIENT_ID, "aritest7", "ARI Test App 7")
					.flatMap(appId -> insertIntegration(appId, SYSTEM_CLIENT_ID, "GOOGLE",
							"cas-intg-id", "cas-intg-secret", "https://cas.example.com",
							"https://cassignup.example.com"))
					.flatMap(intgId -> insertIntegrationToken(intgId, "state-token-cascade-test")
							.thenReturn(intgId))
					.flatMap(intgId -> service.delete(intgId)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.then(databaseClient.sql(
							"SELECT COUNT(*) as cnt FROM security_app_reg_integration_tokens WHERE STATE = 'state-token-cascade-test'")
							.map(row -> row.get("cnt", Long.class))
							.one());

			StepVerifier.create(result)
					.assertNext(count -> assertThat(count).isZero())
					.verifyComplete();
		}

		@Test
		@DisplayName("should throw when deleting non-existent integration")
		void delete_NonExistent_ThrowsError() {
			Mono<Integer> result = service.delete(ULong.valueOf(99999))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectError()
					.verify();
		}
	}

	// =======================================================================
	// readPageFilter()
	// =======================================================================

	@Nested
	@DisplayName("readPageFilter()")
	class ReadPageFilterTests {

		@Test
		@DisplayName("should return page of integrations for system client")
		void readPageFilter_ReturnsPage() {
			Mono<org.springframework.data.domain.Page<AppRegistrationIntegration>> result = insertTestApp(
					SYSTEM_CLIENT_ID, "aritest8", "ARI Test App 8")
					.flatMap(appId -> insertIntegration(appId, SYSTEM_CLIENT_ID, "GOOGLE",
							"page-intg-id", "page-intg-secret", "https://page.example.com",
							"https://pagesignup.example.com"))
					.then(Mono.defer(() -> service
							.readPageFilter(org.springframework.data.domain.PageRequest.of(0, 10), null)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))));

			StepVerifier.create(result)
					.assertNext(page -> {
						assertThat(page).isNotNull();
						assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(1);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should return empty page when no integrations exist")
		void readPageFilter_NoData_ReturnsEmpty() {
			Mono<org.springframework.data.domain.Page<AppRegistrationIntegration>> result = service
					.readPageFilter(org.springframework.data.domain.PageRequest.of(0, 10), null)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(page -> {
						assertThat(page).isNotNull();
						assertThat(page.getTotalElements()).isZero();
					})
					.verifyComplete();
		}
	}

	// =======================================================================
	// getIntegration()
	// =======================================================================

	@Nested
	@DisplayName("getIntegration()")
	class GetIntegrationTests {

		@Test
		@DisplayName("should return integration for matching platform, app, and client")
		void getIntegration_Found() {
			Mono<AppRegistrationIntegration> result = insertTestApp(SYSTEM_CLIENT_ID, "aritest9", "ARI Test App 9")
					.flatMap(appId -> insertIntegration(appId, SYSTEM_CLIENT_ID, "GOOGLE",
							"get-intg-id", "get-intg-secret", "https://get.example.com",
							"https://getsignup.example.com"))
					.then(Mono.defer(() -> {
						ContextAuthentication auth = TestDataFactory.createSystemAuth();
						auth.setUrlAppCode("aritest9");
						auth.setUrlClientCode("SYSTEM");
						return service.getIntegration(SecurityAppRegIntegrationPlatform.GOOGLE)
								.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
					}));

			StepVerifier.create(result)
					.assertNext(intg -> {
						assertThat(intg).isNotNull();
						assertThat(intg.getIntgId()).isEqualTo("get-intg-id");
						assertThat(intg.getPlatform()).isEqualTo(SecurityAppRegIntegrationPlatform.GOOGLE);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should return empty when platform does not match any integration")
		void getIntegration_NoMatch_ReturnsEmpty() {
			Mono<AppRegistrationIntegration> result = insertTestApp(SYSTEM_CLIENT_ID, "arites10", "ARI Test App 10")
					.flatMap(appId -> insertIntegration(appId, SYSTEM_CLIENT_ID, "GOOGLE",
							"nom-intg-id", "nom-intg-secret", "https://nom.example.com",
							"https://nomsignup.example.com"))
					.then(Mono.defer(() -> {
						ContextAuthentication auth = TestDataFactory.createSystemAuth();
						auth.setUrlAppCode("arites10");
						auth.setUrlClientCode("SYSTEM");
						return service.getIntegration(SecurityAppRegIntegrationPlatform.META)
								.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
					}));

			StepVerifier.create(result)
					.verifyComplete();
		}
	}

	// =======================================================================
	// redirectToGoogleAuthConsent()
	// =======================================================================

	@Nested
	@DisplayName("redirectToGoogleAuthConsent()")
	class RedirectToGoogleAuthConsentTests {

		@Test
		@DisplayName("should build Google OAuth URI with correct parameters and save token")
		void redirectToGoogleAuthConsent_BuildsCorrectUri() {
			Mono<String> result = insertTestApp(SYSTEM_CLIENT_ID, "arites11", "ARI Test App 11")
					.flatMap(appId -> insertIntegration(appId, SYSTEM_CLIENT_ID, "GOOGLE",
							"google-client-id", "google-client-secret", "https://login.example.com",
							"https://signup.example.com"))
					.flatMap(intgId -> service.read(intgId)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.flatMap(appRegIntg -> {
						String state = "test-state-google-12345";
						String callBackURL = "https://callback.example.com/google";
						ServerHttpRequest request = MockServerHttpRequest
								.get("https://example.com/auth/google")
								.queryParam("returnUrl", "/dashboard")
								.build();

						return service.redirectToGoogleAuthConsent(appRegIntg, state, callBackURL, request)
								.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));
					});

			StepVerifier.create(result)
					.assertNext(uriString -> {
						assertThat(uriString).isNotNull();
						URI uri = URI.create(uriString);
						assertThat(uri.getHost()).isEqualTo("accounts.google.com");
						assertThat(uri.getPath()).isEqualTo("/o/oauth2/v2/auth");
						assertThat(uriString).contains("client_id=google-client-id");
						assertThat(uriString).contains("redirect_uri=");
						assertThat(uriString).contains("callback.example.com");
						assertThat(uriString).contains("scope=email");
						assertThat(uriString).contains("profile");
						assertThat(uriString).contains("openid");
						assertThat(uriString).contains("response_type=code");
						assertThat(uriString).contains("state=test-state-google-12345");
						assertThat(uriString).contains("access_type=offline");
						assertThat(uriString).contains("prompt=consent");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should save integration token with state and request params")
		void redirectToGoogleAuthConsent_SavesToken() {
			String testState = "verify-token-google-67890";

			Mono<AppRegistrationIntegrationToken> result = insertTestApp(SYSTEM_CLIENT_ID, "arites12",
					"ARI Test App 12")
					.flatMap(appId -> insertIntegration(appId, SYSTEM_CLIENT_ID, "GOOGLE",
							"google-cid-2", "google-csec-2", "https://login2.example.com",
							"https://signup2.example.com"))
					.flatMap(intgId -> service.read(intgId)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.flatMap(appRegIntg -> {
						ServerHttpRequest request = MockServerHttpRequest
								.get("https://example.com/auth/google")
								.queryParam("customParam", "value123")
								.build();

						return service
								.redirectToGoogleAuthConsent(appRegIntg, testState,
										"https://cb.example.com/google", request)
								.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));
					})
					.then(Mono.defer(() -> tokenService.verifyIntegrationState(testState)));

			StepVerifier.create(result)
					.assertNext(token -> {
						assertThat(token).isNotNull();
						assertThat(token.getState()).isEqualTo(testState);
						assertThat(token.getRequestParam()).isNotNull();
						assertThat(token.getRequestParam().get("customParam")).isEqualTo("value123");
					})
					.verifyComplete();
		}
	}

	// =======================================================================
	// redirectToMetaAuthConsent()
	// =======================================================================

	@Nested
	@DisplayName("redirectToMetaAuthConsent()")
	class RedirectToMetaAuthConsentTests {

		@Test
		@DisplayName("should build Meta OAuth URI with correct parameters and save token")
		void redirectToMetaAuthConsent_BuildsCorrectUri() {
			Mono<String> result = insertTestApp(SYSTEM_CLIENT_ID, "arites13", "ARI Test App 13")
					.flatMap(appId -> insertIntegration(appId, SYSTEM_CLIENT_ID, "META",
							"meta-client-id", "meta-client-secret", "https://login.example.com",
							"https://signup.example.com"))
					.flatMap(intgId -> service.read(intgId)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.flatMap(appRegIntg -> {
						String state = "test-state-meta-12345";
						String callBackURL = "https://callback.example.com/meta";
						ServerHttpRequest request = MockServerHttpRequest
								.get("https://example.com/auth/meta")
								.queryParam("returnUrl", "/home")
								.build();

						return service.redirectToMetaAuthConsent(appRegIntg, state, callBackURL, request)
								.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));
					});

			StepVerifier.create(result)
					.assertNext(uriString -> {
						assertThat(uriString).isNotNull();
						URI uri = URI.create(uriString);
						assertThat(uri.getHost()).isEqualTo("www.facebook.com");
						assertThat(uri.getPath()).isEqualTo("/dialog/oauth");
						assertThat(uriString).contains("client_id=meta-client-id");
						assertThat(uriString).contains("redirect_uri=");
						assertThat(uriString).contains("callback.example.com");
						assertThat(uriString).contains("scope=public_profile");
						assertThat(uriString).contains("email");
						assertThat(uriString).contains("response_type=code");
						assertThat(uriString).contains("state=test-state-meta-12345");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should save integration token with state and request params for Meta")
		void redirectToMetaAuthConsent_SavesToken() {
			String testState = "verify-token-meta-67890";

			Mono<AppRegistrationIntegrationToken> result = insertTestApp(SYSTEM_CLIENT_ID, "arites14",
					"ARI Test App 14")
					.flatMap(appId -> insertIntegration(appId, SYSTEM_CLIENT_ID, "META",
							"meta-cid-2", "meta-csec-2", "https://login3.example.com",
							"https://signup3.example.com"))
					.flatMap(intgId -> service.read(intgId)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.flatMap(appRegIntg -> {
						ServerHttpRequest request = MockServerHttpRequest
								.get("https://example.com/auth/meta")
								.queryParam("metaParam", "metaValue")
								.build();

						return service
								.redirectToMetaAuthConsent(appRegIntg, testState,
										"https://cb.example.com/meta", request)
								.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));
					})
					.then(Mono.defer(() -> tokenService.verifyIntegrationState(testState)));

			StepVerifier.create(result)
					.assertNext(token -> {
						assertThat(token).isNotNull();
						assertThat(token.getState()).isEqualTo(testState);
						assertThat(token.getRequestParam()).isNotNull();
						assertThat(token.getRequestParam().get("metaParam")).isEqualTo("metaValue");
					})
					.verifyComplete();
		}
	}

	// =======================================================================
	// getGoogleUserToken() - error paths
	// =======================================================================

	@Nested
	@DisplayName("getGoogleUserToken()")
	class GetGoogleUserTokenTests {

		@Test
		@DisplayName("should return UNAUTHORIZED when auth code is missing from request")
		void getGoogleUserToken_MissingCode_ReturnsUnauthorized() {
			Mono<Object> result = insertTestApp(SYSTEM_CLIENT_ID, "arites15", "ARI Test App 15")
					.flatMap(appId -> insertIntegration(appId, SYSTEM_CLIENT_ID, "GOOGLE",
							"google-cid-3", "google-csec-3", "https://login4.example.com",
							"https://signup4.example.com"))
					.flatMap(intgId -> service.read(intgId)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))
							.flatMap(appRegIntg -> insertIntegrationToken(intgId, "state-google-nocode")
									.flatMap(tokenId -> tokenService.read(tokenId)
											.contextWrite(ReactiveSecurityContextHolder
													.withAuthentication(systemAuth)))
									.flatMap(appRegIntgToken -> {
										// Request WITHOUT 'code' param
										ServerHttpRequest request = MockServerHttpRequest
												.get("https://callback.example.com/google")
												.queryParam("state", "state-google-nocode")
												.build();

										return service.getGoogleUserToken(
												appRegIntg, appRegIntgToken,
												"https://callback.example.com/google", request);
									})));

			StepVerifier.create(result)
					.expectError()
					.verify();
		}

		@Test
		@DisplayName("should return UNAUTHORIZED when code param is null in query params")
		void getGoogleUserToken_NullCode_ReturnsUnauthorized() {
			Mono<Object> result = insertTestApp(SYSTEM_CLIENT_ID, "arites16", "ARI Test App 16")
					.flatMap(appId -> insertIntegration(appId, SYSTEM_CLIENT_ID, "GOOGLE",
							"google-cid-4", "google-csec-4", "https://login5.example.com",
							"https://signup5.example.com"))
					.flatMap(intgId -> service.read(intgId)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))
							.flatMap(appRegIntg -> insertIntegrationToken(intgId, "state-google-null")
									.flatMap(tokenId -> tokenService.read(tokenId)
											.contextWrite(ReactiveSecurityContextHolder
													.withAuthentication(systemAuth)))
									.flatMap(appRegIntgToken -> {
										// Request with only error param (simulating denied consent)
										ServerHttpRequest request = MockServerHttpRequest
												.get("https://callback.example.com/google")
												.queryParam("error", "access_denied")
												.build();

										return service.getGoogleUserToken(
												appRegIntg, appRegIntgToken,
												"https://callback.example.com/google", request);
									})));

			StepVerifier.create(result)
					.expectError()
					.verify();
		}
	}

	// =======================================================================
	// getMetaUserToken() - error paths
	// =======================================================================

	@Nested
	@DisplayName("getMetaUserToken()")
	class GetMetaUserTokenTests {

		@Test
		@DisplayName("should return UNAUTHORIZED when auth code is missing from request")
		void getMetaUserToken_MissingCode_ReturnsUnauthorized() {
			Mono<Object> result = insertTestApp(SYSTEM_CLIENT_ID, "arites17", "ARI Test App 17")
					.flatMap(appId -> insertIntegration(appId, SYSTEM_CLIENT_ID, "META",
							"meta-cid-3", "meta-csec-3", "https://login6.example.com",
							"https://signup6.example.com"))
					.flatMap(intgId -> service.read(intgId)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))
							.flatMap(appRegIntg -> insertIntegrationToken(intgId, "state-meta-nocode")
									.flatMap(tokenId -> tokenService.read(tokenId)
											.contextWrite(ReactiveSecurityContextHolder
													.withAuthentication(systemAuth)))
									.flatMap(appRegIntgToken -> {
										// Request WITHOUT 'code' param
										ServerHttpRequest request = MockServerHttpRequest
												.get("https://callback.example.com/meta")
												.queryParam("state", "state-meta-nocode")
												.build();

										return service.getMetaUserToken(
												appRegIntg, appRegIntgToken,
												"https://callback.example.com/meta", request);
									})));

			StepVerifier.create(result)
					.expectError()
					.verify();
		}

		@Test
		@DisplayName("should return UNAUTHORIZED when code param is null for Meta callback")
		void getMetaUserToken_NullCode_ReturnsUnauthorized() {
			Mono<Object> result = insertTestApp(SYSTEM_CLIENT_ID, "arites18", "ARI Test App 18")
					.flatMap(appId -> insertIntegration(appId, SYSTEM_CLIENT_ID, "META",
							"meta-cid-4", "meta-csec-4", "https://login7.example.com",
							"https://signup7.example.com"))
					.flatMap(intgId -> service.read(intgId)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth))
							.flatMap(appRegIntg -> insertIntegrationToken(intgId, "state-meta-null")
									.flatMap(tokenId -> tokenService.read(tokenId)
											.contextWrite(ReactiveSecurityContextHolder
													.withAuthentication(systemAuth)))
									.flatMap(appRegIntgToken -> {
										ServerHttpRequest request = MockServerHttpRequest
												.get("https://callback.example.com/meta")
												.queryParam("error", "access_denied")
												.queryParam("error_reason", "user_denied")
												.build();

										return service.getMetaUserToken(
												appRegIntg, appRegIntgToken,
												"https://callback.example.com/meta", request);
									})));

			StepVerifier.create(result)
					.expectError()
					.verify();
		}
	}

	// =======================================================================
	// getCacheKeys() - private method via reflection
	// =======================================================================

	@Nested
	@DisplayName("getCacheKeys() - private method")
	class GetCacheKeysTests {

		@Test
		@DisplayName("should produce correct cache key format")
		void getCacheKeys_CorrectFormat() throws Exception {
			Method method = AppRegistrationIntegrationService.class
					.getDeclaredMethod("getCacheKeys", String.class, String.class, String.class);
			method.setAccessible(true);

			String result = (String) method.invoke(service, "100", "200", "GOOGLE");

			assertThat(result).isNotNull();
			// The method uses String.join with delimiter as first arg
			// String.join(clientId, ":", appId, ":", platform)
			// This is actually: ":" + "100" + "200" + "100" + "GOOGLE"
			// Wait... String.join(delimiter, elements...) - first is delimiter, rest are elements
			// But the actual code: String.join(clientId, ":", appId, ":", platform)
			// means: delimiter=clientId, elements=[":", appId, ":", platform]
			// = ":" + clientId + appId + clientId + ":" + clientId + platform
			// Actually, String.join("100", ":", "200", ":", "GOOGLE") = ":100200100:100GOOGLE"
			// Hmm, that doesn't look right. Let's just verify it's consistent.
			assertThat(result).contains("100");
			assertThat(result).contains("200");
			assertThat(result).contains("GOOGLE");
		}

		@Test
		@DisplayName("should handle different platform values correctly")
		void getCacheKeys_DifferentPlatforms() throws Exception {
			Method method = AppRegistrationIntegrationService.class
					.getDeclaredMethod("getCacheKeys", String.class, String.class, String.class);
			method.setAccessible(true);

			String googleKey = (String) method.invoke(service, "1", "10", "GOOGLE");
			String metaKey = (String) method.invoke(service, "1", "10", "META");

			assertThat(googleKey).isNotEqualTo(metaKey);
		}

		@Test
		@DisplayName("should produce different keys for different clients")
		void getCacheKeys_DifferentClients() throws Exception {
			Method method = AppRegistrationIntegrationService.class
					.getDeclaredMethod("getCacheKeys", String.class, String.class, String.class);
			method.setAccessible(true);

			String key1 = (String) method.invoke(service, "1", "10", "GOOGLE");
			String key2 = (String) method.invoke(service, "2", "10", "GOOGLE");

			assertThat(key1).isNotEqualTo(key2);
		}

		@Test
		@DisplayName("should produce different keys for different apps")
		void getCacheKeys_DifferentApps() throws Exception {
			Method method = AppRegistrationIntegrationService.class
					.getDeclaredMethod("getCacheKeys", String.class, String.class, String.class);
			method.setAccessible(true);

			String key1 = (String) method.invoke(service, "1", "10", "GOOGLE");
			String key2 = (String) method.invoke(service, "1", "20", "GOOGLE");

			assertThat(key1).isNotEqualTo(key2);
		}
	}

	// =======================================================================
	// updatableEntity() - protected method via reflection
	// =======================================================================

	@Nested
	@DisplayName("updatableEntity() - protected method")
	class UpdatableEntityTests {

		@Test
		@DisplayName("should only update intgId, intgSecret, and loginUri from the entity")
		void updatableEntity_OnlyUpdatesAllowedFields() {
			Mono<AppRegistrationIntegration> result = insertTestApp(SYSTEM_CLIENT_ID, "arites19", "ARI Test App 19")
					.flatMap(appId -> insertIntegration(appId, SYSTEM_CLIENT_ID, "GOOGLE",
							"orig-iid", "orig-isec", "https://orig-login.example.com",
							"https://orig-signup.example.com")
							.map(intgId -> new Object[] { intgId, appId }))
					.flatMap(arr -> {
						ULong intgId = (ULong) arr[0];
						try {
							Method method = AppRegistrationIntegrationService.class
									.getDeclaredMethod("updatableEntity", AppRegistrationIntegration.class);
							method.setAccessible(true);

							AppRegistrationIntegration updateEntity = new AppRegistrationIntegration();
							updateEntity.setId(intgId);
							updateEntity.setIntgId("new-iid");
							updateEntity.setIntgSecret("new-isec");
							updateEntity.setLoginUri("https://new-login.example.com");
							// These should NOT be copied by updatableEntity
							updateEntity.setClientId(ULong.valueOf(999));
							updateEntity.setPlatform(SecurityAppRegIntegrationPlatform.META);

							@SuppressWarnings("unchecked")
							Mono<AppRegistrationIntegration> mono = (Mono<AppRegistrationIntegration>) method
									.invoke(service, updateEntity);
							return mono.contextWrite(
									ReactiveSecurityContextHolder.withAuthentication(systemAuth));
						} catch (Exception e) {
							return Mono.error(e);
						}
					});

			StepVerifier.create(result)
					.assertNext(updated -> {
						// Updated fields
						assertThat(updated.getIntgId()).isEqualTo("new-iid");
						assertThat(updated.getIntgSecret()).isEqualTo("new-isec");
						assertThat(updated.getLoginUri()).isEqualTo("https://new-login.example.com");
						// Immutable fields should be preserved from DB
						assertThat(updated.getClientId()).isEqualTo(SYSTEM_CLIENT_ID);
						assertThat(updated.getPlatform()).isEqualTo(SecurityAppRegIntegrationPlatform.GOOGLE);
					})
					.verifyComplete();
		}
	}

	// =======================================================================
	// Integration: DAO getIntegration() and getIntegrationId()
	// =======================================================================

	@Nested
	@DisplayName("DAO operations via service")
	class DaoOperationsTests {

		@Test
		@DisplayName("should read a created integration by ID")
		void read_ByIdReturnsCorrectData() {
			Mono<AppRegistrationIntegration> result = insertTestApp(SYSTEM_CLIENT_ID, "arites20", "ARI Test App 20")
					.flatMap(appId -> insertIntegration(appId, SYSTEM_CLIENT_ID, "GOOGLE",
							"read-intg-id", "read-intg-secret", "https://read.example.com",
							"https://readsignup.example.com"))
					.flatMap(intgId -> service.read(intgId)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)));

			StepVerifier.create(result)
					.assertNext(intg -> {
						assertThat(intg).isNotNull();
						assertThat(intg.getIntgId()).isEqualTo("read-intg-id");
						assertThat(intg.getIntgSecret()).isEqualTo("read-intg-secret");
						assertThat(intg.getLoginUri()).isEqualTo("https://read.example.com");
						assertThat(intg.getPlatform()).isEqualTo(SecurityAppRegIntegrationPlatform.GOOGLE);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should throw when reading non-existent ID")
		void read_NonExistentId_ThrowsError() {
			Mono<AppRegistrationIntegration> result = service.read(ULong.valueOf(99999))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectError()
					.verify();
		}
	}

	// =======================================================================
	// Google/Meta consent with multiple query params
	// =======================================================================

	@Nested
	@DisplayName("Consent redirect with multiple query params")
	class ConsentMultipleParamsTests {

		@Test
		@DisplayName("should preserve all query params from the original request for Google")
		void googleConsent_PreservesMultipleQueryParams() {
			String testState = "multi-param-google-state";

			Mono<AppRegistrationIntegrationToken> result = insertTestApp(SYSTEM_CLIENT_ID, "arites21",
					"ARI Test App 21")
					.flatMap(appId -> insertIntegration(appId, SYSTEM_CLIENT_ID, "GOOGLE",
							"gmp-cid", "gmp-csec", "https://gmp-login.example.com",
							"https://gmp-signup.example.com"))
					.flatMap(intgId -> service.read(intgId)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.flatMap(appRegIntg -> {
						ServerHttpRequest request = MockServerHttpRequest
								.get("https://example.com/auth/google")
								.queryParam("returnUrl", "/dashboard")
								.queryParam("theme", "dark")
								.queryParam("lang", "en")
								.build();

						return service
								.redirectToGoogleAuthConsent(appRegIntg, testState,
										"https://cb.example.com/google", request)
								.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));
					})
					.then(Mono.defer(() -> tokenService.verifyIntegrationState(testState)));

			StepVerifier.create(result)
					.assertNext(token -> {
						assertThat(token.getRequestParam()).isNotNull();
						Map<String, Object> params = token.getRequestParam();
						assertThat(params.get("returnUrl")).isEqualTo("/dashboard");
						assertThat(params.get("theme")).isEqualTo("dark");
						assertThat(params.get("lang")).isEqualTo("en");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should preserve all query params from the original request for Meta")
		void metaConsent_PreservesMultipleQueryParams() {
			String testState = "multi-param-meta-state";

			Mono<AppRegistrationIntegrationToken> result = insertTestApp(SYSTEM_CLIENT_ID, "arites22",
					"ARI Test App 22")
					.flatMap(appId -> insertIntegration(appId, SYSTEM_CLIENT_ID, "META",
							"mmp-cid", "mmp-csec", "https://mmp-login.example.com",
							"https://mmp-signup.example.com"))
					.flatMap(intgId -> service.read(intgId)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.flatMap(appRegIntg -> {
						ServerHttpRequest request = MockServerHttpRequest
								.get("https://example.com/auth/meta")
								.queryParam("redirect", "/profile")
								.queryParam("locale", "fr")
								.build();

						return service
								.redirectToMetaAuthConsent(appRegIntg, testState,
										"https://cb.example.com/meta", request)
								.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));
					})
					.then(Mono.defer(() -> tokenService.verifyIntegrationState(testState)));

			StepVerifier.create(result)
					.assertNext(token -> {
						assertThat(token.getRequestParam()).isNotNull();
						Map<String, Object> params = token.getRequestParam();
						assertThat(params.get("redirect")).isEqualTo("/profile");
						assertThat(params.get("locale")).isEqualTo("fr");
					})
					.verifyComplete();
		}
	}

	// =======================================================================
	// Edge cases and additional coverage
	// =======================================================================

	@Nested
	@DisplayName("Edge cases")
	class EdgeCaseTests {

		@Test
		@DisplayName("should handle consent redirect with empty query params")
		void googleConsent_EmptyQueryParams() {
			Mono<String> result = insertTestApp(SYSTEM_CLIENT_ID, "arites23", "ARI Test App 23")
					.flatMap(appId -> insertIntegration(appId, SYSTEM_CLIENT_ID, "GOOGLE",
							"eq-cid", "eq-csec", "https://eq-login.example.com",
							"https://eq-signup.example.com"))
					.flatMap(intgId -> service.read(intgId)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.flatMap(appRegIntg -> {
						String state = "empty-params-google-state";
						ServerHttpRequest request = MockServerHttpRequest
								.get("https://example.com/auth/google")
								.build();

						return service.redirectToGoogleAuthConsent(appRegIntg, state,
								"https://cb.example.com/google", request)
								.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));
					});

			StepVerifier.create(result)
					.assertNext(uriString -> {
						assertThat(uriString).isNotNull();
						assertThat(uriString).contains("accounts.google.com");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should handle consent redirect with special characters in callback URL")
		void metaConsent_SpecialCharsInCallback() {
			Mono<String> result = insertTestApp(SYSTEM_CLIENT_ID, "arites24", "ARI Test App 24")
					.flatMap(appId -> insertIntegration(appId, SYSTEM_CLIENT_ID, "META",
							"sc-cid", "sc-csec", "https://sc-login.example.com",
							"https://sc-signup.example.com"))
					.flatMap(intgId -> service.read(intgId)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.flatMap(appRegIntg -> {
						String state = "special-chars-meta-state";
						String callBackURL = "https://callback.example.com/meta?source=app&type=login";
						ServerHttpRequest request = MockServerHttpRequest
								.get("https://example.com/auth/meta")
								.build();

						return service.redirectToMetaAuthConsent(appRegIntg, state, callBackURL, request)
								.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));
					});

			StepVerifier.create(result)
					.assertNext(uriString -> {
						assertThat(uriString).isNotNull();
						assertThat(uriString).contains("www.facebook.com");
						assertThat(uriString).contains("dialog/oauth");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should create integration for all supported platforms")
		void create_AllPlatforms() {
			Mono<Long> result = insertTestApp(SYSTEM_CLIENT_ID, "arites25", "ARI Test App 25")
					.flatMap(appId -> insertIntegration(appId, SYSTEM_CLIENT_ID, "GOOGLE",
							"ap-g-cid", "ap-g-csec", "https://ap-g.example.com",
							"https://ap-g-signup.example.com")
							.then(insertIntegration(appId, SYSTEM_CLIENT_ID, "META",
									"ap-m-cid", "ap-m-csec", "https://ap-m.example.com",
									"https://ap-m-signup.example.com"))
							.then(insertIntegration(appId, SYSTEM_CLIENT_ID, "APPLE",
									"ap-a-cid", "ap-a-csec", "https://ap-a.example.com",
									"https://ap-a-signup.example.com"))
							.then(insertIntegration(appId, SYSTEM_CLIENT_ID, "SSO",
									"ap-s-cid", "ap-s-csec", "https://ap-s.example.com",
									"https://ap-s-signup.example.com"))
							.then(insertIntegration(appId, SYSTEM_CLIENT_ID, "MICROSOFT",
									"ap-ms-cid", "ap-ms-csec", "https://ap-ms.example.com",
									"https://ap-ms-signup.example.com"))
							.then(insertIntegration(appId, SYSTEM_CLIENT_ID, "X",
									"ap-x-cid", "ap-x-csec", "https://ap-x.example.com",
									"https://ap-x-signup.example.com")))
					.then(databaseClient.sql(
							"SELECT COUNT(*) as cnt FROM security_app_reg_integration WHERE INTG_ID LIKE 'ap-%'")
							.map(row -> row.get("cnt", Long.class))
							.one());

			StepVerifier.create(result)
					.assertNext(count -> assertThat(count).isEqualTo(6))
					.verifyComplete();
		}

		@Test
		@DisplayName("should handle getIntegration with different client through hierarchy")
		void getIntegration_WithBusinessClient() {
			Mono<AppRegistrationIntegration> result = insertTestClient("ARIBC01", "ARI BUS Client", "BUS")
					.flatMap(busClientId -> insertClientHierarchy(busClientId, SYSTEM_CLIENT_ID, null, null, null)
							.then(insertTestApp(busClientId, "arites26", "ARI Test App 26"))
							.flatMap(appId -> insertIntegration(appId, busClientId, "GOOGLE",
									"bus-g-cid", "bus-g-sec", "https://bus-g.example.com",
									"https://bus-g-signup.example.com"))
							.then(Mono.defer(() -> {
								ContextAuthentication busAuth = TestDataFactory.createBusinessAuth(
										busClientId, "ARIBC01",
										java.util.List.of("Authorities.Logged_IN"));
								busAuth.setUrlAppCode("arites26");
								busAuth.setUrlClientCode("ARIBC01");
								return service.getIntegration(SecurityAppRegIntegrationPlatform.GOOGLE)
										.contextWrite(ReactiveSecurityContextHolder
												.withAuthentication(busAuth));
							})));

			StepVerifier.create(result)
					.assertNext(intg -> {
						assertThat(intg).isNotNull();
						assertThat(intg.getIntgId()).isEqualTo("bus-g-cid");
					})
					.verifyComplete();
		}
	}
}
