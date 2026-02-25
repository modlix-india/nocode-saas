package com.fincity.security.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.security.enums.ClientLevelType;
import com.fincity.security.jooq.enums.SecurityAppAppUsageType;
import com.fincity.security.jooq.enums.SecurityUserStatusCode;
import com.fincity.security.model.AuthenticationPasswordType;
import com.fincity.security.model.ClientRegistrationRequest;
import com.fincity.security.service.appregistration.ClientRegistrationService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ClientRegistrationServiceIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private ClientRegistrationService clientRegistrationService;

	private ContextAuthentication systemAuth;

	@BeforeEach
	void setUp() {
		setupMockBeans();
		systemAuth = TestDataFactory.createSystemAuth();
		systemAuth.setUrlAppCode("appbuilder");
		systemAuth.setUrlClientCode("SYSTEM");
	}

	@AfterEach
	void tearDown() {
		databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
				.then(databaseClient.sql("DELETE FROM security_v2_user_role WHERE USER_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_profile_user WHERE USER_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_profile_role WHERE PROFILE_ID IN (SELECT ID FROM security_profile WHERE CLIENT_ID > 1)").then())
				.then(databaseClient.sql("DELETE FROM security_profile WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_user_token WHERE USER_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client_manager WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql(
						"DELETE FROM security_app_access WHERE APP_ID IN (SELECT ID FROM security_app WHERE CLIENT_ID > 1 OR APP_CODE LIKE 'crtest%')")
						.then())
				.then(databaseClient
						.sql("DELETE FROM security_app_property WHERE APP_ID IN (SELECT ID FROM security_app WHERE APP_CODE LIKE 'crtest%')")
						.then())
				.then(databaseClient.sql("DELETE FROM security_app WHERE APP_CODE LIKE 'crtest%'").then())
				.then(databaseClient.sql("DELETE FROM security_user WHERE ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}

	// -----------------------------------------------------------------------
	// Helper: insert test app with APP_USAGE_TYPE
	// -----------------------------------------------------------------------
	private Mono<ULong> insertTestAppWithUsageType(ULong clientId, String appCode, String appName,
			String usageType) {
		return databaseClient.sql(
				"INSERT INTO security_app (CLIENT_ID, APP_NAME, APP_CODE, APP_TYPE, APP_USAGE_TYPE) VALUES (:clientId, :appName, :appCode, 'APP', :usageType)")
				.bind("clientId", clientId.longValue())
				.bind("appName", appName)
				.bind("appCode", appCode)
				.bind("usageType", usageType)
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	// -----------------------------------------------------------------------
	// Helper: insert app property for registration type
	// -----------------------------------------------------------------------
	private Mono<Void> insertAppProperty(ULong appId, ULong clientId, String propName, String propValue) {
		return databaseClient.sql(
				"INSERT INTO security_app_property (APP_ID, CLIENT_ID, NAME, VALUE) VALUES (:appId, :clientId, :propName, :propValue)")
				.bind("appId", appId.longValue())
				.bind("clientId", clientId.longValue())
				.bind("propName", propName)
				.bind("propValue", propValue)
				.then();
	}

	// -----------------------------------------------------------------------
	// Helper: insert app access
	// -----------------------------------------------------------------------
	private Mono<Void> insertAppAccess(ULong clientId, ULong appId, boolean editAccess) {
		return databaseClient.sql(
				"INSERT INTO security_app_access (CLIENT_ID, APP_ID, EDIT_ACCESS) VALUES (:clientId, :appId, :editAccess)")
				.bind("clientId", clientId.longValue())
				.bind("appId", appId.longValue())
				.bind("editAccess", editAccess ? 1 : 0)
				.then();
	}

	// -----------------------------------------------------------------------
	// Helper: create an unauthenticated context
	// -----------------------------------------------------------------------
	private ContextAuthentication createUnauthenticatedContext() {
		ContextUser user = new ContextUser();
		user.setId(BigInteger.ZERO);
		user.setClientId(BigInteger.valueOf(1));
		user.setUserName("anonymous");
		user.setEmailId("anon@test.com");
		user.setFirstName("Anonymous");
		user.setLastName("User");
		user.setStatusCode(SecurityUserStatusCode.ACTIVE.getLiteral());
		user.setStringAuthorities(List.of());

		ContextAuthentication ca = new ContextAuthentication();
		ca.setUser(user);
		ca.setAuthenticated(false);
		ca.setClientCode("SYSTEM");
		ca.setClientTypeCode("SYS");
		ca.setLoggedInFromClientId(BigInteger.valueOf(1));
		ca.setLoggedInFromClientCode("SYSTEM");
		ca.setUrlAppCode("appbuilder");
		ca.setUrlClientCode("SYSTEM");
		return ca;
	}

	// -----------------------------------------------------------------------
	// checkUsageType tests (private method tested via reflection)
	// -----------------------------------------------------------------------
	@Nested
	@DisplayName("checkUsageType()")
	class CheckUsageTypeTests {

		private Method checkUsageTypeMethod;

		@BeforeEach
		void setUpReflection() throws Exception {
			checkUsageTypeMethod = ClientRegistrationService.class.getDeclaredMethod(
					"checkUsageType", SecurityAppAppUsageType.class, ClientLevelType.class, boolean.class);
			checkUsageTypeMethod.setAccessible(true);
		}

		@SuppressWarnings("unchecked")
		private Mono<Boolean> invokeCheckUsageType(SecurityAppAppUsageType usageType,
				ClientLevelType levelType, boolean isBusinessClient) throws Exception {
			return (Mono<Boolean>) checkUsageTypeMethod.invoke(
					clientRegistrationService, usageType, levelType, isBusinessClient);
		}

		@Test
		@DisplayName("Standalone app (S) - should reject registration")
		void standaloneApp_RejectsRegistration() throws Exception {
			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.S, ClientLevelType.OWNER, false))
					.expectErrorMatches(e -> e instanceof GenericException
							&& e.getMessage().contains("Standalone"))
					.verify();
		}

		@Test
		@DisplayName("Business only app (B) - should reject registration")
		void businessOnlyApp_RejectsRegistration() throws Exception {
			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B, ClientLevelType.OWNER, true))
					.expectErrorMatches(e -> e instanceof GenericException
							&& e.getMessage().contains("Business"))
					.verify();
		}

		// --- B2C ---

		@Test
		@DisplayName("B2C owner individual - should succeed")
		void b2cOwnerIndividual_Succeeds() throws Exception {
			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B2C, ClientLevelType.OWNER, false))
					.assertNext(result -> assertThat(result).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("B2C non-owner - should reject")
		void b2cNonOwner_Rejects() throws Exception {
			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B2C, ClientLevelType.CLIENT, false))
					.expectErrorMatches(e -> e instanceof GenericException
							&& e.getMessage().contains("owner"))
					.verify();
		}

		@Test
		@DisplayName("B2C business client - should reject")
		void b2cBusinessClient_Rejects() throws Exception {
			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B2C, ClientLevelType.OWNER, true))
					.expectErrorMatches(e -> e instanceof GenericException
							&& e.getMessage().contains("Business clients"))
					.verify();
		}

		// --- B2B ---

		@Test
		@DisplayName("B2B owner business - should succeed")
		void b2bOwnerBusiness_Succeeds() throws Exception {
			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B2B, ClientLevelType.OWNER, true))
					.assertNext(result -> assertThat(result).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("B2B non-owner - should reject")
		void b2bNonOwner_Rejects() throws Exception {
			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B2B, ClientLevelType.CLIENT, true))
					.expectErrorMatches(e -> e instanceof GenericException
							&& e.getMessage().contains("owner"))
					.verify();
		}

		@Test
		@DisplayName("B2B individual client - should reject")
		void b2bIndividualClient_Rejects() throws Exception {
			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B2B, ClientLevelType.OWNER, false))
					.expectErrorMatches(e -> e instanceof GenericException
							&& e.getMessage().contains("Individual"))
					.verify();
		}

		// --- B2X ---

		@Test
		@DisplayName("B2X owner - should succeed for both business and individual")
		void b2xOwner_Succeeds() throws Exception {
			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B2X, ClientLevelType.OWNER, false))
					.assertNext(result -> assertThat(result).isTrue())
					.verifyComplete();

			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B2X, ClientLevelType.OWNER, true))
					.assertNext(result -> assertThat(result).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("B2X non-owner - should reject")
		void b2xNonOwner_Rejects() throws Exception {
			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B2X, ClientLevelType.CLIENT, false))
					.expectErrorMatches(e -> e instanceof GenericException
							&& e.getMessage().contains("owner"))
					.verify();
		}

		// --- B2B2B ---

		@Test
		@DisplayName("B2B2B owner business - should succeed")
		void b2b2bOwnerBusiness_Succeeds() throws Exception {
			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B2B2B, ClientLevelType.OWNER, true))
					.assertNext(result -> assertThat(result).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("B2B2B client business - should succeed")
		void b2b2bClientBusiness_Succeeds() throws Exception {
			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B2B2B, ClientLevelType.CLIENT, true))
					.assertNext(result -> assertThat(result).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("B2B2B customer - should reject")
		void b2b2bCustomer_Rejects() throws Exception {
			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B2B2B, ClientLevelType.CUSTOMER, true))
					.expectErrorMatches(e -> e instanceof GenericException
							&& e.getMessage().contains("owner"))
					.verify();
		}

		@Test
		@DisplayName("B2B2B individual client - should reject")
		void b2b2bIndividualClient_Rejects() throws Exception {
			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B2B2B, ClientLevelType.OWNER, false))
					.expectErrorMatches(e -> e instanceof GenericException
							&& e.getMessage().contains("Individual"))
					.verify();
		}

		// --- B2B2C ---

		@Test
		@DisplayName("B2B2C owner business - should succeed")
		void b2b2cOwnerBusiness_Succeeds() throws Exception {
			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B2B2C, ClientLevelType.OWNER, true))
					.assertNext(result -> assertThat(result).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("B2B2C client individual - should succeed")
		void b2b2cClientIndividual_Succeeds() throws Exception {
			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B2B2C, ClientLevelType.CLIENT, false))
					.assertNext(result -> assertThat(result).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("B2B2C owner individual - should reject")
		void b2b2cOwnerIndividual_Rejects() throws Exception {
			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B2B2C, ClientLevelType.OWNER, false))
					.expectErrorMatches(e -> e instanceof GenericException
							&& e.getMessage().contains("Business clients are required"))
					.verify();
		}

		@Test
		@DisplayName("B2B2C client business - should reject")
		void b2b2cClientBusiness_Rejects() throws Exception {
			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B2B2C, ClientLevelType.CLIENT, true))
					.expectErrorMatches(e -> e instanceof GenericException
							&& e.getMessage().contains("Business clients are not allowed"))
					.verify();
		}

		@Test
		@DisplayName("B2B2C customer - should reject")
		void b2b2cCustomer_Rejects() throws Exception {
			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B2B2C, ClientLevelType.CUSTOMER, false))
					.expectErrorMatches(e -> e instanceof GenericException
							&& e.getMessage().contains("owner"))
					.verify();
		}

		// --- B2B2X ---

		@Test
		@DisplayName("B2B2X owner business - should succeed")
		void b2b2xOwnerBusiness_Succeeds() throws Exception {
			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B2B2X, ClientLevelType.OWNER, true))
					.assertNext(result -> assertThat(result).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("B2B2X client any - should succeed")
		void b2b2xClientAny_Succeeds() throws Exception {
			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B2B2X, ClientLevelType.CLIENT, false))
					.assertNext(result -> assertThat(result).isTrue())
					.verifyComplete();

			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B2B2X, ClientLevelType.CLIENT, true))
					.assertNext(result -> assertThat(result).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("B2B2X owner individual - should reject")
		void b2b2xOwnerIndividual_Rejects() throws Exception {
			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B2B2X, ClientLevelType.OWNER, false))
					.expectErrorMatches(e -> e instanceof GenericException
							&& e.getMessage().contains("Business clients are required"))
					.verify();
		}

		@Test
		@DisplayName("B2B2X customer - should reject")
		void b2b2xCustomer_Rejects() throws Exception {
			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B2B2X, ClientLevelType.CUSTOMER, true))
					.expectErrorMatches(e -> e instanceof GenericException
							&& e.getMessage().contains("owner"))
					.verify();
		}

		// --- B2X2C ---

		@Test
		@DisplayName("B2X2C owner any - should succeed")
		void b2x2cOwnerAny_Succeeds() throws Exception {
			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B2X2C, ClientLevelType.OWNER, true))
					.assertNext(result -> assertThat(result).isTrue())
					.verifyComplete();

			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B2X2C, ClientLevelType.OWNER, false))
					.assertNext(result -> assertThat(result).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("B2X2C client individual - should succeed")
		void b2x2cClientIndividual_Succeeds() throws Exception {
			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B2X2C, ClientLevelType.CLIENT, false))
					.assertNext(result -> assertThat(result).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("B2X2C client business - should reject")
		void b2x2cClientBusiness_Rejects() throws Exception {
			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B2X2C, ClientLevelType.CLIENT, true))
					.expectErrorMatches(e -> e instanceof GenericException
							&& e.getMessage().contains("Business clients are not allowed"))
					.verify();
		}

		@Test
		@DisplayName("B2X2C customer - should reject")
		void b2x2cCustomer_Rejects() throws Exception {
			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B2X2C, ClientLevelType.CUSTOMER, false))
					.expectErrorMatches(e -> e instanceof GenericException
							&& e.getMessage().contains("owner"))
					.verify();
		}

		// --- B2X2X ---

		@Test
		@DisplayName("B2X2X owner any - should succeed")
		void b2x2xOwnerAny_Succeeds() throws Exception {
			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B2X2X, ClientLevelType.OWNER, true))
					.assertNext(result -> assertThat(result).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("B2X2X client any - should succeed")
		void b2x2xClientAny_Succeeds() throws Exception {
			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B2X2X, ClientLevelType.CLIENT, false))
					.assertNext(result -> assertThat(result).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("B2X2X customer - should reject")
		void b2x2xCustomer_Rejects() throws Exception {
			StepVerifier.create(invokeCheckUsageType(SecurityAppAppUsageType.B2X2X, ClientLevelType.CUSTOMER, true))
					.expectErrorMatches(e -> e instanceof GenericException
							&& e.getMessage().contains("owner"))
					.verify();
		}
	}

	// -----------------------------------------------------------------------
	// getValidClientName tests (private method tested via reflection)
	// -----------------------------------------------------------------------
	@Nested
	@DisplayName("getValidClientName()")
	class GetValidClientNameTests {

		private Method getValidClientNameMethod;

		@BeforeEach
		void setUpReflection() throws Exception {
			getValidClientNameMethod = ClientRegistrationService.class.getDeclaredMethod(
					"getValidClientName", ClientRegistrationRequest.class);
			getValidClientNameMethod.setAccessible(true);
		}

		private String invokeGetValidClientName(ClientRegistrationRequest request) throws Exception {
			return (String) getValidClientNameMethod.invoke(clientRegistrationService, request);
		}

		@Test
		@DisplayName("clientName is set - should return clientName")
		void clientNameSet_ReturnsClientName() throws Exception {
			ClientRegistrationRequest request = new ClientRegistrationRequest();
			request.setClientName("Test Company");

			String result = invokeGetValidClientName(request);
			assertThat(result).isEqualTo("Test Company");
		}

		@Test
		@DisplayName("firstName and lastName set - should return concatenation")
		void firstAndLastNameSet_ReturnsConcatenation() throws Exception {
			ClientRegistrationRequest request = new ClientRegistrationRequest();
			request.setFirstName("John");
			request.setLastName("Doe");

			String result = invokeGetValidClientName(request);
			assertThat(result).isEqualTo("JohnDoe");
		}

		@Test
		@DisplayName("only firstName set - should return firstName")
		void onlyFirstNameSet_ReturnsFirstName() throws Exception {
			ClientRegistrationRequest request = new ClientRegistrationRequest();
			request.setFirstName("John");

			String result = invokeGetValidClientName(request);
			assertThat(result).isEqualTo("John");
		}

		@Test
		@DisplayName("only emailId set - should return emailId")
		void onlyEmailIdSet_ReturnsEmailId() throws Exception {
			ClientRegistrationRequest request = new ClientRegistrationRequest();
			request.setEmailId("test@example.com");

			String result = invokeGetValidClientName(request);
			assertThat(result).isEqualTo("test@example.com");
		}

		@Test
		@DisplayName("only userName set - should return userName")
		void onlyUserNameSet_ReturnsUserName() throws Exception {
			ClientRegistrationRequest request = new ClientRegistrationRequest();
			request.setUserName("testuser");

			String result = invokeGetValidClientName(request);
			assertThat(result).isEqualTo("testuser");
		}

		@Test
		@DisplayName("nothing set - should return null")
		void nothingSet_ReturnsNull() throws Exception {
			ClientRegistrationRequest request = new ClientRegistrationRequest();

			String result = invokeGetValidClientName(request);
			assertThat(result).isNull();
		}

		@Test
		@DisplayName("clientName takes priority over firstName/lastName")
		void clientNamePriority_OverFirstLastName() throws Exception {
			ClientRegistrationRequest request = new ClientRegistrationRequest();
			request.setClientName("Acme Corp");
			request.setFirstName("John");
			request.setLastName("Doe");
			request.setEmailId("john@acme.com");

			String result = invokeGetValidClientName(request);
			assertThat(result).isEqualTo("Acme Corp");
		}
	}

	// -----------------------------------------------------------------------
	// register() error path tests
	// -----------------------------------------------------------------------
	@Nested
	@DisplayName("register() error paths")
	class RegisterErrorPathTests {

		@Test
		@DisplayName("null passType - should return error")
		void nullPassType_ReturnsError() {
			ClientRegistrationRequest request = new ClientRegistrationRequest();
			request.setClientName("Test Client");
			request.setEmailId("test@test.com");
			// passType is null by default

			StepVerifier.create(clientRegistrationService.register(request, null, null))
					.expectErrorMatches(e -> e instanceof GenericException
							&& e.getMessage().contains("password"))
					.verify();
		}
	}

	// -----------------------------------------------------------------------
	// registerWSocial() error path tests
	// -----------------------------------------------------------------------
	@Nested
	@DisplayName("registerWSocial() error paths")
	class RegisterWSocialErrorPathTests {

		@Test
		@DisplayName("blank social register state - should return error")
		void blankSocialRegisterState_ReturnsError() {
			ClientRegistrationRequest request = new ClientRegistrationRequest();
			request.setClientName("Test Client");
			request.setEmailId("test@test.com");
			// socialRegisterState is null

			StepVerifier.create(clientRegistrationService.registerWSocial(null, null, request))
					.expectErrorMatches(e -> e instanceof GenericException
							&& e.getMessage().contains("Social register state"))
					.verify();
		}
	}

	// -----------------------------------------------------------------------
	// preRegisterCheckOne() tests
	// -----------------------------------------------------------------------
	@Nested
	@DisplayName("preRegisterCheckOne()")
	class PreRegisterCheckOneTests {

		@Test
		@DisplayName("authenticated user - should reject with 'Logout to register'")
		void authenticatedUser_RejectsWithLogout() {
			ClientRegistrationRequest request = new ClientRegistrationRequest();
			request.setEmailId("new@test.com");
			request.setFirstName("New");
			request.setLastName("User");
			request.setPassword("Test@1234567");
			request.setPassType(AuthenticationPasswordType.PASSWORD);

			// systemAuth is already authenticated
			Mono<Boolean> result = clientRegistrationService.preRegisterCheckOne(request)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& e.getMessage().contains("Logout"))
					.verify();
		}

		@Test
		@DisplayName("unauthenticated context with no-registration app - should reject")
		void unauthenticatedWithNoRegistrationApp_Rejects() {

			ContextAuthentication unauthCtx = createUnauthenticatedContext();

			// Set up app with NO_REGISTRATION property
			Mono<Boolean> result = insertTestAppWithUsageType(ULong.valueOf(1), "crtest01", "CRTest App 01", "B2C")
					.flatMap(appId -> insertAppProperty(appId, ULong.valueOf(1),
							"REGISTRATION_TYPE", "REGISTRATION_TYPE_NO_REGISTRATION")
							.thenReturn(appId))
					.flatMap(appId -> {
						unauthCtx.setUrlAppCode("crtest01");
						return clientRegistrationService.preRegisterCheckOne(
								new ClientRegistrationRequest()
										.setEmailId("noreguser@test.com")
										.setFirstName("NoReg")
										.setLastName("User")
										.setPassword("Test@1234567")
										.setPassType(AuthenticationPasswordType.PASSWORD));
					})
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(unauthCtx));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException)
					.verify();
		}
	}

	// -----------------------------------------------------------------------
	// registerApp() tests
	// -----------------------------------------------------------------------
	@Nested
	@DisplayName("registerApp()")
	class RegisterAppTests {

		@Test
		@DisplayName("register client-level user for an app owned by SYSTEM - should succeed")
		void registerClientLevelUserForApp_Succeeds() {

			// registerApp calls getClientLevelType which returns CLIENT level (not OWNER)
			// when the app belongs to a parent (SYSTEM) and the registering client is a child.
			// checkIfUserIsOwner checks via profile -> role -> "Owner" role name.
			Mono<Boolean> result = insertTestClient("CRTBUS1", "CR Test Business 1", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertClientManager(clientId, ULong.valueOf(1)))
							.then(insertTestUser(clientId, "crtuser1", "crtuser1@test.com", "fincity@123"))
							.flatMap(userId ->
									// App owned by SYSTEM (clientId=1), so registering client gets CLIENT level
									insertTestAppWithUsageType(ULong.valueOf(1), "crtest02",
											"CR Test App 02", "B2C")
											.flatMap(appId -> insertAppAccess(clientId, appId, true)
													// Create "Owner" role for this app under the client
													.then(databaseClient.sql(
															"INSERT INTO security_v2_role (CLIENT_ID, APP_ID, NAME, SHORT_NAME, DESCRIPTION) VALUES (:clientId, :appId, 'Owner', 'OWNER', 'Owner role')")
															.bind("clientId", clientId.longValue())
															.bind("appId", appId.longValue())
															.filter(s -> s.returnGeneratedValues("ID"))
															.map(row -> ULong.valueOf(row.get("ID", Long.class)))
															.one())
													// Create a profile and link to the Owner role
													.flatMap(roleId -> databaseClient.sql(
															"INSERT INTO security_profile (CLIENT_ID, APP_ID, NAME, DESCRIPTION) VALUES (:clientId, :appId, 'OwnerProfile', 'Owner Profile')")
															.bind("clientId", clientId.longValue())
															.bind("appId", appId.longValue())
															.filter(s -> s.returnGeneratedValues("ID"))
															.map(row -> ULong.valueOf(row.get("ID", Long.class)))
															.one()
															.flatMap(profileId -> databaseClient.sql(
																	"INSERT INTO security_profile_role (PROFILE_ID, ROLE_ID, EXCLUDE) VALUES (:profileId, :roleId, 0)")
																	.bind("profileId", profileId.longValue())
																	.bind("roleId", roleId.longValue())
																	.then()
																	.then(databaseClient.sql(
																			"INSERT INTO security_profile_user (PROFILE_ID, USER_ID) VALUES (:profileId, :userId)")
																			.bind("profileId", profileId.longValue())
																			.bind("userId", userId.longValue())
																			.then())))
													.then(clientRegistrationService.registerApp("crtest02",
															clientId, userId)))))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(registered -> assertThat(registered).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("register with non-owner user - should fail")
		void registerNonOwnerUser_Fails() {

			Mono<Boolean> result = insertTestClient("CRTBUS2", "CR Test Business 2", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertClientManager(clientId, ULong.valueOf(1)))
							.then(insertTestUser(clientId, "crtuser2", "crtuser2@test.com", "fincity@123"))
							.flatMap(userId -> insertTestAppWithUsageType(clientId, "crtest03",
									"CR Test App 03", "B2C")
									.flatMap(appId -> insertAppAccess(clientId, appId, true)
											// Do NOT insert into security_client_user_owner
											.then(clientRegistrationService.registerApp("crtest03",
													clientId, userId)))))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException)
					.verify();
		}

		@Test
		@DisplayName("register with non-existent app code - should fail")
		void registerNonExistentAppCode_Fails() {

			Mono<Boolean> result = insertTestClient("CRTBUS3", "CR Test Business 3", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertClientManager(clientId, ULong.valueOf(1)))
							.then(insertTestUser(clientId, "crtuser3", "crtuser3@test.com", "fincity@123"))
							.flatMap(userId -> clientRegistrationService.registerApp("nonexist",
									clientId, userId)))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException)
					.verify();
		}

		@Test
		@DisplayName("register with null userId - should fail")
		void registerNullUserId_Fails() {

			Mono<Boolean> result = insertTestClient("CRTBUS4", "CR Test Business 4", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertClientManager(clientId, ULong.valueOf(1)))
							.then(insertTestAppWithUsageType(clientId, "crtest04",
									"CR Test App 04", "B2C")
									.flatMap(appId -> insertAppAccess(clientId, appId, true)
											.then(clientRegistrationService.registerApp("crtest04",
													clientId, null)))))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException)
					.verify();
		}
	}

	// -----------------------------------------------------------------------
	// evokeRegistrationEvents() tests
	// -----------------------------------------------------------------------
	@Nested
	@DisplayName("evokeRegistrationEvents()")
	class EvokeRegistrationEventsTests {

		@Test
		@DisplayName("evoke with valid user and client - exercises the event creation path")
		void evokeWithValidUserAndClient_ExercisesPath() {

			org.springframework.mock.http.server.reactive.MockServerHttpRequest mockRequest =
					org.springframework.mock.http.server.reactive.MockServerHttpRequest
							.get("/api/security/clients/register")
							.header("X-Forwarded-Host", "localhost")
							.header("X-Forwarded-Proto", "http")
							.header("X-Forwarded-Port", "8080")
							.build();

			// evokeRegistrationEvents calls getClientAuthenticationResponse which attempts
			// to authenticate. With PASSWORD type and an unhashed password in DB, authentication
			// will fail. This test verifies the method exercises the path up to authentication.
			Mono<Boolean> result = insertTestClient("CRTBUS5", "CR Test Business 5", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertClientManager(clientId, ULong.valueOf(1)))
							.then(insertTestUser(clientId, "crtuser5", "crtuser5@test.com", "fincity@123"))
							.flatMap(userId -> insertTestAppWithUsageType(ULong.valueOf(1), "crtest05",
									"CR Test App 05", "B2C")
									.flatMap(appId -> insertAppAccess(clientId, appId, false)
											.thenReturn(userId)))
							.flatMap(userId -> {
								ClientRegistrationRequest request = new ClientRegistrationRequest();
								request.setUserId(userId);
								request.setPassType(AuthenticationPasswordType.PASSWORD);
								return clientRegistrationService.evokeRegistrationEvents(
										request, mockRequest, null);
							}))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			// The method will error during authentication since the test user has
			// an unhashed password, which exercises the code path through the service.
			StepVerifier.create(result)
					.expectError()
					.verify();
		}

		@Test
		@DisplayName("evoke with non-existent user - should fail")
		void evokeWithNonExistentUser_Fails() {

			org.springframework.mock.http.server.reactive.MockServerHttpRequest mockRequest =
					org.springframework.mock.http.server.reactive.MockServerHttpRequest
							.get("/api/security/clients/register")
							.header("X-Forwarded-Host", "localhost")
							.header("X-Forwarded-Proto", "http")
							.header("X-Forwarded-Port", "8080")
							.build();

			ClientRegistrationRequest request = new ClientRegistrationRequest();
			request.setUserId(ULong.valueOf(999999));
			request.setPassType(AuthenticationPasswordType.PASSWORD);

			Mono<Boolean> result = clientRegistrationService.evokeRegistrationEvents(request, mockRequest, null)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectError()
					.verify();
		}
	}

	// -----------------------------------------------------------------------
	// generatePassword logic tests (via reflection)
	// -----------------------------------------------------------------------
	@Nested
	@DisplayName("generatePassword()")
	class GeneratePasswordTests {

		@Test
		@DisplayName("PASSWORD type with explicit password - sets password on user")
		void passwordTypeWithExplicit_SetsPassword() throws Exception {
			Method method = ClientRegistrationService.class.getDeclaredMethod(
					"generatePassword", ClientRegistrationRequest.class,
					com.fincity.security.dto.policy.AbstractPolicy.class,
					com.fincity.security.dto.User.class);
			method.setAccessible(true);

			ClientRegistrationRequest request = new ClientRegistrationRequest();
			request.setPassword("MyStr0ng@Pass");
			request.setPassType(AuthenticationPasswordType.PASSWORD);

			com.fincity.security.dto.User user = new com.fincity.security.dto.User();
			com.fincity.security.dto.policy.ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();

			String result = (String) method.invoke(clientRegistrationService, request, policy, user);
			assertThat(result).isEqualTo("MyStr0ng@Pass");
			assertThat(user.getPassword()).isEqualTo("MyStr0ng@Pass");
		}

		@Test
		@DisplayName("PIN type with explicit pin - sets pin on user")
		void pinTypeWithExplicit_SetsPin() throws Exception {
			Method method = ClientRegistrationService.class.getDeclaredMethod(
					"generatePassword", ClientRegistrationRequest.class,
					com.fincity.security.dto.policy.AbstractPolicy.class,
					com.fincity.security.dto.User.class);
			method.setAccessible(true);

			ClientRegistrationRequest request = new ClientRegistrationRequest();
			request.setPin("123456");
			request.setPassType(AuthenticationPasswordType.PIN);

			com.fincity.security.dto.User user = new com.fincity.security.dto.User();
			com.fincity.security.dto.policy.ClientPinPolicy policy = TestDataFactory.createPinPolicy();

			String result = (String) method.invoke(clientRegistrationService, request, policy, user);
			assertThat(result).isEqualTo("123456");
			assertThat(user.getPin()).isEqualTo("123456");
		}

		@Test
		@DisplayName("PASSWORD type with blank password - generates from policy")
		void passwordTypeBlank_GeneratesFromPolicy() throws Exception {
			Method method = ClientRegistrationService.class.getDeclaredMethod(
					"generatePassword", ClientRegistrationRequest.class,
					com.fincity.security.dto.policy.AbstractPolicy.class,
					com.fincity.security.dto.User.class);
			method.setAccessible(true);

			ClientRegistrationRequest request = new ClientRegistrationRequest();
			request.setPassType(AuthenticationPasswordType.PASSWORD);
			// password left blank

			com.fincity.security.dto.User user = new com.fincity.security.dto.User();
			com.fincity.security.dto.policy.ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();

			String result = (String) method.invoke(clientRegistrationService, request, policy, user);
			assertThat(result).isNotNull();
			assertThat(result).isNotBlank();
			assertThat(user.getPassword()).isEqualTo(result);
		}

		@Test
		@DisplayName("OTP type - should return null")
		void otpType_ReturnsNull() throws Exception {
			Method method = ClientRegistrationService.class.getDeclaredMethod(
					"generatePassword", ClientRegistrationRequest.class,
					com.fincity.security.dto.policy.AbstractPolicy.class,
					com.fincity.security.dto.User.class);
			method.setAccessible(true);

			ClientRegistrationRequest request = new ClientRegistrationRequest();
			request.setPassType(AuthenticationPasswordType.OTP);

			com.fincity.security.dto.User user = new com.fincity.security.dto.User();
			com.fincity.security.dto.policy.ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();

			String result = (String) method.invoke(clientRegistrationService, request, policy, user);
			assertThat(result).isNull();
		}
	}

	// -----------------------------------------------------------------------
	// checkSubDomainAvailability tests (via reflection)
	// -----------------------------------------------------------------------
	@Nested
	@DisplayName("checkSubDomainAvailability()")
	class CheckSubDomainAvailabilityTests {

		@SuppressWarnings("unchecked")
		private Mono<String> invokeCheckSubDomainAvailability(String subDomain, String suffix,
				boolean isBusinessClient) throws Exception {
			Method method = ClientRegistrationService.class.getDeclaredMethod(
					"checkSubDomainAvailability", String.class, String.class, boolean.class);
			method.setAccessible(true);
			return (Mono<String>) method.invoke(clientRegistrationService, subDomain, suffix, isBusinessClient);
		}

		@Test
		@DisplayName("non-business client - should return empty string")
		void nonBusinessClient_ReturnsEmpty() throws Exception {
			StepVerifier.create(invokeCheckSubDomainAvailability("mysub", ".test.com", false))
					.assertNext(result -> assertThat(result).isEmpty())
					.verifyComplete();
		}

		@Test
		@DisplayName("business client with blank subdomain - should return empty string")
		void blankSubDomain_ReturnsEmpty() throws Exception {
			StepVerifier.create(invokeCheckSubDomainAvailability("", ".test.com", true))
					.assertNext(result -> assertThat(result).isEmpty())
					.verifyComplete();
		}

		@Test
		@DisplayName("business client with null subdomain - should return empty string")
		void nullSubDomain_ReturnsEmpty() throws Exception {
			StepVerifier.create(invokeCheckSubDomainAvailability(null, ".test.com", true))
					.assertNext(result -> assertThat(result).isEmpty())
					.verifyComplete();
		}
	}

	// -----------------------------------------------------------------------
	// fetchAppProp tests (via reflection)
	// -----------------------------------------------------------------------
	@Nested
	@DisplayName("fetchAppProp()")
	class FetchAppPropTests {

		@SuppressWarnings("unchecked")
		private Mono<String> invokeFetchAppProp(ULong clientId, ULong appId, String appCode,
				String propName) throws Exception {
			Method method = ClientRegistrationService.class.getDeclaredMethod(
					"fetchAppProp", ULong.class, ULong.class, String.class, String.class);
			method.setAccessible(true);
			return (Mono<String>) method.invoke(clientRegistrationService, clientId, appId, appCode, propName);
		}

		@Test
		@DisplayName("app with registration property - returns property value")
		void appWithProperty_ReturnsValue() {

			Mono<String> result = insertTestAppWithUsageType(ULong.valueOf(1), "crtest06", "CRTest App 06", "B2C")
					.flatMap(appId -> insertAppProperty(appId, ULong.valueOf(1),
							"REGISTRATION_TYPE", "REGISTRATION_TYPE_NO_VERIFICATION")
							.thenReturn(appId))
					.flatMap(appId -> {
						try {
							return invokeFetchAppProp(ULong.valueOf(1), appId, null, "REGISTRATION_TYPE");
						} catch (Exception e) {
							return Mono.error(e);
						}
					})
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(value -> assertThat(value).contains("REGISTRATION_TYPE"))
					.verifyComplete();
		}

		@Test
		@DisplayName("app without registration property - returns empty string")
		void appWithoutProperty_ReturnsEmpty() {

			Mono<String> result = insertTestAppWithUsageType(ULong.valueOf(1), "crtest07", "CRTest App 07", "B2C")
					.flatMap(appId -> {
						try {
							return invokeFetchAppProp(ULong.valueOf(1), appId, null, "REGISTRATION_TYPE");
						} catch (Exception e) {
							return Mono.error(e);
						}
					})
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(value -> assertThat(value).isEmpty())
					.verifyComplete();
		}
	}

	// -----------------------------------------------------------------------
	// verifyClient tests (via reflection)
	// -----------------------------------------------------------------------
	@Nested
	@DisplayName("verifyClient()")
	class VerifyClientTests {

		@SuppressWarnings("unchecked")
		private Mono<Boolean> invokeVerifyClient(ContextAuthentication ca, String regProp,
				String emailId, String phoneNumber, String otp) throws Exception {
			Method method = ClientRegistrationService.class.getDeclaredMethod(
					"verifyClient", ContextAuthentication.class, String.class,
					String.class, String.class, String.class);
			method.setAccessible(true);
			return (Mono<Boolean>) method.invoke(clientRegistrationService, ca, regProp,
					emailId, phoneNumber, otp);
		}

		@Test
		@DisplayName("no verification type - should return true")
		void noVerificationType_ReturnsTrue() throws Exception {
			StepVerifier.create(invokeVerifyClient(systemAuth,
					"REGISTRATION_TYPE_NO_VERIFICATION", "test@test.com", null, null))
					.assertNext(result -> assertThat(result).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("user with Client_CREATE authority - should return true")
		void userWithCreateAuthority_ReturnsTrue() throws Exception {
			// systemAuth has Authorities.Client_CREATE
			StepVerifier.create(invokeVerifyClient(systemAuth,
					"REGISTRATION_TYPE_VERIFICATION", "test@test.com", null, null))
					.assertNext(result -> assertThat(result).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("user without authority and with verification - should verify OTP")
		void userWithoutAuthority_VerifiesOtp() throws Exception {
			ContextAuthentication nonPrivilegedAuth = TestDataFactory.createBusinessAuth(
					ULong.valueOf(2), "TESTCLI",
					List.of("Authorities.Logged_IN"));
			nonPrivilegedAuth.setUrlAppCode("appbuilder");
			nonPrivilegedAuth.setUrlClientCode("TESTCLI");

			// OTP verification will fail since there's no valid OTP in the system
			StepVerifier.create(invokeVerifyClient(nonPrivilegedAuth,
					"REGISTRATION_TYPE_VERIFICATION", "test@test.com", null, "1234"))
					.expectError()
					.verify();
		}
	}

	// -----------------------------------------------------------------------
	// End-to-end registration flow (limited test with preRegisterCheckOne)
	// -----------------------------------------------------------------------
	@Nested
	@DisplayName("End-to-end partial flow tests")
	class EndToEndTests {

		@Test
		@DisplayName("preRegisterCheckOne with unauthenticated context and no-verification app - returns true")
		void preRegisterCheckOneWithNoVerification_ReturnsTrue() {

			ContextAuthentication unauthCtx = createUnauthenticatedContext();
			unauthCtx.setUrlAppCode("crtest08");

			Mono<Boolean> result = insertTestAppWithUsageType(ULong.valueOf(1), "crtest08",
					"CRTest App 08", "B2C")
					.flatMap(appId -> insertAppProperty(appId, ULong.valueOf(1),
							"REGISTRATION_TYPE", "REGISTRATION_TYPE_NO_VERIFICATION")
							.thenReturn(appId))
					.flatMap(appId -> {
						ClientRegistrationRequest request = new ClientRegistrationRequest();
						request.setEmailId("newuser@test.com");
						request.setFirstName("New");
						request.setLastName("User");
						request.setPassword("Test@1234567");
						request.setPassType(AuthenticationPasswordType.PASSWORD);
						return clientRegistrationService.preRegisterCheckOne(request);
					})
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(unauthCtx));

			StepVerifier.create(result)
					.assertNext(check -> assertThat(check).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("preRegisterCheckOne with unauthenticated context and no-reg app - should error")
		void preRegisterCheckOneWithNoRegistration_Errors() {

			ContextAuthentication unauthCtx = createUnauthenticatedContext();
			unauthCtx.setUrlAppCode("crtest09");

			Mono<Boolean> result = insertTestAppWithUsageType(ULong.valueOf(1), "crtest09",
					"CRTest App 09", "B2C")
					.flatMap(appId -> insertAppProperty(appId, ULong.valueOf(1),
							"REGISTRATION_TYPE", "REGISTRATION_TYPE_NO_REGISTRATION")
							.thenReturn(appId))
					.flatMap(appId -> {
						ClientRegistrationRequest request = new ClientRegistrationRequest();
						request.setEmailId("noreg@test.com");
						request.setFirstName("NoReg");
						request.setLastName("User");
						request.setPassword("Test@1234567");
						request.setPassType(AuthenticationPasswordType.PASSWORD);
						return clientRegistrationService.preRegisterCheckOne(request);
					})
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(unauthCtx));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException)
					.verify();
		}

		@Test
		@DisplayName("preRegisterCheckOne with existing individual user - should conflict error")
		void preRegisterCheckOneWithExistingUser_ConflictError() {

			ContextAuthentication unauthCtx = createUnauthenticatedContext();
			unauthCtx.setUrlAppCode("crtest10");

			Mono<Boolean> result = insertTestAppWithUsageType(ULong.valueOf(1), "crtest10",
					"CRTest App 10", "B2C")
					.flatMap(appId -> insertAppProperty(appId, ULong.valueOf(1),
							"REGISTRATION_TYPE", "REGISTRATION_TYPE_NO_VERIFICATION")
							.thenReturn(appId))
					// Create the individual client and user first
					.flatMap(appId -> insertTestClient("CRTIND", "CR Test Indiv", "INDV")
							.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null,
									null)
									.then(insertTestUser(clientId, "existuser", "existuser@test.com",
											"fincity@123"))
									.thenReturn(appId)))
					.flatMap(appId -> {
						ClientRegistrationRequest request = new ClientRegistrationRequest();
						request.setEmailId("existuser@test.com");
						request.setUserName("existuser");
						request.setFirstName("Exist");
						request.setLastName("User");
						request.setPassword("Test@1234567");
						request.setPassType(AuthenticationPasswordType.PASSWORD);
						return clientRegistrationService.preRegisterCheckOne(request);
					})
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(unauthCtx));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == org.springframework.http.HttpStatus.CONFLICT)
					.verify();
		}
	}

	// -----------------------------------------------------------------------
	// ClientRegistrationRequest model tests
	// -----------------------------------------------------------------------
	@Nested
	@DisplayName("ClientRegistrationRequest model")
	class ClientRegistrationRequestModelTests {

		@Test
		@DisplayName("getIdentifier returns emailId when set")
		void getIdentifier_ReturnsEmailId() {
			ClientRegistrationRequest request = new ClientRegistrationRequest();
			request.setEmailId("test@example.com");
			request.setPhoneNumber("1234567890");

			assertThat(request.getIdentifier()).isEqualTo("test@example.com");
		}

		@Test
		@DisplayName("getIdentifier returns phoneNumber when emailId is blank")
		void getIdentifier_ReturnsPhoneNumber() {
			ClientRegistrationRequest request = new ClientRegistrationRequest();
			request.setPhoneNumber("1234567890");

			assertThat(request.getIdentifier()).isEqualTo("1234567890");
		}

		@Test
		@DisplayName("getIdentifier returns null when nothing set")
		void getIdentifier_ReturnsNull() {
			ClientRegistrationRequest request = new ClientRegistrationRequest();

			assertThat(request.getIdentifier()).isNull();
		}

		@Test
		@DisplayName("getInputPassType returns PASSWORD when passType is set")
		void getInputPassType_ReturnsPassType() {
			ClientRegistrationRequest request = new ClientRegistrationRequest();
			request.setPassType(AuthenticationPasswordType.PASSWORD);

			assertThat(request.getInputPassType()).isEqualTo(AuthenticationPasswordType.PASSWORD);
		}

		@Test
		@DisplayName("getInputPassType infers PASSWORD from password field")
		void getInputPassType_InfersFromPassword() {
			ClientRegistrationRequest request = new ClientRegistrationRequest();
			request.setPassword("test123");

			assertThat(request.getInputPassType()).isEqualTo(AuthenticationPasswordType.PASSWORD);
		}

		@Test
		@DisplayName("getInputPassType infers PIN from pin field")
		void getInputPassType_InfersFromPin() {
			ClientRegistrationRequest request = new ClientRegistrationRequest();
			request.setPin("123456");

			assertThat(request.getInputPassType()).isEqualTo(AuthenticationPasswordType.PIN);
		}

		@Test
		@DisplayName("getInputPassType infers OTP from otp field")
		void getInputPassType_InfersFromOtp() {
			ClientRegistrationRequest request = new ClientRegistrationRequest();
			request.setOtp("1234");

			assertThat(request.getInputPassType()).isEqualTo(AuthenticationPasswordType.OTP);
		}

		@Test
		@DisplayName("isBusinessClient defaults to false")
		void isBusinessClient_DefaultsFalse() {
			ClientRegistrationRequest request = new ClientRegistrationRequest();
			assertThat(request.isBusinessClient()).isFalse();
		}

		@Test
		@DisplayName("chain setters work correctly")
		void chainSetters_WorkCorrectly() {
			ClientRegistrationRequest request = new ClientRegistrationRequest()
					.setClientName("Test")
					.setEmailId("test@test.com")
					.setBusinessClient(true)
					.setBusinessType("COMMON")
					.setLocaleCode("en")
					.setSubDomain("test")
					.setSubDomainSuffix(".example.com");

			assertThat(request.getClientName()).isEqualTo("Test");
			assertThat(request.getEmailId()).isEqualTo("test@test.com");
			assertThat(request.isBusinessClient()).isTrue();
			assertThat(request.getBusinessType()).isEqualTo("COMMON");
			assertThat(request.getLocaleCode()).isEqualTo("en");
			assertThat(request.getSubDomain()).isEqualTo("test");
			assertThat(request.getSubDomainSuffix()).isEqualTo(".example.com");
		}
	}
}
