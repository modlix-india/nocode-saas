package com.fincity.saas.inttest.leadzump.realestate;

import static org.assertj.core.api.Assertions.assertThat;

import com.fincity.saas.inttest.base.BaseIntegrationTest;
import com.fincity.saas.inttest.base.EntityProcessorApi;
import com.fincity.saas.inttest.base.ProfileHelper;
import com.fincity.saas.inttest.base.SecurityApi;
import io.restassured.response.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Partner read guardrail — verifies role-based filtering on
 * {@code POST /api/entity/processor/partners/query} and {@code GET /partners/{id}}.
 *
 * <p>Rules under test:
 * <ul>
 *   <li>Caller must have {@code Authorities.ROLE_Client_CREATE} to hit the endpoints at all.</li>
 *   <li>If the caller has {@code ROLE_Owner} or {@code ROLE_Client_Manager}, they see every partner.</li>
 *   <li>Otherwise rows are filtered to partners whose denormalized {@code clientManagerIds}
 *       column contains {@code ,&lt;callerUserId&gt;,}.</li>
 * </ul>
 *
 * <p>Setup: Builder admin (Owner) registers, two channel-partner sub-clients (CP_A, CP_B)
 * are registered + corresponding partners created. A Sales-Member team-member is invited and
 * assigned as client manager of CP_A only. After {@link EntityProcessorApi#triggerPartnerDenorm}
 * runs, CP_A's {@code clientManagerIds} should contain the team-member's userId; CP_B's should not.
 *
 * <p>Also covers Part 2 of the change: {@code clientStatusCode} should be present on the
 * Partner JSON after denorm sync (mirrors {@code security_client.STATUS_CODE}).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PartnerGuardrailScenario extends BaseIntegrationTest {

    private static final String PASSWORD = "Test@1234";

    // Builder admin (Owner role)
    private static String token;
    private static String clientCode;
    private static String appCode;
    private static String parentClientCode;
    private static Number userId;
    private static EntityProcessorApi api;

    // Two channel-partner sub-clients
    private static Number cpAClientId;
    private static Number cpBClientId;
    private static String cpAClientName;
    private static String cpBClientName;
    private static Number cpAPartnerId;
    private static Number cpBPartnerId;

    // Team member who will become Client Manager of CP_A only
    private static String managerEmail;
    private static String managerToken;
    private static Number managerUserId;
    private static EntityProcessorApi managerApi;

    // Test plumbing
    private static SecurityApi secApi;
    private static Number salesMemberProfileId;
    private static String uid;

    @BeforeAll
    void setup() {
        appCode = prop("leadzump.app.code");
        parentClientCode = prop("leadzump.client.code");

        uid = UUID.randomUUID().toString().substring(0, 8);
        String email = "guardrail-builder-" + uid + "@inttest.local";

        secApi = new SecurityApi(baseHost());
        String otp = prop("otp");

        Response otpRes = secApi.generateRegistrationOtp(parentClientCode, appCode, email);
        assertThat(otpRes.statusCode()).as("Generate OTP").isEqualTo(200);

        Response regRes = secApi.register(parentClientCode, appCode, mapOf(
                "clientName", "Guardrail_Builder_" + uid,
                "firstName", "Guardrail", "lastName", "Builder",
                "emailId", email, "password", PASSWORD,
                "passType", "PASSWORD", "businessClient", true, "otp", otp));
        assertThat(regRes.statusCode()).as("Register builder").isIn(200, 201);

        clientCode = regRes.body().path("authentication.client.code");
        userId = regRes.body().path("authentication.user.id");

        // Re-authenticate so the token resolves the correct managed-client context
        Response authRes = secApi.authenticate(parentClientCode, appCode, email, PASSWORD);
        assertThat(authRes.statusCode()).as("Builder re-auth").isEqualTo(200);
        token = authRes.body().path("accessToken");
        if (clientCode == null || clientCode.isBlank())
            clientCode = authRes.body().path("user.clientCode");

        api = new EntityProcessorApi(givenAuth(token, parentClientCode, appCode));

        ProfileHelper profiles = ProfileHelper.load(secApi, token, parentClientCode, appCode);
        salesMemberProfileId = profiles.getByName("Sales Member");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S1: Register two channel-partner sub-clients + create partners
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(100)
    void s1_registerCPAandCPB() {
        cpAClientName = "Guardrail_CPA_" + uid;
        Response regA = secApi.registerClient(token, parentClientCode, appCode, mapOf(
                "clientName", cpAClientName,
                "firstName", "CPA_Admin", "lastName", "IntTest",
                "emailId", "guardrail-cpa-" + uid + "@inttest.local",
                "password", PASSWORD, "passType", "PASSWORD",
                "businessClient", true));
        assertThat(regA.statusCode()).as("Register CP_A: " + regA.body().asString()).isIn(200, 201);
        cpAClientId = pickClientId(regA);

        cpBClientName = "Guardrail_CPB_" + uid;
        Response regB = secApi.registerClient(token, parentClientCode, appCode, mapOf(
                "clientName", cpBClientName,
                "firstName", "CPB_Admin", "lastName", "IntTest",
                "emailId", "guardrail-cpb-" + uid + "@inttest.local",
                "password", PASSWORD, "passType", "PASSWORD",
                "businessClient", true));
        assertThat(regB.statusCode()).as("Register CP_B: " + regB.body().asString()).isIn(200, 201);
        cpBClientId = pickClientId(regB);
    }

    @Test
    @Order(110)
    void s1_createPartnersForBothCPs() {
        Response resA = api.createPartner(mapOf("name", "CPA_Partner_" + uid, "clientId", cpAClientId));
        assertThat(resA.statusCode()).as("Create CP_A partner: " + resA.body().asString()).isIn(200, 201);
        cpAPartnerId = resA.body().path("id");

        Response resB = api.createPartner(mapOf("name", "CPB_Partner_" + uid, "clientId", cpBClientId));
        assertThat(resB.statusCode()).as("Create CP_B partner: " + resB.body().asString()).isIn(200, 201);
        cpBPartnerId = resB.body().path("id");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S2: Owner (builder) sees ALL partners — no row filter applied
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(200)
    void s2_ownerQueryReturnsBothPartners() {
        Response res = api.queryPartners(0, 50);
        assertThat(res.statusCode()).as("Owner query partners").isEqualTo(200);

        List<Map<String, Object>> content = res.body().path("content");
        assertThat(content).as("Owner sees CP_A and CP_B")
                .extracting(p -> p.get("clientName"))
                .contains(cpAClientName, cpBClientName);
    }

    @Test
    @Order(210)
    void s2_ownerCanGetEachPartnerById() {
        Response a = api.getPartner(cpAPartnerId);
        assertThat(a.statusCode()).as("Owner GET CP_A").isEqualTo(200);
        assertThat((String) a.body().path("clientName")).isEqualTo(cpAClientName);

        Response b = api.getPartner(cpBPartnerId);
        assertThat(b.statusCode()).as("Owner GET CP_B").isEqualTo(200);
        assertThat((String) b.body().path("clientName")).isEqualTo(cpBClientName);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S3: Invite Sales Member; assign as Client Manager of CP_A only;
    //      run denorm so clientManagerIds gets populated.
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(300)
    void s3_inviteSalesMember_assignAsCMofCPA() {
        managerEmail = "guardrail-mgr-" + uid + "@inttest.local";
        Response inv = secApi.inviteUser(token, parentClientCode, appCode, mapOf(
                "emailId", managerEmail,
                "firstName", "GuardrailMgr", "lastName", "IntTest",
                "profileId", salesMemberProfileId, "reportingTo", userId));
        assertThat(inv.statusCode()).as("Invite manager: " + inv.body().asString()).isIn(200, 201);
        String code = inv.body().path("userRequest.inviteCode");

        Response acc = secApi.acceptInvite(parentClientCode, appCode, mapOf(
                "emailId", managerEmail,
                "firstName", "GuardrailMgr", "lastName", "IntTest",
                "password", PASSWORD, "passType", "PASSWORD", "inviteCode", code));
        assertThat(acc.statusCode()).as("Accept invite").isIn(200, 201);
        managerUserId = acc.body().path("authentication.user.id");

        // Assign as client manager of CP_A only
        Response assign = secApi.assignClientManager(
                token, parentClientCode, appCode, managerUserId, cpAClientId);
        assertThat(assign.statusCode())
                .as("Assign manager as CM of CP_A: " + assign.body().asString())
                .isIn(200, 201);

        // Authenticate the manager so we can use their token
        Response mAuth = secApi.authenticate(parentClientCode, appCode, managerEmail, PASSWORD);
        assertThat(mAuth.statusCode()).as("Manager auth").isEqualTo(200);
        managerToken = mAuth.body().path("accessToken");
        managerApi = new EntityProcessorApi(givenAuth(managerToken, parentClientCode, appCode));
    }

    @Test
    @Order(310)
    void s3_runFullDenormSync() {
        Response res = EntityProcessorApi.triggerPartnerDenorm(false);
        assertThat(res.statusCode()).as("Denorm sync").isEqualTo(200);
    }

    @Test
    @Order(320)
    void s3_clientManagerIdsPopulatedOnCPA_notOnCPB() {
        Response res = api.queryPartners(0, 50);
        List<Map<String, Object>> content = res.body().path("content");

        Map<String, Object> cpA = content.stream()
                .filter(p -> cpAClientName.equals(p.get("clientName")))
                .findFirst().orElse(null);
        Map<String, Object> cpB = content.stream()
                .filter(p -> cpBClientName.equals(p.get("clientName")))
                .findFirst().orElse(null);
        assertThat(cpA).as("CP_A partner present").isNotNull();
        assertThat(cpB).as("CP_B partner present").isNotNull();

        String marker = "," + managerUserId + ",";
        assertThat((String) cpA.get("clientManagerIds"))
                .as("CP_A clientManagerIds contains the assigned manager")
                .contains(marker);
        String cpBIds = (String) cpB.get("clientManagerIds");
        assertThat(cpBIds == null || !cpBIds.contains(marker))
                .as("CP_B clientManagerIds does NOT contain the manager")
                .isTrue();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S4: Sales Member is blocked at the @PreAuthorize gate
    //
    //  By design, Sales Member / Sales Manager profiles do not carry
    //  Authorities.ROLE_Client_CREATE — they have no business with the
    //  partner API even when they are an entry in clientManagerIds.
    //  Both /query and GET /{id} must return 403.
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(400)
    void s4_salesMemberBlockedFromQuery() {
        Response res = managerApi.queryPartners(0, 50);
        assertThat(res.statusCode())
                .as("Sales Member must be blocked at the @PreAuthorize gate, even when listed in clientManagerIds")
                .isEqualTo(403);
    }

    @Test
    @Order(410)
    void s4_salesMemberBlockedFromGetById_evenForOwnCPA() {
        Response res = managerApi.getPartner(cpAPartnerId);
        assertThat(res.statusCode())
                .as("Sales Member must be blocked from GET /{id} even on a partner they manage")
                .isEqualTo(403);
    }

    @Test
    @Order(420)
    void s4_salesMemberBlockedFromGetById_forCPB() {
        Response res = managerApi.getPartner(cpBPartnerId);
        assertThat(res.statusCode())
                .as("Sales Member must be blocked from GET /{id} for partners outside their scope")
                .isEqualTo(403);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S5: Part 2 of the change — clientStatusCode is synced
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(500)
    void s5_clientStatusCodePopulatedAfterDenorm() {
        Response res = api.queryPartners(0, 50);
        List<Map<String, Object>> content = res.body().path("content");

        Map<String, Object> cpA = content.stream()
                .filter(p -> cpAClientName.equals(p.get("clientName")))
                .findFirst().orElse(null);
        assertThat(cpA).as("CP_A present").isNotNull();
        assertThat((String) cpA.get("clientStatusCode"))
                .as("clientStatusCode populated from security_client.STATUS_CODE")
                .isEqualTo("ACTIVE");
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private static Number pickClientId(Response reg) {
        Number id = reg.body().path("authentication.client.id");
        return id != null ? id : reg.body().path("client.id");
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> mapOf(Object... keyValues) {
        Map<Object, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) map.put(keyValues[i], keyValues[i + 1]);
        return (Map<K, V>) map;
    }
}
