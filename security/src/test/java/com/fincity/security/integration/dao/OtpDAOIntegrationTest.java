package com.fincity.security.integration.dao;

import static org.junit.jupiter.api.Assertions.*;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.security.dao.OtpDAO;
import com.fincity.security.enums.otp.OtpPurpose;
import com.fincity.security.integration.AbstractIntegrationTest;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class OtpDAOIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private OtpDAO otpDAO;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);

	private ULong testAppId;
	private ULong testUserId;

	@BeforeEach
	void setUp() {
		setupMockBeans();

		String ts = String.valueOf(System.currentTimeMillis());

		testAppId = insertTestApp(SYSTEM_CLIENT_ID, "otpapp_" + ts, "OTP Test App")
				.block();

		testUserId = insertTestUser(SYSTEM_CLIENT_ID, "otpuser_" + ts, "otpuser_" + ts + "@test.com", "password123")
				.block();
	}

	@AfterEach
	void tearDown() {
		databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
				.then(databaseClient.sql("DELETE FROM security_otp WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_user WHERE ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_app WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}

	private Mono<ULong> insertTestOtp(ULong appId, ULong userId, String email, String phone, String purpose,
			String code) {

		var spec = databaseClient.sql(
				"INSERT INTO security_otp (APP_ID, USER_ID, EMAIL_ID, PHONE_NUMBER, PURPOSE, UNIQUE_CODE, TARGET_TYPE, EXPIRES_AT) "
						+ "VALUES (:appId, :userId, :email, :phone, :purpose, :code, 'EMAIL', DATE_ADD(NOW(), INTERVAL 10 MINUTE))")
				.bind("appId", appId.longValue());

		spec = userId != null ? spec.bind("userId", userId.longValue()) : spec.bindNull("userId", Long.class);
		spec = email != null ? spec.bind("email", email) : spec.bindNull("email", String.class);
		spec = phone != null ? spec.bind("phone", phone) : spec.bindNull("phone", String.class);
		spec = spec.bind("purpose", purpose);
		spec = spec.bind("code", code);

		return spec.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	private Mono<ULong> insertTestOtpWithDelay(ULong appId, ULong userId, String email, String phone,
			String purpose, String code, int delaySeconds) {

		var spec = databaseClient.sql(
				"INSERT INTO security_otp (APP_ID, USER_ID, EMAIL_ID, PHONE_NUMBER, PURPOSE, UNIQUE_CODE, TARGET_TYPE, EXPIRES_AT, CREATED_AT) "
						+ "VALUES (:appId, :userId, :email, :phone, :purpose, :code, 'EMAIL', "
						+ "DATE_ADD(NOW(), INTERVAL 10 MINUTE), DATE_ADD(NOW(), INTERVAL :delay SECOND))")
				.bind("appId", appId.longValue());

		spec = userId != null ? spec.bind("userId", userId.longValue()) : spec.bindNull("userId", Long.class);
		spec = email != null ? spec.bind("email", email) : spec.bindNull("email", String.class);
		spec = phone != null ? spec.bind("phone", phone) : spec.bindNull("phone", String.class);
		spec = spec.bind("purpose", purpose);
		spec = spec.bind("code", code);
		spec = spec.bind("delay", delaySeconds);

		return spec.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	@Nested
	@DisplayName("getLatestOtp() - by userId")
	class GetLatestOtpByUserTests {

		@Test
		void singleOtp_ReturnsIt() {
			StepVerifier.create(
					insertTestOtp(testAppId, testUserId, null, null, OtpPurpose.LOGIN.name(), "CODE001")
							.then(otpDAO.getLatestOtp(testAppId, testUserId, OtpPurpose.LOGIN)))
					.assertNext(otp -> {
						assertNotNull(otp);
						assertEquals(testAppId, otp.getAppId());
						assertEquals(testUserId, otp.getUserId());
						assertEquals("CODE001", otp.getUniqueCode());
						assertEquals(OtpPurpose.LOGIN.name(), otp.getPurpose());
					})
					.verifyComplete();
		}

		@Test
		void multipleOtps_ReturnsLatest() {
			StepVerifier.create(
					insertTestOtpWithDelay(testAppId, testUserId, null, null, OtpPurpose.LOGIN.name(), "OLDER_CODE",
							-10)
							.then(insertTestOtpWithDelay(testAppId, testUserId, null, null, OtpPurpose.LOGIN.name(),
									"NEWER_CODE", 0))
							.then(otpDAO.getLatestOtp(testAppId, testUserId, OtpPurpose.LOGIN)))
					.assertNext(otp -> {
						assertNotNull(otp);
						assertEquals("NEWER_CODE", otp.getUniqueCode());
					})
					.verifyComplete();
		}

		@Test
		void differentPurpose_ReturnsOnlyMatchingPurpose() {
			StepVerifier.create(
					insertTestOtp(testAppId, testUserId, null, null, OtpPurpose.LOGIN.name(), "LOGIN_CODE")
							.then(insertTestOtp(testAppId, testUserId, null, null,
									OtpPurpose.PASSWORD_RESET.name(), "RESET_CODE"))
							.then(otpDAO.getLatestOtp(testAppId, testUserId, OtpPurpose.PASSWORD_RESET)))
					.assertNext(otp -> {
						assertNotNull(otp);
						assertEquals("RESET_CODE", otp.getUniqueCode());
						assertEquals(OtpPurpose.PASSWORD_RESET.name(), otp.getPurpose());
					})
					.verifyComplete();
		}

		@Test
		void nullUserId_ReturnsEmpty() {
			StepVerifier.create(otpDAO.getLatestOtp(testAppId, (ULong) null, OtpPurpose.LOGIN))
					.verifyComplete();
		}

		@Test
		void differentApp_ReturnsEmpty() {
			ULong otherAppId = insertTestApp(SYSTEM_CLIENT_ID, "otherapp_" + System.currentTimeMillis(),
					"Other App").block();

			StepVerifier.create(
					insertTestOtp(testAppId, testUserId, null, null, OtpPurpose.LOGIN.name(), "APP_CODE")
							.then(otpDAO.getLatestOtp(otherAppId, testUserId, OtpPurpose.LOGIN)))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getLatestOtp() - by email and phone")
	class GetLatestOtpByEmailTests {

		@Test
		void byEmail_ReturnsOtp() {
			String email = "otpemail_" + System.currentTimeMillis() + "@test.com";

			StepVerifier.create(
					insertTestOtp(testAppId, null, email, null, OtpPurpose.REGISTRATION.name(), "EMAIL_CODE")
							.then(otpDAO.getLatestOtp(testAppId, email, null, OtpPurpose.REGISTRATION)))
					.assertNext(otp -> {
						assertNotNull(otp);
						assertEquals(email, otp.getEmailId());
						assertEquals("EMAIL_CODE", otp.getUniqueCode());
					})
					.verifyComplete();
		}

		@Test
		void byPhone_ReturnsOtp() {
			String phone = "+1234567890";

			StepVerifier.create(
					insertTestOtp(testAppId, null, null, phone, OtpPurpose.VERIFICATION.name(), "PHONE_CODE")
							.then(otpDAO.getLatestOtp(testAppId, null, phone, OtpPurpose.VERIFICATION)))
					.assertNext(otp -> {
						assertNotNull(otp);
						assertEquals(phone, otp.getPhoneNumber());
						assertEquals("PHONE_CODE", otp.getUniqueCode());
					})
					.verifyComplete();
		}

		@Test
		void blankEmailAndPhone_ReturnsEmpty() {
			StepVerifier.create(otpDAO.getLatestOtp(testAppId, "", "", OtpPurpose.LOGIN))
					.verifyComplete();
		}

		@Test
		void nullEmailAndPhone_ReturnsEmpty() {
			StepVerifier.create(otpDAO.getLatestOtp(testAppId, (String) null, null, OtpPurpose.LOGIN))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getLatestOtpCode() - returns only the code string")
	class GetLatestOtpCodeTests {

		@Test
		void byUser_ReturnsCodeOnly() {
			StepVerifier.create(
					insertTestOtp(testAppId, testUserId, null, null, OtpPurpose.LOGIN.name(), "JUSTCODE")
							.then(otpDAO.getLatestOtpCode(testAppId, testUserId, OtpPurpose.LOGIN)))
					.assertNext(code -> {
						assertNotNull(code);
						assertTrue(code.contains("JUSTCODE"));
					})
					.verifyComplete();
		}

		@Test
		void byUser_NullUserId_ReturnsEmpty() {
			StepVerifier.create(otpDAO.getLatestOtpCode(testAppId, (ULong) null, OtpPurpose.LOGIN))
					.verifyComplete();
		}

		@Test
		void byEmail_ReturnsCode() {
			String email = "codemail_" + System.currentTimeMillis() + "@test.com";

			StepVerifier.create(
					insertTestOtp(testAppId, null, email, null, OtpPurpose.REGISTRATION.name(), "EMAILCODE")
							.then(otpDAO.getLatestOtpCode(testAppId, email, null, OtpPurpose.REGISTRATION)))
					.assertNext(code -> {
						assertNotNull(code);
						assertTrue(code.contains("EMAILCODE"));
					})
					.verifyComplete();
		}

		@Test
		void byEmailAndPhone_NullBoth_ReturnsEmpty() {
			StepVerifier.create(otpDAO.getLatestOtpCode(testAppId, (String) null, null, OtpPurpose.LOGIN))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("increaseVerifyCounts()")
	class IncreaseVerifyCountsTests {

		@Test
		void existingOtp_ReturnsTrue() {
			StepVerifier.create(
					insertTestOtp(testAppId, testUserId, null, null, OtpPurpose.LOGIN.name(), "BOOL_CODE")
							.flatMap(otpDAO::increaseVerifyCounts))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void nonExistentOtp_ReturnsFalse() {
			StepVerifier.create(otpDAO.increaseVerifyCounts(ULong.valueOf(999999)))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		void multipleIncrements_AccumulateCorrectly() {
			StepVerifier.create(
					insertTestOtp(testAppId, testUserId, null, null, OtpPurpose.LOGIN.name(), "MULTI_VERIFY")
							.flatMap(otpId -> otpDAO.increaseVerifyCounts(otpId)
									.then(otpDAO.increaseVerifyCounts(otpId))
									.then(otpDAO.increaseVerifyCounts(otpId))
									.then(databaseClient.sql(
											"SELECT VERIFY_LEGS_COUNTS FROM security_otp WHERE ID = :id")
											.bind("id", otpId.longValue())
											.map(row -> row.get("VERIFY_LEGS_COUNTS", Short.class))
											.one())))
					.assertNext(count -> assertEquals((short) 3, count))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("No OTP exists scenarios")
	class NoOtpTests {

		@Test
		void getLatestOtpByUser_NoOtp_ReturnsEmpty() {
			StepVerifier.create(otpDAO.getLatestOtp(testAppId, testUserId, OtpPurpose.LOGIN))
					.verifyComplete();
		}

		@Test
		void getLatestOtpByEmail_NoOtp_ReturnsEmpty() {
			StepVerifier.create(
					otpDAO.getLatestOtp(testAppId, "nonexistent@nowhere.com", null, OtpPurpose.REGISTRATION))
					.verifyComplete();
		}

		@Test
		void getLatestOtpCodeByUser_NoOtp_ReturnsEmpty() {
			StepVerifier.create(otpDAO.getLatestOtpCode(testAppId, testUserId, OtpPurpose.PASSWORD_RESET))
					.verifyComplete();
		}

		@Test
		void wrongPurpose_ReturnsEmpty() {
			StepVerifier.create(
					insertTestOtp(testAppId, testUserId, null, null, OtpPurpose.LOGIN.name(), "PURPOSETEST")
							.then(otpDAO.getLatestOtp(testAppId, testUserId, OtpPurpose.VERIFICATION)))
					.verifyComplete();
		}
	}
}
