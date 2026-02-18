package com.fincity.security.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.model.EntityProcessorUser;
import com.fincity.saas.commons.security.model.NotificationUser;
import com.fincity.saas.commons.security.model.UsersListRequest;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.Profile;
import com.fincity.security.dto.User;
import com.fincity.security.dto.UserClient;
import com.fincity.security.dto.UserInvite;
import com.fincity.security.dto.UserRequest;
import com.fincity.security.jooq.enums.SecurityClientStatusCode;
import com.fincity.security.jooq.enums.SecurityUserStatusCode;
import com.fincity.security.model.AuthenticationRequest;
import com.fincity.security.model.RegistrationResponse;
import com.fincity.security.model.RequestUpdatePassword;
import com.fincity.security.model.UserAppAccessRequest;
import com.fincity.security.model.UserRegistrationRequest;
import com.fincity.security.service.UserInviteService;
import com.fincity.security.service.UserRequestService;
import com.fincity.security.service.UserService;
import com.fincity.security.service.UserSubOrganizationService;
import com.fincity.security.testutil.TestWebSecurityConfig;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@WebFluxTest
@ContextConfiguration(classes = { UserController.class, TestWebSecurityConfig.class })
class UserControllerTest {

    private static final String BASE_PATH = "/api/security/users";

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private UserService userService;

    @MockBean
    private UserInviteService inviteService;

    @MockBean
    private UserSubOrganizationService userSubOrgService;

    @MockBean
    private UserRequestService requestService;

    private User sampleUser;
    private Profile sampleProfile;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setId(ULong.valueOf(1));
        sampleUser.setClientId(ULong.valueOf(10));
        sampleUser.setUserName("testuser");
        sampleUser.setEmailId("test@example.com");
        sampleUser.setFirstName("Test");
        sampleUser.setLastName("User");
        sampleUser.setStatusCode(SecurityUserStatusCode.ACTIVE);
        sampleUser.setAccountNonExpired(true);
        sampleUser.setAccountNonLocked(true);
        sampleUser.setCredentialsNonExpired(true);
        sampleUser.setNoFailedAttempt((short) 0);
        sampleUser.setNoPinFailedAttempt((short) 0);
        sampleUser.setNoOtpFailedAttempt((short) 0);
        sampleUser.setNoOtpResendAttempts((short) 0);

