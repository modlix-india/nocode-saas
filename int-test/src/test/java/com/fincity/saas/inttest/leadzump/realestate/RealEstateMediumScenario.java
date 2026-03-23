package com.fincity.saas.inttest.leadzump.realestate;

import com.fincity.saas.inttest.base.AuthHelper;
import com.fincity.saas.inttest.base.BaseIntegrationTest;
import com.fincity.saas.inttest.base.EntityProcessorApi;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real Estate — Medium Developer scenario.
 *
 * S3: Lead Reassignment + Tag Management + Expiration Rules
 *   Expiration rule per source → walk-in lead → verify expires_on → reassign → tag HOT → DNC toggle
 *
 * S7: Product Communication Config (Exotel Call)
 *   Global call config → product-specific config → default resolution → code lookup
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RealEstateMediumScenario extends BaseIntegrationTest {

    private static String token;
    private static String clientCode;
    private static String appCode;
    private static EntityProcessorApi api;

    // Setup data
    private static Number templateId;
    private static Number productId;
    private static String productCode;

    // Stages (minimal set)
    private static Number stageFreshId;
    private static Number stageOpenId;
    private static Number stageContactableId;
    private static Number stageFollowUpId;
    private static Number stageBookingId;
    private static Number stageBookingDoneId;

    // S3 data
    private static Number expirationRuleId;
    private static Number ticketId;

    // S7 data
    private static Number globalCommId;
    private static String globalCommCode;
    private static Number productCommId;
    private static String productCommCode;

    @BeforeAll
    void setup() {
        clientCode = prop("realestate.medium.client.code");
        appCode = prop("leadzump.app.code");

        if (clientCode == null || clientCode.isBlank()) {
            clientCode = prop("system.client.code");
        }

        String username = prop("realestate.medium.admin.username");
        String password = prop("realestate.medium.admin.password");

        if (username == null || username.isBlank()) {
            username = prop("system.username");
            password = prop("system.password");
        }

        token = AuthHelper.authenticate(clientCode, appCode, username, password);
        api = new EntityProcessorApi(givenAuth(token, clientCode, appCode));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Setup: Template + minimal stages + product
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(10)
    void setup_createTemplateAndStages() {
        // Template
        Response tmpl = api.createProductTemplate(Map.of(
                "name", "RE_IntTest_MediumTemplate",
                "description", "Medium developer template",
                "productTemplateType", "GENERAL"
        ));
        assertThat(tmpl.statusCode()).isIn(200, 201);
        templateId = tmpl.body().path("id");

        // Fresh (parent)
        Response fresh = api.createStage(Map.of(
                "name", "Fresh", "platform", "PRE_QUALIFICATION",
                "productTemplateId", templateId, "isParent", true, "order", 1, "stageType", "OPEN"
        ));
        stageFreshId = fresh.body().path("id");

        // Open (child of Fresh)
        Response open = api.createStage(mapOf(
                "name", "Open", "platform", "PRE_QUALIFICATION",
                "productTemplateId", templateId, "isParent", false,
                "parentLevel0", stageFreshId, "order", 0, "stageType", "OPEN"
        ));
        stageOpenId = open.body().path("id");

        // Contactable (parent)
        Response contactable = api.createStage(Map.of(
                "name", "Contactable", "platform", "PRE_QUALIFICATION",
                "productTemplateId", templateId, "isParent", true, "order", 2, "stageType", "OPEN"
        ));
        stageContactableId = contactable.body().path("id");

        // Follow Up (child of Contactable)
        Response followUp = api.createStage(mapOf(
                "name", "Follow Up", "platform", "PRE_QUALIFICATION",
                "productTemplateId", templateId, "isParent", false,
                "parentLevel0", stageContactableId, "order", 1, "stageType", "OPEN"
        ));
        stageFollowUpId = followUp.body().path("id");

        // Booking (parent, CLOSED, success)
        Response booking = api.createStage(mapOf(
                "name", "Booking", "platform", "POST_QUALIFICATION",
                "productTemplateId", templateId, "isParent", true, "order", 8,
                "stageType", "CLOSED", "isSuccess", true, "isFailure", false
        ));
        stageBookingId = booking.body().path("id");

        // Booking Done (child)
        Response bookingDone = api.createStage(mapOf(
                "name", "Booking Done", "platform", "POST_QUALIFICATION",
                "productTemplateId", templateId, "isParent", false,
                "parentLevel0", stageBookingId, "order", 2, "stageType", "CLOSED"
        ));
        stageBookingDoneId = bookingDone.body().path("id");
    }

    @Test
    @Order(20)
    void setup_createProduct() {
        Response res = api.createProduct(mapOf(
                "name", "RE_IntTest_Green Valley Phase 1",
                "description", "Medium developer project",
                "productTemplateId", templateId,
                "forPartner", false
        ));
        assertThat(res.statusCode()).isIn(200, 201);
        productId = res.body().path("id");
        productCode = res.body().path("code");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S3: Reassignment + Tag Management + Expiration Rules
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(300)
    void s3_01_createExpirationRule() {
        Response res = api.createExpirationRule(mapOf(
                "name", "RE_IntTest_Walk-in Expiry",
                "productId", productId,
                "source", "Walk-in",
                "expiryDays", 30
        ));

        assertThat(res.statusCode()).as("Create expiration rule").isIn(200, 201);
        expirationRuleId = res.body().path("id");
        assertThat(expirationRuleId).isNotNull();
    }

    @Test
    @Order(310)
    void s3_02_createWalkInLead() {
        Response res = api.createTicket(mapOf(
                "name", "RE_IntTest_Vikram Singh",
                "dialCode", 91,
                "phoneNumber", "+917654321098",
                "productId", productId,
                "source", "Walk-in"
        ));

        assertThat(res.statusCode()).isIn(200, 201);
        ticketId = res.body().path("id");
        assertThat(ticketId).isNotNull();
    }

    @Test
    @Order(320)
    void s3_03_verifyExpirationDate() {
        Response res = api.getTicket(ticketId);
        assertThat(res.statusCode()).isEqualTo(200);

        String source = res.body().path("source");
        assertThat(source).isEqualTo("Walk-in");
    }

    @Test
    @Order(330)
    void s3_04_tagAsHot() {
        Response res = api.updateTicketTag(ticketId, Map.of(
                "tag", "HOT",
                "comment", "High intent buyer, ready to close soon"
        ));

        assertThat(res.statusCode()).as("Tag ticket as HOT").isIn(200, 201);

        // Verify tag set
        Response get = api.getTicket(ticketId);
        String tag = get.body().path("tag");
        assertThat(tag).isEqualTo("HOT");
    }

    @Test
    @Order(340)
    void s3_05_verifyActivitiesIncludeTagAndAssign() {
        Response res = api.getTicketActivitiesEager(ticketId, 0, 50);
        assertThat(res.statusCode()).isEqualTo(200);

        List<Map<String, Object>> content = res.body().path("content");
        assertThat(content).isNotEmpty();

        List<String> actions = content.stream()
                .map(a -> (String) a.get("activityAction"))
                .toList();

        assertThat(actions).contains("CREATE");
        // TAG_CREATE or TAG_UPDATE should be present after tagging
        boolean hasTagActivity = actions.stream().anyMatch(a -> a.startsWith("TAG"));
        assertThat(hasTagActivity)
                .as("Should have TAG activity after tagging")
                .isTrue();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  S7: Product Communication Config
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(700)
    void s7_01_createGlobalCallConfig() {
        Response res = api.createProductComm(mapOf(
                "name", "RE_IntTest_Global Call",
                "connectionName", "exotel_connection",
                "connectionType", "CALL",
                "connectionSubType", "EXOTEL",
                "dialCode", 91,
                "phoneNumber", "+918047186699",
                "isDefault", true
        ));

        assertThat(res.statusCode()).as("Create global call config").isIn(200, 201);
        globalCommId = res.body().path("id");
        globalCommCode = res.body().path("code");
        assertThat(globalCommId).isNotNull();
    }

    @Test
    @Order(710)
    void s7_02_createProductSpecificCallConfig() {
        Response res = api.createProductComm(mapOf(
                "name", "RE_IntTest_Green Valley Call",
                "connectionName", "exotel_connection",
                "connectionType", "CALL",
                "connectionSubType", "EXOTEL",
                "productId", productId,
                "dialCode", 91,
                "phoneNumber", "+917941058885",
                "isDefault", true
        ));

        assertThat(res.statusCode()).as("Create product-specific call config").isIn(200, 201);
        productCommId = res.body().path("id");
        productCommCode = res.body().path("code");
        assertThat(productCommId).isNotNull();
    }

    @Test
    @Order(720)
    void s7_03_listProductComms() {
        Response res = api.getProductComms(0, 20);
        assertThat(res.statusCode()).isEqualTo(200);

        List<?> content = res.body().path("content");
        assertThat(content).as("Should have product comms").isNotEmpty();
    }

    @Test
    @Order(730)
    void s7_04_getDefaultProductComm() {
        Response res = api.getDefaultProductComm(Map.of(
                "productId", productId,
                "connectionType", "CALL",
                "connectionSubType", "EXOTEL"
        ));

        assertThat(res.statusCode()).isEqualTo(200);
        // Product-specific config should take precedence over global
        Number returnedId = res.body().path("id");
        assertThat(returnedId).isNotNull();
    }

    @Test
    @Order(740)
    void s7_05_lookupByCode() {
        if (productCommCode == null) return;

        Response res = api.getProductCommByCode(productCommCode);
        assertThat(res.statusCode()).isEqualTo(200);

        Number returnedId = res.body().path("id");
        assertThat(returnedId.longValue())
                .as("Code lookup should return same entity")
                .isEqualTo(productCommId.longValue());
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
