package com.fincity.saas.inttest.security;

import com.fincity.saas.inttest.base.BaseIntegrationTest;
import com.fincity.saas.inttest.base.SecurityApi;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that deleted / inactive users cannot access protected APIs
 * using a token that was issued before their account was deactivated or deleted.
 *
 * Setup:
 *   - Register an ADMIN user (client owner) — stays active throughout.
 *   - Admin invites a TARGET user who accepts the invite and gets a token.
 *
 * Flow:
 *   1. Confirm the target user's token works on a protected endpoint.
 *   2. Admin deactivates the target user → verify stale token is rejected.
 *   3. Admin reactivates the target user → verify token works again.
 *   4. Admin soft-deletes the target user → verify stale token is rejected.
 *   5. Verify the deleted user cannot re-authenticate.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class StaleTokenScenario extends BaseIntegrationTest {

    private static SecurityApi secApi;
    private static String appCode;
    private static String parentClientCode;

    /** Admin (client owner) — always active. */
    private static String adminToken;
    private static String clientCode;

    /** Target user — the one being deactivated/deleted. */
    private static String targetToken;
    private static Number targetUserId;
    private static String targetEmail;
    private static final String TARGET_PASSWORD = "Test@1234";

    @BeforeAll
    void setup() {
        appCode = prop("leadzump.app.code");
        parentClientCode = prop("leadzump.client.code");
        secApi = new SecurityApi(baseHost());

        String uid = UUID.randomUUID().toString().substring(0, 8);
        String otp = prop("otp");

        // ── Register admin (client owner) ──
        String adminEmail = "stale-admin-" + uid + "@inttest.local";
        String adminPassword = "Test@1234";

        Response otpRes = secApi.generateRegistrationOtp(parentClientCode, appCode, adminEmail);
        assertThat(otpRes.statusCode()).as("Admin: Generate OTP").isEqualTo(200);

        Response regRes = secApi.register(parentClientCode, appCode, mapOf(
                "clientName", "StaleToken_" + uid,
                "firstName", "StaleAdmin",
                "lastName", "IntTest",
                "emailId", adminEmail,
                "password", adminPassword,
                "passType", "PASSWORD",
                "businessClient", true,
                "otp", otp
        ));
        assertThat(regRes.statusCode()).as("Admin: Self-registration").isIn(200, 201);

        adminToken = regRes.body().path("authentication.accessToken");
        assertThat(adminToken).as("Admin: accessToken").isNotNull().isNotEmpty();

        clientCode = regRes.body().path("authentication.client.code");
        if (clientCode == null || clientCode.isBlank()) {
            Response authRes = secApi.authenticate(parentClientCode, appCode, adminEmail, adminPassword);
            assertThat(authRes.statusCode()).as("Admin: Post-registration auth").isEqualTo(200);
            adminToken = authRes.body().path("accessToken");
            clientCode = authRes.body().path("user.clientCode");
        }

        // ── Invite target user ──
        targetEmail = "stale-target-" + uid + "@inttest.local";

        Number adminUserId = regRes.body().path("authentication.user.id");

        Response inviteRes = secApi.inviteUser(adminToken, clientCode, appCode, mapOf(
                "emailId", targetEmail,
                "firstName", "StaleTarget",
                "lastName", "IntTest",
                "profileId", 122,
                "reportingTo", adminUserId
        ));
        assertThat(inviteRes.statusCode()).as("Invite target user").isIn(200, 201);
        String inviteCode = inviteRes.body().path("userRequest.inviteCode");
        assertThat(inviteCode).as("Invite code").isNotNull();

        // Accept invite
        Response acceptRes = secApi.acceptInvite(clientCode, appCode, mapOf(
                "emailId", targetEmail,
                "firstName", "StaleTarget",
                "lastName", "IntTest",
                "password", TARGET_PASSWORD,
                "passType", "PASSWORD",
                "inviteCode", inviteCode
        ));
        assertThat(acceptRes.statusCode()).as("Accept invite").isIn(200, 201);

        // Authenticate target user to get their token
        Response targetAuth = secApi.authenticate(clientCode, appCode, targetEmail, TARGET_PASSWORD);
        assertThat(targetAuth.statusCode()).as("Target: authenticate").isEqualTo(200);
        targetToken = targetAuth.body().path("accessToken");
        targetUserId = targetAuth.body().path("user.id");
        assertThat(targetToken).as("Target: accessToken").isNotNull().isNotEmpty();
        assertThat(targetUserId).as("Target: userId").isNotNull();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Baseline: target user's token works while active
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(100)
    void baseline_tokenWorksWhileActive() {
        Response res = callProtectedEndpointWithTargetToken();
        assertThat(res.statusCode())
                .as("Active user should access protected endpoint")
                .isIn(200, 201);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Inactive user: stale token must be rejected
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(200)
    void inactiveUser_adminDeactivatesTarget() {
        Response res = secApi.makeUserInActive(adminToken, clientCode, appCode, targetUserId);
        assertThat(res.statusCode()).as("Admin deactivates target").isIn(200, 201);
    }

    @Test
    @Order(210)
    void inactiveUser_staleTokenRejected() {
        Response res = callProtectedEndpointWithTargetToken();
        assertThat(res.statusCode())
                .as("Inactive user's stale token should be rejected (401 or 403)")
                .isIn(401, 403);
    }

    @Test
    @Order(220)
    void inactiveUser_adminReactivatesTarget() {
        Response res = secApi.makeUserActive(adminToken, clientCode, appCode, targetUserId);
        assertThat(res.statusCode()).as("Admin reactivates target").isIn(200, 201);
    }

    @Test
    @Order(230)
    void inactiveUser_afterReactivation_tokenWorks() {
        Response res = callProtectedEndpointWithTargetToken();
        assertThat(res.statusCode())
                .as("Reactivated user should be able to access protected endpoint again")
                .isIn(200, 201);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Deleted user: stale token must be rejected
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(300)
    void deletedUser_adminDeletesTarget() {
        Response res = secApi.deleteUser(adminToken, clientCode, appCode, targetUserId);
        assertThat(res.statusCode())
                .as("Admin soft-deletes target")
                .isIn(200, 204);
    }

    @Test
    @Order(310)
    void deletedUser_staleTokenRejected() {
        Response res = callProtectedEndpointWithTargetToken();
        assertThat(res.statusCode())
                .as("Deleted user's stale token should be rejected (401 or 403)")
                .isIn(401, 403);
    }

    @Test
    @Order(320)
    void deletedUser_reAuthenticationFails() {
        Response res = secApi.authenticate(clientCode, appCode, targetEmail, TARGET_PASSWORD);
        assertThat(res.statusCode())
                .as("Deleted user should not be able to re-authenticate")
                .isIn(401, 403);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Calls a protected endpoint on entity-processor (not security) using the target's stale token.
     * This is important because each downstream service caches tokens independently via verifyToken,
     * so we must verify that the token is rejected across service boundaries.
     */
    private Response callProtectedEndpointWithTargetToken() {
        return givenAuth(targetToken, clientCode, appCode)
                .get("/api/entity/processor/products/forms");
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> mapOf(Object... keyValues) {
        Map<Object, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return (Map<K, V>) map;
    }
}
