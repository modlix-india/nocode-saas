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
	private UserInviteDAO dao;

	private UserInviteService service;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);
	private static final ULong BUS_CLIENT_ID = ULong.valueOf(2);
	private static final ULong USER_ID = ULong.valueOf(10);
	private static final ULong PROFILE_ID = ULong.valueOf(100);
	private static final ULong REPORTING_TO_ID = ULong.valueOf(20);

	@BeforeEach
	void setUp() {
		service = new UserInviteService(msgService, clientService, authenticationService, userDao, soxLogService,
				profileService, appService, clientHierarchyService);

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

			when(userDao.checkUserExistsForInvite(eq(SYSTEM_CLIENT_ID), eq("testuser"), eq("test@example.com"),
					isNull()))
					.thenReturn(Mono.just(false));
			when(dao.create(any(UserInvite.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.createInvite(invite))
					.assertNext(result -> {
						assertNotNull(result);
						assertEquals(Boolean.FALSE, result.get("existingUser"));
					})
					.verifyComplete();

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
			when(userDao.checkUserExistsForInvite(eq(BUS_CLIENT_ID), eq("testuser"), eq("test@example.com"),
					isNull()))
					.thenReturn(Mono.just(false));
			when(dao.create(any(UserInvite.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.createInvite(invite))
					.assertNext(result -> {
						assertNotNull(result);
						assertEquals(Boolean.FALSE, result.get("existingUser"));
					})
					.verifyComplete();
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
			when(userDao.checkUserExistsForInvite(eq(SYSTEM_CLIENT_ID), eq("testuser"), eq("test@example.com"),
					isNull()))
					.thenReturn(Mono.just(false));
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

			when(profileService.hasAccessToProfiles(eq(SYSTEM_CLIENT_ID), eq(Set.of(PROFILE_ID))))
					.thenReturn(Mono.just(true));
			when(userDao.checkUserExistsForInvite(eq(SYSTEM_CLIENT_ID), eq("existinguser"),
					eq("existing@example.com"), isNull()))
					.thenReturn(Mono.just(true));
			when(userDao.getUserForInvite(eq(SYSTEM_CLIENT_ID), eq("existinguser"), eq("existing@example.com"),
					isNull()))
					.thenReturn(Mono.just(existingUser));
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

			when(userDao.checkUserExistsForInvite(eq(SYSTEM_CLIENT_ID), eq("existinguser"),
					eq("existing@example.com"), isNull()))
					.thenReturn(Mono.just(true));

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
			when(userDao.checkUserExistsForInvite(eq(SYSTEM_CLIENT_ID), eq("testuser"), eq("test@example.com"),
					isNull()))
					.thenReturn(Mono.just(false));
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
}
