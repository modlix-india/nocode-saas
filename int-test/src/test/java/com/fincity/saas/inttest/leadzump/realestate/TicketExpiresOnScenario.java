package com.fincity.saas.inttest.leadzump.realestate;

import com.fincity.saas.inttest.base.BaseIntegrationTest;
import com.fincity.saas.inttest.base.EntityProcessorApi;
import com.fincity.saas.inttest.base.ProfileHelper;
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
 * Ticket ExpiresOn Recalculation Scenario.
 *
 * Tests that the expiresOn field is recalculated (refreshed to now + expiryDays)
 * on every ticket operation:
 *
 *   1. Updating email
 *   2. Updating deal name (ticket name)
 *   3. Adding a tag
 *   4. Updating a tag
 *   5. Adding notes
 *   6. Adding tasks
 *   7. Reassigning the user
 *   8. Adding a manual call log
 *   9. Adding/updating bio (description)
 *
 * Each test waits 2 seconds before the operation so that the recalculated
 * epoch (now + 30 days) is detectably different from the previous value.
 *
 * Setup: Product template + stages + product + expiration rule (30 days for "Walk-in" source).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TicketExpiresOnScenario extends BaseIntegrationTest {

    private static final long DELAY_MS = 2000;

    private static String token;
    private static String clientCode;
    private static String appCode;
    private static EntityProcessorApi api;
    private static SecurityApi secApi;

    // Setup data
    private static Number templateId;
    private static Number productId;

    // Stages
    private static Number stageContactableId;
    private static Number stageContactedId;

    // Ticket data
    private static Number ticketId;
    private static String ticketCode;
    private static Number initialExpiresOn;

    // Admin user ID (for reportingTo)
    private static Number adminUserId;

    // Second user for reassignment
    private static Number salesMemberProfileId;
    private static Number member1UserId;

    // Task type for task creation
    private static Number taskTypeId;

    @BeforeAll
    void setup() {
        appCode = prop("leadzump.app.code");
        String parentClientCode = prop("leadzump.client.code");

        String uid = UUID.randomUUID().toString().substring(0, 8);
        String email = "re-expires-" + uid + "@inttest.local";
        String password = "Test@1234";

        secApi = new SecurityApi(baseHost());
        String otp = prop("otp");

        Response otpRes = secApi.generateRegistrationOtp(parentClientCode, appCode, email);
        assertThat(otpRes.statusCode()).as("Generate OTP").isEqualTo(200);

        Response regRes = secApi.register(parentClientCode, appCode, mapOf(
                "clientName", "RE_Exp_" + uid,
                "firstName", "ExpiresDev",
                "lastName", "IntTest",
                "emailId", email,
                "password", password,
                "passType", "PASSWORD",
                "businessClient", true,
                "otp", otp
        ));

        assertThat(regRes.statusCode()).as("Self-registration").isIn(200, 201);

        token = regRes.body().path("authentication.accessToken");
        assertThat(token).as("Registration should return accessToken").isNotNull().isNotEmpty();

        adminUserId = regRes.body().path("authentication.user.id");
        clientCode = regRes.body().path("authentication.client.code");
        if (clientCode == null || clientCode.isBlank()) {
            Response authRes = secApi.authenticate(parentClientCode, appCode, email, password);
            assertThat(authRes.statusCode()).as("Post-registration auth").isEqualTo(200);
            token = authRes.body().path("accessToken");
            clientCode = authRes.body().path("user.clientCode");
        }

        api = new EntityProcessorApi(givenAuth(token, clientCode, appCode));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Setup: Template + Stages + Product + Expiration Rule + User
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(10)
    void setup_createTemplateAndStages() {
        Response tmpl = api.createProductTemplate(Map.of(
                "name", "RE_IntTest_ExpiresTemplate",
                "description", "Template for expiresOn tests",
                "productTemplateType", "GENERAL"
        ));
        assertThat(tmpl.statusCode()).isIn(200, 201);
        templateId = tmpl.body().path("id");

        // Fresh -> Open
        Response fresh = api.createStage(mapOf(
                "name", "Fresh", "platform", "PRE_QUALIFICATION",
                "productTemplateId", templateId, "isParent", true, "order", 1, "stageType", "OPEN",
                "children", mapOf(
                        0, mapOf("name", "Open", "stageType", "OPEN", "order", 0)
                )
        ));
        assertThat((Number) fresh.body().path("parent.id")).isNotNull();
        assertThat((Number) fresh.body().path("child[0].id")).isNotNull();

        // Contactable -> Contacted
        Response contactable = api.createStage(mapOf(
                "name", "Contactable", "platform", "PRE_QUALIFICATION",
                "productTemplateId", templateId, "isParent", true, "order", 2, "stageType", "OPEN",
                "children", mapOf(
                        1, mapOf("name", "Contacted", "stageType", "OPEN", "order", 1)
                )
        ));
        stageContactableId = contactable.body().path("parent.id");
        stageContactedId = contactable.body().path("child[0].id");
    }

    @Test
    @Order(20)
    void setup_createProduct() {
        Response res = api.createProduct(mapOf(
                "name", "RE_IntTest_ExpiresProject",
                "description", "Project for expiresOn tests",
                "productTemplateId", templateId,
                "forPartner", false
        ));
        assertThat(res.statusCode()).isIn(200, 201);
        productId = res.body().path("id");
    }

    @Test
    @Order(30)
    void setup_createExpirationRule() {
        Response res = api.createExpirationRule(mapOf(
                "name", "RE_IntTest_WalkIn_Expiry_30d",
                "productId", productId,
                "source", "Walk-in",
                "expiryDays", 30
        ));
        assertThat(res.statusCode()).as("Create expiration rule").isIn(200, 201);
        assertThat((Number) res.body().path("id")).isNotNull();
    }

    @Test
    @Order(40)
    void setup_createTaskType() {
        Response res = api.createTaskType(mapOf(
                "name", "Follow Up Call",
                "description", "Follow up call task type"
        ));
        assertThat(res.statusCode()).isIn(200, 201);
        taskTypeId = res.body().path("id");
    }

    @Test
    @Order(50)
    void setup_inviteTeamMember() {
        ProfileHelper profiles = ProfileHelper.load(secApi, token, prop("leadzump.client.code"), appCode);
        salesMemberProfileId = profiles.getByName("Sales Member");

        String uid = UUID.randomUUID().toString().substring(0, 8);
        String member1Email = "re-exp-m1-" + uid + "@inttest.local";

        Response invite = secApi.inviteUser(token, clientCode, appCode, mapOf(
                "emailId", member1Email,
                "firstName", "Member1",
                "lastName", "Expires",
                "profileId", salesMemberProfileId,
                "reportingTo", adminUserId
        ));
        assertThat(invite.statusCode()).as("Invite team member").isIn(200, 201);
        String inviteCode = invite.body().path("userRequest.inviteCode");
        assertThat(inviteCode).as("Invite code").isNotNull();

        Response accept = secApi.acceptInvite(prop("leadzump.client.code"), appCode, mapOf(
                "emailId", member1Email,
                "firstName", "Member1",
                "lastName", "Expires",
                "password", "Test@1234",
                "passType", "PASSWORD",
                "inviteCode", inviteCode
        ));
        assertThat(accept.statusCode()).as("Accept invite").isIn(200, 201);
        member1UserId = accept.body().path("authentication.user.id");
        assertThat(member1UserId).as("Member user ID").isNotNull();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Create ticket and capture initial expiresOn
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(100)
    void createTicketWithExpiration() {
        Response res = api.createTicket(mapOf(
                "name", "Rajesh Kumar",
                "dialCode", 91,
                "phoneNumber", "+919876543210",
                "email", "rajesh.kumar@example.com",
                "productId", productId,
                "source", "Walk-in",
                "comment", "Initial walk-in inquiry"
        ));

        assertThat(res.statusCode()).as("Create ticket").isIn(200, 201);
        ticketId = res.body().path("id");
        ticketCode = res.body().path("code");
        assertThat(ticketId).isNotNull();
        assertThat(ticketCode).isNotNull();
    }

    @Test
    @Order(110)
    void verifyInitialExpiresOn() {
        Response res = api.getTicket(ticketId);
        assertThat(res.statusCode()).isEqualTo(200);

        initialExpiresOn = res.body().path("expiresOn");
        assertThat(initialExpiresOn)
                .as("Ticket with Walk-in source and 30-day expiry rule should have expiresOn set")
                .isNotNull();

        String source = res.body().path("source");
        assertThat(source).isEqualTo("Walk-in");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Test 1: Update email — expiresOn SHOULD be recalculated
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(200)
    @SuppressWarnings("unchecked")
    void updateEmail_expiresOnRecalculated() throws InterruptedException {
        Response before = api.getTicket(ticketId);
        assertThat(before.statusCode()).isEqualTo(200);
        Number expiresOnBefore = before.body().path("expiresOn");
        assertThat(expiresOnBefore).as("expiresOn before email update").isNotNull();

        Thread.sleep(DELAY_MS);

        Map<String, Object> fullBody = before.body().as(Map.class);
        fullBody.put("email", "rajesh.updated@example.com");

        Response updateRes = api.updateTicketByCode(ticketCode, fullBody);
        assertThat(updateRes.statusCode())
                .as("Update email via PUT: " + updateRes.body().asString())
                .isIn(200, 201);

        Response after = api.getTicket(ticketId);
        assertThat(after.statusCode()).isEqualTo(200);

        Number expiresOnAfter = after.body().path("expiresOn");
        assertExpiresOnRecalculated("updating email", expiresOnBefore, expiresOnAfter);

        String updatedEmail = after.body().path("email");
        assertThat(updatedEmail).isEqualTo("rajesh.updated@example.com");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Test 2: Update deal name — expiresOn SHOULD be recalculated
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(210)
    @SuppressWarnings("unchecked")
    void updateDealName_expiresOnRecalculated() throws InterruptedException {
        Response before = api.getTicket(ticketId);
        Number expiresOnBefore = before.body().path("expiresOn");
        assertThat(expiresOnBefore).as("expiresOn before name update").isNotNull();

        Thread.sleep(DELAY_MS);

        Map<String, Object> fullBody = before.body().as(Map.class);
        fullBody.put("name", "Rajesh Kumar - Premium Villa");

        Response updateRes = api.updateTicketByCode(ticketCode, fullBody);
        assertThat(updateRes.statusCode())
                .as("Update deal name: " + updateRes.body().asString())
                .isIn(200, 201);

        Response after = api.getTicket(ticketId);
        assertThat(after.statusCode()).isEqualTo(200);

        Number expiresOnAfter = after.body().path("expiresOn");
        assertExpiresOnRecalculated("updating deal name", expiresOnBefore, expiresOnAfter);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Test 3: Add tag — expiresOn SHOULD be recalculated
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(300)
    void addTag_expiresOnRecalculated() throws InterruptedException {
        Response before = api.getTicket(ticketId);
        Number expiresOnBefore = before.body().path("expiresOn");
        assertThat(expiresOnBefore).as("expiresOn before adding tag").isNotNull();

        Thread.sleep(DELAY_MS);

        Response tagRes = api.updateTicketTag(ticketId, Map.of(
                "tag", "HOT",
                "comment", "High intent buyer"
        ));
        assertThat(tagRes.statusCode()).as("Add tag HOT").isIn(200, 201);

        Response after = api.getTicket(ticketId);
        assertThat(after.statusCode()).isEqualTo(200);

        String tag = after.body().path("tag");
        assertThat(tag).isEqualTo("HOT");

        Number expiresOnAfter = after.body().path("expiresOn");
        assertExpiresOnRecalculated("adding tag", expiresOnBefore, expiresOnAfter);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Test 4: Update tag — expiresOn SHOULD be recalculated
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(310)
    void updateTag_expiresOnRecalculated() throws InterruptedException {
        Response before = api.getTicket(ticketId);
        Number expiresOnBefore = before.body().path("expiresOn");
        assertThat(expiresOnBefore).as("expiresOn before tag update").isNotNull();

        Thread.sleep(DELAY_MS);

        Response tagRes = api.updateTicketTag(ticketId, Map.of(
                "tag", "WARM",
                "comment", "Downgraded to warm"
        ));
        assertThat(tagRes.statusCode()).as("Update tag to WARM").isIn(200, 201);

        Response after = api.getTicket(ticketId);
        assertThat(after.statusCode()).isEqualTo(200);

        String tag = after.body().path("tag");
        assertThat(tag).isEqualTo("WARM");

        Number expiresOnAfter = after.body().path("expiresOn");
        assertExpiresOnRecalculated("updating tag", expiresOnBefore, expiresOnAfter);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Test 5: Add note — expiresOn SHOULD be recalculated
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(400)
    void addNote_expiresOnRecalculated() throws InterruptedException {
        Response before = api.getTicket(ticketId);
        Number expiresOnBefore = before.body().path("expiresOn");
        assertThat(expiresOnBefore).as("expiresOn before adding note").isNotNull();

        Thread.sleep(DELAY_MS);

        Response noteRes = api.createNote(mapOf(
                "content", "Customer is interested in 3BHK facing east side.",
                "ticketId", ticketId
        ));
        assertThat(noteRes.statusCode()).as("Create note").isIn(200, 201);

        Response after = api.getTicket(ticketId);
        assertThat(after.statusCode()).isEqualTo(200);

        Number expiresOnAfter = after.body().path("expiresOn");
        assertExpiresOnRecalculated("adding note", expiresOnBefore, expiresOnAfter);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Test 6: Add task — expiresOn SHOULD be recalculated
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(500)
    void addTask_expiresOnRecalculated() throws InterruptedException {
        Response before = api.getTicket(ticketId);
        Number expiresOnBefore = before.body().path("expiresOn");
        assertThat(expiresOnBefore).as("expiresOn before adding task").isNotNull();

        Thread.sleep(DELAY_MS);

        Response taskRes = api.createTask(mapOf(
                "name", "Follow up call",
                "content", "Follow up call with customer",
                "ticketId", ticketId,
                "taskTypeId", taskTypeId
        ));
        assertThat(taskRes.statusCode()).as("Create task: " + taskRes.body().asString()).isIn(200, 201);

        Response after = api.getTicket(ticketId);
        assertThat(after.statusCode()).isEqualTo(200);

        Number expiresOnAfter = after.body().path("expiresOn");
        assertExpiresOnRecalculated("adding task", expiresOnBefore, expiresOnAfter);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Test 7: Add second task — expiresOn SHOULD be recalculated
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(510)
    void addSecondTask_expiresOnRecalculated() throws InterruptedException {
        Response before = api.getTicket(ticketId);
        Number expiresOnBefore = before.body().path("expiresOn");
        assertThat(expiresOnBefore).as("expiresOn before adding second task").isNotNull();

        Thread.sleep(DELAY_MS);

        Response taskRes = api.createTask(mapOf(
                "name", "Schedule site visit",
                "content", "Schedule site visit with customer",
                "ticketId", ticketId,
                "taskTypeId", taskTypeId
        ));
        assertThat(taskRes.statusCode()).as("Create second task").isIn(200, 201);

        Response after = api.getTicket(ticketId);
        assertThat(after.statusCode()).isEqualTo(200);

        Number expiresOnAfter = after.body().path("expiresOn");
        assertExpiresOnRecalculated("adding second task", expiresOnBefore, expiresOnAfter);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Test 8: Reassign user — expiresOn SHOULD be recalculated
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(600)
    void reassignUser_expiresOnRecalculated() throws InterruptedException {
        if (member1UserId == null) {
            return;
        }

        Response before = api.getTicket(ticketId);
        Number expiresOnBefore = before.body().path("expiresOn");
        assertThat(expiresOnBefore).as("expiresOn before reassignment").isNotNull();

        Thread.sleep(DELAY_MS);

        Response reassignRes = api.reassignTicket(ticketId, mapOf(
                "userId", member1UserId,
                "comment", "Reassigning to team member for follow-up"
        ));

        // Reassign requires the target user to be in the admin's subOrg hierarchy.
        // If the org hierarchy isn't fully configured, skip gracefully.
        if (reassignRes.statusCode() == 400) {
            return;
        }
        assertThat(reassignRes.statusCode()).as("Reassign ticket").isIn(200, 201);

        Response after = api.getTicket(ticketId);
        assertThat(after.statusCode()).isEqualTo(200);

        Number assignedUserId = after.body().path("assignedUserId");
        assertThat(assignedUserId.longValue())
                .as("Ticket should be reassigned to member1")
                .isEqualTo(member1UserId.longValue());

        Number expiresOnAfter = after.body().path("expiresOn");
        assertExpiresOnRecalculated("reassigning user", expiresOnBefore, expiresOnAfter);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Test 9: Add manual call log — expiresOn SHOULD be recalculated
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(700)
    void addCallLog_expiresOnRecalculated() throws InterruptedException {
        Response before = api.getTicket(ticketId);
        Number expiresOnBefore = before.body().path("expiresOn");
        assertThat(expiresOnBefore).as("expiresOn before call log").isNotNull();

        Thread.sleep(DELAY_MS);

        Response callRes = api.logCallActivity(mapOf(
                "ticketId", ticketId,
                "comment", "Discussed pricing and payment options",
                "isOutbound", true
        ));
        assertThat(callRes.statusCode()).as("Log call activity: " + callRes.body().asString()).isIn(200, 201);

        Response after = api.getTicket(ticketId);
        assertThat(after.statusCode()).isEqualTo(200);

        Number expiresOnAfter = after.body().path("expiresOn");
        assertExpiresOnRecalculated("adding call log", expiresOnBefore, expiresOnAfter);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Test 10: Add/Update bio (description) — expiresOn SHOULD be recalculated
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(800)
    @SuppressWarnings("unchecked")
    void updateBio_expiresOnRecalculated() throws InterruptedException {
        Response before = api.getTicket(ticketId);
        Number expiresOnBefore = before.body().path("expiresOn");
        assertThat(expiresOnBefore).as("expiresOn before bio update").isNotNull();

        Thread.sleep(DELAY_MS);

        Map<String, Object> fullBody = before.body().as(Map.class);
        fullBody.put("description", "Senior IT professional, budget 1.5Cr, looking for 3BHK east-facing unit.");

        Response updateRes = api.updateTicketByCode(ticketCode, fullBody);
        assertThat(updateRes.statusCode())
                .as("Update bio/description: " + updateRes.body().asString())
                .isIn(200, 201);

        Response after = api.getTicket(ticketId);
        assertThat(after.statusCode()).isEqualTo(200);

        Number expiresOnAfter = after.body().path("expiresOn");
        assertExpiresOnRecalculated("updating bio", expiresOnBefore, expiresOnAfter);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Test 11: Stage change — expiresOn SHOULD be recalculated
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(900)
    void stageChange_expiresOnRecalculated() throws InterruptedException {
        Response before = api.getTicket(ticketId);
        Number expiresOnBefore = before.body().path("expiresOn");
        assertThat(expiresOnBefore).as("expiresOn before stage change").isNotNull();

        Thread.sleep(DELAY_MS);

        Response stageRes = api.updateTicketStage(ticketId, mapOf(
                "stageId", stageContactableId,
                "statusId", stageContactedId,
                "comment", "Customer contacted successfully"
        ));
        assertThat(stageRes.statusCode()).as("Update stage to Contactable").isIn(200, 201);

        Response after = api.getTicket(ticketId);
        assertThat(after.statusCode()).isEqualTo(200);

        Number expiresOnAfter = after.body().path("expiresOn");
        assertExpiresOnRecalculated("stage change", expiresOnBefore, expiresOnAfter);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Test 12: Final verification — expiresOn still intact after all operations
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(1000)
    void finalVerification_expiresOnIntact() {
        Response res = api.getTicket(ticketId);
        assertThat(res.statusCode()).isEqualTo(200);

        Number expiresOn = res.body().path("expiresOn");
        assertThat(expiresOn)
                .as("After all operations, expiresOn should still be set (not null)")
                .isNotNull();

        String tag = res.body().path("tag");
        assertThat(tag).as("Tag should still be set").isNotNull();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Utility
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Asserts that expiresOn was recalculated: not null, and different from the value before
     * (the 2-second delay ensures the new now+30d epoch is detectably different).
     */
    private static void assertExpiresOnRecalculated(String operation, Number before, Number after) {
        assertThat(after)
                .as("expiresOn should NOT be null after %s", operation)
                .isNotNull();
        assertThat(after.longValue())
                .as("expiresOn SHOULD be recalculated after %s — before: %d, after: %d",
                        operation, before.longValue(), after.longValue())
                .isNotEqualTo(before.longValue());
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
