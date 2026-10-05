package com.fincity.security.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.security.dao.UserDAO;
import com.fincity.security.dao.UserInviteDAO;
import com.fincity.security.dto.User;
import com.fincity.security.dto.UserInvite;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class UserInviteServiceTest extends AbstractServiceUnitTest {

	@Mock
	private SecurityMessageResourceService msgService;

	@Mock
	private ClientService clientService;

	@Mock
	private AuthenticationService authenticationService;

	@Mock
	private UserDAO userDao;

	@Mock
	private SoxLogService soxLogService;

	@Mock
	private ProfileService profileService;

	@Mock
	private AppService appService;

	@Mock
	private ClientHierarchyService clientHierarchyService;

	@Mock
	private ClientActivityService clientActivityService;

	@Mock
	private UserInviteDAO dao;

	private UserInviteService service;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);
	private static final ULong BUS_CLIENT_ID = ULong.valueOf(2);
	@Mock
	private OrgStructureService orgStructureService;

	@Mock
	private DesignationService designationService;

	private static final ULong USER_ID = ULong.valueOf(10);
	private static final ULong PROFILE_ID = ULong.valueOf(100);
	private static final ULong REPORTING_TO_ID = ULong.valueOf(20);

	@BeforeEach
	void setUp() {
		service = new UserInviteService(msgService, clientService, authenticationService, userDao, soxLogService,
				profileService, appService, clientHierarchyService, clientActivityService, orgStructureService,
				designationService);
		lenient().when(orgStructureService.evict(any())).thenReturn(Mono.just(Boolean.TRUE));

		var daoField = org.springframework.util.ReflectionUtils.findField(service.getClass(), "dao");
		daoField.setAccessible(true);
		org.springframework.util.ReflectionUtils.setField(daoField, service, dao);

		setupMessageResourceService(msgService);
	}

	// =========================================================================
	// createInvite()
	// =========================================================================

	@Nested
	@DisplayName("createInvite()")
	class CreateInviteTests {

		@Test
		void createInvite_NullClientId_UsesAuthClientId_CreatesNewInvite() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			UserInvite invite = new UserInvite();
			invite.setEmailId("test@example.com");
			invite.setUserName("testuser");

			UserInvite created = new UserInvite();
			created.setId(ULong.valueOf(1));

			when(userDao.getUsersWithAnyIdentity(eq(SYSTEM_CLIENT_ID), eq("testuser"), eq("test@example.com"),
					isNull(), isNull()))
					.thenReturn(Flux.empty());
			when(dao.getInviteWithAnyIdentity(eq(SYSTEM_CLIENT_ID), eq("test@example.com"), isNull()))
					.thenReturn(Mono.empty());
			when(dao.create(any(UserInvite.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.createInvite(invite))
					.assertNext(result -> {
						assertNotNull(result);
						assertEquals(Boolean.FALSE, result.get("existingUser"));
					})
					.verifyComplete();

			verify(clientActivityService, atLeastOnce()).createLog(any(), eq("User Invite Created"), anyString());
			assertEquals(SYSTEM_CLIENT_ID, invite.getClientId());
		}

		@Test
		void createInvite_WithClientId_ChecksManaged_CreatesInvite() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			UserInvite invite = new UserInvite();
			invite.setClientId(BUS_CLIENT_ID);
			invite.setEmailId("test@example.com");
			invite.setUserName("testuser");

			UserInvite created = new UserInvite();
			created.setId(ULong.valueOf(1));

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(true));
			when(userDao.getUsersWithAnyIdentity(eq(BUS_CLIENT_ID), eq("testuser"), eq("test@example.com"),
					isNull(), isNull()))
					.thenReturn(Flux.empty());
			when(dao.getInviteWithAnyIdentity(eq(BUS_CLIENT_ID), eq("test@example.com"), isNull()))
					.thenReturn(Mono.empty());
			when(dao.create(any(UserInvite.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.createInvite(invite))
					.assertNext(result -> {
						assertNotNull(result);
						assertEquals(Boolean.FALSE, result.get("existingUser"));
					})
					.verifyComplete();

			verify(clientActivityService, atLeastOnce()).createLog(any(), eq("User Invite Created"), anyString());
		}

		@Test
		void createInvite_NotManagedClient_ThrowsForbidden() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUS",
					List.of("Authorities.User_CREATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			UserInvite invite = new UserInvite();
			invite.setClientId(ULong.valueOf(99));
			invite.setEmailId("test@example.com");

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(ULong.valueOf(99))))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.createInvite(invite))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void createInvite_WithReportingTo_ValidatesInSameClient() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			User reportingUser = new User();
			reportingUser.setId(REPORTING_TO_ID);
			reportingUser.setClientId(SYSTEM_CLIENT_ID);

			UserInvite invite = new UserInvite();
			invite.setEmailId("test@example.com");
			invite.setUserName("testuser");
			invite.setReportingTo(REPORTING_TO_ID);

			UserInvite created = new UserInvite();
			created.setId(ULong.valueOf(1));

			when(userDao.readById(REPORTING_TO_ID)).thenReturn(Mono.just(reportingUser));
			when(userDao.getUsersWithAnyIdentity(eq(SYSTEM_CLIENT_ID), eq("testuser"), eq("test@example.com"),
					isNull(), isNull()))
					.thenReturn(Flux.empty());
			when(dao.getInviteWithAnyIdentity(eq(SYSTEM_CLIENT_ID), eq("test@example.com"), isNull()))
					.thenReturn(Mono.empty());
			when(dao.create(any(UserInvite.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.createInvite(invite))
					.assertNext(result -> assertNotNull(result))
					.verifyComplete();
		}

		@Test
		void createInvite_ReportingToDifferentClient_ThrowsForbidden() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			User reportingUser = new User();
			reportingUser.setId(REPORTING_TO_ID);
			reportingUser.setClientId(BUS_CLIENT_ID);

			UserInvite invite = new UserInvite();
			invite.setEmailId("test@example.com");
			invite.setReportingTo(REPORTING_TO_ID);

			when(userDao.readById(REPORTING_TO_ID)).thenReturn(Mono.just(reportingUser));

			StepVerifier.create(service.createInvite(invite))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void createInvite_ExistingUserWithProfile_AddsProfile() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			UserInvite invite = new UserInvite();
			invite.setEmailId("existing@example.com");
			invite.setUserName("existinguser");
			invite.setProfileId(PROFILE_ID);

			User existingUser = TestDataFactory.createActiveUser(USER_ID, SYSTEM_CLIENT_ID);
			existingUser.setEmailId("existing@example.com");
			existingUser.setUserName("existinguser");

			when(profileService.hasAccessToProfiles(eq(SYSTEM_CLIENT_ID), eq(Set.of(PROFILE_ID))))
					.thenReturn(Mono.just(true));
			when(userDao.getUsersWithAnyIdentity(eq(SYSTEM_CLIENT_ID), eq("existinguser"),
					eq("existing@example.com"), isNull(), isNull()))
					.thenReturn(Flux.just(existingUser));
			when(userDao.addProfileToUser(USER_ID, PROFILE_ID)).thenReturn(Mono.just(1));

			StepVerifier.create(service.createInvite(invite))
					.assertNext(result -> {
						assertNotNull(result);
						assertEquals(Boolean.TRUE, result.get("existingUser"));
					})
					.verifyComplete();

			verify(userDao).addProfileToUser(USER_ID, PROFILE_ID);
		}

		@Test
		void createInvite_ExistingUserNoProfile_ReturnsEmpty() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			UserInvite invite = new UserInvite();
			invite.setEmailId("existing@example.com");
			invite.setUserName("existinguser");
			// no profileId set

			User existingUser = TestDataFactory.createActiveUser(USER_ID, SYSTEM_CLIENT_ID);
			existingUser.setEmailId("existing@example.com");
			existingUser.setUserName("existinguser");

			when(userDao.getUsersWithAnyIdentity(eq(SYSTEM_CLIENT_ID), eq("existinguser"),
					eq("existing@example.com"), isNull(), isNull()))
					.thenReturn(Flux.just(existingUser));

			// addUserProfile returns empty when profileId is null
			StepVerifier.create(service.createInvite(invite))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void createInvite_WithProfileId_ChecksProfileAccess() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			UserInvite invite = new UserInvite();
			invite.setEmailId("test@example.com");
			invite.setUserName("testuser");
			invite.setProfileId(PROFILE_ID);

			UserInvite created = new UserInvite();
			created.setId(ULong.valueOf(1));

			when(profileService.hasAccessToProfiles(eq(SYSTEM_CLIENT_ID), eq(Set.of(PROFILE_ID))))
					.thenReturn(Mono.just(true));
			when(userDao.getUsersWithAnyIdentity(eq(SYSTEM_CLIENT_ID), eq("testuser"), eq("test@example.com"),
					isNull(), isNull()))
					.thenReturn(Flux.empty());
			when(dao.getInviteWithAnyIdentity(eq(SYSTEM_CLIENT_ID), eq("test@example.com"), isNull()))
					.thenReturn(Mono.empty());
			when(dao.create(any(UserInvite.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.createInvite(invite))
					.assertNext(result -> assertNotNull(result))
					.verifyComplete();

			verify(profileService).hasAccessToProfiles(eq(SYSTEM_CLIENT_ID), eq(Set.of(PROFILE_ID)));
		}

		@Test
		void createInvite_ProfileAccessDenied_ThrowsForbidden() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			UserInvite invite = new UserInvite();
			invite.setEmailId("test@example.com");
			invite.setUserName("testuser");
			invite.setProfileId(PROFILE_ID);

			when(profileService.hasAccessToProfiles(eq(SYSTEM_CLIENT_ID), eq(Set.of(PROFILE_ID))))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.createInvite(invite))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void createInvite_ExistingUsersEmailWithOtherPhone_ThrowsConflictNamingEmail() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			UserInvite invite = new UserInvite();
			invite.setEmailId("existing@example.com");
			invite.setPhoneNumber("+910000000001");
			invite.setProfileId(PROFILE_ID);

			User existingUser = TestDataFactory.createActiveUser(USER_ID, SYSTEM_CLIENT_ID);
			existingUser.setEmailId("Existing@Example.com");
			existingUser.setPhoneNumber("+919999999999");

			when(profileService.hasAccessToProfiles(eq(SYSTEM_CLIENT_ID), eq(Set.of(PROFILE_ID))))
					.thenReturn(Mono.just(true));
			when(userDao.getUsersWithAnyIdentity(eq(SYSTEM_CLIENT_ID), isNull(), eq("existing@example.com"),
					eq("+910000000001"), isNull()))
					.thenReturn(Flux.just(existingUser));

			// Used to miss the user (email AND phone had to match) and insert an invite, which then hit
			// the invite table's unique key and came back as a 500.
			StepVerifier.create(service.createInvite(invite))
					.expectErrorMatches(e -> e instanceof GenericException ge
							&& ge.getStatusCode() == HttpStatus.CONFLICT)
					.verify();

			verify(msgService).throwMessage(any(), eq(SecurityMessageResourceService.USER_IDENTITY_TAKEN),
					eq("email"));
			verify(dao, never()).create(any(UserInvite.class));
			verify(userDao, never()).addProfileToUser(any(), any());
		}

		@Test
		void createInvite_IdentitiesOfTwoUsers_ThrowsConflict() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			UserInvite invite = new UserInvite();
			invite.setEmailId("a@example.com");
			invite.setPhoneNumber("+910000000002");

			User a = TestDataFactory.createActiveUser(USER_ID, SYSTEM_CLIENT_ID);
			a.setEmailId("a@example.com");
			a.setPhoneNumber("+910000000001");
			User b = TestDataFactory.createActiveUser(ULong.valueOf(11), SYSTEM_CLIENT_ID);
			b.setEmailId("b@example.com");
			b.setPhoneNumber("+910000000002");

			when(userDao.getUsersWithAnyIdentity(eq(SYSTEM_CLIENT_ID), isNull(), eq("a@example.com"),
					eq("+910000000002"), isNull()))
					.thenReturn(Flux.just(a, b));

			StepVerifier.create(service.createInvite(invite))
					.expectErrorMatches(e -> e instanceof GenericException ge
							&& ge.getStatusCode() == HttpStatus.CONFLICT)
					.verify();

			verify(dao, never()).create(any(UserInvite.class));
		}

		@Test
		void createInvite_PendingInviteForPhone_ThrowsConflictNamingPhone() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			UserInvite invite = new UserInvite();
			invite.setEmailId("new@example.com");
			invite.setPhoneNumber("+910000000003");

			UserInvite pending = new UserInvite();
			pending.setEmailId("other@example.com");
			pending.setPhoneNumber("+910000000003");

			when(userDao.getUsersWithAnyIdentity(eq(SYSTEM_CLIENT_ID), isNull(), eq("new@example.com"),
					eq("+910000000003"), isNull()))
					.thenReturn(Flux.empty());
			when(dao.getInviteWithAnyIdentity(eq(SYSTEM_CLIENT_ID), eq("new@example.com"), eq("+910000000003")))
					.thenReturn(Mono.just(pending));

			StepVerifier.create(service.createInvite(invite))
					.expectErrorMatches(e -> e instanceof GenericException ge
							&& ge.getStatusCode() == HttpStatus.CONFLICT)
					.verify();

			verify(msgService).throwMessage(any(), eq(SecurityMessageResourceService.USER_INVITE_PENDING),
					eq("phone number"));
			verify(dao, never()).create(any(UserInvite.class));
		}

		@Test
		void createInvite_InsertHitsUniqueKey_ThrowsConflict() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			UserInvite invite = new UserInvite();
			invite.setEmailId("race@example.com");

			when(userDao.getUsersWithAnyIdentity(eq(SYSTEM_CLIENT_ID), isNull(), eq("race@example.com"),
					isNull(), isNull()))
					.thenReturn(Flux.empty());
			when(dao.getInviteWithAnyIdentity(eq(SYSTEM_CLIENT_ID), eq("race@example.com"), isNull()))
					.thenReturn(Mono.empty());
			when(dao.create(any(UserInvite.class))).thenReturn(Mono.error(
					new org.jooq.exception.IntegrityConstraintViolationException("Duplicate entry")));

			StepVerifier.create(service.createInvite(invite))
					.expectErrorMatches(e -> e instanceof GenericException ge
							&& ge.getStatusCode() == HttpStatus.CONFLICT)
					.verify();
		}
	}

	// =========================================================================
	// getUserInvitation()
	// =========================================================================

	@Nested
	@DisplayName("getUserInvitation()")
	class GetUserInvitationTests {

		@Test
		void getUserInvitation_ExistingCode_ReturnsInvite() {
			UserInvite invite = new UserInvite();
			invite.setId(ULong.valueOf(1));
			invite.setInviteCode("abc123");

			when(dao.getUserInvitation("abc123")).thenReturn(Mono.just(invite));

			StepVerifier.create(service.getUserInvitation("abc123"))
					.assertNext(result -> assertEquals(ULong.valueOf(1), result.getId()))
					.verifyComplete();
		}

		@Test
		void getUserInvitation_UnknownCode_ReturnsEmpty() {
			when(dao.getUserInvitation("unknown")).thenReturn(Mono.empty());

			StepVerifier.create(service.getUserInvitation("unknown"))
					.verifyComplete();
		}
	}

	// =========================================================================
	// deleteUserInvitation()
	// =========================================================================

	@Nested
	@DisplayName("deleteUserInvitation()")
	class DeleteUserInvitationTests {

		@Test
		void deleteUserInvitation_ExistingCode_ReturnsTrue() {
			when(dao.deleteUserInvitation("abc123")).thenReturn(Mono.just(true));

			StepVerifier.create(service.deleteUserInvitation("abc123"))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void deleteUserInvitation_UnknownCode_ReturnsFalse() {
			when(dao.deleteUserInvitation("unknown")).thenReturn(Mono.just(false));

			StepVerifier.create(service.deleteUserInvitation("unknown"))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	// =========================================================================
	// authority guards on the invite surface
	// =========================================================================

	@Nested
	@DisplayName("invite authority guards")
	class InviteAuthorityGuardTests {

		private String preAuthorize(String method, Class<?>... params) throws NoSuchMethodException {
			var annotation = UserInviteService.class.getMethod(method, params)
					.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
			return annotation == null ? null : annotation.value();
		}

		@Test
		@DisplayName("createInvite needs User_CREATE: an invite code is a credential for a new account")
		void createInviteRequiresUserCreate() throws NoSuchMethodException {
			assertEquals("hasAuthority('Authorities.User_CREATE')",
					preAuthorize("createInvite", com.fincity.security.dto.UserInvite.class));
		}

		@Test
		void revokeInvitationRequiresUserCreateOrDelete() throws NoSuchMethodException {
			assertEquals("hasAnyAuthority('Authorities.User_CREATE', 'Authorities.User_DELETE')",
					preAuthorize("revokeInvitation", String.class));
		}
	}
}
