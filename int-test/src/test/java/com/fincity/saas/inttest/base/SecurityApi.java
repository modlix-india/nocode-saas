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
     * Register a client on behalf of an authenticated user (e.g. builder admin creating a CP).
     * Uses the caller's auth token so the new client is created as a sub-client.
     */
    public Response registerClient(String token, String clientCode, String appCode, Map<String, Object> body) {
        return given()
                .baseUri(baseHost + "/" + appCode + "/" + clientCode + "/page")
                .header("Authorization", "Bearer " + token)
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
    public Response acceptInvite(String parentClientCode, String appCode, Map<String, Object> body) {
        return given()
                .baseUri(baseHost + "/" + appCode + "/" + parentClientCode + "/page")
                .header("clientCode", parentClientCode)
                .header("appCode", appCode)
                .header("X-Forwarded-Host", forwardedHost)
                .header("X-Real-IP", "127.0.0.1")
                .contentType("application/json")
                .body(body)
                .post(SEC + "/users/acceptInvite");
    }

    /**
     * Deactivate a user (requires admin auth).
     */
    public Response makeUserInActive(String token, String clientCode, String appCode, Number userId) {
        return given()
                .baseUri(baseHost + "/" + appCode + "/" + clientCode + "/page")
                .header("Authorization", "Bearer " + token)
                .header("clientCode", clientCode)
                .header("appCode", appCode)
                .header("X-Forwarded-Host", forwardedHost)
                .header("X-Real-IP", "127.0.0.1")
                .contentType("application/json")
                .queryParam("userId", userId)
                .patch(SEC + "/users/makeUserInActive");
    }

    /**
     * Reactivate a user (requires admin auth).
     */
    public Response makeUserActive(String token, String clientCode, String appCode, Number userId) {
        return given()
                .baseUri(baseHost + "/" + appCode + "/" + clientCode + "/page")
                .header("Authorization", "Bearer " + token)
                .header("clientCode", clientCode)
                .header("appCode", appCode)
                .header("X-Forwarded-Host", forwardedHost)
                .header("X-Real-IP", "127.0.0.1")
                .contentType("application/json")
                .queryParam("userId", userId)
                .patch(SEC + "/users/makeUserActive");
    }

    /**
     * Soft-delete a user (requires admin auth with User_DELETE authority).
     */
    public Response deleteUser(String token, String clientCode, String appCode, Number userId) {
        return given()
                .baseUri(baseHost + "/" + appCode + "/" + clientCode + "/page")
                .header("Authorization", "Bearer " + token)
                .header("clientCode", clientCode)
                .header("appCode", appCode)
                .header("X-Forwarded-Host", forwardedHost)
                .header("X-Real-IP", "127.0.0.1")
                .contentType("application/json")
                .delete(SEC + "/users/" + userId);
    }

    /**
     * Get an application by its appCode. Returns the full App object including id.
     */
    public Response getAppByCode(String token, String clientCode, String appCode) {
        var spec = given()
                .baseUri(baseHost + "/" + appCode + "/" + clientCode + "/page")
                .header("clientCode", clientCode)
                .header("appCode", appCode)
                .header("X-Forwarded-Host", forwardedHost)
                .header("X-Real-IP", "127.0.0.1")
                .contentType("application/json");
        if (token != null) spec = spec.header("Authorization", "Bearer " + token);
        return spec.get(SEC.replace("/security", "/security/applications") + "/appCode/" + appCode);
    }

    /**
     * List profiles for a given appId (numeric).
     */
    public Response listProfiles(String token, String clientCode, String appCode, Object appId) {
        var spec = given()
                .baseUri(baseHost + "/" + appCode + "/" + clientCode + "/page")
                .header("clientCode", clientCode)
                .header("appCode", appCode)
                .header("X-Forwarded-Host", forwardedHost)
                .header("X-Real-IP", "127.0.0.1")
                .contentType("application/json")
                .queryParam("size", 100);
        if (token != null) spec = spec.header("Authorization", "Bearer " + token);
        return spec.get("/api/security/app/" + appId + "/profiles");
    }

    /**
     * Assign a user as client manager of a client.
     * POST /api/security/client-managers/{userId}/{clientId}
     */
    public Response assignClientManager(String token, String clientCode, String appCode,
            Number userId, Number clientId) {
        return given()
                .baseUri(baseHost + "/" + appCode + "/" + clientCode + "/page")
                .header("Authorization", "Bearer " + token)
                .header("clientCode", clientCode)
                .header("appCode", appCode)
                .header("X-Forwarded-Host", forwardedHost)
                .header("X-Real-IP", "127.0.0.1")
                .contentType("application/json")
                .post(SEC + "/client-managers/" + userId + "/" + clientId);
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
