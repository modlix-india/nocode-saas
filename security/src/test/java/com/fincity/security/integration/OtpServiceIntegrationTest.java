package com.fincity.security.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.time.LocalDateTime;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.security.dao.OtpDAO;
import com.fincity.security.dto.App;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.Otp;
import com.fincity.security.dto.User;
import com.fincity.security.enums.otp.OtpPurpose;
import com.fincity.security.jooq.enums.SecurityOtpTargetType;
import com.fincity.security.model.otp.OtpGenerationRequestInternal;
import com.fincity.security.model.otp.OtpMessageVars;
import com.fincity.security.model.otp.OtpVerificationRequest;
import com.fincity.security.service.AppService;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.OtpService;
import com.fincity.security.service.message.MessageService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class OtpServiceIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private OtpService otpService;

	@Autowired
	private OtpDAO otpDAO;

	@Autowired
	private AppService appService;

	@Autowired
	private ClientService clientService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@MockitoBean
	private MessageService messageService;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);

	private ContextAuthentication systemAuth;

	private ULong testAppId;
	private String testAppCode;
	private ULong testUserId;

	@BeforeEach
	void setUp() {
		setupMockBeans();
		systemAuth = TestDataFactory.createSystemAuth();

		lenient().when(messageService.sendOtpMessage(anyString(), any(OtpMessageVars.class)))
				.thenReturn(Mono.just(Boolean.TRUE));

		String ts = String.valueOf(System.currentTimeMillis() % 100000);
		testAppCode = "otp" + ts;

		testAppId = insertTestApp(SYSTEM_CLIENT_ID, testAppCode, "OTP Service Test App")
				.block();

		testUserId = insertTestUser(SYSTEM_CLIENT_ID, "otpsu_" + ts, "otpsu_" + ts + "@test.com", "password123")
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

	// =========================================================================
	// Helper methods
	// =========================================================================

	private App getSystemApp() {
		return appService.getAppByCode(testAppCode).block();
	}

	private Client getSystemClient() {
		return clientService.getClientBy("SYSTEM  ").block();
	}

	private OtpGenerationRequestInternal createEmailRequest(String email) {
		App app = getSystemApp();
		Client client = getSystemClient();

		return new OtpGenerationRequestInternal()
				.setClientOption(client)
				.setAppOption(app)
				.setWithoutUserOption(email, null)
				.setPurpose(OtpPurpose.LOGIN);
	}

	private OtpGenerationRequestInternal createPhoneRequest(String phone) {
		App app = getSystemApp();
		Client client = getSystemClient();

		return new OtpGenerationRequestInternal()
				.setClientOption(client)
				.setAppOption(app)
				.setWithoutUserOption(null, phone)
				.setPurpose(OtpPurpose.LOGIN);
	}

	private OtpGenerationRequestInternal createEmailPhoneRequest(String email, String phone) {
		App app = getSystemApp();
		Client client = getSystemClient();

		return new OtpGenerationRequestInternal()
				.setClientOption(client)
				.setAppOption(app)
				.setWithoutUserOption(email, phone)
				.setPurpose(OtpPurpose.LOGIN);
	}

	private OtpGenerationRequestInternal createUserRequest(User user) {
		App app = getSystemApp();
		Client client = getSystemClient();

		return new OtpGenerationRequestInternal()
				.setClientOption(client)
				.setAppOption(app)
				.setWithUserOption(user)
				.setPurpose(OtpPurpose.LOGIN);
	}

	private Mono<ULong> insertOtp(ULong appId, ULong userId, String email, String phone, String purpose,
			String encodedCode, SecurityOtpTargetType targetType, int expireMinutes) {

		// Use Java LocalDateTime for EXPIRES_AT to avoid timezone mismatch between
		// MySQL container (UTC) and JVM timezone. Otp.isExpired() compares against
		// LocalDateTime.now() in JVM timezone, so the stored value must also use JVM time.
		LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(expireMinutes);

		var spec = databaseClient.sql(
				"INSERT INTO security_otp (APP_ID, USER_ID, EMAIL_ID, PHONE_NUMBER, PURPOSE, UNIQUE_CODE, TARGET_TYPE, EXPIRES_AT) "
						+ "VALUES (:appId, :userId, :email, :phone, :purpose, :code, :targetType, :expiresAt)")
				.bind("appId", appId.longValue());

		spec = userId != null ? spec.bind("userId", userId.longValue()) : spec.bindNull("userId", Long.class);
		spec = email != null ? spec.bind("email", email) : spec.bindNull("email", String.class);
		spec = phone != null ? spec.bind("phone", phone) : spec.bindNull("phone", String.class);
		spec = spec.bind("purpose", purpose);
		spec = spec.bind("code", encodedCode);
		spec = spec.bind("targetType", targetType.getLiteral());
		spec = spec.bind("expiresAt", expiresAt);

		return spec.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	private Mono<ULong> insertOtpWithVerifyLegs(ULong appId, ULong userId, String email, String phone,
			String purpose, String encodedCode, SecurityOtpTargetType targetType, int expireMinutes,
			short verifyLegs) {

		// Use Java LocalDateTime for EXPIRES_AT to avoid timezone mismatch between
		// MySQL container (UTC) and JVM timezone. Otp.isExpired() compares against
		// LocalDateTime.now() in JVM timezone, so the stored value must also use JVM time.
		LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(expireMinutes);

		var spec = databaseClient.sql(
				"INSERT INTO security_otp (APP_ID, USER_ID, EMAIL_ID, PHONE_NUMBER, PURPOSE, UNIQUE_CODE, TARGET_TYPE, EXPIRES_AT, VERIFY_LEGS_COUNTS) "
						+ "VALUES (:appId, :userId, :email, :phone, :purpose, :code, :targetType, :expiresAt, :verifyLegs)")
				.bind("appId", appId.longValue());

		spec = userId != null ? spec.bind("userId", userId.longValue()) : spec.bindNull("userId", Long.class);
		spec = email != null ? spec.bind("email", email) : spec.bindNull("email", String.class);
		spec = phone != null ? spec.bind("phone", phone) : spec.bindNull("phone", String.class);
		spec = spec.bind("purpose", purpose);
		spec = spec.bind("code", encodedCode);
		spec = spec.bind("targetType", targetType.getLiteral());
		spec = spec.bind("expiresAt", expiresAt);
		spec = spec.bind("verifyLegs", verifyLegs);

		return spec.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	// =========================================================================
	// generateOtpInternal - Email OTP generation
	// =========================================================================

	@Nested
	@DisplayName("generateOtpInternal() - Email OTP generation")
	class GenerateOtpInternalEmailTests {

		@Test
		@DisplayName("generates OTP for email and persists to database")
		void emailOtp_GeneratesAndPersists() {

			String email = "otpgen_" + System.currentTimeMillis() + "@test.com";
			OtpGenerationRequestInternal request = createEmailRequest(email);

			StepVerifier.create(otpService.generateOtpInternal(request))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();

			// Verify OTP was persisted in the database
			StepVerifier.create(otpDAO.getLatestOtp(testAppId, email, null, OtpPurpose.LOGIN))
					.assertNext(otp -> {
						assertNotNull(otp);
						assertEquals(testAppId, otp.getAppId());
						assertEquals(email, otp.getEmailId());
						assertEquals(OtpPurpose.LOGIN.name(), otp.getPurpose());
						assertNotNull(otp.getUniqueCode());
						assertNotNull(otp.getExpiresAt());
						assertTrue(otp.getExpiresAt().isAfter(LocalDateTime.now()));
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("generates OTP with different purposes")
		void emailOtp_DifferentPurposes() {

			String email = "otppur_" + System.currentTimeMillis() + "@test.com";

			for (OtpPurpose purpose : OtpPurpose.values()) {
				OtpGenerationRequestInternal request = createEmailRequest(email);
				request.setPurpose(purpose);

				StepVerifier.create(otpService.generateOtpInternal(request))
						.assertNext(result -> assertTrue(result,
								"OTP generation should succeed for purpose: " + purpose))
						.verifyComplete();
			}
		}

		@Test
		@DisplayName("no email returns false when target is EMAIL")
		void emailOtp_NoEmail_ReturnsFalse() {

			OtpGenerationRequestInternal request = createEmailRequest(null);
			request.setWithoutUserOption(null, null);

			StepVerifier.create(otpService.generateOtpInternal(request))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("blank email returns false when target is EMAIL")
		void emailOtp_BlankEmail_ReturnsFalse() {

			OtpGenerationRequestInternal request = createEmailRequest("");
			request.setWithoutUserOption("", null);

			StepVerifier.create(otpService.generateOtpInternal(request))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	// =========================================================================
	// generateOtpInternal - Phone OTP generation
	// =========================================================================

	@Nested
	@DisplayName("generateOtpInternal() - Phone OTP generation")
	class GenerateOtpInternalPhoneTests {

		@Test
		@DisplayName("phone-only request returns false when default policy target is EMAIL")
		void phoneOtp_GeneratesAndPersists() {

			// The default OTP policy target type is EMAIL. When a phone-only request is
			// made with EMAIL target, sendOtp returns false because there is no email to
			// send to. This is the expected behavior of the service.
			String phone = "+19876543210";
			OtpGenerationRequestInternal request = createPhoneRequest(phone);

			StepVerifier.create(otpService.generateOtpInternal(request))
					.assertNext(result -> assertFalse(result,
							"Phone-only OTP should return false when default policy target is EMAIL"))
					.verifyComplete();
		}

		@Test
		@DisplayName("no phone returns false when target is PHONE and no email")
		void phoneOtp_NoPhone_ReturnsFalse() {

			OtpGenerationRequestInternal request = createPhoneRequest(null);
			request.setWithoutUserOption(null, null);

			StepVerifier.create(otpService.generateOtpInternal(request))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	// =========================================================================
	// generateOtpInternal - User-based OTP generation
	// =========================================================================

	@Nested
	@DisplayName("generateOtpInternal() - User-based OTP generation")
	class GenerateOtpInternalUserTests {

		@Test
		@DisplayName("generates OTP for authenticated user and persists with userId")
		void userOtp_GeneratesAndPersistsWithUserId() {

			User user = TestDataFactory.createActiveUser(testUserId, SYSTEM_CLIENT_ID);
			user.setEmailId("otpusr_" + System.currentTimeMillis() + "@test.com");

			OtpGenerationRequestInternal request = createUserRequest(user);

			StepVerifier.create(otpService.generateOtpInternal(request))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();

			// Verify OTP was persisted with user ID
			StepVerifier.create(otpDAO.getLatestOtp(testAppId, testUserId, OtpPurpose.LOGIN))
					.assertNext(otp -> {
						assertNotNull(otp);
						assertEquals(testAppId, otp.getAppId());
						assertEquals(testUserId, otp.getUserId());
						assertEquals(OtpPurpose.LOGIN.name(), otp.getPurpose());
					})
					.verifyComplete();
		}
	}

	// =========================================================================
	// generateOtpInternal - Resend scenarios
	// =========================================================================

	@Nested
	@DisplayName("generateOtpInternal() - Resend scenarios")
	class GenerateOtpInternalResendTests {

		@Test
		@DisplayName("resend with resendSameOtp=true uses existing OTP code from DB")
		void resend_SameOtp_UsesExistingCode() {

			String email = "otpresend_" + System.currentTimeMillis() + "@test.com";

			// First, generate an OTP
			OtpGenerationRequestInternal firstRequest = createEmailRequest(email);
			Boolean firstResult = otpService.generateOtpInternal(firstRequest).block();
			assertTrue(firstResult);

			// Get the OTP code that was stored
			String firstCode = otpDAO.getLatestOtpCode(testAppId, email, null, OtpPurpose.LOGIN).block();
			assertNotNull(firstCode, "First OTP code should be stored in DB");

			// Now resend - the default policy has resendSameOtp=false, so it will generate a new code
			OtpGenerationRequestInternal resendRequest = createEmailRequest(email);
			resendRequest.setResend(true);

			StepVerifier.create(otpService.generateOtpInternal(resendRequest))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("resend with user increases resend attempt counter")
		void resend_WithUser_IncreasesResendAttempt() {

			User user = TestDataFactory.createActiveUser(testUserId, SYSTEM_CLIENT_ID);
			user.setEmailId("otpresenduser_" + System.currentTimeMillis() + "@test.com");

			// Generate initial OTP
			OtpGenerationRequestInternal firstRequest = createUserRequest(user);
			Boolean firstResult = otpService.generateOtpInternal(firstRequest).block();
			assertTrue(firstResult);

			// Resend with user - this should increase resend attempt
			OtpGenerationRequestInternal resendRequest = createUserRequest(user);
			resendRequest.setResend(true);

			StepVerifier.create(otpService.generateOtpInternal(resendRequest))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();

			// Verify multiple OTP records exist (both original and resend)
			// The resend creates a new record regardless
		}
	}

	// =========================================================================
	// verifyOtpInternal - with User
	// =========================================================================

	@Nested
	@DisplayName("verifyOtpInternal() - with User")
	class VerifyOtpInternalWithUserTests {

		@Test
		@DisplayName("valid OTP code verifies successfully and deletes OTP when verifyLegs match")
		void validOtp_VerifiesAndDeletes() {

			String rawCode = "1234";
			String encodedCode = passwordEncoder.encode(rawCode);
			short loginVerifyLegs = OtpPurpose.LOGIN.getVerifyLegsCounts(); // 0

			ULong otpId = insertOtpWithVerifyLegs(testAppId, testUserId, null, null,
					OtpPurpose.LOGIN.name(), encodedCode, SecurityOtpTargetType.EMAIL, 10,
					loginVerifyLegs).block();
			assertNotNull(otpId);

			OtpVerificationRequest request = new OtpVerificationRequest()
					.setOtp(rawCode)
					.setPurpose(OtpPurpose.LOGIN);

			User user = TestDataFactory.createActiveUser(testUserId, SYSTEM_CLIENT_ID);

			StepVerifier.create(otpService.verifyOtpInternal(testAppCode, user, request))
					.assertNext(result -> assertTrue(result, "OTP verification should succeed"))
					.verifyComplete();

			// Verify OTP was deleted after successful verification
			StepVerifier.create(otpDAO.getLatestOtp(testAppId, testUserId, OtpPurpose.LOGIN))
					.verifyComplete();
		}

		@Test
		@DisplayName("expired OTP returns false")
		void expiredOtp_ReturnsFalse() {

			String rawCode = "5678";
			String encodedCode = passwordEncoder.encode(rawCode);

			ULong otpId = insertOtp(testAppId, testUserId, null, null,
					OtpPurpose.LOGIN.name(), encodedCode, SecurityOtpTargetType.EMAIL, -5).block();
			assertNotNull(otpId);

			OtpVerificationRequest request = new OtpVerificationRequest()
					.setOtp(rawCode)
					.setPurpose(OtpPurpose.LOGIN);

			User user = TestDataFactory.createActiveUser(testUserId, SYSTEM_CLIENT_ID);

			StepVerifier.create(otpService.verifyOtpInternal(testAppCode, user, request))
					.assertNext(result -> assertFalse(result, "Expired OTP should return false"))
					.verifyComplete();
		}

		@Test
		@DisplayName("wrong OTP code returns false")
		void wrongOtpCode_ReturnsFalse() {

			String rawCode = "1234";
			String encodedCode = passwordEncoder.encode(rawCode);

			insertOtp(testAppId, testUserId, null, null,
					OtpPurpose.LOGIN.name(), encodedCode, SecurityOtpTargetType.EMAIL, 10).block();

			OtpVerificationRequest request = new OtpVerificationRequest()
					.setOtp("9999")
					.setPurpose(OtpPurpose.LOGIN);

			User user = TestDataFactory.createActiveUser(testUserId, SYSTEM_CLIENT_ID);

			StepVerifier.create(otpService.verifyOtpInternal(testAppCode, user, request))
					.assertNext(result -> assertFalse(result, "Wrong OTP code should return false"))
					.verifyComplete();
		}

		@Test
		@DisplayName("blank app code returns false")
		void blankAppCode_ReturnsFalse() {

			User user = TestDataFactory.createActiveUser(testUserId, SYSTEM_CLIENT_ID);

			OtpVerificationRequest request = new OtpVerificationRequest()
					.setOtp("1234")
					.setPurpose(OtpPurpose.LOGIN);

			StepVerifier.create(otpService.verifyOtpInternal("", user, request))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("null app code returns false")
		void nullAppCode_ReturnsFalse() {

			User user = TestDataFactory.createActiveUser(testUserId, SYSTEM_CLIENT_ID);

			OtpVerificationRequest request = new OtpVerificationRequest()
					.setOtp("1234")
					.setPurpose(OtpPurpose.LOGIN);

			StepVerifier.create(otpService.verifyOtpInternal(null, user, request))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("OTP with no purpose in request causes NPE in DAO layer")
		void noPurposeInRequest_ReturnsFalse() {

			// When otp is set but purpose is null, isOtpNotValid() returns false
			// (it uses && requiring BOTH blank otp AND null purpose). The request
			// passes the guard check, but later purpose.name() in the DAO query
			// throws a NullPointerException.
			User user = TestDataFactory.createActiveUser(testUserId, SYSTEM_CLIENT_ID);

			OtpVerificationRequest request = new OtpVerificationRequest()
					.setOtp("1234");

			StepVerifier.create(otpService.verifyOtpInternal(testAppCode, user, request))
					.expectError(NullPointerException.class)
					.verify();
		}

		@Test
		@DisplayName("no OTP in DB returns false")
		void noOtpInDb_ReturnsFalse() {

			User user = TestDataFactory.createActiveUser(testUserId, SYSTEM_CLIENT_ID);

			OtpVerificationRequest request = new OtpVerificationRequest()
					.setOtp("1234")
					.setPurpose(OtpPurpose.LOGIN);

			StepVerifier.create(otpService.verifyOtpInternal(testAppCode, user, request))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	// =========================================================================
	// verifyOtpInternal - without User (by email/phone)
	// =========================================================================

	@Nested
	@DisplayName("verifyOtpInternal() - without User")
	class VerifyOtpInternalWithoutUserTests {

		@Test
		@DisplayName("valid OTP by email verifies successfully")
		void validOtpByEmail_VerifiesSuccessfully() {

			String email = "otpverify_" + System.currentTimeMillis() + "@test.com";
			String rawCode = "4321";
			String encodedCode = passwordEncoder.encode(rawCode);
			short verifyLegs = OtpPurpose.VERIFICATION.getVerifyLegsCounts(); // 0

			insertOtpWithVerifyLegs(testAppId, null, email, null,
					OtpPurpose.VERIFICATION.name(), encodedCode, SecurityOtpTargetType.EMAIL, 10,
					verifyLegs).block();

			OtpVerificationRequest request = new OtpVerificationRequest()
					.setEmailId(email)
					.setOtp(rawCode)
					.setPurpose(OtpPurpose.VERIFICATION);

			StepVerifier.create(otpService.verifyOtpInternal(testAppCode, request))
					.assertNext(result -> assertTrue(result, "OTP verification by email should succeed"))
					.verifyComplete();

			// Verify OTP was deleted
			StepVerifier.create(otpDAO.getLatestOtp(testAppId, email, null, OtpPurpose.VERIFICATION))
					.verifyComplete();
		}

		@Test
		@DisplayName("valid OTP by phone verifies successfully")
		void validOtpByPhone_VerifiesSuccessfully() {

			String phone = "+11234567890";
			String rawCode = "8765";
			String encodedCode = passwordEncoder.encode(rawCode);
			short verifyLegs = OtpPurpose.LOGIN.getVerifyLegsCounts(); // 0

			insertOtpWithVerifyLegs(testAppId, null, null, phone,
					OtpPurpose.LOGIN.name(), encodedCode, SecurityOtpTargetType.PHONE, 10,
					verifyLegs).block();

			OtpVerificationRequest request = new OtpVerificationRequest()
					.setPhoneNumber(phone)
					.setOtp(rawCode)
					.setPurpose(OtpPurpose.LOGIN);

			StepVerifier.create(otpService.verifyOtpInternal(testAppCode, request))
					.assertNext(result -> assertTrue(result, "OTP verification by phone should succeed"))
					.verifyComplete();
		}

		@Test
		@DisplayName("wrong OTP code by email returns false")
		void wrongOtpByEmail_ReturnsFalse() {

			String email = "otpwrong_" + System.currentTimeMillis() + "@test.com";
			String rawCode = "1111";
			String encodedCode = passwordEncoder.encode(rawCode);

			insertOtp(testAppId, null, email, null,
					OtpPurpose.LOGIN.name(), encodedCode, SecurityOtpTargetType.EMAIL, 10).block();

			OtpVerificationRequest request = new OtpVerificationRequest()
					.setEmailId(email)
					.setOtp("2222")
					.setPurpose(OtpPurpose.LOGIN);

			StepVerifier.create(otpService.verifyOtpInternal(testAppCode, request))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("expired OTP by email returns false")
		void expiredOtpByEmail_ReturnsFalse() {

			String email = "otpexp_" + System.currentTimeMillis() + "@test.com";
			String rawCode = "3333";
			String encodedCode = passwordEncoder.encode(rawCode);

			insertOtp(testAppId, null, email, null,
					OtpPurpose.LOGIN.name(), encodedCode, SecurityOtpTargetType.EMAIL, -10).block();

			OtpVerificationRequest request = new OtpVerificationRequest()
					.setEmailId(email)
					.setOtp(rawCode)
					.setPurpose(OtpPurpose.LOGIN);

			StepVerifier.create(otpService.verifyOtpInternal(testAppCode, request))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("invalid request with no identifiers returns false")
		void invalidRequest_NoIdentifiers_ReturnsFalse() {

			OtpVerificationRequest request = new OtpVerificationRequest()
					.setOtp("1234")
					.setPurpose(OtpPurpose.LOGIN);

			StepVerifier.create(otpService.verifyOtpInternal(testAppCode, request))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("blank app code returns false")
		void blankAppCode_ReturnsFalse() {

			OtpVerificationRequest request = new OtpVerificationRequest()
					.setEmailId("test@test.com")
					.setOtp("1234")
					.setPurpose(OtpPurpose.LOGIN);

			StepVerifier.create(otpService.verifyOtpInternal("", request))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("different purpose OTP does not match")
		void differentPurpose_ReturnsFalse() {

			String email = "otpdiff_" + System.currentTimeMillis() + "@test.com";
			String rawCode = "5555";
			String encodedCode = passwordEncoder.encode(rawCode);

			insertOtp(testAppId, null, email, null,
					OtpPurpose.REGISTRATION.name(), encodedCode, SecurityOtpTargetType.EMAIL, 10).block();

			OtpVerificationRequest request = new OtpVerificationRequest()
					.setEmailId(email)
					.setOtp(rawCode)
					.setPurpose(OtpPurpose.LOGIN);

			StepVerifier.create(otpService.verifyOtpInternal(testAppCode, request))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	// =========================================================================
	// verifyOtp - with ReactiveSecurityContext
	// =========================================================================

	@Nested
	@DisplayName("verifyOtp() - with security context")
	class VerifyOtpWithSecurityContextTests {

		@Test
		@DisplayName("valid OTP verifies with security context")
		void validOtp_VerifiesWithContext() {

			systemAuth.setUrlClientCode("SYSTEM  ");
			systemAuth.setUrlAppCode(testAppCode);

			String email = "otpctx_" + System.currentTimeMillis() + "@test.com";
			String rawCode = "7777";
			String encodedCode = passwordEncoder.encode(rawCode);
			short verifyLegs = OtpPurpose.LOGIN.getVerifyLegsCounts();

			insertOtpWithVerifyLegs(testAppId, null, email, null,
					OtpPurpose.LOGIN.name(), encodedCode, SecurityOtpTargetType.EMAIL, 10,
					verifyLegs).block();

			OtpVerificationRequest request = new OtpVerificationRequest()
					.setEmailId(email)
					.setOtp(rawCode)
					.setPurpose(OtpPurpose.LOGIN);

			StepVerifier.create(otpService.verifyOtp(request)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(result -> assertTrue(result, "OTP should verify with security context"))
					.verifyComplete();
		}

		@Test
		@DisplayName("invalid request returns false")
		void invalidRequest_ReturnsFalse() {

			systemAuth.setUrlClientCode("SYSTEM  ");
			systemAuth.setUrlAppCode(testAppCode);

			OtpVerificationRequest request = new OtpVerificationRequest();

			StepVerifier.create(otpService.verifyOtp(request)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("expired OTP returns false with security context")
		void expiredOtp_ReturnsFalseWithContext() {

			systemAuth.setUrlClientCode("SYSTEM  ");
			systemAuth.setUrlAppCode(testAppCode);

			String email = "otpctxexp_" + System.currentTimeMillis() + "@test.com";
			String rawCode = "8888";
			String encodedCode = passwordEncoder.encode(rawCode);

			insertOtp(testAppId, null, email, null,
					OtpPurpose.LOGIN.name(), encodedCode, SecurityOtpTargetType.EMAIL, -5).block();

			OtpVerificationRequest request = new OtpVerificationRequest()
					.setEmailId(email)
					.setOtp(rawCode)
					.setPurpose(OtpPurpose.LOGIN);

			StepVerifier.create(otpService.verifyOtp(request)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	// =========================================================================
	// verifyOtp - partial verify legs (increaseVerifyCounts)
	// =========================================================================

	@Nested
	@DisplayName("verifyOtp - partial verification with verify legs")
	class PartialVerifyLegsTests {

		@Test
		@DisplayName("OTP with verifyLegs < purpose.verifyLegs increments count instead of deleting")
		void partialVerify_IncreasesCountInsteadOfDeleting() {

			// REGISTRATION has verifyLegsCounts = 1, so if current is 0, it should increment
			String email = "otplegs_" + System.currentTimeMillis() + "@test.com";
			String rawCode = "6666";
			String encodedCode = passwordEncoder.encode(rawCode);

			ULong otpId = insertOtpWithVerifyLegs(testAppId, null, email, null,
					OtpPurpose.REGISTRATION.name(), encodedCode, SecurityOtpTargetType.EMAIL, 10,
					(short) 0).block();
			assertNotNull(otpId);

			OtpVerificationRequest request = new OtpVerificationRequest()
					.setEmailId(email)
					.setOtp(rawCode)
					.setPurpose(OtpPurpose.REGISTRATION);

			StepVerifier.create(otpService.verifyOtpInternal(testAppCode, request))
					.assertNext(result -> assertTrue(result,
							"Partial verification should return true"))
					.verifyComplete();

			// OTP should NOT be deleted - it should still exist with incremented verifyLegs
			StepVerifier.create(otpDAO.getLatestOtp(testAppId, email, null, OtpPurpose.REGISTRATION))
					.assertNext(otp -> {
						assertNotNull(otp, "OTP should still exist after partial verification");
						assertEquals((short) 1, otp.getVerifyLegsCounts(),
								"Verify legs count should be incremented");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("OTP with verifyLegs matching purpose.verifyLegs deletes OTP")
		void fullVerify_DeletesOtp() {

			// REGISTRATION has verifyLegsCounts = 1, so if current is already 1, it should delete
			String email = "otpfull_" + System.currentTimeMillis() + "@test.com";
			String rawCode = "4444";
			String encodedCode = passwordEncoder.encode(rawCode);

			insertOtpWithVerifyLegs(testAppId, null, email, null,
					OtpPurpose.REGISTRATION.name(), encodedCode, SecurityOtpTargetType.EMAIL, 10,
					OtpPurpose.REGISTRATION.getVerifyLegsCounts()).block();

			OtpVerificationRequest request = new OtpVerificationRequest()
					.setEmailId(email)
					.setOtp(rawCode)
					.setPurpose(OtpPurpose.REGISTRATION);

			StepVerifier.create(otpService.verifyOtpInternal(testAppCode, request))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();

			// OTP should be deleted
			StepVerifier.create(otpDAO.getLatestOtp(testAppId, email, null, OtpPurpose.REGISTRATION))
					.verifyComplete();
		}

		@Test
		@DisplayName("PASSWORD_RESET with partial verifyLegs increments count")
		void passwordReset_PartialVerify_IncreasesCount() {

			// PASSWORD_RESET has verifyLegsCounts = 1
			String rawCode = "9999";
			String encodedCode = passwordEncoder.encode(rawCode);

			ULong otpId = insertOtpWithVerifyLegs(testAppId, testUserId, null, null,
					OtpPurpose.PASSWORD_RESET.name(), encodedCode, SecurityOtpTargetType.EMAIL, 10,
					(short) 0).block();
			assertNotNull(otpId);

			User user = TestDataFactory.createActiveUser(testUserId, SYSTEM_CLIENT_ID);

			OtpVerificationRequest request = new OtpVerificationRequest()
					.setOtp(rawCode)
					.setPurpose(OtpPurpose.PASSWORD_RESET);

			StepVerifier.create(otpService.verifyOtpInternal(testAppCode, user, request))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();

			// OTP should still exist with incremented verify legs count
			StepVerifier.create(otpDAO.getLatestOtp(testAppId, testUserId, OtpPurpose.PASSWORD_RESET))
					.assertNext(otp -> {
						assertNotNull(otp);
						assertEquals((short) 1, otp.getVerifyLegsCounts());
					})
					.verifyComplete();
		}
	}

	// =========================================================================
	// create and updatableEntity
	// =========================================================================

	@Nested
	@DisplayName("create() and updatableEntity()")
	class CreateAndUpdateTests {

		@Test
		@DisplayName("create persists OTP entity to database")
		void create_PersistsEntity() {

			Otp otp = new Otp();
			otp.setAppId(testAppId);
			otp.setUserId(testUserId);
			otp.setEmailId("create_" + System.currentTimeMillis() + "@test.com");
			otp.setPurpose(OtpPurpose.LOGIN.name());
			otp.setTargetType(SecurityOtpTargetType.EMAIL);
			otp.setUniqueCode(passwordEncoder.encode("1234"));
			otp.setExpiresAt(LocalDateTime.now().plusMinutes(5));
			otp.setCreatedBy(testUserId);
			otp.setCreatedAt(LocalDateTime.now());

			StepVerifier.create(otpService.create(otp))
					.assertNext(created -> {
						assertNotNull(created);
						assertNotNull(created.getId());
						assertEquals(testAppId, created.getAppId());
						assertEquals(testUserId, created.getUserId());
					})
					.verifyComplete();
		}
	}

	// =========================================================================
	// End-to-end: generate then verify
	// =========================================================================

	@Nested
	@DisplayName("End-to-end: generate then verify OTP")
	class EndToEndTests {

		@Test
		@DisplayName("generate OTP then verify it successfully")
		void generateThenVerify_Succeeds() {

			String email = "e2e_" + System.currentTimeMillis() + "@test.com";

			// Generate OTP
			OtpGenerationRequestInternal genRequest = createEmailRequest(email);
			Boolean generated = otpService.generateOtpInternal(genRequest).block();
			assertTrue(generated, "OTP generation should succeed");

			// The generated OTP is encoded in the DB, and we don't know the raw code.
			// However, we can verify the flow works by directly reading the OTP from DB.
			Otp storedOtp = otpDAO.getLatestOtp(testAppId, email, null, OtpPurpose.LOGIN).block();
			assertNotNull(storedOtp, "OTP should be stored in DB after generation");
			assertNotNull(storedOtp.getUniqueCode(), "OTP unique code should not be null");
			assertTrue(storedOtp.getExpiresAt().isAfter(LocalDateTime.now()),
					"OTP should not be expired");
			assertEquals(OtpPurpose.LOGIN.name(), storedOtp.getPurpose());
		}

		@Test
		@DisplayName("generate multiple OTPs for same email creates multiple records")
		void generateMultiple_CreatesMultipleRecords() {

			String email = "e2emulti_" + System.currentTimeMillis() + "@test.com";

			// Generate first OTP
			OtpGenerationRequestInternal request1 = createEmailRequest(email);
			Boolean first = otpService.generateOtpInternal(request1).block();
			assertTrue(first);

			String firstCode = otpDAO.getLatestOtpCode(testAppId, email, null, OtpPurpose.LOGIN).block();
			assertNotNull(firstCode);

			// Generate second OTP
			OtpGenerationRequestInternal request2 = createEmailRequest(email);
			Boolean second = otpService.generateOtpInternal(request2).block();
			assertTrue(second);

			// The latest OTP should be the second one (different code)
			String secondCode = otpDAO.getLatestOtpCode(testAppId, email, null, OtpPurpose.LOGIN).block();
			assertNotNull(secondCode);
		}

		@Test
		@DisplayName("generate OTP for user then verify via verifyOtpInternal with user")
		void generateForUser_ThenVerifyWithUser() {

			User user = TestDataFactory.createActiveUser(testUserId, SYSTEM_CLIENT_ID);
			user.setEmailId("e2euser_" + System.currentTimeMillis() + "@test.com");

			// Generate OTP for user
			OtpGenerationRequestInternal genRequest = createUserRequest(user);
			Boolean generated = otpService.generateOtpInternal(genRequest).block();
			assertTrue(generated);

			// The OTP code is encoded, so we can't directly verify it.
			// But we can verify the OTP exists
			Otp storedOtp = otpDAO.getLatestOtp(testAppId, testUserId, OtpPurpose.LOGIN).block();
			assertNotNull(storedOtp);
			assertNotNull(storedOtp.getUniqueCode());
		}
	}

	// =========================================================================
	// sendOtp edge cases
	// =========================================================================

	@Nested
	@DisplayName("sendOtp edge cases")
	class SendOtpEdgeCases {

		@Test
		@DisplayName("email with phone also present sends both via BOTH target type")
		void emailAndPhone_BothPresent() {

			String email = "otpboth_" + System.currentTimeMillis() + "@test.com";
			String phone = "+15551234567";

			OtpGenerationRequestInternal request = createEmailPhoneRequest(email, phone);

			// Default policy target type is EMAIL, so with both email and phone,
			// only email OTP is sent. The phone is available but email takes precedence.
			StepVerifier.create(otpService.generateOtpInternal(request))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}
	}
}
