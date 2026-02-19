package com.fincity.security.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

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
import com.fincity.security.dao.UserRequestDAO;
import com.fincity.security.dto.App;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.User;
import com.fincity.security.dto.UserRequest;
import com.fincity.security.jooq.enums.SecurityUserRequestStatus;
import com.fincity.security.model.UserAppAccessRequest;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class UserRequestServiceTest extends AbstractServiceUnitTest {

	@Mock
	private UserRequestDAO dao;

	@Mock
	private SecurityMessageResourceService msgService;

	@Mock
	private ClientService clientService;

	@Mock
	private UserDAO userDao;

	@Mock
	private ProfileService profileService;

	@Mock
	private AppService appService;

	private UserRequestService service;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);
	private static final ULong USER_ID = ULong.valueOf(10);
	private static final ULong APP_ID = ULong.valueOf(200);
	private static final ULong PROFILE_ID = ULong.valueOf(300);

	@BeforeEach
	void setUp() {
		service = new UserRequestService(msgService, clientService, userDao, profileService, appService);

		var daoField = org.springframework.util.ReflectionUtils.findField(
				service.getClass().getSuperclass().getSuperclass(), "dao");
		daoField.setAccessible(true);
		org.springframework.util.ReflectionUtils.setField(daoField, service, dao);

		setupMessageResourceService(msgService);
	}

	// =========================================================================
	// createRequest()
	// =========================================================================

	@Nested
	@DisplayName("createRequest()")
	class CreateRequestTests {

		@Test
		void createRequest_NewRequest_CreatesSuccessfully() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Client client = new Client();
			client.setId(SYSTEM_CLIENT_ID);
			client.setCode("SYSTEM");

			App app = new App();
			app.setId(APP_ID);
			app.setAppCode("testApp");

			UserAppAccessRequest request = new UserAppAccessRequest();
			request.setAppCode("testApp");

			UserRequest created = new UserRequest();
			created.setId(ULong.valueOf(1));
			created.setStatus(SecurityUserRequestStatus.PENDING);

			when(clientService.getClientBy("SYSTEM")).thenReturn(Mono.just(client));
			when(appService.getAppByCode("testApp")).thenReturn(Mono.just(app));
			when(profileService.checkIfUserHasAnyProfile(any(), eq("testApp"))).thenReturn(Mono.just(false));
			when(dao.checkPendingRequestExists(any(), eq(APP_ID))).thenReturn(Mono.just(false));
			when(dao.create(any(UserRequest.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.createRequest(request))
					.assertNext(result -> {
						assertNotNull(result);
						assertEquals(SecurityUserRequestStatus.PENDING, result.getStatus());
					})
					.verifyComplete();
		}

		@Test
		void createRequest_UserAlreadyHasAccess_ThrowsBadRequest() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Client client = new Client();
			client.setId(SYSTEM_CLIENT_ID);

			App app = new App();
			app.setId(APP_ID);
			app.setAppCode("testApp");

			UserAppAccessRequest request = new UserAppAccessRequest();
			request.setAppCode("testApp");

			when(clientService.getClientBy("SYSTEM")).thenReturn(Mono.just(client));
			when(appService.getAppByCode("testApp")).thenReturn(Mono.just(app));
			when(profileService.checkIfUserHasAnyProfile(any(), eq("testApp"))).thenReturn(Mono.just(true));

			StepVerifier.create(service.createRequest(request))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void createRequest_PendingRequestExists_ThrowsBadRequest() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Client client = new Client();
			client.setId(SYSTEM_CLIENT_ID);

			App app = new App();
			app.setId(APP_ID);
			app.setAppCode("testApp");

			UserAppAccessRequest request = new UserAppAccessRequest();
			request.setAppCode("testApp");

			when(clientService.getClientBy("SYSTEM")).thenReturn(Mono.just(client));
			when(appService.getAppByCode("testApp")).thenReturn(Mono.just(app));
			when(profileService.checkIfUserHasAnyProfile(any(), eq("testApp"))).thenReturn(Mono.just(false));
			when(dao.checkPendingRequestExists(any(), eq(APP_ID))).thenReturn(Mono.just(true));

			StepVerifier.create(service.createRequest(request))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}
	}

	// =========================================================================
	// acceptRequest()
	// =========================================================================

	@Nested
	@DisplayName("acceptRequest()")
	class AcceptRequestTests {

		@Test
		void acceptRequest_NullRequestId_ThrowsBadRequest() {
			UserAppAccessRequest request = new UserAppAccessRequest();
			request.setProfileId(PROFILE_ID);

			StepVerifier.create(service.acceptRequest(request, null, null))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void acceptRequest_NullProfileId_ThrowsBadRequest() {
			UserAppAccessRequest request = new UserAppAccessRequest();
			request.setRequestId("REQ-123");

			StepVerifier.create(service.acceptRequest(request, null, null))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void acceptRequest_ValidPendingRequest_ApprovesAndAddsProfile() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			UserRequest userRequest = new UserRequest();
			userRequest.setId(ULong.valueOf(1));
			userRequest.setUserId(USER_ID);
			userRequest.setClientId(SYSTEM_CLIENT_ID);
			userRequest.setAppId(APP_ID);
			userRequest.setStatus(SecurityUserRequestStatus.PENDING);

			UserAppAccessRequest request = new UserAppAccessRequest();
			request.setRequestId("REQ-123");
			request.setProfileId(PROFILE_ID);

			when(dao.readByRequestId("REQ-123")).thenReturn(Mono.just(userRequest));
			when(userDao.addProfileToUser(USER_ID, PROFILE_ID)).thenReturn(Mono.just(1));
			when(dao.update(any(UserRequest.class))).thenReturn(Mono.just(userRequest));

			StepVerifier.create(service.acceptRequest(request, null, null))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();

			verify(userDao).addProfileToUser(USER_ID, PROFILE_ID);
		}

		@Test
		void acceptRequest_NonPendingRequest_ThrowsBadRequest() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			UserRequest userRequest = new UserRequest();
			userRequest.setId(ULong.valueOf(1));
			userRequest.setStatus(SecurityUserRequestStatus.APPROVED);

			UserAppAccessRequest request = new UserAppAccessRequest();
			request.setRequestId("REQ-123");
			request.setProfileId(PROFILE_ID);

			when(dao.readByRequestId("REQ-123")).thenReturn(Mono.just(userRequest));

			StepVerifier.create(service.acceptRequest(request, null, null))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}
	}

	// =========================================================================
	// rejectRequest()
	// =========================================================================

	@Nested
	@DisplayName("rejectRequest()")
	class RejectRequestTests {

		@Test
		void rejectRequest_NullRequestId_ThrowsBadRequest() {
			StepVerifier.create(service.rejectRequest(null))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void rejectRequest_ValidPendingRequest_Rejects() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			UserRequest userRequest = new UserRequest();
			userRequest.setId(ULong.valueOf(1));
			userRequest.setStatus(SecurityUserRequestStatus.PENDING);

			when(dao.readByRequestId("REQ-123")).thenReturn(Mono.just(userRequest));
			when(dao.update(any(UserRequest.class))).thenReturn(Mono.just(userRequest));

			StepVerifier.create(service.rejectRequest("REQ-123"))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void rejectRequest_NonPendingRequest_ThrowsBadRequest() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			UserRequest userRequest = new UserRequest();
			userRequest.setId(ULong.valueOf(1));
			userRequest.setStatus(SecurityUserRequestStatus.REJECTED);

			when(dao.readByRequestId("REQ-456")).thenReturn(Mono.just(userRequest));

			StepVerifier.create(service.rejectRequest("REQ-456"))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}
	}

	// =========================================================================
	// getRequestUser()
	// =========================================================================

	@Nested
	@DisplayName("getRequestUser()")
	class GetRequestUserTests {

		@Test
		void getRequestUser_NullRequestId_ThrowsBadRequest() {
			StepVerifier.create(service.getRequestUser(null))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void getRequestUser_ValidRequest_Managed_ReturnsUser() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			UserRequest userRequest = new UserRequest();
			userRequest.setId(ULong.valueOf(1));
			userRequest.setUserId(USER_ID);
			userRequest.setClientId(SYSTEM_CLIENT_ID);

			User user = new User();
			user.setId(USER_ID);
			user.setUserName("testuser");

			when(dao.readByRequestId("REQ-789")).thenReturn(Mono.just(userRequest));
			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(SYSTEM_CLIENT_ID)))
					.thenReturn(Mono.just(true));
			when(userDao.readById(USER_ID)).thenReturn(Mono.just(user));

			StepVerifier.create(service.getRequestUser("REQ-789"))
					.assertNext(result -> {
						assertEquals(USER_ID, result.getId());
						assertEquals("testuser", result.getUserName());
					})
					.verifyComplete();
		}

		@Test
		void getRequestUser_NotManaged_ReturnsEmpty() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(ULong.valueOf(2), "BUS",
					List.of("Authorities.User_READ", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			UserRequest userRequest = new UserRequest();
			userRequest.setId(ULong.valueOf(1));
			userRequest.setUserId(USER_ID);
			userRequest.setClientId(SYSTEM_CLIENT_ID);

			when(dao.readByRequestId("REQ-789")).thenReturn(Mono.just(userRequest));
			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(SYSTEM_CLIENT_ID)))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.getRequestUser("REQ-789"))
					.verifyComplete();
		}
	}
}
