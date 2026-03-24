package com.fincity.saas.inttest.base;

import io.restassured.response.Response;

import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Helper for security service REST API calls (registration, authentication).
 * Registration endpoints are public (no auth required).
 * All calls go through the nginx reverse proxy.
 */
public class SecurityApi {

    private static final String SEC = "/api/security";

    private final String baseHost;
    private final String forwardedHost;

    public SecurityApi(String baseHost) {
        this.baseHost = baseHost.endsWith("/") ? baseHost.substring(0, baseHost.length() - 1) : baseHost;
        this.forwardedHost = this.baseHost.replaceFirst("https?://", "");
    }

    /**
     * Step 1: Generate OTP for registration.
     */
    public Response generateRegistrationOtp(String clientCode, String appCode, String emailId) {
        return given()
                .baseUri(baseHost + "/" + appCode + "/" + clientCode + "/page")
                .header("clientCode", clientCode)
                .header("appCode", appCode)
                .header("X-Forwarded-Host", forwardedHost)
                .header("X-Real-IP", "127.0.0.1")
                .contentType("application/json")
                .body(Map.of("emailId", emailId, "purpose", "REGISTRATION"))
                .post(SEC + "/clients/register/otp/generate");
    }

    /**
     * Step 2: Register a new client + user (with OTP).
     */
    public Response register(String clientCode, String appCode, Map<String, Object> body) {
        return given()
                .baseUri(baseHost + "/" + appCode + "/" + clientCode + "/page")
                .header("clientCode", clientCode)
                .header("appCode", appCode)
                .header("X-Forwarded-Host", forwardedHost)
                .header("X-Real-IP", "127.0.0.1")
                .contentType("application/json")
                .body(body)
                .post(SEC + "/clients/register");
    }

    /**
     * Invite a user (requires builder admin auth).
     */
    public Response inviteUser(String token, String clientCode, String appCode, Map<String, Object> body) {
        return given()
                .baseUri(baseHost + "/" + appCode + "/" + clientCode + "/page")
                .header("Authorization", "Bearer " + token)
                .header("clientCode", clientCode)
                .header("appCode", appCode)
                .header("X-Forwarded-Host", forwardedHost)
                .header("X-Real-IP", "127.0.0.1")
                .contentType("application/json")
                .body(body)
                .post(SEC + "/users/invite");
    }

    /**
     * Accept an invite (public endpoint, no auth).
     */
    public Response acceptInvite(String clientCode, String appCode, Map<String, Object> body) {
        return given()
                .baseUri(baseHost + "/" + appCode + "/" + clientCode + "/page")
                .header("clientCode", clientCode)
                .header("appCode", appCode)
                .header("X-Forwarded-Host", forwardedHost)
                .header("X-Real-IP", "127.0.0.1")
                .contentType("application/json")
                .body(body)
                .post(SEC + "/users/acceptInvite");
    }

    /**
     * Authenticate and return the raw Response (extract accessToken from body).
     */
    public Response authenticate(String clientCode, String appCode, String userName, String password) {
        return given()
                .baseUri(baseHost + "/" + appCode + "/" + clientCode + "/page")
                .header("clientCode", clientCode)
                .header("appCode", appCode)
                .header("X-Forwarded-Host", forwardedHost)
                .header("X-Real-IP", "127.0.0.1")
                .contentType("application/json")
                .body(Map.of("userName", userName, "password", password))
                .post(SEC + "/authenticate");
    }
}
