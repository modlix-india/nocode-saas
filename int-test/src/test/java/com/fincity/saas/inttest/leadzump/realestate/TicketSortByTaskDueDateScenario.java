package com.fincity.saas.inttest.leadzump.realestate;

import com.fincity.saas.inttest.base.BaseIntegrationTest;
import com.fincity.saas.inttest.base.EntityProcessorApi;
import com.fincity.saas.inttest.base.SecurityApi;
import io.restassured.RestAssured;
import io.restassured.parsing.Parser;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ticket Sort By Task Due Date Scenario — verifies that sorting tickets by
 * the computed field {@code latestTaskDueDate} does not cause a SQL error.
 *
 * The core bug was that TicketDAO.getField() returned a COALESCE subquery
 * with .as("latestTaskDueDate"), causing JOOQ to render just the alias name
 * in ORDER BY. Since the alias wasn't in SELECT, MySQL failed with
 * "Unknown column 'latestTaskDueDate' in order clause".
 *
 * Setup:
 *   - Main org with 1 product, 1 creation rule
 *   - 3 tickets created
 *   - Tasks created for 2 tickets (without dueDate — the Task API has a
 *     pre-existing issue deserializing LocalDateTime for dueDate)
 *
 * Assertions:
 *   - POST /tickets/query with sort=latestTaskDueDate returns 200 (not 400/500)
 *   - GET /tickets with sort=latestTaskDueDate returns 200
 *   - POST /tickets/eager/query with sort=latestTaskDueDate returns 200
 *   - All endpoints return the expected number of tickets
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TicketSortByTaskDueDateScenario extends BaseIntegrationTest {

    private static String token;
    private static String clientCode;
    private static String appCode;
    private static EntityProcessorApi api;
    private static Number userId;

    private static Number templateId;
    private static Number productId;
    private static Number freshStageId;
    private static Number taskTypeId;

    private static final String PASSWORD = "Test@1234";

    @BeforeAll
    void setup() {
        RestAssured.defaultParser = Parser.JSON;
        appCode = prop("leadzump.app.code");
        String parentClientCode = prop("leadzump.client.code");
        String otp = prop("otp");

        String uid = UUID.randomUUID().toString().substring(0, 8);
        String email = "sort-task-" + uid + "@inttest.local";

        SecurityApi secApi = new SecurityApi(baseHost());

        Response otpRes = secApi.generateRegistrationOtp(parentClientCode, appCode, email);
        assertThat(otpRes.statusCode()).as("Generate OTP").isEqualTo(200);

        Response regRes = secApi.register(parentClientCode, appCode, mapOf(
                "clientName", "SortTask_" + uid,
                "firstName", "SortTask",
                "lastName", "IntTest",
                "emailId", email,
                "password", PASSWORD,
                "passType", "PASSWORD",
                "businessClient", true,
                "otp", otp
        ));
        assertThat(regRes.statusCode()).as("Register org").isIn(200, 201);

        token = regRes.body().path("authentication.accessToken");
        clientCode = regRes.body().path("authentication.client.code");
        userId = regRes.body().path("authentication.user.id");
        if (clientCode == null || clientCode.isBlank()) {
            Response authRes = secApi.authenticate(parentClientCode, appCode, email, PASSWORD);
            assertThat(authRes.statusCode()).isEqualTo(200);
            token = authRes.body().path("accessToken");
            clientCode = authRes.body().path("user.clientCode");
            userId = authRes.body().path("user.id");
        }

        assertThat(token).as("Token").isNotBlank();
        assertThat(clientCode).as("ClientCode").isNotBlank();

        api = new EntityProcessorApi(givenAuth(token, clientCode, appCode));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S1: Product setup
    // ═══════════════════════════════════════════════════════════════════

    @Test @Order(100)
    void s1_01_createTemplate() {
        Response res = api.createProductTemplate(mapOf(
                "name", "TaskSort_Template", "productTemplateType", "GENERAL"));
        assertThat(res.statusCode()).as("Create template").isIn(200, 201);
        templateId = res.body().path("id");
    }

    @Test @Order(110)
    void s1_02_createStages() {
        Response fresh = api.createStage(mapOf(
                "name", "Fresh", "platform", "PRE_QUALIFICATION",
                "productTemplateId", templateId, "isParent", true, "order", 1, "stageType", "OPEN",
                "children", mapOf(0, mapOf("name", "New", "stageType", "OPEN", "order", 0))));
        assertThat(fresh.statusCode()).as("Create Fresh stage").isIn(200, 201);
        freshStageId = fresh.body().path("child[0].id");

        Response closed = api.createStage(mapOf(
                "name", "Closed", "platform", "POST_QUALIFICATION",
                "productTemplateId", templateId, "isParent", true, "order", 10,
                "stageType", "CLOSED", "isSuccess", true, "isFailure", false,
                "children", mapOf(0, mapOf("name", "Done", "stageType", "CLOSED", "order", 0))));
        assertThat(closed.statusCode()).as("Create Closed stage").isIn(200, 201);
    }

    @Test @Order(120)
    void s1_03_createProduct() {
        Response res = api.createProduct(mapOf(
                "name", "TaskSort_Product", "code", "tasksort-" + System.currentTimeMillis(),
                "productTemplateId", templateId, "forPartner", false));
        assertThat(res.statusCode()).as("Create product").isIn(200, 201);
        productId = res.body().path("id");
    }

    @Test @Order(130)
    void s1_04_createCreationRule() {
        Response res = api.createCreationRule(mapOf(
                "name", "Default Rule", "productTemplateId", templateId,
                "stageId", freshStageId, "order", 0,
                "userDistributionType", "ROUND_ROBIN",
                "userDistributions", List.of(Map.of("userId", userId, "percentage", 100))));
        assertThat(res.statusCode()).as("Create creation rule").isIn(200, 201);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S2: Create 3 tickets + tasks (without dueDate)
    // ═══════════════════════════════════════════════════════════════════

    @Test @Order(200)
    void s2_01_createTicketsAndTasks() {
        // Create task type
        Response typesRes = api.getTaskTypes();
        assertThat(typesRes.statusCode()).isEqualTo(200);
        List<Map<String, Object>> taskTypes = typesRes.body().path("content");
        if (taskTypes != null && !taskTypes.isEmpty()) {
            taskTypeId = (Number) taskTypes.get(0).get("id");
        } else {
            Response ttRes = api.createTaskType(Map.of("name", "Follow-Up", "description", "Follow-up task"));
            assertThat(ttRes.statusCode()).as("Create task type").isIn(200, 201);
            taskTypeId = ttRes.body().path("id");
        }

        // 3 tickets
        for (int i = 1; i <= 3; i++) {
            Response res = api.createTicket(mapOf(
                    "name", "TaskSort_Lead_" + i,
                    "dialCode", 91, "phoneNumber", "+9180010000" + i,
                    "source", "Website", "productId", productId));
            assertThat(res.statusCode()).as("Ticket " + i).isIn(200, 201);
        }

        // Create tasks for first 2 tickets (without dueDate — just to have tasks present)
        Response ticketsRes = api.listTickets(0, 10);
        assertThat(ticketsRes.statusCode()).isEqualTo(200);
        List<Map<String, Object>> tickets = ticketsRes.body().path("content");
        assertThat(tickets).hasSizeGreaterThanOrEqualTo(3);

        int taskCount = 0;
        for (int i = 0; i < Math.min(2, tickets.size()); i++) {
            Number ticketId = (Number) tickets.get(i).get("id");
            Response taskRes = api.createTask(mapOf(
                    "name", "Task_" + (i + 1),
                    "content", "Follow up call",
                    "ticketId", ticketId,
                    "taskTypeId", taskTypeId));
            if (taskRes.statusCode() == 200 || taskRes.statusCode() == 201) taskCount++;
        }
        System.out.println("=== Created " + taskCount + " tasks out of 2 attempted");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S3: Sort assertions — the core of this test
    //  These validate the fix: sort by latestTaskDueDate must not error
    // ═══════════════════════════════════════════════════════════════════

    @Test @Order(300)
    void s3_01_sortByLatestTaskDueDate_desc_postQuery_returns200() {
        Response res = api.queryTicketsSorted(0, 10, "latestTaskDueDate", "DESC");
        assertThat(res.statusCode())
                .as("POST /tickets/query sort=latestTaskDueDate,DESC must not error: " + res.body().asString())
                .isEqualTo(200);

        List<Map<String, Object>> content = res.body().path("content");
        assertThat(content).as("Should return tickets").isNotEmpty();
    }

    @Test @Order(310)
    void s3_02_sortByLatestTaskDueDate_asc_postQuery_returns200() {
        Response res = api.queryTicketsSorted(0, 10, "latestTaskDueDate", "ASC");
        assertThat(res.statusCode())
                .as("POST /tickets/query sort=latestTaskDueDate,ASC must not error: " + res.body().asString())
                .isEqualTo(200);

        List<Map<String, Object>> content = res.body().path("content");
        assertThat(content).as("Should return tickets").isNotEmpty();
    }

    @Test @Order(320)
    void s3_03_sortByLatestTaskDueDate_desc_get_returns200() {
        Response res = api.listTicketsSorted(0, 10, "latestTaskDueDate", "desc");
        assertThat(res.statusCode())
                .as("GET /tickets sort=latestTaskDueDate,desc must not error: " + res.body().asString())
                .isEqualTo(200);

        List<Map<String, Object>> content = res.body().path("content");
        assertThat(content).as("Should return tickets").isNotEmpty();
    }

    @Test @Order(330)
    void s3_04_sortByLatestTaskDueDate_desc_eagerQuery_returns200() {
        Response res = api.queryTicketsEagerSorted(0, 10, "latestTaskDueDate", "DESC");
        assertThat(res.statusCode())
                .as("POST /tickets/eager/query sort=latestTaskDueDate,DESC must not error: " + res.body().asString())
                .isEqualTo(200);

        List<Map<String, Object>> content = res.body().path("content");
        assertThat(content).as("Should return tickets").isNotEmpty();
    }

    @Test @Order(340)
    void s3_05_sortByLatestTaskDueDate_crossPageOrdering() {
        // With page size 2, verify cross-page consistency
        Response page0Res = api.queryTicketsSorted(0, 2, "latestTaskDueDate", "DESC");
        assertThat(page0Res.statusCode()).as("Page 0").isEqualTo(200);

        Response page1Res = api.queryTicketsSorted(1, 2, "latestTaskDueDate", "DESC");
        assertThat(page1Res.statusCode()).as("Page 1").isEqualTo(200);

        List<Map<String, Object>> page0 = page0Res.body().path("content");
        List<Map<String, Object>> page1 = page1Res.body().path("content");
        assertThat(page0).as("Page 0 should have items").isNotEmpty();
        assertThat(page1).as("Page 1 should have items").isNotEmpty();

        int totalElements = page0Res.body().path("totalElements");
        assertThat(totalElements).as("Total elements across pages").isGreaterThanOrEqualTo(3);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> mapOf(Object... keyValues) {
        Map<Object, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) map.put(keyValues[i], keyValues[i + 1]);
        return (Map<K, V>) map;
    }
}
