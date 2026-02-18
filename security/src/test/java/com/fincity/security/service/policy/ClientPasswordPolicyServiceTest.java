package com.fincity.security.service.policy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dao.policy.ClientPasswordPolicyDAO;
import com.fincity.security.dto.App;
import com.fincity.security.dto.PastPassword;
import com.fincity.security.dto.policy.ClientPasswordPolicy;
import com.fincity.security.model.AuthenticationPasswordType;
import com.fincity.security.service.AbstractServiceUnitTest;
import com.fincity.security.service.AppService;
import com.fincity.security.service.ClientHierarchyService;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.SecurityMessageResourceService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ClientPasswordPolicyServiceTest extends AbstractServiceUnitTest {

	@Mock
	private ClientPasswordPolicyDAO dao;

	@Mock
	private SecurityMessageResourceService messageResourceService;

	@Mock
	private CacheService cacheService;

	@Mock
	private PasswordEncoder encoder;

	@Mock
	private ClientService clientService;

	@Mock
	private ClientHierarchyService clientHierarchyService;

	@Mock
	private AppService appService;

	private ClientPasswordPolicyService service;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);
	private static final ULong BUS_CLIENT_ID = ULong.valueOf(2);
	private static final ULong APP_ID = ULong.valueOf(100);
	private static final ULong USER_ID = ULong.valueOf(10);
	private static final ULong POLICY_ID = ULong.valueOf(50);

	@BeforeEach
	void setUp() {
		service = new ClientPasswordPolicyService(messageResourceService, cacheService, encoder);

		// Inject the DAO using reflection.
		// ClientPasswordPolicyService -> AbstractPolicyService -> AbstractJOOQUpdatableDataService ->
		// AbstractJOOQDataService (has 'dao' field)
		try {
			var daoField = service.getClass().getSuperclass().getSuperclass().getSuperclass().getDeclaredField("dao");
			daoField.setAccessible(true);
			daoField.set(service, dao);
		} catch (Exception e) {
			throw new RuntimeException("Failed to inject DAO", e);
		}

		// Inject services via setters (AbstractPolicyService uses @Autowired setter injection)
		service.setClientService(clientService);
		service.setClientHierarchyService(clientHierarchyService);
		service.setAppService(appService);

		setupMessageResourceService(messageResourceService);
		setupCacheService(cacheService);
		setupCacheEmptyValueOrGet(cacheService);
	}

	private void setupCacheEmptyValueOrGet(CacheService cs) {
		lenient().when(cs.cacheEmptyValueOrGet(anyString(), any(), any()))
				.thenAnswer(invocation -> {
					java.util.function.Supplier<Mono<?>> supplier = invocation.getArgument(1);
					return supplier.get();
				});
	}

	// ===== checkAllConditions tests =====

	@Nested
	class CheckAllConditions {

		@Test
		void allValid_ReturnsTrue() {
			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			String validPassword = "Test@12345678";

			when(dao.getPastPasswordsBasedOnPolicy(any(ClientPasswordPolicy.class), eq(USER_ID)))
					.thenReturn(Flux.empty());

			StepVerifier.create(service.checkAllConditions(policy, USER_ID, validPassword))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void missingUppercase_WhenAllCharsAreUppercase_ThrowsBadRequest() {
			// The checkDoesntExistsInBetween method returns true when ALL characters are
			// within [A,Z], which triggers CAPITAL_LETTERS_MISSING
			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			policy.setAtleastOneUppercase(true);
			String allUppercase = "ABCDEFGHIJKLM";

			when(dao.getPastPasswordsBasedOnPolicy(any(ClientPasswordPolicy.class), eq(USER_ID)))
					.thenReturn(Flux.empty());

			StepVerifier.create(service.checkAllConditions(policy, USER_ID, allUppercase))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void missingLowercase_WhenAllCharsAreLowercase_ThrowsBadRequest() {
			// When all characters are in [a,z], the second check in checkAlphanumericExists triggers
			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			policy.setAtleastOneUppercase(true);
			policy.setAtleastOneLowercase(true);
			String allLowercase = "abcdefghijklm";

			when(dao.getPastPasswordsBasedOnPolicy(any(ClientPasswordPolicy.class), eq(USER_ID)))
					.thenReturn(Flux.empty());

			StepVerifier.create(service.checkAllConditions(policy, USER_ID, allLowercase))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void missingDigit_WhenAllCharsAreDigits_ThrowsBadRequest() {
			// When all characters are digits [0,9], the digit check triggers
			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			policy.setAtleastOneUppercase(false);
			policy.setAtleastOneLowercase(false);
			policy.setAtleastOneDigit(true);
			String allDigits = "1234567890123";

			when(dao.getPastPasswordsBasedOnPolicy(any(ClientPasswordPolicy.class), eq(USER_ID)))
					.thenReturn(Flux.empty());

			StepVerifier.create(service.checkAllConditions(policy, USER_ID, allDigits))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void missingSpecialChar_ThrowsBadRequest() {
			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			policy.setAtleastOneUppercase(false);
			policy.setAtleastOneLowercase(false);
			policy.setAtleastOneDigit(false);
			policy.setAtleastOneSpecialChar(true);
			String noSpecialChars = "TestPassword12";

			when(dao.getPastPasswordsBasedOnPolicy(any(ClientPasswordPolicy.class), eq(USER_ID)))
					.thenReturn(Flux.empty());

			StepVerifier.create(service.checkAllConditions(policy, USER_ID, noSpecialChars))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void hasSpaces_WhenNotAllowed_ThrowsBadRequest() {
			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			policy.setAtleastOneUppercase(false);
			policy.setAtleastOneLowercase(false);
			policy.setAtleastOneDigit(false);
			policy.setAtleastOneSpecialChar(false);
			policy.setSpacesAllowed(false);
			String passwordWithSpace = "Test @12345678";

			when(dao.getPastPasswordsBasedOnPolicy(any(ClientPasswordPolicy.class), eq(USER_ID)))
					.thenReturn(Flux.empty());

			StepVerifier.create(service.checkAllConditions(policy, USER_ID, passwordWithSpace))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void hasSpaces_WhenAllowed_ReturnsTrue() {
			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			policy.setAtleastOneUppercase(false);
			policy.setAtleastOneLowercase(false);
			policy.setAtleastOneDigit(false);
			policy.setAtleastOneSpecialChar(false);
			policy.setSpacesAllowed(true);
			policy.setRegex(null);
			policy.setPassMinLength((short) 5);
			policy.setPassMaxLength((short) 30);
			String passwordWithSpace = "Test @12345678";

			when(dao.getPastPasswordsBasedOnPolicy(any(ClientPasswordPolicy.class), eq(USER_ID)))
					.thenReturn(Flux.empty());

			StepVerifier.create(service.checkAllConditions(policy, USER_ID, passwordWithSpace))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void tooShort_ThrowsBadRequest() {
			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			policy.setAtleastOneUppercase(false);
			policy.setAtleastOneLowercase(false);
			policy.setAtleastOneDigit(false);
			policy.setAtleastOneSpecialChar(false);
			policy.setSpacesAllowed(true);
			policy.setRegex(null);
			policy.setPassMinLength((short) 12);
			policy.setPassMaxLength((short) 20);
			String shortPassword = "T@1a";

			when(dao.getPastPasswordsBasedOnPolicy(any(ClientPasswordPolicy.class), eq(USER_ID)))
					.thenReturn(Flux.empty());

			StepVerifier.create(service.checkAllConditions(policy, USER_ID, shortPassword))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void tooLong_ThrowsBadRequest() {
			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			policy.setAtleastOneUppercase(false);
			policy.setAtleastOneLowercase(false);
			policy.setAtleastOneDigit(false);
			policy.setAtleastOneSpecialChar(false);
			policy.setSpacesAllowed(true);
			policy.setRegex(null);
			policy.setPassMinLength((short) 5);
			policy.setPassMaxLength((short) 10);
			String longPassword = "Test@1234567890";

			when(dao.getPastPasswordsBasedOnPolicy(any(ClientPasswordPolicy.class), eq(USER_ID)))
					.thenReturn(Flux.empty());

			StepVerifier.create(service.checkAllConditions(policy, USER_ID, longPassword))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void regexMismatch_ThrowsBadRequest() {
			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			policy.setAtleastOneUppercase(false);
			policy.setAtleastOneLowercase(false);
			policy.setAtleastOneDigit(false);
			policy.setAtleastOneSpecialChar(false);
			policy.setSpacesAllowed(true);
			policy.setRegex("^[A-Z].*$");
			policy.setPassMinLength((short) 5);
			policy.setPassMaxLength((short) 30);
			String noMatchPassword = "test@12345678";

			when(dao.getPastPasswordsBasedOnPolicy(any(ClientPasswordPolicy.class), eq(USER_ID)))
					.thenReturn(Flux.empty());

			StepVerifier.create(service.checkAllConditions(policy, USER_ID, noMatchPassword))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void regexMatch_ReturnsTrue() {
			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			policy.setAtleastOneUppercase(false);
			policy.setAtleastOneLowercase(false);
			policy.setAtleastOneDigit(false);
			policy.setAtleastOneSpecialChar(false);
			policy.setSpacesAllowed(true);
			policy.setRegex("^[A-Z].*$");
			policy.setPassMinLength((short) 5);
			policy.setPassMaxLength((short) 30);
			String matchPassword = "Test@12345678";

			when(dao.getPastPasswordsBasedOnPolicy(any(ClientPasswordPolicy.class), eq(USER_ID)))
					.thenReturn(Flux.empty());

			StepVerifier.create(service.checkAllConditions(policy, USER_ID, matchPassword))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void pastPasswordMatch_ThrowsBadRequest() {
			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			policy.setAtleastOneUppercase(false);
			policy.setAtleastOneLowercase(false);
			policy.setAtleastOneDigit(false);
			policy.setAtleastOneSpecialChar(false);
			policy.setSpacesAllowed(true);
			policy.setPassMinLength((short) 5);
			policy.setPassMaxLength((short) 30);
			String password = "Test@12345678";

			PastPassword pastPassword = new PastPassword();
			pastPassword.setUserId(USER_ID);
			pastPassword.setPassword("hashedPassword");
			pastPassword.setPasswordHashed(true);

			when(dao.getPastPasswordsBasedOnPolicy(any(ClientPasswordPolicy.class), eq(USER_ID)))
					.thenReturn(Flux.just(pastPassword));
			when(encoder.matches(eq(USER_ID + password), eq("hashedPassword")))
					.thenReturn(true);

			StepVerifier.create(service.checkAllConditions(policy, USER_ID, password))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void pastPasswordMatch_UnhashedPassword_ThrowsBadRequest() {
			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			policy.setAtleastOneUppercase(false);
			policy.setAtleastOneLowercase(false);
			policy.setAtleastOneDigit(false);
			policy.setAtleastOneSpecialChar(false);
			policy.setSpacesAllowed(true);
			policy.setPassMinLength((short) 5);
			policy.setPassMaxLength((short) 30);
			String password = "Test@12345678";

			PastPassword pastPassword = new PastPassword();
			pastPassword.setUserId(USER_ID);
			pastPassword.setPassword(password);
			pastPassword.setPasswordHashed(false);

			when(dao.getPastPasswordsBasedOnPolicy(any(ClientPasswordPolicy.class), eq(USER_ID)))
					.thenReturn(Flux.just(pastPassword));

			StepVerifier.create(service.checkAllConditions(policy, USER_ID, password))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void pastPasswordNoMatch_ReturnsTrue() {
			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			policy.setAtleastOneUppercase(false);
			policy.setAtleastOneLowercase(false);
			policy.setAtleastOneDigit(false);
			policy.setAtleastOneSpecialChar(false);
			policy.setSpacesAllowed(true);
			policy.setPassMinLength((short) 5);
			policy.setPassMaxLength((short) 30);
			String password = "Test@12345678";

			PastPassword pastPassword = new PastPassword();
			pastPassword.setUserId(USER_ID);
			pastPassword.setPassword("differentHashedPassword");
			pastPassword.setPasswordHashed(true);

			when(dao.getPastPasswordsBasedOnPolicy(any(ClientPasswordPolicy.class), eq(USER_ID)))
					.thenReturn(Flux.just(pastPassword));
			when(encoder.matches(eq(USER_ID + password), eq("differentHashedPassword")))
					.thenReturn(false);

			StepVerifier.create(service.checkAllConditions(policy, USER_ID, password))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void nullUserId_SkipsPastPasswordCheck_ReturnsTrue() {
			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			policy.setAtleastOneUppercase(false);
			policy.setAtleastOneLowercase(false);
			policy.setAtleastOneDigit(false);
			policy.setAtleastOneSpecialChar(false);
			policy.setSpacesAllowed(true);
			policy.setRegex(null);
			policy.setPassMinLength((short) 5);
			policy.setPassMaxLength((short) 30);
			String password = "Test@12345678";

			StepVerifier.create(service.checkAllConditions(policy, null, password))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();

			verifyNoInteractions(dao);
		}

		@Test
		void allDisabled_ReturnsTrue() {
			ClientPasswordPolicy policy = new ClientPasswordPolicy();
			policy.setAtleastOneUppercase(false);
			policy.setAtleastOneLowercase(false);
			policy.setAtleastOneDigit(false);
			policy.setAtleastOneSpecialChar(false);
			policy.setSpacesAllowed(true);
			policy.setRegex(null);
			policy.setPassMinLength(null);
			policy.setPassMaxLength(null);
			policy.setPassHistoryCount((short) 0);
			String password = "anypassword";

			when(dao.getPastPasswordsBasedOnPolicy(any(ClientPasswordPolicy.class), eq(USER_ID)))
					.thenReturn(Flux.empty());

			StepVerifier.create(service.checkAllConditions(policy, USER_ID, password))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void checkAllConditions_WithClientIdAndAppId_DelegatesToPolicyCheck() {
			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			policy.setId(POLICY_ID);
			policy.setClientId(BUS_CLIENT_ID);
			policy.setAppId(APP_ID);
			policy.setAtleastOneUppercase(false);
			policy.setAtleastOneLowercase(false);
			policy.setAtleastOneDigit(false);
			policy.setAtleastOneSpecialChar(false);
			policy.setSpacesAllowed(true);
			policy.setRegex(null);
			policy.setPassMinLength((short) 5);
			policy.setPassMaxLength((short) 30);

			when(clientHierarchyService.getClientHierarchyIds(BUS_CLIENT_ID))
					.thenReturn(Flux.just(BUS_CLIENT_ID));
			when(dao.getClientAppPolicy(BUS_CLIENT_ID, APP_ID))
					.thenReturn(Mono.just(policy));

			when(dao.getPastPasswordsBasedOnPolicy(any(ClientPasswordPolicy.class), eq(USER_ID)))
					.thenReturn(Flux.empty());

			StepVerifier.create(service.checkAllConditions(BUS_CLIENT_ID, APP_ID, USER_ID, "Test@12345678"))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}
	}

	// ===== getClientAppPolicy tests =====

	@Nested
	class GetClientAppPolicy {

		@Test
		void policyExists_ReturnsCachedPolicy() {
			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			policy.setId(POLICY_ID);
			policy.setClientId(BUS_CLIENT_ID);
			policy.setAppId(APP_ID);

			when(clientHierarchyService.getClientHierarchyIds(BUS_CLIENT_ID))
					.thenReturn(Flux.just(BUS_CLIENT_ID));
			when(dao.getClientAppPolicy(BUS_CLIENT_ID, APP_ID))
					.thenReturn(Mono.just(policy));

			StepVerifier.create(service.getClientAppPolicy(BUS_CLIENT_ID, APP_ID))
					.assertNext(result -> {
						assertEquals(POLICY_ID, result.getId());
						assertEquals(BUS_CLIENT_ID, result.getClientId());
					})
					.verifyComplete();
		}

		@Test
		void fallsBackToHierarchy_WhenDirectPolicyNotFound() {
			ClientPasswordPolicy parentPolicy = TestDataFactory.createPasswordPolicy();
			parentPolicy.setId(POLICY_ID);
			parentPolicy.setClientId(SYSTEM_CLIENT_ID);
			parentPolicy.setAppId(APP_ID);

			when(clientHierarchyService.getClientHierarchyIds(BUS_CLIENT_ID))
					.thenReturn(Flux.just(BUS_CLIENT_ID, SYSTEM_CLIENT_ID));
			when(dao.getClientAppPolicy(BUS_CLIENT_ID, APP_ID))
					.thenReturn(Mono.empty());
			when(dao.getClientAppPolicy(SYSTEM_CLIENT_ID, APP_ID))
					.thenReturn(Mono.just(parentPolicy));

			StepVerifier.create(service.getClientAppPolicy(BUS_CLIENT_ID, APP_ID))
					.assertNext(result -> {
						assertEquals(POLICY_ID, result.getId());
						assertEquals(SYSTEM_CLIENT_ID, result.getClientId());
					})
					.verifyComplete();
		}

		@Test
		void returnsDefault_WhenNoPolicyFound() {
			when(clientHierarchyService.getClientHierarchyIds(BUS_CLIENT_ID))
					.thenReturn(Flux.just(BUS_CLIENT_ID));
			when(dao.getClientAppPolicy(BUS_CLIENT_ID, APP_ID))
					.thenReturn(Mono.empty());

			StepVerifier.create(service.getClientAppPolicy(BUS_CLIENT_ID, APP_ID))
					.assertNext(result -> {
						assertEquals(ULong.MIN, result.getId());
						assertTrue(result.isAtleastOneUppercase());
						assertTrue(result.isAtleastOneLowercase());
						assertTrue(result.isAtleastOneDigit());
						assertTrue(result.isAtleastOneSpecialChar());
						assertFalse(result.isSpacesAllowed());
						assertEquals((short) 12, result.getPassMinLength());
						assertEquals((short) 20, result.getPassMaxLength());
					})
					.verifyComplete();
		}
	}

	// ===== CRUD tests =====

	@Nested
	class CrudOperations {

		@Test
		void create_SuccessfullyCreatesPolicy() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			policy.setClientId(SYSTEM_CLIENT_ID);
			policy.setAppId(APP_ID);

			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testApp");

			when(appService.getAppByCode(any())).thenReturn(Mono.just(app));
			when(clientHierarchyService.getClientHierarchyIds(SYSTEM_CLIENT_ID))
					.thenReturn(Flux.just(SYSTEM_CLIENT_ID));
			when(dao.getClientAppPolicy(SYSTEM_CLIENT_ID, APP_ID)).thenReturn(Mono.empty());
			when(appService.getAppById(APP_ID)).thenReturn(Mono.just(app));
			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(SYSTEM_CLIENT_ID)))
					.thenReturn(Mono.just(true));
			when(appService.hasWriteAccess(eq(APP_ID), any(ULong.class))).thenReturn(Mono.just(true));
			when(dao.create(any(ClientPasswordPolicy.class))).thenReturn(Mono.just(policy));

			StepVerifier.create(service.create(policy))
					.assertNext(result -> {
						assertNotNull(result);
						assertTrue(result.isAtleastOneUppercase());
					})
					.verifyComplete();
		}

		@Test
		void update_EvictsCache() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientPasswordPolicy existing = TestDataFactory.createPasswordPolicy();
			existing.setId(POLICY_ID);
			existing.setClientId(BUS_CLIENT_ID);
			existing.setAppId(APP_ID);

			ClientPasswordPolicy updated = TestDataFactory.createPasswordPolicy();
			updated.setId(POLICY_ID);
			updated.setClientId(BUS_CLIENT_ID);
			updated.setAppId(APP_ID);
			updated.setPassMinLength((short) 15);

			App app = TestDataFactory.createOwnApp(APP_ID, BUS_CLIENT_ID, "testApp");

			when(dao.readById(POLICY_ID)).thenReturn(Mono.just(existing));
			when(appService.getAppById(APP_ID)).thenReturn(Mono.just(app));
			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(true));
			when(appService.hasWriteAccess(eq(APP_ID), any(ULong.class))).thenReturn(Mono.just(true));
			when(dao.canBeUpdated(POLICY_ID)).thenReturn(Mono.just(true));
			when(dao.update(any(ClientPasswordPolicy.class))).thenReturn(Mono.just(updated));

			StepVerifier.create(service.update(updated))
					.assertNext(result -> assertNotNull(result))
					.verifyComplete();

			verify(cacheService).evict(eq("clientPasswordPolicy"), eq(BUS_CLIENT_ID + ":" + APP_ID));
		}

		@Test
		void delete_EvictsCache() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientPasswordPolicy existing = TestDataFactory.createPasswordPolicy();
			existing.setId(POLICY_ID);
			existing.setClientId(BUS_CLIENT_ID);
			existing.setAppId(APP_ID);

			App app = TestDataFactory.createOwnApp(APP_ID, BUS_CLIENT_ID, "testApp");

			when(dao.readById(POLICY_ID)).thenReturn(Mono.just(existing));
			when(appService.getAppById(APP_ID)).thenReturn(Mono.just(app));
			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(true));
			when(appService.hasWriteAccess(eq(APP_ID), any(ULong.class))).thenReturn(Mono.just(true));
			when(dao.canBeUpdated(POLICY_ID)).thenReturn(Mono.just(true));
			when(dao.delete(POLICY_ID)).thenReturn(Mono.just(1));

			StepVerifier.create(service.delete(POLICY_ID))
					.assertNext(result -> assertEquals(1, result))
					.verifyComplete();

			verify(cacheService).evict(eq("clientPasswordPolicy"), eq(BUS_CLIENT_ID + ":" + APP_ID));
		}
	}

	// ===== Metadata tests =====

	@Nested
	class Metadata {

		@Test
		void getAuthenticationPasswordType_ReturnsPassword() {
			assertEquals(AuthenticationPasswordType.PASSWORD, service.getAuthenticationPasswordType());
		}

		@Test
		void getPolicyCacheName_ReturnsCorrectName() {
			assertEquals("clientPasswordPolicy", service.getPolicyCacheName());
		}
	}
}