        sampleProfile = new Profile();
        sampleProfile.setId(ULong.valueOf(100));
        sampleProfile.setClientId(ULong.valueOf(10));
        sampleProfile.setAppId(ULong.valueOf(5));
        sampleProfile.setName("TestProfile");
        sampleProfile.setTitle("Test Profile");
    }

    // ==================== Profile Assignment ====================

    @Nested
    @DisplayName("GET /{userId}/assignProfile/{profileId}")
    class AssignProfileTests {

        @Test
        @DisplayName("Should return 200 with true when profile is assigned successfully")
        void assignProfile_Success_Returns200() {

            when(userService.assignProfileToUser(eq(ULong.valueOf(1)), eq(ULong.valueOf(100))))
                    .thenReturn(Mono.just(Boolean.TRUE));

            webTestClient.get()
                    .uri(BASE_PATH + "/1/assignProfile/100")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class).isEqualTo(true);

            verify(userService).assignProfileToUser(eq(ULong.valueOf(1)), eq(ULong.valueOf(100)));
        }

        @Test
        @DisplayName("Should return error when service throws forbidden for profile assignment")
        void assignProfile_Forbidden_ReturnsError() {

            when(userService.assignProfileToUser(any(ULong.class), any(ULong.class)))
                    .thenReturn(Mono.error(new GenericException(HttpStatus.FORBIDDEN, "No permission")));

            webTestClient.get()
                    .uri(BASE_PATH + "/1/assignProfile/100")
                    .exchange()
                    .expectStatus().isForbidden();

            verify(userService).assignProfileToUser(eq(ULong.valueOf(1)), eq(ULong.valueOf(100)));
        }
    }

    // ==================== Profile Removal ====================

    @Nested
    @DisplayName("GET /{userId}/removeProfile/{profileId}")
    class RemoveProfileTests {

        @Test
        @DisplayName("Should return 200 with true when profile is removed successfully")
        void removeProfile_Success_Returns200() {

            when(userService.removeProfileFromUser(eq(ULong.valueOf(1)), eq(ULong.valueOf(100))))
                    .thenReturn(Mono.just(Boolean.TRUE));

            webTestClient.get()
                    .uri(BASE_PATH + "/1/removeProfile/100")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class).isEqualTo(true);

            verify(userService).removeProfileFromUser(eq(ULong.valueOf(1)), eq(ULong.valueOf(100)));
        }
    }

    // ==================== Assigned Profiles ====================

    @Nested
    @DisplayName("GET /{userId}/app/{appId}/assignedProfiles")
    class AssignedProfilesTests {

        @Test
        @DisplayName("Should return 200 with list of assigned profiles")
        void assignedProfiles_Success_ReturnsList() {

            when(userService.assignedProfiles(eq(ULong.valueOf(1)), eq(ULong.valueOf(5))))
                    .thenReturn(Mono.just(List.of(sampleProfile)));

            webTestClient.get()
                    .uri(BASE_PATH + "/1/app/5/assignedProfiles")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[0].name").isEqualTo("TestProfile")
                    .jsonPath("$[0].title").isEqualTo("Test Profile");

            verify(userService).assignedProfiles(eq(ULong.valueOf(1)), eq(ULong.valueOf(5)));
        }
    }

    // ==================== Role Assignment ====================

    @Nested
    @DisplayName("Role Assignment and Removal")
    class RoleTests {

        @Test
        @DisplayName("Should return 200 with true when role is assigned successfully")
        void assignRole_Success_Returns200() {

            when(userService.assignRoleToUser(eq(ULong.valueOf(1)), eq(ULong.valueOf(50))))
                    .thenReturn(Mono.just(Boolean.TRUE));

            webTestClient.get()
                    .uri(BASE_PATH + "/1/assignRole/50")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class).isEqualTo(true);

            verify(userService).assignRoleToUser(eq(ULong.valueOf(1)), eq(ULong.valueOf(50)));
        }

        @Test
        @DisplayName("Should return 200 with true when role is removed successfully")
        void removeRole_Success_Returns200() {

            when(userService.removeRoleFromUser(eq(ULong.valueOf(1)), eq(ULong.valueOf(50))))
                    .thenReturn(Mono.just(Boolean.TRUE));

            webTestClient.get()
                    .uri(BASE_PATH + "/1/removeRole/50")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class).isEqualTo(true);

            verify(userService).removeRoleFromUser(eq(ULong.valueOf(1)), eq(ULong.valueOf(50)));
        }

        @Test
        @DisplayName("Should return error when role assignment fails with not found")
        void assignRole_NotFound_ReturnsError() {

            when(userService.assignRoleToUser(any(ULong.class), any(ULong.class)))
                    .thenReturn(Mono.error(new GenericException(HttpStatus.NOT_FOUND, "Role not found")));

            webTestClient.get()
                    .uri(BASE_PATH + "/1/assignRole/999")
                    .exchange()
                    .expectStatus().isNotFound();

            verify(userService).assignRoleToUser(eq(ULong.valueOf(1)), eq(ULong.valueOf(999)));
        }
    }

    // ==================== Password Update ====================

    @Nested
    @DisplayName("Password Update Endpoints")
    class PasswordUpdateTests {

        @Test
        @DisplayName("Should return 200 when password is updated with userId path variable")
        void updatePasswordWithUserId_Success_Returns200() {

            when(userService.updatePassword(eq(ULong.valueOf(1)), any(RequestUpdatePassword.class)))
                    .thenReturn(Mono.just(Boolean.TRUE));

            RequestUpdatePassword reqPassword = new RequestUpdatePassword();
            reqPassword.setOldPassword("oldpass");
            reqPassword.setNewPassword("NewPass123!");

            webTestClient.post()
                    .uri(BASE_PATH + "/1/updatePassword")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(reqPassword)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class).isEqualTo(true);

            verify(userService).updatePassword(eq(ULong.valueOf(1)), any(RequestUpdatePassword.class));
        }

        @Test
        @DisplayName("Should return 200 when password is updated without userId")
        void updatePasswordWithoutUserId_Success_Returns200() {

            when(userService.updatePassword(any(RequestUpdatePassword.class)))
                    .thenReturn(Mono.just(Boolean.TRUE));

            RequestUpdatePassword reqPassword = new RequestUpdatePassword();
            reqPassword.setOldPassword("oldpass");
            reqPassword.setNewPassword("NewPass123!");

            webTestClient.post()
                    .uri(BASE_PATH + "/updatePassword")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(reqPassword)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class).isEqualTo(true);

            verify(userService).updatePassword(any(RequestUpdatePassword.class));
        }

        @Test
        @DisplayName("Should return error when password update fails with bad request")
        void updatePassword_BadRequest_ReturnsError() {

            when(userService.updatePassword(any(RequestUpdatePassword.class)))
                    .thenReturn(Mono.error(
                            new GenericException(HttpStatus.BAD_REQUEST, "Password does not meet policy")));

            RequestUpdatePassword reqPassword = new RequestUpdatePassword();
            reqPassword.setNewPassword("weak");

            webTestClient.post()
                    .uri(BASE_PATH + "/updatePassword")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(reqPassword)
                    .exchange()
                    .expectStatus().isBadRequest();

            verify(userService).updatePassword(any(RequestUpdatePassword.class));
        }
    }

    // ==================== OTP Reset Password ====================

    @Nested
    @DisplayName("OTP Reset Password Endpoints")
    class OtpResetPasswordTests {

        @Test
        @DisplayName("Should return 200 with true when OTP is generated for password reset")
        void generateOtpResetPassword_Success_Returns200() {

            when(userService.generateOtpResetPassword(any(AuthenticationRequest.class), any()))
                    .thenReturn(Mono.just(Boolean.TRUE));

            AuthenticationRequest authReq = new AuthenticationRequest()
                    .setUserName("testuser@example.com");

            webTestClient.post()
                    .uri(BASE_PATH + "/reset/password/otp/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(authReq)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class).isEqualTo(true);

            verify(userService).generateOtpResetPassword(any(AuthenticationRequest.class), any());
        }

        @Test
        @DisplayName("Should return 200 with true when OTP is verified for password reset")
        void verifyOtpResetPassword_Success_Returns200() {

            when(userService.verifyOtpResetPassword(any(AuthenticationRequest.class)))
                    .thenReturn(Mono.just(Boolean.TRUE));

            AuthenticationRequest authReq = new AuthenticationRequest()
                    .setUserName("testuser@example.com")
                    .setOtp("123456");

            webTestClient.post()
                    .uri(BASE_PATH + "/reset/password/otp/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(authReq)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class).isEqualTo(true);

            verify(userService).verifyOtpResetPassword(any(AuthenticationRequest.class));
        }

        @Test
        @DisplayName("Should return 200 with true when password is reset")
        void resetPassword_Success_Returns200() {

            when(userService.resetPassword(any(RequestUpdatePassword.class)))
                    .thenReturn(Mono.just(Boolean.TRUE));

            RequestUpdatePassword reqPassword = new RequestUpdatePassword();
            reqPassword.setNewPassword("NewSecurePass123!");
            reqPassword.setAuthRequest(new AuthenticationRequest()
                    .setUserName("testuser@example.com")
                    .setOtp("123456"));

            webTestClient.post()
                    .uri(BASE_PATH + "/reset/password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(reqPassword)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class).isEqualTo(true);

            verify(userService).resetPassword(any(RequestUpdatePassword.class));
        }
    }

    // ==================== User Status (Active/Inactive/Unblock) ====================

    @Nested
    @DisplayName("User Status Management Endpoints")
    class UserStatusTests {

        @Test
        @DisplayName("Should return 200 with true when user is made active")
        void makeUserActive_Success_Returns200() {

            when(userService.makeUserActive(eq(ULong.valueOf(1))))
                    .thenReturn(Mono.just(Boolean.TRUE));

            webTestClient.patch()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/makeUserActive")
                            .queryParam("userId", "1")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class).isEqualTo(true);

            verify(userService).makeUserActive(eq(ULong.valueOf(1)));
        }

        @Test
        @DisplayName("Should return 200 with true when user is made inactive")
        void makeUserInActive_Success_Returns200() {

            when(userService.makeUserInActive(eq(ULong.valueOf(1))))
                    .thenReturn(Mono.just(Boolean.TRUE));

            webTestClient.patch()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/makeUserInActive")
                            .queryParam("userId", "1")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class).isEqualTo(true);

            verify(userService).makeUserInActive(eq(ULong.valueOf(1)));
        }

        @Test
        @DisplayName("Should return 200 with true when user is unblocked")
        void unblockUser_Success_Returns200() {

            when(userService.unblockUser(eq(ULong.valueOf(1))))
                    .thenReturn(Mono.just(Boolean.TRUE));

            webTestClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/unblockUser")
                            .queryParam("userId", "1")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class).isEqualTo(true);

            verify(userService).unblockUser(eq(ULong.valueOf(1)));
        }
    }

    // ==================== Find User Clients ====================

    @Nested
    @DisplayName("POST /findUserClients")
    class FindUserClientsTests {

        @Test
        @DisplayName("Should return 200 with filtered active user clients")
        void findUserClients_Success_ReturnsActiveClients() {

            Client activeClient = new Client();
            activeClient.setCode("ACTIVE_CLIENT");
            activeClient.setName("Active Client");
            activeClient.setStatusCode(SecurityClientStatusCode.ACTIVE);

            Client inactiveClient = new Client();
            inactiveClient.setCode("INACTIVE_CLIENT");
            inactiveClient.setName("Inactive Client");
            inactiveClient.setStatusCode(SecurityClientStatusCode.INACTIVE);

            UserClient activeUc = new UserClient()
                    .setUserId(ULong.valueOf(1))
                    .setClient(activeClient);

            UserClient inactiveUc = new UserClient()
                    .setUserId(ULong.valueOf(1))
                    .setClient(inactiveClient);

            when(userService.findUserClients(any(AuthenticationRequest.class), eq(true), any()))
                    .thenReturn(Mono.just(List.of(activeUc, inactiveUc)));

            AuthenticationRequest authReq = new AuthenticationRequest()
                    .setUserName("testuser");

            webTestClient.post()
                    .uri(BASE_PATH + "/findUserClients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(authReq)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.length()").isEqualTo(1)
                    .jsonPath("$[0].client.code").isEqualTo("ACTIVE_CLIENT");

            verify(userService).findUserClients(any(AuthenticationRequest.class), eq(true), any());
        }

        @Test
        @DisplayName("Should filter out clients with null client objects")
        void findUserClients_NullClients_FiltersOut() {

            UserClient nullClientUc = new UserClient()
                    .setUserId(ULong.valueOf(1))
                    .setClient(null);

            when(userService.findUserClients(any(AuthenticationRequest.class), eq(true), any()))
                    .thenReturn(Mono.just(List.of(nullClientUc)));

            webTestClient.post()
                    .uri(BASE_PATH + "/findUserClients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new AuthenticationRequest().setUserName("testuser"))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.length()").isEqualTo(0);

            verify(userService).findUserClients(any(AuthenticationRequest.class), eq(true), any());
        }
    }

    // ==================== User Invite ====================

    @Nested
    @DisplayName("User Invite Endpoints")
    class UserInviteTests {

        @Test
        @DisplayName("Should return 200 with invite details when invite is created")
        void inviteUser_Success_Returns200() {

            Map<String, Object> result = Map.of("inviteCode", "abc123", "emailId", "invite@test.com");

            when(inviteService.createInvite(any(UserInvite.class)))
                    .thenReturn(Mono.just(result));

            UserInvite invite = new UserInvite();
            invite.setEmailId("invite@test.com");
            invite.setFirstName("Invited");
            invite.setLastName("User");

            webTestClient.post()
                    .uri(BASE_PATH + "/invite")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(invite)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.inviteCode").isEqualTo("abc123")
                    .jsonPath("$.emailId").isEqualTo("invite@test.com");

            verify(inviteService).createInvite(any(UserInvite.class));
        }

        @Test
        @DisplayName("Should return 200 with invite details for a given code")
        void getInvite_Success_Returns200() {

            UserInvite invite = new UserInvite();
            invite.setInviteCode("abc123");
            invite.setEmailId("invite@test.com");
            invite.setFirstName("Invited");

            when(inviteService.getUserInvitation(eq("abc123")))
                    .thenReturn(Mono.just(invite));

            webTestClient.get()
                    .uri(BASE_PATH + "/inviteDetails/abc123")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.inviteCode").isEqualTo("abc123")
                    .jsonPath("$.emailId").isEqualTo("invite@test.com");

            verify(inviteService).getUserInvitation(eq("abc123"));
        }

        @Test
        @DisplayName("Should return 200 with RegistrationResponse when invite is accepted")
        void acceptInvite_Success_Returns200() {

            RegistrationResponse response = new RegistrationResponse()
                    .setCreated(true)
                    .setUserId(ULong.valueOf(99));

            when(inviteService.acceptInvite(any(UserRegistrationRequest.class), any(), any()))
                    .thenReturn(Mono.just(response));

            UserRegistrationRequest regReq = new UserRegistrationRequest()
                    .setUserName("newuser")
                    .setEmailId("new@test.com")
                    .setPassword("Password123!")
                    .setInviteCode("abc123");

            webTestClient.post()
                    .uri(BASE_PATH + "/acceptInvite")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(regReq)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.created").isEqualTo(true);

            verify(inviteService).acceptInvite(any(UserRegistrationRequest.class), any(), any());
        }

        @Test
        @DisplayName("Should return 200 with true when invite is rejected/deleted")
        void rejectInvite_Success_Returns200() {

            when(inviteService.deleteUserInvitation(eq("abc123")))
                    .thenReturn(Mono.just(Boolean.TRUE));

            webTestClient.delete()
                    .uri(BASE_PATH + "/invite/abc123")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class).isEqualTo(true);

            verify(inviteService).deleteUserInvitation(eq("abc123"));
        }

        @Test
        @DisplayName("Should return 200 with paginated invited users")
        void getAllInvitedUsers_Success_ReturnsPaged() {

            UserInvite invite = new UserInvite();
            invite.setEmailId("invite@test.com");
            invite.setFirstName("Invited");

            Page<UserInvite> page = new PageImpl<>(List.of(invite), PageRequest.of(0, 10), 1);

            when(inviteService.getAllInvitedUsers(any(), any()))
                    .thenReturn(Mono.just(page));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/invites")
                            .queryParam("page", "0")
                            .queryParam("size", "10")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content[0].emailId").isEqualTo("invite@test.com");

            verify(inviteService).getAllInvitedUsers(any(), any());
        }
    }

    // ==================== Internal Endpoints ====================

    @Nested
    @DisplayName("Internal Endpoints")
    class InternalEndpointTests {

        @Test
        @DisplayName("Should return 200 with user when internal getUserById is called")
        void getUserInternal_Success_Returns200() {

            when(userService.readById(eq(ULong.valueOf(1)), any()))
                    .thenReturn(Mono.just(sampleUser));

            webTestClient.get()
                    .uri(BASE_PATH + "/internal/1")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.userName").isEqualTo("testuser")
                    .jsonPath("$.emailId").isEqualTo("test@example.com");

            verify(userService).readById(eq(ULong.valueOf(1)), any());
        }

        @Test
        @DisplayName("Should return 200 with user list when internal getUsersByIds is called")
        void getUsersInternal_Success_ReturnsList() {

            when(userService.readByIds(anyList(), any()))
                    .thenReturn(Mono.just(List.of(sampleUser)));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/internal")
                            .queryParam("userIds", "1", "2")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[0].userName").isEqualTo("testuser");

            verify(userService).readByIds(anyList(), any());
        }

        @Test
        @DisplayName("Should return 200 with user list for batch internal endpoint")
        void getUsersInternalBatch_Success_ReturnsList() {

            when(userService.readByIds(anyList(), any()))
                    .thenReturn(Mono.just(List.of(sampleUser)));

            webTestClient.post()
                    .uri(BASE_PATH + "/internal/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(List.of(BigInteger.ONE, BigInteger.TWO))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[0].userName").isEqualTo("testuser");

            verify(userService).readByIds(anyList(), any());
        }

        @Test
        @DisplayName("Should return 200 with users for client IDs internal endpoint")
        void getClientUsersInternal_Success_ReturnsList() {

            when(userService.readByClientIds(anyList(), any(), any()))
                    .thenReturn(Mono.just(List.of(sampleUser)));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/internal/clients")
                            .queryParam("clientIds", "10", "20")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[0].userName").isEqualTo("testuser");

            verify(userService).readByClientIds(anyList(), any(), any());
        }

        @Test
        @DisplayName("Should return 200 with users for batch client IDs internal endpoint")
        void getClientUsersInternalBatch_Success_ReturnsList() {

            when(userService.readByClientIds(anyList(), any(), any()))
                    .thenReturn(Mono.just(List.of(sampleUser)));

            webTestClient.post()
                    .uri(BASE_PATH + "/internal/clients/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(List.of(BigInteger.TEN))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[0].userName").isEqualTo("testuser");

            verify(userService).readByClientIds(anyList(), any(), any());
        }

        @Test
        @DisplayName("Should return 200 with admin emails map")
        void getUserAdminEmails_Success_ReturnsMap() {

            Map<String, Object> emailsMap = Map.of("admin", "admin@example.com");

            when(userService.getUserAdminEmails(any()))
                    .thenReturn(Mono.just(emailsMap));

            webTestClient.get()
                    .uri(BASE_PATH + "/internal/adminEmails")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.admin").isEqualTo("admin@example.com");

            verify(userService).getUserAdminEmails(any());
        }

        @Test
        @DisplayName("Should return 200 with notification users")
        void getUsersForNotification_Success_ReturnsList() {

            NotificationUser nu = new NotificationUser()
                    .setId(1L)
                    .setClientId(10L)
                    .setEmailId("test@example.com");

            when(userService.getUsersForNotification(any(UsersListRequest.class)))
                    .thenReturn(Mono.just(List.of(nu)));

            UsersListRequest req = new UsersListRequest()
                    .setUserIds(List.of(1L))
                    .setClientCode("TESTCLIENT");

            webTestClient.post()
                    .uri(BASE_PATH + "/internal/notification")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(req)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[0].emailId").isEqualTo("test@example.com");

            verify(userService).getUsersForNotification(any(UsersListRequest.class));
        }

        @Test
        @DisplayName("Should return 200 with entity processor users")
        void getUsersForEntityProcessor_Success_ReturnsList() {

            EntityProcessorUser epu = new EntityProcessorUser()
                    .setId(1L)
                    .setRoleId(5L)
                    .setDesignationId(10L);

            when(userService.getUsersForEntityProcessor(any(UsersListRequest.class)))
                    .thenReturn(Mono.just(List.of(epu)));

            UsersListRequest req = new UsersListRequest()
                    .setUserIds(List.of(1L));

            webTestClient.post()
                    .uri(BASE_PATH + "/internal/processor")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(req)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[0].id").isEqualTo(1);

            verify(userService).getUsersForEntityProcessor(any(UsersListRequest.class));
        }

        @Test
        @DisplayName("Should return 200 with single entity processor user for userId")
        void getUserForEntityProcessor_Success_ReturnsUser() {

            EntityProcessorUser epu = new EntityProcessorUser()
                    .setId(1L)
                    .setRoleId(5L);

            when(userService.getUserForEntityProcessor(eq(ULong.valueOf(1)), any(UsersListRequest.class)))
                    .thenReturn(Mono.just(epu));

            UsersListRequest req = new UsersListRequest()
                    .setClientCode("TESTCLIENT");

            webTestClient.post()
                    .uri(BASE_PATH + "/internal/1/processor")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(req)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo(1)
                    .jsonPath("$.roleId").isEqualTo(5);

            verify(userService).getUserForEntityProcessor(eq(ULong.valueOf(1)), any(UsersListRequest.class));
        }

        @Test
        @DisplayName("Should return 200 with paginated users for internal query")
        void readPageFilterInternal_Success_ReturnsPage() {

            Page<User> page = new PageImpl<>(List.of(sampleUser), PageRequest.of(0, 10), 1);

            when(userService.readPageFilterInternal(any(), any()))
                    .thenReturn(Mono.just(page));
            when(userService.fillDetails(anyList(), any()))
                    .thenReturn(Mono.just(List.of(sampleUser)));

            Query query = new Query();
            query.setPage(0);
            query.setSize(10);

            webTestClient.post()
                    .uri(BASE_PATH + "/internal/query")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(query)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content[0].userName").isEqualTo("testuser");

            verify(userService).readPageFilterInternal(any(), any());
        }
    }

    // ==================== User Exists ====================

    @Nested
    @DisplayName("GET /exists")
    class UserExistsTests {

        @Test
        @DisplayName("Should return 200 with true when user exists by username")
        void exists_ByUsername_ReturnsTrue() {

            when(userService.checkUserExistsAcrossApps(eq("testuser"), isNull(), isNull()))
                    .thenReturn(Mono.just(Boolean.TRUE));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/exists")
                            .queryParam("username", "testuser")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class).isEqualTo(true);

            verify(userService).checkUserExistsAcrossApps(eq("testuser"), isNull(), isNull());
        }

        @Test
        @DisplayName("Should return 200 with false when user does not exist")
        void exists_UserNotFound_ReturnsFalse() {

            when(userService.checkUserExistsAcrossApps(isNull(), eq("nobody@test.com"), isNull()))
                    .thenReturn(Mono.just(Boolean.FALSE));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/exists")
                            .queryParam("email", "nobody@test.com")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class).isEqualTo(false);

            verify(userService).checkUserExistsAcrossApps(isNull(), eq("nobody@test.com"), isNull());
        }
    }

    // ==================== Sub-Organization ====================

    @Nested
    @DisplayName("Sub-Organization Endpoints")
    class SubOrganizationTests {

        @Test
        @DisplayName("Should return 200 with list of sub-org user IDs for current user")
        void getCurrentUserSubOrgUserIds_Success_ReturnsList() {

            when(userSubOrgService.getCurrentUserSubOrg())
                    .thenReturn(Flux.just(ULong.valueOf(1), ULong.valueOf(2), ULong.valueOf(3)));

            webTestClient.get()
                    .uri(BASE_PATH + "/sub-org")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.length()").isEqualTo(3);

            verify(userSubOrgService).getCurrentUserSubOrg();
        }

        @Test
        @DisplayName("Should return 200 with sub-org user IDs for internal endpoint")
        void getUserSubOrgInternal_Success_ReturnsList() {

            when(userSubOrgService.getUserSubOrgInternal(eq("testapp"), eq(ULong.valueOf(10)), eq(ULong.valueOf(1))))
                    .thenReturn(Flux.just(ULong.valueOf(1), ULong.valueOf(2)));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/internal/1/sub-org")
                            .queryParam("appCode", "testapp")
                            .queryParam("clientId", "10")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.length()").isEqualTo(2);

            verify(userSubOrgService).getUserSubOrgInternal(eq("testapp"), eq(ULong.valueOf(10)), eq(ULong.valueOf(1)));
        }
    }

    // ==================== Reporting Manager & Designation ====================

    @Nested
    @DisplayName("Reporting Manager and Designation Endpoints")
    class ReportingAndDesignationTests {

        @Test
        @DisplayName("Should return 200 with updated user when reporting manager is changed")
        void updateReportingManager_Success_Returns200() {

            when(userSubOrgService.updateManager(eq(ULong.valueOf(1)), eq(ULong.valueOf(5))))
                    .thenReturn(Mono.just(sampleUser));

            webTestClient.put()
                    .uri(BASE_PATH + "/1/reportingManager/5")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.userName").isEqualTo("testuser");

            verify(userSubOrgService).updateManager(eq(ULong.valueOf(1)), eq(ULong.valueOf(5)));
        }

        @Test
        @DisplayName("Should return 200 with updated user when designation is changed")
        void updateDesignation_Success_Returns200() {

            when(userService.updateDesignation(eq(ULong.valueOf(1)), eq(ULong.valueOf(20))))
                    .thenReturn(Mono.just(sampleUser));

            webTestClient.put()
                    .uri(BASE_PATH + "/1/designation/20")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.userName").isEqualTo("testuser");

            verify(userService).updateDesignation(eq(ULong.valueOf(1)), eq(ULong.valueOf(20)));
        }
    }

    // ==================== User Request ====================

    @Nested
    @DisplayName("User Request Endpoints")
    class UserRequestTests {

        @Test
        @DisplayName("Should return 200 with UserRequest when request is created")
        void userRequest_Success_Returns200() {

            UserRequest userRequest = new UserRequest();
            userRequest.setRequestId("req-uuid-123");

            when(requestService.createRequest(any(UserAppAccessRequest.class)))
                    .thenReturn(Mono.just(userRequest));

            UserAppAccessRequest appReq = new UserAppAccessRequest()
                    .setAppCode("testapp")
                    .setCallbackUrl("https://app.example.com");

            webTestClient.post()
                    .uri(BASE_PATH + "/request")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(appReq)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.requestId").isEqualTo("req-uuid-123");

            verify(requestService).createRequest(any(UserAppAccessRequest.class));
        }

        @Test
        @DisplayName("Should return 200 with true when request is accepted")
        void acceptRequest_Success_Returns200() {

            when(requestService.acceptRequest(any(UserAppAccessRequest.class), any(), any()))
                    .thenReturn(Mono.just(Boolean.TRUE));

            UserAppAccessRequest appReq = new UserAppAccessRequest()
                    .setRequestId("req-uuid-123")
                    .setProfileId(ULong.valueOf(100));

            webTestClient.post()
                    .uri(BASE_PATH + "/acceptRequest")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(appReq)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class).isEqualTo(true);

            verify(requestService).acceptRequest(any(UserAppAccessRequest.class), any(), any());
        }

        @Test
        @DisplayName("Should return 200 with true when request is rejected")
        void rejectRequest_Success_Returns200() {

            when(requestService.rejectRequest(eq("req-uuid-123")))
                    .thenReturn(Mono.just(Boolean.TRUE));

            webTestClient.post()
                    .uri(BASE_PATH + "/rejectRequest/req-uuid-123")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class).isEqualTo(true);

            verify(requestService).rejectRequest(eq("req-uuid-123"));
        }

        @Test
        @DisplayName("Should return 200 with user from request ID")
        void getUserFromRequestId_Success_Returns200() {

            when(requestService.getRequestUser(eq("req-uuid-123")))
                    .thenReturn(Mono.just(sampleUser));

            webTestClient.get()
                    .uri(BASE_PATH + "/requestUser/req-uuid-123")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.userName").isEqualTo("testuser");

            verify(requestService).getRequestUser(eq("req-uuid-123"));
        }
    }

    // ==================== CRUD from Base Controller ====================

    @Nested
    @DisplayName("Base Controller CRUD Endpoints")
    class BaseCrudTests {

        @Test
        @DisplayName("Should return 200 with user when created via POST")
        void create_Success_Returns200() {

            when(userService.create(any(User.class)))
                    .thenReturn(Mono.just(sampleUser));

            User newUser = new User();
            newUser.setUserName("newuser");
            newUser.setEmailId("new@test.com");
            newUser.setFirstName("New");
            newUser.setLastName("User");
            newUser.setStatusCode(SecurityUserStatusCode.ACTIVE);

            webTestClient.post()
                    .uri(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(newUser)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.userName").isEqualTo("testuser");

            verify(userService).create(any(User.class));
        }

        @Test
        @DisplayName("Should return 200 with user when read by ID")
        void read_Success_Returns200() {

            when(userService.read(eq(ULong.valueOf(1))))
                    .thenReturn(Mono.just(sampleUser));

            webTestClient.get()
                    .uri(BASE_PATH + "/1")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.userName").isEqualTo("testuser")
                    .jsonPath("$.firstName").isEqualTo("Test");

            verify(userService).read(eq(ULong.valueOf(1)));
        }

        @Test
        @DisplayName("Should return 404 when user is not found by ID")
        void read_NotFound_Returns404() {

            when(userService.read(eq(ULong.valueOf(999))))
                    .thenReturn(Mono.empty());

            webTestClient.get()
                    .uri(BASE_PATH + "/999")
                    .exchange()
                    .expectStatus().isNotFound();

            verify(userService).read(eq(ULong.valueOf(999)));
        }

        @Test
        @DisplayName("Should return 204 when user is deleted")
        void delete_Success_Returns204() {

            when(userService.delete(eq(ULong.valueOf(1))))
                    .thenReturn(Mono.just(1));

            webTestClient.delete()
                    .uri(BASE_PATH + "/1")
                    .exchange()
                    .expectStatus().isNoContent();

            verify(userService).delete(eq(ULong.valueOf(1)));
        }

        @Test
        @DisplayName("Should return 200 with updated user on PUT")
        void put_Success_Returns200() {

            when(userService.update(any(User.class)))
                    .thenReturn(Mono.just(sampleUser));

            User updatedUser = new User();
            updatedUser.setUserName("updateduser");
            updatedUser.setEmailId("updated@test.com");
            updatedUser.setStatusCode(SecurityUserStatusCode.ACTIVE);

            webTestClient.put()
                    .uri(BASE_PATH + "/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(updatedUser)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.userName").isEqualTo("testuser");

            verify(userService).update(any(User.class));
        }

        @Test
        @DisplayName("Should return 200 with patched user on PATCH")
        void patch_Success_Returns200() {

            when(userService.update(eq(ULong.valueOf(1)), anyMap()))
                    .thenReturn(Mono.just(sampleUser));

            webTestClient.patch()
                    .uri(BASE_PATH + "/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("firstName", "Updated"))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.userName").isEqualTo("testuser");

            verify(userService).update(eq(ULong.valueOf(1)), anyMap());
        }

        @Test
        @DisplayName("Should return paginated users on GET with query params")
        void readPageFilter_Success_ReturnsPage() {

            Page<User> page = new PageImpl<>(List.of(sampleUser), PageRequest.of(0, 10), 1);

            when(userService.readPageFilter(any(), nullable(AbstractCondition.class)))
                    .thenReturn(Mono.just(page));
            when(userService.fillDetails(anyList(), any()))
                    .thenReturn(Mono.just(List.of(sampleUser)));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH)
                            .queryParam("page", "0")
                            .queryParam("size", "10")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content[0].userName").isEqualTo("testuser");

            verify(userService).readPageFilter(any(), nullable(AbstractCondition.class));
        }

        @Test
        @DisplayName("Should return bad request for noMapping endpoint")
        void readPageFilter_NoMapping_ReturnsBadRequest() {

            webTestClient.post()
                    .uri(BASE_PATH + "/noMapping")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new Query())
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }
}
