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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ticket Bucket Analytics Scenario — exercises the analytics endpoints
 * with focus on:
 *
 *   S1: Setup (register, template, stages, product, creation rule, tickets, stage transitions)
 *   S2: Stage-count analytics (assigned-users, products, created-bys, clients)
 *   S3: Status-count analytics (assigned-users, products, created-bys, clients)
 *   S4: onlyCurrentStageStatus flag — current snapshot vs activity-based funnel
 *   S5: Response metadata — productTemplates, selectedProductTemplates, stageHierarchies
 *   S6: Product template filter — filtering by productTemplateIds
 *   S7: Cross-tab and date endpoints (products×clients, clients/dates, created-bys unique)
 *   S8: Edge cases — empty filters, narrowDateRange, includeAll, source filter
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TicketBucketAnalyticsScenario extends BaseIntegrationTest {

    private static String token;
    private static String clientCode;
    private static String appCode;
    private static EntityProcessorApi api;

    private static Number templateId;
    private static Number productId;
    private static Number userId;

    // Stage IDs (child stages — the ones we move tickets to)
    private static Number stageOpenId;          // child of Fresh (PRE_QUAL)
    private static Number stageContactableId;   // child of Contactable (PRE_QUAL)
    private static Number stageVisitDoneId;     // child of Visit (POST_QUAL)
    private static Number stageBookingDoneId;   // child of Booking (POST_QUAL)

    private static Number ticket1Id;
    private static Number ticket2Id;
    private static Number ticket3Id;

    // Wide date range covering test data (epoch seconds — approx Mar 1 to Mar 31, 2026 IST)
    private static final long START_DATE = 1772303400L;
    private static final long END_DATE = 1774981799L;
    private static final String TIMEZONE = "Asia/Kolkata";

    @BeforeAll
    void setup() {
        appCode = prop("leadzump.app.code");
        String parentClientCode = prop("leadzump.client.code");

        String uid = UUID.randomUUID().toString().substring(0, 8);
        String email = "re-bucket-" + uid + "@inttest.local";
        String password = "Test@1234";

        SecurityApi secApi = new SecurityApi(baseHost());
        String otp = prop("otp");

        Response otpRes = secApi.generateRegistrationOtp(parentClientCode, appCode, email);
        assertThat(otpRes.statusCode()).as("Generate OTP").isEqualTo(200);

        Response regRes = secApi.register(parentClientCode, appCode, mapOf(
                "clientName", "RE_Bucket_" + uid,
                "firstName", "BucketTest",
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

        userId = regRes.body().path("authentication.user.id");
        api = new EntityProcessorApi(givenAuth(token, clientCode, appCode));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S1: Setup — Template, Stages, Product, Creation Rule, Tickets
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(100)
    void s1_01_createProductTemplate() {
        Response res = api.createProductTemplate(mapOf(
                "name", "RE_IntTest_AnalyticsTemplate",
                "description", "Template for analytics testing",
                "productTemplateType", "GENERAL"
        ));

        assertThat(res.statusCode()).as("Create product template").isIn(200, 201);
        templateId = res.body().path("id");
        assertThat(templateId).isNotNull();
    }

    @Test
    @Order(110)
    void s1_02_createPreQualStages() {
        // Fresh → Open (child)
        Response fresh = api.createStage(mapOf(
                "name", "Fresh",
                "platform", "PRE_QUALIFICATION",
                "productTemplateId", templateId,
                "isParent", true,
                "order", 1,
                "stageType", "OPEN",
                "children", mapOf(
                        0, mapOf("name", "Open", "stageType", "OPEN", "order", 0)
                )
        ));
        assertThat(fresh.statusCode()).as("Create Fresh stage").isIn(200, 201);
        stageOpenId = fresh.body().path("child[0].id");

        // Contactable → Contacted (child)
        Response contactable = api.createStage(mapOf(
                "name", "Contactable",
                "platform", "PRE_QUALIFICATION",
                "productTemplateId", templateId,
                "isParent", true,
                "order", 2,
                "stageType", "OPEN",
                "children", mapOf(
                        0, mapOf("name", "Contacted", "stageType", "OPEN", "order", 0)
                )
        ));
        assertThat(contactable.statusCode()).as("Create Contactable stage").isIn(200, 201);
        stageContactableId = contactable.body().path("child[0].id");
    }

    @Test
    @Order(120)
    void s1_03_createPostQualStages() {
        // Visit → Visit Done (child)
        Response visit = api.createStage(mapOf(
                "name", "Visit",
                "platform", "POST_QUALIFICATION",
                "productTemplateId", templateId,
                "isParent", true,
                "order", 4,
                "stageType", "OPEN",
                "children", mapOf(
                        0, mapOf("name", "Visit Done", "stageType", "OPEN", "order", 0)
                )
        ));
        assertThat(visit.statusCode()).as("Create Visit stage").isIn(200, 201);
        stageVisitDoneId = visit.body().path("child[0].id");

        // Booking → Booking Done (child, success)
        Response booking = api.createStage(mapOf(
                "name", "Booking",
                "platform", "POST_QUALIFICATION",
                "productTemplateId", templateId,
                "isParent", true,
                "order", 8,
                "stageType", "CLOSED",
                "isSuccess", true,
                "isFailure", false,
                "children", mapOf(
                        0, mapOf("name", "Booking Done", "stageType", "CLOSED", "order", 0)
                )
        ));
        assertThat(booking.statusCode()).as("Create Booking stage").isIn(200, 201);
        stageBookingDoneId = booking.body().path("child[0].id");
    }

    @Test
    @Order(130)
    void s1_04_createProduct() {
        Response res = api.createProduct(mapOf(
                "name", "RE_IntTest_Analytics Tower",
                "description", "Product for analytics integration testing",
                "productTemplateId", templateId,
                "forPartner", true
        ));

        assertThat(res.statusCode()).as("Create product").isIn(200, 201);
        productId = res.body().path("id");
        assertThat(productId).isNotNull();
    }

    @Test
    @Order(140)
    void s1_05_createCreationRule() {
        Response ruleRes = api.createCreationRule(mapOf(
                "name", "Default Rule",
                "productTemplateId", templateId,
                "stageId", stageOpenId,
                "order", 0,
                "userDistributionType", "ROUND_ROBIN",
                "userDistributions", List.of(Map.of(
                        "userId", userId,
                        "percentage", 100
                ))
        ));
        assertThat(ruleRes.statusCode()).as("Create creation rule").isIn(200, 201);
    }

    @Test
    @Order(150)
    void s1_06_createTicket1_websiteSource() {
        Response res = api.createTicket(mapOf(
                "name", "RE_IntTest_AnalyticsLead1",
                "dialCode", 91,
                "phoneNumber", "+919100000001",
                "email", "analytics-lead1@inttest.local",
                "productId", productId,
                "source", "Website",
                "subSource", "Landing Page"
        ));

        assertThat(res.statusCode()).as("Create ticket 1").isIn(200, 201);
        ticket1Id = res.body().path("id");
        assertThat(ticket1Id).isNotNull();
    }

    @Test
    @Order(160)
    void s1_07_createTicket2_socialSource() {
        Response res = api.createTicket(mapOf(
                "name", "RE_IntTest_AnalyticsLead2",
                "dialCode", 91,
                "phoneNumber", "+919100000002",
                "email", "analytics-lead2@inttest.local",
                "productId", productId,
                "source", "Social Media",
                "subSource", "Facebook"
        ));

        assertThat(res.statusCode()).as("Create ticket 2").isIn(200, 201);
        ticket2Id = res.body().path("id");
        assertThat(ticket2Id).isNotNull();
    }

    @Test
    @Order(170)
    void s1_08_createTicket3_websiteSource() {
        Response res = api.createTicket(mapOf(
                "name", "RE_IntTest_AnalyticsLead3",
                "dialCode", 91,
                "phoneNumber", "+919100000003",
                "email", "analytics-lead3@inttest.local",
                "productId", productId,
                "source", "Website",
                "subSource", "Contact Form"
        ));

        assertThat(res.statusCode()).as("Create ticket 3").isIn(200, 201);
        ticket3Id = res.body().path("id");
        assertThat(ticket3Id).isNotNull();
    }

    @Test
    @Order(180)
    void s1_09_moveTicket1ToContactable() {
        Response res = api.updateTicketStage(ticket1Id, Map.of(
                "stageId", stageContactableId,
                "comment", "Contacted via phone"
        ));
        assertThat(res.statusCode()).as("Move ticket1 to Contactable").isIn(200, 201);
    }

    @Test
    @Order(190)
    void s1_10_moveTicket2ToVisitDone() {
        // Move ticket2 through stages: Contactable → Visit Done
        Response r1 = api.updateTicketStage(ticket2Id, Map.of(
                "stageId", stageContactableId,
                "comment", "Contacted via social"
        ));
        assertThat(r1.statusCode()).as("Move ticket2 to Contactable").isIn(200, 201);

        Response r2 = api.updateTicketStage(ticket2Id, Map.of(
                "stageId", stageVisitDoneId,
                "comment", "Site visit completed"
        ));
        assertThat(r2.statusCode()).as("Move ticket2 to Visit Done").isIn(200, 201);
    }

    @Test
    @Order(200)
    void s1_11_moveTicket3ToBookingDone() {
        // Move ticket3 through all stages to Booking Done
        Response r1 = api.updateTicketStage(ticket3Id, Map.of(
                "stageId", stageContactableId,
                "comment", "Contacted"
        ));
        assertThat(r1.statusCode()).as("Move ticket3 to Contactable").isIn(200, 201);

        Response r2 = api.updateTicketStage(ticket3Id, Map.of(
                "stageId", stageVisitDoneId,
                "comment", "Visit done"
        ));
        assertThat(r2.statusCode()).as("Move ticket3 to Visit Done").isIn(200, 201);

        Response r3 = api.updateTicketStage(ticket3Id, Map.of(
                "stageId", stageBookingDoneId,
                "comment", "Booking completed"
        ));
        assertThat(r3.statusCode()).as("Move ticket3 to Booking Done").isIn(200, 201);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S2: Stage-Count Analytics — All Grouping Dimensions
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(300)
    void s2_01_stageCounts_assignedUsers() {
        Response res = api.analyticsStageCounts_AssignedUsers(mapOf(
                "productIds", List.of(productId),
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE
        ));

        assertThat(res.statusCode()).as("Stage counts by assigned users").isEqualTo(200);
        Object content = res.body().path("content");
        assertThat(content).as("Should return content").isNotNull();
    }

    @Test
    @Order(310)
    void s2_02_stageCounts_products() {
        Response res = api.analyticsStageCounts_Products(mapOf(
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE
        ));

        assertThat(res.statusCode()).as("Stage counts by products").isEqualTo(200);
        Object content = res.body().path("content");
        assertThat(content).as("Should return content").isNotNull();
    }

    @Test
    @Order(320)
    void s2_03_stageCounts_createdBys() {
        Response res = api.analyticsStageCounts_CreatedBys(mapOf(
                "productIds", List.of(productId),
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE
        ));

        assertThat(res.statusCode()).as("Stage counts by created-bys").isEqualTo(200);
    }

    @Test
    @Order(330)
    void s2_04_stageCounts_clients() {
        Response res = api.analyticsStageCounts_Clients(mapOf(
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE
        ));

        assertThat(res.statusCode()).as("Stage counts by clients").isEqualTo(200);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S3: Status-Count Analytics — All Grouping Dimensions
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(400)
    void s3_01_statusCounts_assignedUsers() {
        Response res = api.analyticsStatusCounts_AssignedUsers(mapOf(
                "productIds", List.of(productId),
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE
        ));

        assertThat(res.statusCode()).as("Status counts by assigned users").isEqualTo(200);
        Object content = res.body().path("content");
        assertThat(content).as("Should return content").isNotNull();
    }

    @Test
    @Order(410)
    void s3_02_statusCounts_products() {
        Response res = api.analyticsStatusCounts_Products(mapOf(
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE
        ));

        assertThat(res.statusCode()).as("Status counts by products").isEqualTo(200);
        Object content = res.body().path("content");
        assertThat(content).as("Should return content").isNotNull();
    }

    @Test
    @Order(420)
    void s3_03_statusCounts_createdBys() {
        Response res = api.analyticsStatusCounts_CreatedBys(mapOf(
                "productIds", List.of(productId),
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE
        ));

        assertThat(res.statusCode()).as("Status counts by created-bys").isEqualTo(200);
    }

    @Test
    @Order(430)
    void s3_04_statusCounts_clients() {
        Response res = api.analyticsStatusCounts_Clients(mapOf(
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE
        ));

        assertThat(res.statusCode()).as("Status counts by clients").isEqualTo(200);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S4: onlyCurrentStageStatus — Current Snapshot vs Activity Funnel
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(500)
    void s4_01_stageCounts_assignedUsers_currentSnapshot() {
        Response res = api.analyticsStageCounts_AssignedUsers(mapOf(
                "productIds", List.of(productId),
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE,
                "onlyCurrentStageStatus", true
        ));

        assertThat(res.statusCode()).as("Current-state stage counts assigned users").isEqualTo(200);
        Object content = res.body().path("content");
        assertThat(content).as("Should return content").isNotNull();
    }

    @Test
    @Order(510)
    void s4_02_stageCounts_products_currentSnapshot() {
        Response res = api.analyticsStageCounts_Products(mapOf(
                "productIds", List.of(productId),
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE,
                "onlyCurrentStageStatus", true
        ));

        assertThat(res.statusCode()).as("Current-state stage counts products").isEqualTo(200);
        Object content = res.body().path("content");
        assertThat(content).as("Should return content").isNotNull();
    }

    @Test
    @Order(520)
    void s4_03_stageCounts_createdBys_currentSnapshot() {
        Response res = api.analyticsStageCounts_CreatedBys(mapOf(
                "productIds", List.of(productId),
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE,
                "onlyCurrentStageStatus", true
        ));

        assertThat(res.statusCode()).as("Current-state stage counts created-bys").isEqualTo(200);
    }

    @Test
    @Order(530)
    void s4_04_stageCounts_clients_currentSnapshot() {
        Response res = api.analyticsStageCounts_Clients(mapOf(
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE,
                "onlyCurrentStageStatus", true
        ));

        assertThat(res.statusCode()).as("Current-state stage counts clients").isEqualTo(200);
    }

    @Test
    @Order(540)
    void s4_05_statusCounts_assignedUsers_currentSnapshot() {
        Response res = api.analyticsStatusCounts_AssignedUsers(mapOf(
                "productIds", List.of(productId),
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE,
                "onlyCurrentStageStatus", true
        ));

        assertThat(res.statusCode()).as("Current-state status counts assigned users").isEqualTo(200);
        Object content = res.body().path("content");
        assertThat(content).as("Should return content").isNotNull();
    }

    @Test
    @Order(550)
    void s4_06_statusCounts_products_currentSnapshot() {
        Response res = api.analyticsStatusCounts_Products(mapOf(
                "productIds", List.of(productId),
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE,
                "onlyCurrentStageStatus", true
        ));

        assertThat(res.statusCode()).as("Current-state status counts products").isEqualTo(200);
        Object content = res.body().path("content");
        assertThat(content).as("Should return content").isNotNull();
    }

    @Test
    @Order(560)
    void s4_07_currentSnapshot_vs_activityBased_bothSucceed() {
        // Activity-based (default)
        Response activityRes = api.analyticsStageCounts_Products(mapOf(
                "productIds", List.of(productId),
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE,
                "onlyCurrentStageStatus", false
        ));

        // Current-snapshot
        Response currentRes = api.analyticsStageCounts_Products(mapOf(
                "productIds", List.of(productId),
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE,
                "onlyCurrentStageStatus", true
        ));

        assertThat(activityRes.statusCode()).as("Activity-based").isEqualTo(200);
        assertThat(currentRes.statusCode()).as("Current-state").isEqualTo(200);

        Object activityContent = activityRes.body().path("content");
        Object currentContent = currentRes.body().path("content");
        assertThat(activityContent).as("Activity content").isNotNull();
        assertThat(currentContent).as("Current content").isNotNull();
    }

    @Test
    @Order(570)
    void s4_08_clientsMe_currentSnapshot() {
        Response res = api.analyticsProductStages_ClientsMe(mapOf(
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE,
                "onlyCurrentStageStatus", true
        ));

        assertThat(res.statusCode()).as("Clients/me with onlyCurrentStageStatus").isEqualTo(200);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S5: Response Metadata — productTemplates, stageHierarchies
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(600)
    void s5_01_response_contains_productTemplates() {
        Response res = api.analyticsStageCounts_Products(mapOf(
                "productIds", List.of(productId),
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE
        ));

        assertThat(res.statusCode()).isEqualTo(200);
        Object productTemplates = res.body().path("productTemplates");
        assertThat(productTemplates).as("Response should include productTemplates").isNotNull();
    }

    @Test
    @Order(610)
    void s5_02_response_contains_selectedProductTemplates() {
        Response res = api.analyticsStageCounts_Products(mapOf(
                "productIds", List.of(productId),
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE
        ));

        assertThat(res.statusCode()).isEqualTo(200);
        Object selectedTemplates = res.body().path("selectedProductTemplates");
        assertThat(selectedTemplates).as("Response should include selectedProductTemplates").isNotNull();
    }

    @Test
    @Order(620)
    void s5_03_response_contains_stageHierarchies() {
        Response res = api.analyticsStageCounts_Products(mapOf(
                "productIds", List.of(productId),
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE
        ));

        assertThat(res.statusCode()).isEqualTo(200);
        Object stageHierarchies = res.body().path("stageHierarchies");
        assertThat(stageHierarchies).as("Response should include stageHierarchies").isNotNull();
    }

    @Test
    @Order(630)
    void s5_04_stageHierarchies_onAssignedUsersEndpoint() {
        Response res = api.analyticsStageCounts_AssignedUsers(mapOf(
                "productIds", List.of(productId),
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE
        ));

        assertThat(res.statusCode()).isEqualTo(200);
        List<?> stageHierarchies = res.body().path("stageHierarchies");
        assertThat(stageHierarchies).as("stageHierarchies on assigned users endpoint").isNotNull();
    }

    @Test
    @Order(640)
    void s5_05_metadata_on_statusCounts() {
        Response res = api.analyticsStatusCounts_AssignedUsers(mapOf(
                "productIds", List.of(productId),
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE
        ));

        assertThat(res.statusCode()).isEqualTo(200);
        Object templates = res.body().path("productTemplates");
        Object selectedTemplates = res.body().path("selectedProductTemplates");
        assertThat(templates).as("productTemplates on status endpoint").isNotNull();
        assertThat(selectedTemplates).as("selectedProductTemplates on status endpoint").isNotNull();
    }

    @Test
    @Order(650)
    void s5_06_metadata_with_onlyCurrentStageStatus() {
        Response res = api.analyticsStageCounts_Products(mapOf(
                "productIds", List.of(productId),
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE,
                "onlyCurrentStageStatus", true
        ));

        assertThat(res.statusCode()).isEqualTo(200);
        Object templates = res.body().path("productTemplates");
        Object hierarchies = res.body().path("stageHierarchies");
        assertThat(templates).as("productTemplates with onlyCurrentStageStatus").isNotNull();
        assertThat(hierarchies).as("stageHierarchies with onlyCurrentStageStatus").isNotNull();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S6: Product Template Filter
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(700)
    void s6_01_filterByProductTemplateIds() {
        Response res = api.analyticsStageCounts_Products(mapOf(
                "productTemplateIds", List.of(templateId),
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE
        ));

        assertThat(res.statusCode()).as("Filter by productTemplateIds").isEqualTo(200);
        Object content = res.body().path("content");
        assertThat(content).as("Should return content when filtering by template").isNotNull();
    }

    @Test
    @Order(710)
    void s6_02_filterByProductTemplateIds_currentSnapshot() {
        Response res = api.analyticsStageCounts_Products(mapOf(
                "productTemplateIds", List.of(templateId),
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE,
                "onlyCurrentStageStatus", true
        ));

        assertThat(res.statusCode()).as("Filter by template + onlyCurrentStageStatus").isEqualTo(200);
        Object content = res.body().path("content");
        assertThat(content).as("Should return content").isNotNull();
    }

    @Test
    @Order(720)
    void s6_03_filterByBothProductAndTemplate() {
        Response res = api.analyticsStageCounts_Products(mapOf(
                "productIds", List.of(productId),
                "productTemplateIds", List.of(templateId),
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE
        ));

        assertThat(res.statusCode()).as("Filter by both product and template").isEqualTo(200);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S7: Cross-tab and Date Endpoints
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(800)
    void s7_01_productsClients_crossTab() {
        Response res = api.analyticsProductsClients(mapOf(
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE
        ));

        assertThat(res.statusCode()).as("Products x Clients cross-tab").isEqualTo(200);
    }

    @Test
    @Order(810)
    void s7_02_clientsDates_days() {
        Response res = api.analyticsDateCounts_Clients(mapOf(
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE,
                "timePeriod", "DAYS"
        ));

        assertThat(res.statusCode()).as("Date counts by clients (DAYS)").isEqualTo(200);
    }

    @Test
    @Order(820)
    void s7_03_clientsDates_weeks() {
        Response res = api.analyticsDateCounts_Clients(mapOf(
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE,
                "timePeriod", "WEEKS"
        ));

        assertThat(res.statusCode()).as("Date counts by clients (WEEKS)").isEqualTo(200);
    }

    @Test
    @Order(830)
    void s7_04_clientsDates_months() {
        Response res = api.analyticsDateCounts_Clients(mapOf(
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE,
                "timePeriod", "MONTHS"
        ));

        assertThat(res.statusCode()).as("Date counts by clients (MONTHS)").isEqualTo(200);
    }

    @Test
    @Order(840)
    void s7_05_createdBysUniqueClientId() {
        Response res = api.analyticsStageCounts_CreatedBysUniqueClientId(mapOf(
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE
        ));

        assertThat(res.statusCode()).as("Created-bys unique client ID").isEqualTo(200);
    }

    @Test
    @Order(850)
    void s7_06_sourcesAssignedUsers_dateTrend() {
        Response res = api.analyticsStageCounts_SourcesAssignedUsers(mapOf(
                "productIds", List.of(productId),
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE
        ));

        assertThat(res.statusCode()).as("Sources x assigned users date trend").isEqualTo(200);
    }

    @Test
    @Order(860)
    void s7_07_sourcesAssignedUsers_withSourceFilter() {
        Response res = api.analyticsStageCounts_SourcesAssignedUsers(mapOf(
                "productIds", List.of(productId),
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE,
                "sources", List.of("Website")
        ));

        assertThat(res.statusCode()).as("Sources filter on date trend").isEqualTo(200);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S8: Edge Cases
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(900)
    void s8_01_noProductFilter() {
        Response res = api.analyticsStageCounts_Products(mapOf(
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE
        ));

        assertThat(res.statusCode()).as("No product filter should succeed").isEqualTo(200);
    }

    @Test
    @Order(910)
    void s8_02_includeAll_flag() {
        Response res = api.analyticsStageCounts_Products(mapOf(
                "productIds", List.of(productId),
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE,
                "includeAll", true
        ));

        assertThat(res.statusCode()).as("includeAll flag").isEqualTo(200);
    }

    @Test
    @Order(920)
    void s8_03_includeNone_flag() {
        Response res = api.analyticsStageCounts_Products(mapOf(
                "productIds", List.of(productId),
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE,
                "includeNone", true
        ));

        assertThat(res.statusCode()).as("includeNone flag").isEqualTo(200);
    }

    @Test
    @Order(930)
    void s8_04_narrowDateRange() {
        Response res = api.analyticsStageCounts_Products(mapOf(
                "productIds", List.of(productId),
                "startDate", 1774292400L,
                "endDate", 1774378799L,
                "timezone", TIMEZONE
        ));

        assertThat(res.statusCode()).as("Narrow date range").isEqualTo(200);
    }

    @Test
    @Order(940)
    void s8_05_filterBySource() {
        Response res = api.analyticsStageCounts_Products(mapOf(
                "productIds", List.of(productId),
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE,
                "sources", List.of("Website")
        ));

        assertThat(res.statusCode()).as("Filter by source").isEqualTo(200);
    }

    @Test
    @Order(950)
    void s8_06_includePercentage() {
        Response res = api.analyticsStageCounts_Products(mapOf(
                "productIds", List.of(productId),
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE,
                "includePercentage", true
        ));

        assertThat(res.statusCode()).as("includePercentage flag").isEqualTo(200);
    }

    @Test
    @Order(960)
    void s8_07_includeTotal() {
        Response res = api.analyticsStageCounts_Products(mapOf(
                "productIds", List.of(productId),
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE,
                "includeTotal", true
        ));

        assertThat(res.statusCode()).as("includeTotal flag").isEqualTo(200);
    }

    @Test
    @Order(970)
    void s8_08_onlyCurrentStageStatus_withSourceFilter() {
        Response res = api.analyticsStageCounts_Products(mapOf(
                "productIds", List.of(productId),
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE,
                "onlyCurrentStageStatus", true,
                "sources", List.of("Social Media")
        ));

        assertThat(res.statusCode()).as("onlyCurrentStageStatus with source filter").isEqualTo(200);
    }

    @Test
    @Order(980)
    void s8_09_onlyCurrentStageStatus_withSubSourceFilter() {
        Response res = api.analyticsStageCounts_Products(mapOf(
                "productIds", List.of(productId),
                "startDate", START_DATE,
                "endDate", END_DATE,
                "timezone", TIMEZONE,
                "onlyCurrentStageStatus", true,
                "subSources", List.of("Facebook")
        ));

        assertThat(res.statusCode()).as("onlyCurrentStageStatus with subSource filter").isEqualTo(200);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Utility
    // ═══════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> mapOf(Object... keyValues) {
        Map<Object, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return (Map<K, V>) map;
    }
}
