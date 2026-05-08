package com.fincity.saas.inttest.leadzump.realestate;

import com.fincity.saas.inttest.base.BaseIntegrationTest;
import com.fincity.saas.inttest.base.EntityProcessorApi;
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
 * Content Update/Delete ExpiresOn Recalculation Scenario.
 *
 * Verifies that the ticket's expiresOn field is recalculated when:
 *
 *   1. A note is updated
 *   2. A note is deleted
 *   3. A task is updated
 *   4. A task is deleted
 *
 * These were previously missing — only create operations triggered the
 * recalculation. The fix adds resetExpiresOn calls to the update and
 * delete paths in BaseContentService.
 *
 * Setup: Product template + stages + product + expiration rule (30 days
 * for "Walk-in" source) + task type.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ContentUpdateExpiresOnScenario extends BaseIntegrationTest {

    private static final long DELAY_MS = 2000;

    private static String token;
    private static String clientCode;
    private static String appCode;
    private static EntityProcessorApi api;
    private static SecurityApi secApi;

    // Setup data
    private static Number templateId;
    private static Number productId;
    private static Number taskTypeId;

    // Ticket
    private static Number ticketId;
    private static String ticketCode;

    // Note
    private static Number noteId;
    private static String noteCode;

    // Task
    private static Number taskId;
    private static String taskCode;

    // Second note/task for delete tests
    private static Number note2Id;
    private static Number task2Id;

    @BeforeAll
    void setup() {
        appCode = prop("leadzump.app.code");
        String parentClientCode = prop("leadzump.client.code");

        String uid = UUID.randomUUID().toString().substring(0, 8);
        String email = "re-content-exp-" + uid + "@inttest.local";
        String password = "Test@1234";

        secApi = new SecurityApi(baseHost());
        String otp = prop("otp");

        Response otpRes = secApi.generateRegistrationOtp(parentClientCode, appCode, email);
        assertThat(otpRes.statusCode()).as("Generate OTP").isEqualTo(200);

        Response regRes = secApi.register(parentClientCode, appCode, mapOf(
                "clientName", "RE_CExp_" + uid,
                "firstName", "ContentExp",
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
    //  Setup: Template + Stages + Product + Expiration Rule + Task Type
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(10)
    void setup_createTemplateAndStages() {
        Response tmpl = api.createProductTemplate(Map.of(
                "name", "RE_ContentExp_Template",
                "description", "Template for content expiresOn tests",
                "productTemplateType", "GENERAL"
        ));
        assertThat(tmpl.statusCode()).isIn(200, 201);
        templateId = tmpl.body().path("id");

        Response fresh = api.createStage(mapOf(
                "name", "Fresh", "platform", "PRE_QUALIFICATION",
                "productTemplateId", templateId, "isParent", true, "order", 1, "stageType", "OPEN",
                "children", mapOf(
                        0, mapOf("name", "Open", "stageType", "OPEN", "order", 0)
                )
        ));
        assertThat((Number) fresh.body().path("parent.id")).isNotNull();
    }

    @Test
    @Order(20)
    void setup_createProduct() {
        Response res = api.createProduct(mapOf(
                "name", "RE_ContentExp_Project",
                "description", "Project for content expiresOn tests",
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
                "name", "RE_ContentExp_WalkIn_30d",
                "productId", productId,
                "source", "Walk-in",
                "expiryDays", 30
        ));
        assertThat(res.statusCode()).as("Create expiration rule").isIn(200, 201);
    }

    @Test
    @Order(40)
    void setup_createTaskType() {
        Response res = api.createTaskType(mapOf(
                "name", "Site Visit",
                "description", "Site visit task type"
        ));
        assertThat(res.statusCode()).isIn(200, 201);
        taskTypeId = res.body().path("id");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Create ticket + initial note and task
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(100)
    void createTicket() {
        Response res = api.createTicket(mapOf(
                "name", "Priya Sharma",
                "dialCode", 91,
                "phoneNumber", "+919876500001",
                "email", "priya.sharma@example.com",
                "productId", productId,
                "source", "Walk-in",
                "comment", "Initial walk-in inquiry for 2BHK"
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

        Number expiresOn = res.body().path("expiresOn");
        assertThat(expiresOn)
                .as("Ticket with Walk-in source and 30-day expiry rule should have expiresOn")
                .isNotNull();
    }

    @Test
    @Order(120)
    void createNoteForUpdate() {
        Response res = api.createNote(mapOf(
                "content", "Customer wants east-facing flat on higher floor.",
                "ticketId", ticketId
        ));
        assertThat(res.statusCode()).as("Create note for update test").isIn(200, 201);
        noteId = res.body().path("id");
        noteCode = res.body().path("code");
        assertThat(noteId).isNotNull();
        assertThat(noteCode).isNotNull();
    }

    @Test
    @Order(130)
    void createTaskForUpdate() {
        Response res = api.createTask(mapOf(
                "name", "Schedule site visit",
                "content", "Arrange visit for east-wing units",
                "ticketId", ticketId,
                "taskTypeId", taskTypeId
        ));
        assertThat(res.statusCode()).as("Create task for update test").isIn(200, 201);
        taskId = res.body().path("id");
        taskCode = res.body().path("code");
        assertThat(taskId).isNotNull();
        assertThat(taskCode).isNotNull();
    }

    @Test
    @Order(140)
    void createNoteForDelete() {
        Response res = api.createNote(mapOf(
                "content", "This note will be deleted to test expiresOn reset.",
                "ticketId", ticketId
        ));
        assertThat(res.statusCode()).as("Create note for delete test").isIn(200, 201);
        note2Id = res.body().path("id");
        assertThat(note2Id).isNotNull();
    }

    @Test
    @Order(150)
    void createTaskForDelete() {
        Response res = api.createTask(mapOf(
                "name", "Temporary task",
                "content", "This task will be deleted to test expiresOn reset.",
                "ticketId", ticketId,
                "taskTypeId", taskTypeId
        ));
        assertThat(res.statusCode()).as("Create task for delete test").isIn(200, 201);
        task2Id = res.body().path("id");
        assertThat(task2Id).isNotNull();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Test 1: Update note — expiresOn SHOULD be recalculated
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(200)
    @SuppressWarnings("unchecked")
    void updateNote_expiresOnRecalculated() throws InterruptedException {
        Response before = api.getTicket(ticketId);
        assertThat(before.statusCode()).isEqualTo(200);
        Number expiresOnBefore = before.body().path("expiresOn");
        assertThat(expiresOnBefore).as("expiresOn before note update").isNotNull();

        Thread.sleep(DELAY_MS);

        Response noteRes = api.getNote(noteId);
        assertThat(noteRes.statusCode()).isEqualTo(200);
        Map<String, Object> noteBody = noteRes.body().as(Map.class);
        noteBody.put("content", "Updated: Customer prefers 3BHK east-facing on 10th floor.");

        Response updateRes = api.updateNoteByCode(noteCode, noteBody);
        assertThat(updateRes.statusCode())
                .as("Update note: " + updateRes.body().asString())
                .isIn(200, 201);

        Response after = api.getTicket(ticketId);
        assertThat(after.statusCode()).isEqualTo(200);

        Number expiresOnAfter = after.body().path("expiresOn");
        assertExpiresOnRecalculated("updating note", expiresOnBefore, expiresOnAfter);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Test 2: Update task — expiresOn SHOULD be recalculated
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(300)
    @SuppressWarnings("unchecked")
    void updateTask_expiresOnRecalculated() throws InterruptedException {
        Response before = api.getTicket(ticketId);
        assertThat(before.statusCode()).isEqualTo(200);
        Number expiresOnBefore = before.body().path("expiresOn");
        assertThat(expiresOnBefore).as("expiresOn before task update").isNotNull();

        Thread.sleep(DELAY_MS);

        Response taskRes = api.getTask(taskId);
        assertThat(taskRes.statusCode()).isEqualTo(200);
        Map<String, Object> taskBody = taskRes.body().as(Map.class);
        taskBody.put("content", "Updated: Visit rescheduled to next week.");

        Response updateRes = api.updateTaskByCode(taskCode, taskBody);
        assertThat(updateRes.statusCode())
                .as("Update task: " + updateRes.body().asString())
                .isIn(200, 201);

        Response after = api.getTicket(ticketId);
        assertThat(after.statusCode()).isEqualTo(200);

        Number expiresOnAfter = after.body().path("expiresOn");
        assertExpiresOnRecalculated("updating task", expiresOnBefore, expiresOnAfter);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Test 3: Delete note — expiresOn SHOULD be recalculated
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(400)
    void deleteNote_expiresOnRecalculated() throws InterruptedException {
        Response before = api.getTicket(ticketId);
        assertThat(before.statusCode()).isEqualTo(200);
        Number expiresOnBefore = before.body().path("expiresOn");
        assertThat(expiresOnBefore).as("expiresOn before note delete").isNotNull();

        Thread.sleep(DELAY_MS);

        Response deleteRes = api.deleteNote(note2Id);
        assertThat(deleteRes.statusCode())
                .as("Delete note: " + deleteRes.body().asString())
                .isIn(200, 204);

        Response after = api.getTicket(ticketId);
        assertThat(after.statusCode()).isEqualTo(200);

        Number expiresOnAfter = after.body().path("expiresOn");
        assertExpiresOnRecalculated("deleting note", expiresOnBefore, expiresOnAfter);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Test 4: Delete task — expiresOn SHOULD be recalculated
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(500)
    void deleteTask_expiresOnRecalculated() throws InterruptedException {
        Response before = api.getTicket(ticketId);
        assertThat(before.statusCode()).isEqualTo(200);
        Number expiresOnBefore = before.body().path("expiresOn");
        assertThat(expiresOnBefore).as("expiresOn before task delete").isNotNull();

        Thread.sleep(DELAY_MS);

        Response deleteRes = api.deleteTask(task2Id);
        assertThat(deleteRes.statusCode())
                .as("Delete task: " + deleteRes.body().asString())
                .isIn(200, 204);

        Response after = api.getTicket(ticketId);
        assertThat(after.statusCode()).isEqualTo(200);

        Number expiresOnAfter = after.body().path("expiresOn");
        assertExpiresOnRecalculated("deleting task", expiresOnBefore, expiresOnAfter);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Final verification
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(600)
    void finalVerification_expiresOnIntact() {
        Response res = api.getTicket(ticketId);
        assertThat(res.statusCode()).isEqualTo(200);

        Number expiresOn = res.body().path("expiresOn");
        assertThat(expiresOn)
                .as("After all content update/delete operations, expiresOn should still be set")
                .isNotNull();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Utility
    // ═══════════════════════════════════════════════════════════════════

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
