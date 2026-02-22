package com.fincity.security.integration.dao;

import static org.junit.jupiter.api.Assertions.*;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.security.dao.policy.ClientOtpPolicyDAO;
import com.fincity.security.dao.policy.ClientPasswordPolicyDAO;
import com.fincity.security.dao.policy.ClientPinPolicyDAO;
import com.fincity.security.dto.policy.ClientOtpPolicy;
import com.fincity.security.dto.policy.ClientPasswordPolicy;
import com.fincity.security.dto.policy.ClientPinPolicy;
import com.fincity.security.integration.AbstractIntegrationTest;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PolicyDAOIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private ClientPasswordPolicyDAO passwordPolicyDAO;

	@Autowired
	private ClientOtpPolicyDAO otpPolicyDAO;

	@Autowired
	private ClientPinPolicyDAO pinPolicyDAO;

	@BeforeEach
	void setUp() {
		setupMockBeans();
	}

	@AfterEach
	void tearDown() {
		databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
				.then(databaseClient.sql("DELETE FROM security_client_password_policy WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client_otp_policy WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client_pin_policy WHERE CLIENT_ID > 1").then())
				.then(databaseClient
						.sql("DELETE FROM security_app WHERE APP_CODE NOT IN ('appbuilder', 'nothing')").then())
				.then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}

	private Mono<ULong> insertPasswordPolicyViaSQL(ULong clientId, ULong appId) {
		String sql = "INSERT INTO security_client_password_policy (CLIENT_ID, APP_ID, ATLEAST_ONE_UPPERCASE, ATLEAST_ONE_LOWERCASE, ATLEAST_ONE_DIGIT, ATLEAST_ONE_SPECIAL_CHAR, SPACES_ALLOWED, PASS_MIN_LENGTH, PASS_MAX_LENGTH, NO_FAILED_ATTEMPTS, PASS_HISTORY_COUNT, USER_LOCK_TIME) VALUES (:clientId, :appId, 1, 1, 1, 0, 0, 8, 20, 5, 3, 30)";

		var spec = databaseClient.sql(sql)
				.bind("clientId", clientId.longValue());

		if (appId != null) {
			spec = spec.bind("appId", appId.longValue());
		} else {
			spec = spec.bindNull("appId", Long.class);
		}

		return spec.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	private Mono<ULong> insertOtpPolicyViaSQL(ULong clientId, ULong appId) {
		return databaseClient.sql(
				"INSERT INTO security_client_otp_policy (CLIENT_ID, APP_ID, TARGET_TYPE, IS_CONSTANT, IS_NUMERIC, IS_ALPHANUMERIC, LENGTH, RESEND_SAME_OTP, NO_RESEND_ATTEMPTS, EXPIRE_INTERVAL, NO_FAILED_ATTEMPTS, USER_LOCK_TIME) VALUES (:clientId, :appId, 'EMAIL', 0, 1, 0, 6, 0, 3, 5, 3, 30)")
				.bind("clientId", clientId.longValue())
				.bind("appId", appId.longValue())
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	private Mono<ULong> insertPinPolicyViaSQL(ULong clientId, ULong appId) {
		return databaseClient.sql(
				"INSERT INTO security_client_pin_policy (CLIENT_ID, APP_ID, LENGTH, RE_LOGIN_AFTER_INTERVAL, EXPIRY_IN_DAYS, EXPIRY_WARN_IN_DAYS, PIN_HISTORY_COUNT, NO_FAILED_ATTEMPTS, USER_LOCK_TIME) VALUES (:clientId, :appId, 4, 120, 30, 25, 3, 3, 30)")
				.bind("clientId", clientId.longValue())
				.bind("appId", appId.longValue())
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	@Nested
	@DisplayName("PasswordPolicyTests")
	class PasswordPolicyTests {

		@Test
		@DisplayName("should insert password policy via SQL and read it back via getClientAppPolicy")
		void insertAndReadByClientId() {

			Mono<ClientPasswordPolicy> pipeline = insertTestClient("PPCLNT", "Password Policy Client", "BUS")
					.flatMap(clientId -> insertTestApp(clientId, "ppTestApp", "PP Test App")
							.flatMap(appId -> insertPasswordPolicyViaSQL(clientId, appId)
									.then(passwordPolicyDAO.getClientAppPolicy(clientId, appId))));

			StepVerifier.create(pipeline)
					.assertNext(policy -> {
						assertNotNull(policy);
						assertNotNull(policy.getId());
						assertTrue(policy.isAtleastOneUppercase());
						assertTrue(policy.isAtleastOneLowercase());
						assertTrue(policy.isAtleastOneDigit());
						assertFalse(policy.isAtleastOneSpecialChar());
						assertFalse(policy.isSpacesAllowed());
						assertEquals((short) 8, policy.getPassMinLength());
						assertEquals((short) 20, policy.getPassMaxLength());
						assertEquals((short) 5, policy.getNoFailedAttempts());
						assertEquals((short) 3, policy.getPassHistoryCount());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should return empty for non-existent client password policy via getClientAppPolicy")
		void nonExistentClientPolicyReturnsEmpty() {
			StepVerifier.create(
					passwordPolicyDAO.getClientAppPolicy(ULong.valueOf(999999), ULong.valueOf(999999)))
					.verifyComplete();
		}

		@Test
		@DisplayName("should read password policy via getClientAppPolicy with clientId and appId")
		void getClientAppPolicyWithAppId() {

			Mono<ClientPasswordPolicy> pipeline = insertTestClient("PPACLNT", "Pwd App Client", "BUS")
					.flatMap(clientId -> insertTestApp(clientId, "pwdtestapp", "Pwd Test App")
							.flatMap(appId -> insertPasswordPolicyViaSQL(clientId, appId)
									.then(passwordPolicyDAO.getClientAppPolicy(clientId, appId))));

			StepVerifier.create(pipeline)
					.assertNext(policy -> {
						assertNotNull(policy);
						assertNotNull(policy.getId());
						assertNotNull(policy.getClientId());
						assertTrue(policy.isAtleastOneUppercase());
						assertEquals((short) 8, policy.getPassMinLength());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("OtpPolicyTests")
	class OtpPolicyTests {

		@Test
		@DisplayName("should insert OTP policy via SQL and read via getClientAppPolicy")
		void insertAndReadOtpPolicy() {

			Mono<ClientOtpPolicy> pipeline = insertTestClient("OPCLNT", "OTP Policy Client", "BUS")
					.flatMap(clientId -> insertTestApp(clientId, "otptestapp", "OTP Test App")
							.flatMap(appId -> insertOtpPolicyViaSQL(clientId, appId)
									.then(otpPolicyDAO.getClientAppPolicy(clientId, appId))));

			StepVerifier.create(pipeline)
					.assertNext(policy -> {
						assertNotNull(policy);
						assertNotNull(policy.getId());
						assertNotNull(policy.getClientId());
						assertNotNull(policy.getAppId());
						assertEquals((short) 6, policy.getLength());
						assertEquals(5L, policy.getExpireInterval());
						assertEquals((short) 3, policy.getNoFailedAttempts());
						assertEquals((short) 3, policy.getNoResendAttempts());
						assertTrue(policy.isNumeric());
						assertFalse(policy.isAlphanumeric());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should return empty for non-existent client OTP policy")
		void nonExistentOtpPolicyReturnsEmpty() {
			StepVerifier.create(
					otpPolicyDAO.getClientAppPolicy(ULong.valueOf(999999), ULong.valueOf(999999)))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("PinPolicyTests")
	class PinPolicyTests {

		@Test
		@DisplayName("should insert PIN policy via SQL and read via getClientAppPolicy")
		void insertAndReadPinPolicy() {

			Mono<ClientPinPolicy> pipeline = insertTestClient("PNCLNT", "PIN Policy Client", "BUS")
					.flatMap(clientId -> insertTestApp(clientId, "pintestapp", "PIN Test App")
							.flatMap(appId -> insertPinPolicyViaSQL(clientId, appId)
									.then(pinPolicyDAO.getClientAppPolicy(clientId, appId))));

			StepVerifier.create(pipeline)
					.assertNext(policy -> {
						assertNotNull(policy);
						assertNotNull(policy.getId());
						assertNotNull(policy.getClientId());
						assertNotNull(policy.getAppId());
						assertEquals((short) 4, policy.getLength());
						assertEquals(120L, policy.getReLoginAfterInterval());
						assertEquals((short) 30, policy.getExpiryInDays());
						assertEquals((short) 25, policy.getExpiryWarnInDays());
						assertEquals((short) 3, policy.getPinHistoryCount());
						assertEquals((short) 3, policy.getNoFailedAttempts());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("should return empty for non-existent client PIN policy")
		void nonExistentPinPolicyReturnsEmpty() {
			StepVerifier.create(
					pinPolicyDAO.getClientAppPolicy(ULong.valueOf(999999), ULong.valueOf(999999)))
					.verifyComplete();
		}
	}
}
