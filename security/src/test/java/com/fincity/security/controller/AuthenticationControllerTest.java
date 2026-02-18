package com.fincity.security.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.UserAccess;
import com.fincity.security.model.AuthenticationRequest;
import com.fincity.security.model.AuthenticationResponse;
import com.fincity.security.model.MakeOneTimeTimeTokenRequest;
import com.fincity.security.model.UserAppAccessRequest;
import com.fincity.security.service.AuthenticationService;
import com.fincity.security.service.ClientService;
import com.fincity.security.testutil.TestWebSecurityConfig;

import reactor.core.publisher.Mono;

@WebFluxTest
@ContextConfiguration(classes = { AuthenticationController.class, TestWebSecurityConfig.class })
class AuthenticationControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private AuthenticationService authenticationService;

    @MockitoBean
    private ClientService clientService;

    private AuthenticationResponse sampleAuthResponse;
    private ContextUser sampleContextUser;
    private Client sampleClient;

    @BeforeEach
    void setUp() {
        sampleContextUser = new ContextUser()
                .setId(BigInteger.ONE)
                .setClientId(BigInteger.valueOf(10))
                .setUserName("testuser")
                .setEmailId("test@example.com")
                .setFirstName("Test")
                .setLastName("User");

        sampleClient = new Client();
        sampleClient.setCode("TESTCLIENT");
        sampleClient.setName("Test Client");
        sampleClient.setTokenValidityMinutes(30);

        sampleAuthResponse = new AuthenticationResponse()
                .setUser(sampleContextUser)
                .setClient(sampleClient)
                .setAccessToken("test-jwt-token-abc123")
                .setAccessTokenExpiryAt(LocalDateTime.now().plusMinutes(30))
                .setVerifiedAppCode("testapp")
                .setLoggedInClientCode("TESTCLIENT")
                .setLoggedInClientId(BigInteger.valueOf(10))
                .setManagedClientCode("MANAGED")
                .setManagedClientId(BigInteger.valueOf(20));
    }

    // ==================== POST /api/security/authenticate ====================

    @Nested
    @DisplayName("POST /api/security/authenticate")
    class AuthenticateTests {

        @Test
        @DisplayName("Should return 200 with AuthenticationResponse on valid credentials")
        void authenticate_ValidCredentials_Returns200() {

            when(authenticationService.authenticate(any(AuthenticationRequest.class), any(), any()))
                    .thenReturn(Mono.just(sampleAuthResponse));

            webTestClient.post()
                    .uri("/api/security/authenticate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new AuthenticationRequest()
                            .setUserName("testuser")
                            .setPassword("password123"))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.accessToken").isEqualTo("test-jwt-token-abc123")
                    .jsonPath("$.user.userName").isEqualTo("testuser")
                    .jsonPath("$.verifiedAppCode").isEqualTo("testapp")
                    .jsonPath("$.loggedInClientCode").isEqualTo("TESTCLIENT");

            verify(authenticationService).authenticate(any(AuthenticationRequest.class), any(), any());
        }

        @Test
        @DisplayName("Should return 403 when service throws GenericException for invalid credentials")
        void authenticate_InvalidCredentials_Returns403() {

            when(authenticationService.authenticate(any(AuthenticationRequest.class), any(), any()))
                    .thenReturn(Mono.error(new GenericException(HttpStatus.FORBIDDEN, "User credentials mismatched")));

            webTestClient.post()
                    .uri("/api/security/authenticate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new AuthenticationRequest()
                            .setUserName("baduser")
                            .setPassword("wrongpass"))
                    .exchange()
                    .expectStatus().isForbidden();

            verify(authenticationService).authenticate(any(AuthenticationRequest.class), any(), any());
        }

        @Test
        @DisplayName("Should return 200 with empty body when service returns empty Mono")
        void authenticate_ServiceReturnsEmpty_Returns200WithEmptyBody() {

            when(authenticationService.authenticate(any(AuthenticationRequest.class), any(), any()))
                    .thenReturn(Mono.empty());

            webTestClient.post()
                    .uri("/api/security/authenticate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new AuthenticationRequest()
                            .setUserName("unknownuser")
                            .setPassword("somepass"))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody().isEmpty();

            verify(authenticationService).authenticate(any(AuthenticationRequest.class), any(), any());
        }
    }

    // ==================== POST /api/security/authenticate/social ====================

    @Nested
    @DisplayName("POST /api/security/authenticate/social")
    class AuthenticateSocialTests {

        @Test
        @DisplayName("Should return 200 with AuthenticationResponse on valid social authentication")
        void authenticateWSocial_ValidRequest_Returns200() {

            when(authenticationService.authenticateWSocial(any(AuthenticationRequest.class), any(), any()))
                    .thenReturn(Mono.just(sampleAuthResponse));

            AuthenticationRequest socialRequest = new AuthenticationRequest()
                    .setUserName("socialuser@example.com")
                    .setSocialRegisterState("valid-social-state-token");

            webTestClient.post()
                    .uri("/api/security/authenticate/social")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(socialRequest)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.accessToken").isEqualTo("test-jwt-token-abc123")
                    .jsonPath("$.user.emailId").isEqualTo("test@example.com");

            verify(authenticationService).authenticateWSocial(any(AuthenticationRequest.class), any(), any());
        }

        @Test
        @DisplayName("Should return 400 when social register state is missing")
        void authenticateWSocial_MissingSocialState_Returns400() {

            when(authenticationService.authenticateWSocial(any(AuthenticationRequest.class), any(), any()))
                    .thenReturn(Mono.error(
                            new GenericException(HttpStatus.BAD_REQUEST, "Social login failed")));

            webTestClient.post()
                    .uri("/api/security/authenticate/social")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new AuthenticationRequest()
                            .setUserName("socialuser@example.com"))
                    .exchange()
                    .expectStatus().isBadRequest();

            verify(authenticationService).authenticateWSocial(any(AuthenticationRequest.class), any(), any());
        }
    }

    // ==================== POST /api/security/authenticate/otp/generate ====================

    @Nested
    @DisplayName("POST /api/security/authenticate/otp/generate")
    class GenerateOtpTests {

        @Test
        @DisplayName("Should return 200 with true when OTP is generated successfully")
        void generateOtp_ValidRequest_Returns200WithTrue() {

            when(authenticationService.generateOtp(any(AuthenticationRequest.class), any()))
                    .thenReturn(Mono.just(Boolean.TRUE));

            AuthenticationRequest otpRequest = new AuthenticationRequest()
                    .setUserName("testuser@example.com")
                    .setGenerateOtp(true);

            webTestClient.post()
                    .uri("/api/security/authenticate/otp/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(otpRequest)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class).isEqualTo(true);

            verify(authenticationService).generateOtp(any(AuthenticationRequest.class), any());
        }

        @Test
        @DisplayName("Should return 200 with false when generateOtp flag is false")
        void generateOtp_FlagFalse_Returns200WithFalse() {

            when(authenticationService.generateOtp(any(AuthenticationRequest.class), any()))
                    .thenReturn(Mono.just(Boolean.FALSE));

            AuthenticationRequest otpRequest = new AuthenticationRequest()
                    .setUserName("testuser@example.com")
                    .setGenerateOtp(false);

            webTestClient.post()
                    .uri("/api/security/authenticate/otp/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(otpRequest)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class).isEqualTo(false);

            verify(authenticationService).generateOtp(any(AuthenticationRequest.class), any());
        }
    }

    // ==================== GET /api/security/revoke ====================

    @Nested
    @DisplayName("GET /api/security/revoke")
    class RevokeTests {

        @Test
        @DisplayName("Should return 200 on successful token revocation")
        void revoke_ValidRequest_Returns200() {

            when(authenticationService.revoke(eq(false), any()))
                    .thenReturn(Mono.just(1));

            webTestClient.get()
                    .uri("/api/security/revoke")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Void.class);

            verify(authenticationService).revoke(eq(false), any());
        }

        @Test
        @DisplayName("Should pass ssoLogout=true to service when query param is set")
        void revoke_WithSsoLogout_PassesTrueToService() {

            when(authenticationService.revoke(eq(true), any()))
                    .thenReturn(Mono.just(1));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/security/revoke")
                            .queryParam("ssoLogout", "true")
                            .build())
                    .exchange()
                    .expectStatus().isOk();

            verify(authenticationService).revoke(eq(true), any());
        }
    }

    // ==================== GET /api/security/refreshToken ====================

    @Nested
    @DisplayName("GET /api/security/refreshToken")
    class RefreshTokenTests {

        @Test
        @DisplayName("Should return 200 with refreshed AuthenticationResponse")
        void refreshToken_ValidToken_Returns200() {

            when(authenticationService.refreshToken(any(), any()))
                    .thenReturn(Mono.just(sampleAuthResponse));

            webTestClient.get()
                    .uri("/api/security/refreshToken")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.accessToken").isEqualTo("test-jwt-token-abc123")
                    .jsonPath("$.user.userName").isEqualTo("testuser");

            verify(authenticationService).refreshToken(any(), any());
        }

        @Test
        @DisplayName("Should return 401 when token refresh fails due to unauthorized")
        void refreshToken_Unauthorized_Returns401() {

            when(authenticationService.refreshToken(any(), any()))
                    .thenReturn(Mono.error(new GenericException(HttpStatus.UNAUTHORIZED, "Unauthorized")));

            webTestClient.get()
                    .uri("/api/security/refreshToken")
                    .exchange()
                    .expectStatus().isUnauthorized();

            verify(authenticationService).refreshToken(any(), any());
        }
    }

    // ==================== POST /api/security/makeOneTimeToken ====================

    @Nested
    @DisplayName("POST /api/security/makeOneTimeToken")
    class MakeOneTimeTokenTests {

        @Test
        @DisplayName("Should return 200 with token and url on successful creation")
        void makeOneTimeToken_ValidRequest_Returns200() {

            Map<String, String> tokenMap = Map.of(
                    "token", "ott-abc123",
                    "url", "https://example.com/callback?token=ott-abc123");

            when(authenticationService.makeOneTimeToken(any(MakeOneTimeTimeTokenRequest.class), any()))
                    .thenReturn(Mono.just(tokenMap));

            MakeOneTimeTimeTokenRequest request = new MakeOneTimeTimeTokenRequest();
            request.setCallbackUrl("https://example.com/callback?token={token}");
            request.setRememberMe(false);

            webTestClient.post()
                    .uri("/api/security/makeOneTimeToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.token").isEqualTo("ott-abc123")
                    .jsonPath("$.url").isEqualTo("https://example.com/callback?token=ott-abc123");

            verify(authenticationService).makeOneTimeToken(any(MakeOneTimeTimeTokenRequest.class), any());
        }

        @Test
        @DisplayName("Should return 401 when user is not authenticated")
        void makeOneTimeToken_Unauthenticated_Returns401() {

            when(authenticationService.makeOneTimeToken(any(MakeOneTimeTimeTokenRequest.class), any()))
                    .thenReturn(Mono.error(new GenericException(HttpStatus.UNAUTHORIZED, "Unauthorized")));

            MakeOneTimeTimeTokenRequest request = new MakeOneTimeTimeTokenRequest();
            request.setCallbackUrl("https://example.com/callback");

            webTestClient.post()
                    .uri("/api/security/makeOneTimeToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isUnauthorized();

            verify(authenticationService).makeOneTimeToken(any(MakeOneTimeTimeTokenRequest.class), any());
        }
    }

    // ==================== GET /api/security/authenticateWithOneTimeToken/{pathToken} ====================

    @Nested
    @DisplayName("GET /api/security/authenticateWithOneTimeToken/{pathToken}")
    class AuthenticateWithOneTimeTokenTests {

        @Test
        @DisplayName("Should return 200 with ContextAuthentication when path token is valid")
        void authenticateWithOneTimeToken_ValidPathToken_Returns200() {

            when(authenticationService.authenticateWithOneTimeToken(eq("valid-ott-token"), any(), any()))
                    .thenReturn(Mono.just(sampleAuthResponse));

            webTestClient.get()
                    .uri("/api/security/authenticateWithOneTimeToken/valid-ott-token")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.accessToken").isEqualTo("test-jwt-token-abc123")
                    .jsonPath("$.user.userName").isEqualTo("testuser");

            verify(authenticationService).authenticateWithOneTimeToken(eq("valid-ott-token"), any(), any());
        }

        @Test
        @DisplayName("Should return 403 when one-time token is invalid or expired")
        void authenticateWithOneTimeToken_InvalidToken_Returns403() {

            when(authenticationService.authenticateWithOneTimeToken(eq("expired-token"), any(), any()))
                    .thenReturn(Mono.error(
                            new GenericException(HttpStatus.FORBIDDEN, "User credentials mismatched")));

            webTestClient.get()
                    .uri("/api/security/authenticateWithOneTimeToken/expired-token")
                    .exchange()
                    .expectStatus().isForbidden();

            verify(authenticationService).authenticateWithOneTimeToken(eq("expired-token"), any(), any());
        }

        @Test
        @DisplayName("Should use path variable token value in service call")
        void authenticateWithOneTimeToken_PathVariable_Returns200() {

            when(authenticationService.authenticateWithOneTimeToken(eq("path-token-value"), any(), any()))
                    .thenReturn(Mono.just(sampleAuthResponse));

            webTestClient.get()
                    .uri("/api/security/authenticateWithOneTimeToken/path-token-value")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.accessToken").isEqualTo("test-jwt-token-abc123");

            verify(authenticationService).authenticateWithOneTimeToken(eq("path-token-value"), any(), any());
        }
    }

    // ==================== POST /api/security/user/access ====================

    @Nested
    @DisplayName("POST /api/security/user/access")
    class UserAccessTests {

        @Test
        @DisplayName("Should return 200 with UserAccess when user has app access")
        void getUserAccess_UserHasAccess_Returns200() {

            UserAccess userAccess = new UserAccess()
                    .setApp(true)
                    .setOwner(false)
                    .setClientAccess(true)
                    .setAppOneTimeToken("ott-for-app")
                    .setAppURL("https://app.example.com?token=ott-for-app");

            when(authenticationService.getUserAppAccess(any(UserAppAccessRequest.class), any()))
                    .thenReturn(Mono.just(userAccess));

            UserAppAccessRequest request = new UserAppAccessRequest()
                    .setAppCode("myapp")
                    .setCallbackUrl("https://app.example.com?token={token}");

            webTestClient.post()
                    .uri("/api/security/user/access")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.app").isEqualTo(true)
                    .jsonPath("$.owner").isEqualTo(false)
                    .jsonPath("$.clientAccess").isEqualTo(true)
                    .jsonPath("$.appOneTimeToken").isEqualTo("ott-for-app")
                    .jsonPath("$.appURL").isEqualTo("https://app.example.com?token=ott-for-app");

            verify(authenticationService).getUserAppAccess(any(UserAppAccessRequest.class), any());
        }

        @Test
        @DisplayName("Should return 401 when user has no access to the app")
        void getUserAccess_NoAccess_Returns401() {

            when(authenticationService.getUserAppAccess(any(UserAppAccessRequest.class), any()))
                    .thenReturn(Mono.error(
                            new GenericException(HttpStatus.UNAUTHORIZED, "access denied for app code: someapp")));

            UserAppAccessRequest request = new UserAppAccessRequest()
                    .setAppCode("someapp")
                    .setCallbackUrl("https://app.example.com");

            webTestClient.post()
                    .uri("/api/security/user/access")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isUnauthorized();

            verify(authenticationService).getUserAppAccess(any(UserAppAccessRequest.class), any());
        }
    }

    // ==================== GET /api/security/verifyToken ====================

    @Nested
    @DisplayName("GET /api/security/verifyToken")
    class VerifyTokenTests {

        @Test
        @DisplayName("Should return 401 when no security context is available")
        void verifyToken_NoSecurityContext_Returns401() {

            // Without a security context, SecurityContextUtil returns empty Mono.
            // The controller's switchIfEmpty returns 401 UNAUTHORIZED.
            webTestClient.get()
                    .uri("/api/security/verifyToken")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    // ==================== GET /api/security/internal/securityContextAuthentication ====================

    @Nested
    @DisplayName("GET /api/security/internal/securityContextAuthentication")
    class ContextAuthenticationTests {

        @Test
        @DisplayName("Should return 200 with empty body when no security context is available")
        void contextAuthentication_NoContext_ReturnsEmptyBody() {

            // Without a security context, SecurityContextUtil::getUsersContextAuthentication
            // returns empty, so the chain produces an empty Mono resulting in 200 with empty body.
            webTestClient.get()
                    .uri("/api/security/internal/securityContextAuthentication")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody().isEmpty();
        }
    }

    // ==================== Additional edge-case tests ====================

    @Nested
    @DisplayName("Edge cases and error scenarios")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should return 500 when authenticate service throws unexpected RuntimeException")
        void authenticate_ServiceThrowsRuntimeException_Returns500() {

            when(authenticationService.authenticate(any(AuthenticationRequest.class), any(), any()))
                    .thenReturn(Mono.error(new RuntimeException("Unexpected internal error")));

            webTestClient.post()
                    .uri("/api/security/authenticate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new AuthenticationRequest()
                            .setUserName("testuser")
                            .setPassword("password123"))
                    .exchange()
                    .expectStatus().is5xxServerError();

            verify(authenticationService).authenticate(any(AuthenticationRequest.class), any(), any());
        }

        @Test
        @DisplayName("Should return 500 when revoke service throws unexpected error")
        void revoke_ServiceThrowsRuntimeException_Returns500() {

            when(authenticationService.revoke(eq(false), any()))
                    .thenReturn(Mono.error(new RuntimeException("Unexpected error")));

            webTestClient.get()
                    .uri("/api/security/revoke")
                    .exchange()
                    .expectStatus().is5xxServerError();

            verify(authenticationService).revoke(eq(false), any());
        }
    }
}
