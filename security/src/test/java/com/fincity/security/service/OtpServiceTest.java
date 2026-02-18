package com.fincity.security.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.security.dao.OtpDAO;
import com.fincity.security.dto.App;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.Otp;
import com.fincity.security.dto.User;
import com.fincity.security.dto.policy.ClientOtpPolicy;
import com.fincity.security.enums.otp.OtpPurpose;
import com.fincity.security.jooq.enums.SecurityClientOtpPolicyTargetType;
import com.fincity.security.model.otp.OtpGenerationRequestInternal;
import com.fincity.security.model.otp.OtpVerificationRequest;
import com.fincity.security.service.message.MessageService;
import com.fincity.security.service.policy.ClientOtpPolicyService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest extends AbstractServiceUnitTest {

	@Mock
	private OtpDAO dao;

	@Mock
	private ClientService clientService;

	@Mock
	private AppService appService;

	@Mock
	private EventCreationService ecService;

	@Mock
	private MessageService messageService;

	@Mock
	private ClientOtpPolicyService clientOtpPolicyService;

	@Mock
	private PasswordEncoder encoder;

	@Mock
	private UserService userService;

	@InjectMocks
	private OtpService service;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);
	private static final ULong CLIENT_ID = ULong.valueOf(2);
	private static final ULong APP_ID = ULong.valueOf(100);
	private static final ULong USER_ID = ULong.valueOf(10);
	private static final ULong OTP_ID = ULong.valueOf(50);

	@BeforeEach
	void setUp() throws Exception {
		Field daoField = org.springframework.util.ReflectionUtils.findField(service.getClass(), "dao");
		daoField.setAccessible(true);
		daoField.set(service, dao);

		// Inject userService via reflection (lazy setter)
		Field userServiceField = service.getClass().getDeclaredField("userService");
		userServiceField.setAccessible(true);
		userServiceField.set(service, userService);
	}

	// =========================================================================
	// create
	// =========================================================================

	@Nested
	@DisplayName("create")
	class CreateTests {

		@Test
		void create_DelegatesToDao() {

			Otp otp = TestDataFactory.createOtp(OTP_ID, USER_ID);
			when(dao.create(any(Otp.class))).thenReturn(Mono.just(otp));

			StepVerifier.create(service.create(otp))
					.assertNext(result -> {
						assertEquals(OTP_ID, result.getId());
						assertEquals(USER_ID, result.getUserId());
					})
					.verifyComplete();

			verify(dao).create(otp);
		}
	}

	// =========================================================================
	// generateOtpInternal
	// =========================================================================

	@Nested
	@DisplayName("generateOtpInternal")
	class GenerateOtpInternalTests {

		@Test
		void generateOtpInternal_ValidUser_SendsMessage() {

			App app = TestDataFactory.createOwnApp(APP_ID, CLIENT_ID, "testApp");

			ClientOtpPolicy policy = TestDataFactory.createOtpPolicy();
			policy.setTargetType(SecurityClientOtpPolicyTargetType.EMAIL);

			OtpGenerationRequestInternal request = new OtpGenerationRequestInternal()
					.setClientOption(TestDataFactory.createBusinessClient(CLIENT_ID, "TESTCLIENT"))
					.setAppOption(app)
					.setWithoutUserOption("test@test.com", null)
					.setPurpose(OtpPurpose.LOGIN);

			when(appService.getAppByCode("testApp")).thenReturn(Mono.just(app));
			when(clientOtpPolicyService.getClientAppPolicy(CLIENT_ID, APP_ID)).thenReturn(Mono.just(policy));
			when(ecService.createEvent(any(EventQueObject.class))).thenReturn(Mono.just(true));
			when(encoder.encode(anyString())).thenReturn("encodedOtp");
			when(dao.create(any(Otp.class))).thenReturn(Mono.just(TestDataFactory.createOtp(OTP_ID, null)));

			StepVerifier.create(service.generateOtpInternal(request))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();

			verify(ecService).createEvent(any(EventQueObject.class));
		}

		@Test
		void generateOtpInternal_NoEmail_ReturnsFalse() {

			App app = TestDataFactory.createOwnApp(APP_ID, CLIENT_ID, "testApp");

			ClientOtpPolicy policy = TestDataFactory.createOtpPolicy();
			policy.setTargetType(SecurityClientOtpPolicyTargetType.EMAIL);

			OtpGenerationRequestInternal request = new OtpGenerationRequestInternal()
					.setClientOption(TestDataFactory.createBusinessClient(CLIENT_ID, "TESTCLIENT"))
					.setAppOption(app)
					.setWithoutUserOption(null, null)
					.setPurpose(OtpPurpose.LOGIN);

			when(appService.getAppByCode("testApp")).thenReturn(Mono.just(app));
			when(clientOtpPolicyService.getClientAppPolicy(CLIENT_ID, APP_ID)).thenReturn(Mono.just(policy));

			StepVerifier.create(service.generateOtpInternal(request))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		void generateOtpInternal_PublishesEvent() {

			App app = TestDataFactory.createOwnApp(APP_ID, CLIENT_ID, "testApp");

			ClientOtpPolicy policy = TestDataFactory.createOtpPolicy();
			policy.setTargetType(SecurityClientOtpPolicyTargetType.EMAIL);

			OtpGenerationRequestInternal request = new OtpGenerationRequestInternal()
					.setClientOption(TestDataFactory.createBusinessClient(CLIENT_ID, "TESTCLIENT"))
					.setAppOption(app)
					.setWithoutUserOption("user@example.com", null)
					.setPurpose(OtpPurpose.REGISTRATION);

			when(appService.getAppByCode("testApp")).thenReturn(Mono.just(app));
			when(clientOtpPolicyService.getClientAppPolicy(CLIENT_ID, APP_ID)).thenReturn(Mono.just(policy));
			when(ecService.createEvent(any(EventQueObject.class))).thenReturn(Mono.just(true));
			when(encoder.encode(anyString())).thenReturn("encodedOtp");
			when(dao.create(any(Otp.class))).thenReturn(Mono.just(TestDataFactory.createOtp(OTP_ID, null)));

			StepVerifier.create(service.generateOtpInternal(request))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();

			verify(ecService).createEvent(argThat(event -> "USER_OTP_GENERATE".equals(event.getEventName())));
		}

		@Test
		void generateOtpInternal_ResendWithUser_IncreasesResendAttempt() {

			App app = TestDataFactory.createOwnApp(APP_ID, CLIENT_ID, "testApp");

			ClientOtpPolicy policy = TestDataFactory.createOtpPolicy();
			policy.setTargetType(SecurityClientOtpPolicyTargetType.EMAIL);
			policy.setResendSameOtp(true);

			User user = TestDataFactory.createActiveUser(USER_ID, CLIENT_ID);

			OtpGenerationRequestInternal request = new OtpGenerationRequestInternal()
					.setClientOption(TestDataFactory.createBusinessClient(CLIENT_ID, "TESTCLIENT"))
					.setAppOption(app)
					.setWithUserOption(user)
					.setResend(true)
					.setPurpose(OtpPurpose.LOGIN);

			when(appService.getAppByCode("testApp")).thenReturn(Mono.just(app));
			when(clientOtpPolicyService.getClientAppPolicy(CLIENT_ID, APP_ID)).thenReturn(Mono.just(policy));
			when(dao.getLatestOtpCode(APP_ID, USER_ID, OtpPurpose.LOGIN)).thenReturn(Mono.just("1234"));
			when(ecService.createEvent(any(EventQueObject.class))).thenReturn(Mono.just(true));
			when(encoder.encode("1234")).thenReturn("encoded1234");
			when(userService.increaseResendAttempt(USER_ID)).thenReturn(Mono.just((short) 1));
			when(dao.create(any(Otp.class))).thenReturn(Mono.just(TestDataFactory.createOtp(OTP_ID, USER_ID)));

			StepVerifier.create(service.generateOtpInternal(request))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();

			verify(userService).increaseResendAttempt(USER_ID);
		}
	}

	// =========================================================================
	// verifyOtp
	// =========================================================================

	@Nested
	@DisplayName("verifyOtp")
	class VerifyOtpTests {

		@Test
		void verifyOtp_ValidOtp_ReturnsTrue() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			ca.setUrlClientCode("SYSTEM");
			ca.setUrlAppCode("testApp");
			setupSecurityContext(ca);

			Client client = TestDataFactory.createSystemClient();
			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testApp");

			OtpVerificationRequest request = new OtpVerificationRequest()
					.setEmailId("test@test.com")
					.setPurpose(OtpPurpose.LOGIN)
					.setOtp("1234");

			Otp latestOtp = TestDataFactory.createOtp(OTP_ID, USER_ID);
			latestOtp.setUniqueCode("encoded1234");
			latestOtp.setExpiresAt(LocalDateTime.now().plusMinutes(5));
			latestOtp.setVerifyLegsCounts(OtpPurpose.LOGIN.getVerifyLegsCounts());

			when(clientService.getClientBy("SYSTEM")).thenReturn(Mono.just(client));
			when(appService.getAppByCode("testApp")).thenReturn(Mono.just(app));
			when(appService.appInheritance("testApp", "SYSTEM", "SYSTEM"))
					.thenReturn(Mono.just(java.util.List.of("SYSTEM")));
			when(dao.getLatestOtp(APP_ID, "test@test.com", null, OtpPurpose.LOGIN))
					.thenReturn(Mono.just(latestOtp));
			when(encoder.matches("1234", "encoded1234")).thenReturn(true);
			when(dao.delete(OTP_ID)).thenReturn(Mono.just(1));

			StepVerifier.create(service.verifyOtp(request))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void verifyOtp_InvalidOtp_ReturnsFalse() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			ca.setUrlClientCode("SYSTEM");
			ca.setUrlAppCode("testApp");
			setupSecurityContext(ca);

			Client client = TestDataFactory.createSystemClient();
			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testApp");

			OtpVerificationRequest request = new OtpVerificationRequest()
					.setEmailId("test@test.com")
					.setPurpose(OtpPurpose.LOGIN)
					.setOtp("wrong");

			Otp latestOtp = TestDataFactory.createOtp(OTP_ID, USER_ID);
			latestOtp.setUniqueCode("encoded1234");
			latestOtp.setExpiresAt(LocalDateTime.now().plusMinutes(5));

			when(clientService.getClientBy("SYSTEM")).thenReturn(Mono.just(client));
			when(appService.getAppByCode("testApp")).thenReturn(Mono.just(app));
			when(appService.appInheritance("testApp", "SYSTEM", "SYSTEM"))
					.thenReturn(Mono.just(java.util.List.of("SYSTEM")));
			when(dao.getLatestOtp(APP_ID, "test@test.com", null, OtpPurpose.LOGIN))
					.thenReturn(Mono.just(latestOtp));
			when(encoder.matches("wrong", "encoded1234")).thenReturn(false);

			StepVerifier.create(service.verifyOtp(request))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		void verifyOtp_ExpiredOtp_ReturnsFalse() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			ca.setUrlClientCode("SYSTEM");
			ca.setUrlAppCode("testApp");
			setupSecurityContext(ca);

			Client client = TestDataFactory.createSystemClient();
			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testApp");

			OtpVerificationRequest request = new OtpVerificationRequest()
					.setEmailId("test@test.com")
					.setPurpose(OtpPurpose.LOGIN)
					.setOtp("1234");

			Otp latestOtp = TestDataFactory.createOtp(OTP_ID, USER_ID);
			latestOtp.setUniqueCode("encoded1234");
			latestOtp.setExpiresAt(LocalDateTime.now().minusMinutes(5));

			when(clientService.getClientBy("SYSTEM")).thenReturn(Mono.just(client));
			when(appService.getAppByCode("testApp")).thenReturn(Mono.just(app));
			when(appService.appInheritance("testApp", "SYSTEM", "SYSTEM"))
					.thenReturn(Mono.just(java.util.List.of("SYSTEM")));
			when(dao.getLatestOtp(APP_ID, "test@test.com", null, OtpPurpose.LOGIN))
					.thenReturn(Mono.just(latestOtp));

			StepVerifier.create(service.verifyOtp(request))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		void verifyOtp_NotValidRequest_ReturnsFalse() {

			// OtpVerificationRequest with no otp, no purpose, and no identifiers
			OtpVerificationRequest request = new OtpVerificationRequest();

			StepVerifier.create(service.verifyOtp(request))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	// =========================================================================
	// verifyOtpInternal (with User)
	// =========================================================================

	@Nested
	@DisplayName("verifyOtpInternal with User")
	class VerifyOtpInternalWithUserTests {

		@Test
		void verifyOtpInternal_WithUser_FindsByUserId() {

			App app = TestDataFactory.createOwnApp(APP_ID, CLIENT_ID, "testApp");
			User user = TestDataFactory.createActiveUser(USER_ID, CLIENT_ID);

			OtpVerificationRequest request = new OtpVerificationRequest()
					.setOtp("1234")
					.setPurpose(OtpPurpose.LOGIN);

			Otp latestOtp = TestDataFactory.createOtp(OTP_ID, USER_ID);
			latestOtp.setUniqueCode("encoded1234");
			latestOtp.setExpiresAt(LocalDateTime.now().plusMinutes(5));
			latestOtp.setVerifyLegsCounts(OtpPurpose.LOGIN.getVerifyLegsCounts());

			when(appService.getAppByCode("testApp")).thenReturn(Mono.just(app));
			when(dao.getLatestOtp(APP_ID, USER_ID, OtpPurpose.LOGIN)).thenReturn(Mono.just(latestOtp));
			when(encoder.matches("1234", "encoded1234")).thenReturn(true);
			when(dao.delete(OTP_ID)).thenReturn(Mono.just(1));

			StepVerifier.create(service.verifyOtpInternal("testApp", user, request))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();

			verify(dao).getLatestOtp(APP_ID, USER_ID, OtpPurpose.LOGIN);
		}

		@Test
		void verifyOtpInternal_WithUser_WrongOtp_ReturnsFalse() {

			App app = TestDataFactory.createOwnApp(APP_ID, CLIENT_ID, "testApp");
			User user = TestDataFactory.createActiveUser(USER_ID, CLIENT_ID);

			OtpVerificationRequest request = new OtpVerificationRequest()
					.setOtp("wrong")
					.setPurpose(OtpPurpose.LOGIN);

			Otp latestOtp = TestDataFactory.createOtp(OTP_ID, USER_ID);
			latestOtp.setUniqueCode("encoded1234");
			latestOtp.setExpiresAt(LocalDateTime.now().plusMinutes(5));

			when(appService.getAppByCode("testApp")).thenReturn(Mono.just(app));
			when(dao.getLatestOtp(APP_ID, USER_ID, OtpPurpose.LOGIN)).thenReturn(Mono.just(latestOtp));
			when(encoder.matches("wrong", "encoded1234")).thenReturn(false);

			StepVerifier.create(service.verifyOtpInternal("testApp", user, request))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		void verifyOtpInternal_WithUser_BlankAppCode_ReturnsFalse() {

			User user = TestDataFactory.createActiveUser(USER_ID, CLIENT_ID);

			OtpVerificationRequest request = new OtpVerificationRequest()
					.setOtp("1234")
					.setPurpose(OtpPurpose.LOGIN);

			StepVerifier.create(service.verifyOtpInternal("", user, request))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	// =========================================================================
	// verifyOtpInternal (without User)
	// =========================================================================

	@Nested
	@DisplayName("verifyOtpInternal without User")
	class VerifyOtpInternalWithoutUserTests {

		@Test
		void verifyOtpInternal_WithoutUser_FindsByEmail() {

			App app = TestDataFactory.createOwnApp(APP_ID, CLIENT_ID, "testApp");

			OtpVerificationRequest request = new OtpVerificationRequest()
					.setEmailId("test@test.com")
					.setOtp("1234")
					.setPurpose(OtpPurpose.VERIFICATION);

			Otp latestOtp = TestDataFactory.createOtp(OTP_ID, null);
			latestOtp.setUniqueCode("encoded1234");
			latestOtp.setExpiresAt(LocalDateTime.now().plusMinutes(5));
			latestOtp.setVerifyLegsCounts(OtpPurpose.VERIFICATION.getVerifyLegsCounts());

			when(appService.getAppByCode("testApp")).thenReturn(Mono.just(app));
			when(dao.getLatestOtp(APP_ID, "test@test.com", null, OtpPurpose.VERIFICATION))
					.thenReturn(Mono.just(latestOtp));
			when(encoder.matches("1234", "encoded1234")).thenReturn(true);
			when(dao.delete(OTP_ID)).thenReturn(Mono.just(1));

			StepVerifier.create(service.verifyOtpInternal("testApp", request))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();

			verify(dao).getLatestOtp(APP_ID, "test@test.com", null, OtpPurpose.VERIFICATION);
		}

		@Test
		void verifyOtpInternal_WithoutUser_WrongOtp_ReturnsFalse() {

			App app = TestDataFactory.createOwnApp(APP_ID, CLIENT_ID, "testApp");

			OtpVerificationRequest request = new OtpVerificationRequest()
					.setEmailId("test@test.com")
					.setOtp("wrong")
					.setPurpose(OtpPurpose.VERIFICATION);

			Otp latestOtp = TestDataFactory.createOtp(OTP_ID, null);
			latestOtp.setUniqueCode("encoded1234");
			latestOtp.setExpiresAt(LocalDateTime.now().plusMinutes(5));

			when(appService.getAppByCode("testApp")).thenReturn(Mono.just(app));
			when(dao.getLatestOtp(APP_ID, "test@test.com", null, OtpPurpose.VERIFICATION))
					.thenReturn(Mono.just(latestOtp));
			when(encoder.matches("wrong", "encoded1234")).thenReturn(false);

			StepVerifier.create(service.verifyOtpInternal("testApp", request))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		void verifyOtpInternal_WithoutUser_InvalidRequest_ReturnsFalse() {

			// No email, no phone, no otp
			OtpVerificationRequest request = new OtpVerificationRequest();

			StepVerifier.create(service.verifyOtpInternal("testApp", request))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}
}
