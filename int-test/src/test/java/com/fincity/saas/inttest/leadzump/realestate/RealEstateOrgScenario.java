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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real Estate — Organizational Structure scenario.
 *
 * Tests team hierarchy, lead routing via creation rules, round-robin assignment,
 * and visibility/isolation across organizational teams:
 *
 *   - Builder admin registers and sets up template, stages, product
 *   - Invites team members with reporting hierarchy:
 *       SalesManager → SalesMember1, SalesMember2
 *       InsideSalesManager → InsideSalesMember
 *   - Creation rules route leads by source (default → round-robin to sales, CP → inside sales)
 *   - Visibility: members see only own leads, managers see team leads
 *   - Isolation: teams cannot see each other's leads
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RealEstateOrgScenario extends BaseIntegrationTest {

    // Builder admin
    private static String token, clientCode, appCode;
    private static Number userId;
    private static EntityProcessorApi api;
    private static Number templateId, productId;
    private static String productCode;
    private static Number stageFreshId, stageOpenId;

    // Shared UID for unique emails per run
    private static String uid;

    // Profile IDs (resolved dynamically)
    private static Number salesManagerProfileId, salesMemberProfileId;
    private static Number insideSalesManagerProfileId, insideSalesMemberProfileId;

    // Team members — tokens and user IDs
    private static String salesManagerToken, salesMember1Token, salesMember2Token;
    private static String insideSalesManagerToken, insideSalesMemberToken;
    private static Number salesManagerUserId, salesMember1UserId, salesMember2UserId;
    private static Number insideSalesManagerUserId, insideSalesMemberId;

    // Channel Partner
    private static String cp1ClientCode, cp1AdminToken;
    private static Number cp1AdminUserId, partnerId;

    // Leads
    private static Number lead1Id, lead2Id, cpLead1Id;

    @BeforeAll
    void setup() {
        appCode = prop("leadzump.app.code");
        String parentClientCode = prop("leadzump.client.code");

        uid = UUID.randomUUID().toString().substring(0, 8);
        String email = "re-org-" + uid + "@inttest.local";
        String password = "Test@1234";

        SecurityApi secApi = new SecurityApi(baseHost());
        String otp = prop("otp");

        Response otpRes = secApi.generateRegistrationOtp(parentClientCode, appCode, email);
        assertThat(otpRes.statusCode()).as("Generate OTP").isEqualTo(200);

        Response regRes = secApi.register(parentClientCode, appCode, mapOf(
                "clientName", "RE_Org_" + uid,
                "firstName", "OrgBuilder",
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

        // Resolve profile IDs dynamically
        ProfileHelper profiles = ProfileHelper.load(secApi, token, parentClientCode, appCode);
        salesManagerProfileId = profiles.getByName("Sales Manager");
        salesMemberProfileId = profiles.getByName("Sales Member");
        insideSalesManagerProfileId = profiles.getByName("Inside Sales Manager");
        insideSalesMemberProfileId = profiles.getByName("Inside Sales Member");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Setup: Template, Stages, Product
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(100)
    void setup_createTemplateAndStages() {
        Response tmpl = api.createProductTemplate(Map.of(
                "name", "RE_OrgTest_Template",
                "description", "Org test",
                "productTemplateType", "GENERAL"
        ));
        assertThat(tmpl.statusCode()).as("Create product template").isIn(200, 201);
        templateId = tmpl.body().path("id");
        assertThat(templateId).isNotNull();

        // Fresh → Open (nested child)
        Response stages = api.createStage(mapOf(
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
        assertThat(stages.statusCode()).as("Create Fresh stage with Open child").isIn(200, 201);
        stageFreshId = stages.body().path("parent.id");
        stageOpenId = stages.body().path("child[0].id");
        assertThat(stageFreshId).as("Parent stage ID").isNotNull();
        assertThat(stageOpenId).as("Child stage ID").isNotNull();
    }

    @Test
    @Order(110)
    void setup_createProduct() {
        Response res = api.createProduct(mapOf(
                "name", "RE_OrgTest_Product",
                "description", "Org test product",
                "productTemplateId", templateId,
                "forPartner", true
        ));

        assertThat(res.statusCode()).as("Create product").isIn(200, 201);
        productId = res.body().path("id");
        productCode = res.body().path("code");
        assertThat(productId).isNotNull();
        assertThat(productCode).as("Product code").isNotNull().isNotEmpty();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Org: Invite Team Members
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(200)
    void org_inviteSalesManager() {
        SecurityApi secApi = new SecurityApi(baseHost());

        Response invRes = secApi.inviteUser(token, clientCode, appCode, mapOf(
                "emailId", "salesmgr-" + uid + "@inttest.local",
                "firstName", "SalesManager",
                "lastName", "OrgTest",
                "profileId", salesManagerProfileId,
                "reportingTo", userId
        ));
        assertThat(invRes.statusCode()).as("Invite SalesManager").isIn(200, 201);
        String inviteCode = invRes.body().path("userRequest.inviteCode");
        assertThat(inviteCode).as("Invite code for SalesManager").isNotNull();

        Response accRes = secApi.acceptInvite(prop("leadzump.client.code"), appCode, mapOf(
                "emailId", "salesmgr-" + uid + "@inttest.local",
                "firstName", "SalesManager",
                "lastName", "OrgTest",
                "password", "Test@1234",
                "passType", "PASSWORD",
                "inviteCode", inviteCode
        ));
        assertThat(accRes.statusCode()).as("Accept invite SalesManager").isIn(200, 201);
        salesManagerToken = accRes.body().path("authentication.accessToken");
        salesManagerUserId = accRes.body().path("authentication.user.id");
        assertThat(salesManagerToken).as("SalesManager token").isNotNull().isNotEmpty();
        assertThat(salesManagerUserId).as("SalesManager userId").isNotNull();
    }

    @Test
    @Order(210)
    void org_inviteSalesMember1() {
        SecurityApi secApi = new SecurityApi(baseHost());

        Response invRes = secApi.inviteUser(token, clientCode, appCode, mapOf(
                "emailId", "salesmem1-" + uid + "@inttest.local",
                "firstName", "SalesMember1",
                "lastName", "OrgTest",
                "profileId", salesMemberProfileId,
                "reportingTo", salesManagerUserId
        ));
        assertThat(invRes.statusCode()).as("Invite SalesMember1").isIn(200, 201);
        String inviteCode = invRes.body().path("userRequest.inviteCode");
        assertThat(inviteCode).as("Invite code for SalesMember1").isNotNull();

        Response accRes = secApi.acceptInvite(prop("leadzump.client.code"), appCode, mapOf(
                "emailId", "salesmem1-" + uid + "@inttest.local",
                "firstName", "SalesMember1",
                "lastName", "OrgTest",
                "password", "Test@1234",
                "passType", "PASSWORD",
                "inviteCode", inviteCode
        ));
        assertThat(accRes.statusCode()).as("Accept invite SalesMember1").isIn(200, 201);
        salesMember1Token = accRes.body().path("authentication.accessToken");
        salesMember1UserId = accRes.body().path("authentication.user.id");
        assertThat(salesMember1Token).as("SalesMember1 token").isNotNull().isNotEmpty();
        assertThat(salesMember1UserId).as("SalesMember1 userId").isNotNull();
    }

    @Test
    @Order(220)
    void org_inviteSalesMember2() {
        SecurityApi secApi = new SecurityApi(baseHost());

        Response invRes = secApi.inviteUser(token, clientCode, appCode, mapOf(
                "emailId", "salesmem2-" + uid + "@inttest.local",
                "firstName", "SalesMember2",
                "lastName", "OrgTest",
                "profileId", salesMemberProfileId,
                "reportingTo", salesManagerUserId
        ));
        assertThat(invRes.statusCode()).as("Invite SalesMember2").isIn(200, 201);
        String inviteCode = invRes.body().path("userRequest.inviteCode");
        assertThat(inviteCode).as("Invite code for SalesMember2").isNotNull();

        Response accRes = secApi.acceptInvite(prop("leadzump.client.code"), appCode, mapOf(
                "emailId", "salesmem2-" + uid + "@inttest.local",
                "firstName", "SalesMember2",
                "lastName", "OrgTest",
                "password", "Test@1234",
                "passType", "PASSWORD",
                "inviteCode", inviteCode
        ));
        assertThat(accRes.statusCode()).as("Accept invite SalesMember2").isIn(200, 201);
        salesMember2Token = accRes.body().path("authentication.accessToken");
        salesMember2UserId = accRes.body().path("authentication.user.id");
        assertThat(salesMember2Token).as("SalesMember2 token").isNotNull().isNotEmpty();
        assertThat(salesMember2UserId).as("SalesMember2 userId").isNotNull();
    }

    @Test
    @Order(230)
    void org_inviteInsideSalesManager() {
        SecurityApi secApi = new SecurityApi(baseHost());

        Response invRes = secApi.inviteUser(token, clientCode, appCode, mapOf(
                "emailId", "insidesmgr-" + uid + "@inttest.local",
                "firstName", "InsideSalesManager",
                "lastName", "OrgTest",
                "profileId", insideSalesManagerProfileId,
                "reportingTo", userId
        ));
        assertThat(invRes.statusCode()).as("Invite InsideSalesManager").isIn(200, 201);
        String inviteCode = invRes.body().path("userRequest.inviteCode");
        assertThat(inviteCode).as("Invite code for InsideSalesManager").isNotNull();

        Response accRes = secApi.acceptInvite(prop("leadzump.client.code"), appCode, mapOf(
                "emailId", "insidesmgr-" + uid + "@inttest.local",
                "firstName", "InsideSalesManager",
                "lastName", "OrgTest",
                "password", "Test@1234",
                "passType", "PASSWORD",
                "inviteCode", inviteCode
        ));
        assertThat(accRes.statusCode()).as("Accept invite InsideSalesManager").isIn(200, 201);
        insideSalesManagerToken = accRes.body().path("authentication.accessToken");
        insideSalesManagerUserId = accRes.body().path("authentication.user.id");
        assertThat(insideSalesManagerToken).as("InsideSalesManager token").isNotNull().isNotEmpty();
        assertThat(insideSalesManagerUserId).as("InsideSalesManager userId").isNotNull();
    }

    @Test
    @Order(240)
    void org_inviteInsideSalesMember() {
        SecurityApi secApi = new SecurityApi(baseHost());

        Response invRes = secApi.inviteUser(token, clientCode, appCode, mapOf(
                "emailId", "insidesm-" + uid + "@inttest.local",
                "firstName", "InsideSalesMember",
                "lastName", "OrgTest",
                "profileId", insideSalesMemberProfileId,
                "reportingTo", insideSalesManagerUserId
        ));
        assertThat(invRes.statusCode()).as("Invite InsideSalesMember").isIn(200, 201);
        String inviteCode = invRes.body().path("userRequest.inviteCode");
        assertThat(inviteCode).as("Invite code for InsideSalesMember").isNotNull();

        Response accRes = secApi.acceptInvite(prop("leadzump.client.code"), appCode, mapOf(
                "emailId", "insidesm-" + uid + "@inttest.local",
                "firstName", "InsideSalesMember",
                "lastName", "OrgTest",
                "password", "Test@1234",
                "passType", "PASSWORD",
                "inviteCode", inviteCode
        ));
        assertThat(accRes.statusCode()).as("Accept invite InsideSalesMember").isIn(200, 201);
        insideSalesMemberToken = accRes.body().path("authentication.accessToken");
        insideSalesMemberId = accRes.body().path("authentication.user.id");
        assertThat(insideSalesMemberToken).as("InsideSalesMember token").isNotNull().isNotEmpty();
        assertThat(insideSalesMemberId).as("InsideSalesMember userId").isNotNull();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Rules: Creation Rules for Lead Routing
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(300)
    void rules_createDefaultCreationRule() {
        Response res = api.createCreationRule(mapOf(
                "name", "Default Website Rule",
                "productTemplateId", templateId,
                "stageId", stageFreshId,
                "order", 0,
                "userDistributionType", "ROUND_ROBIN",
                "userDistributions", List.of(
                        Map.of("userId", salesMember1UserId),
                        Map.of("userId", salesMember2UserId)
                )
        ));
        assertThat(res.statusCode()).as("Create default creation rule").isIn(200, 201);
        assertThat((Number) res.body().path("id")).as("Default rule ID").isNotNull();
    }

    @Test
    @Order(310)
    void rules_createCPSourceRule() {
        Response res = api.createCreationRule(mapOf(
                "name", "CP Source Rule",
                "productTemplateId", templateId,
                "stageId", stageFreshId,
                "order", 1,
                "userDistributionType", "ROUND_ROBIN",
                "condition", Map.of(
                        "operator", "AND",
                        "negate", false,
                        "conditions", List.of(Map.of(
                                "field", "Deal.source",
                                "value", "Channel Partner",
                                "operator", "EQUALS",
                                "negate", false
                        ))
                ),
                "userDistributions", List.of(
                        Map.of("userId", insideSalesMemberId)
                )
        ));
        assertThat(res.statusCode()).as("Create CP source creation rule").isIn(200, 201);
        assertThat((Number) res.body().path("id")).as("CP rule ID").isNotNull();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Leads: Create and Verify Assignment
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(500)
    void leads_createWebsiteLead1() {
        Response r1 = EntityProcessorApi.submitWebsiteLead(baseHost(), productCode, mapOf(
                "appCode", appCode,
                "clientCode", clientCode,
                "leadDetails", mapOf(
                        "firstName", "WebLead",
                        "lastName", "One",
                        "phone", mapOf("countryCode", 91, "number", "8000000001"),
                        "source", "Website"
                )
        ));
        assertThat(r1.statusCode()).as("Create website lead 1").isIn(200, 201);
        lead1Id = r1.body().path("id");
        assertThat(lead1Id).as("Lead 1 ID").isNotNull();

        // Assignment verification happens in visibility tests (s600+)
        // The admin can't directly getTicket due to sub-org scoping.
    }

    @Test
    @Order(510)
    void leads_createWebsiteLead2() {
        Response r2 = EntityProcessorApi.submitWebsiteLead(baseHost(), productCode, mapOf(
                "appCode", appCode,
                "clientCode", clientCode,
                "leadDetails", mapOf(
                        "firstName", "WebLead",
                        "lastName", "Two",
                        "phone", mapOf("countryCode", 91, "number", "8000000002"),
                        "source", "Website"
                )
        ));
        assertThat(r2.statusCode()).as("Create website lead 2").isIn(200, 201);
        lead2Id = r2.body().path("id");
        assertThat(lead2Id).as("Lead 2 ID").isNotNull();

        // Assignment verification happens in visibility tests (s600+)
    }

    @Test
    @Order(520)
    void leads_createCPLead() {
        Response res = api.createTicket(mapOf(
                "name", "CP Lead One",
                "dialCode", 91,
                "phoneNumber", "+918000000003",
                "productId", productId,
                "source", "Channel Partner",
                "subSource", "CP1"
        ));
        assertThat(res.statusCode()).as("Create CP lead").isIn(200, 201);
        cpLead1Id = res.body().path("id");
        assertThat(cpLead1Id).as("CP Lead ID").isNotNull();

        // Assignment verification happens in visibility tests (s600+)
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Visibility: Team Members See Only Their Own Leads
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(600)
    void visibility_salesMember1SeesOnlyOwn() {
        EntityProcessorApi sm1Api = new EntityProcessorApi(givenAuth(salesMember1Token, clientCode, appCode));
        Response res = sm1Api.listTickets(0, 100);
        assertThat(res.statusCode()).as("SalesMember1 list tickets").isEqualTo(200);

        List<Map<String, Object>> content = res.body().path("content");
        assertThat(content).as("SalesMember1 should see their assigned leads").isNotNull().isNotEmpty();
        // All visible leads should be assigned to SalesMember1
        content.forEach(ticket -> {
            Number assigned = (Number) ticket.get("assignedUserId");
            assertThat(assigned.longValue()).as("SalesMember1 only sees own leads")
                    .isEqualTo(salesMember1UserId.longValue());
        });
    }

    @Test
    @Order(610)
    void visibility_salesMember2SeesOnlyOwn() {
        EntityProcessorApi sm2Api = new EntityProcessorApi(givenAuth(salesMember2Token, clientCode, appCode));
        Response res = sm2Api.listTickets(0, 100);
        assertThat(res.statusCode()).as("SalesMember2 list tickets").isEqualTo(200);

        List<Map<String, Object>> content = res.body().path("content");
        assertThat(content).as("SalesMember2 should see their assigned lead").isNotNull().hasSize(1);
        Number assigned = (Number) content.get(0).get("assignedUserId");
        assertThat(assigned.longValue()).as("SalesMember2 only sees own leads")
                .isEqualTo(salesMember2UserId.longValue());
    }

    @Test
    @Order(620)
    void visibility_salesManagerSeesTeam() {
        EntityProcessorApi smgrApi = new EntityProcessorApi(givenAuth(salesManagerToken, clientCode, appCode));
        Response res = smgrApi.listTickets(0, 100);
        assertThat(res.statusCode()).as("SalesManager list tickets").isEqualTo(200);

        List<Map<String, Object>> content = res.body().path("content");
        assertThat(content).as("SalesManager should see team leads").isNotEmpty();

        List<Long> ids = content.stream()
                .map(m -> ((Number) m.get("id")).longValue())
                .toList();
        assertThat(ids).as("SalesManager should see team leads")
                .contains(lead1Id.longValue(), lead2Id.longValue());
    }

    @Test
    @Order(630)
    void visibility_insideSalesManagerSeesTeam() {
        EntityProcessorApi ismApi = new EntityProcessorApi(givenAuth(insideSalesManagerToken, clientCode, appCode));
        Response res = ismApi.listTickets(0, 100);
        assertThat(res.statusCode()).as("InsideSalesManager list tickets").isEqualTo(200);

        List<Map<String, Object>> content = res.body().path("content");
        assertThat(content).as("InsideSalesManager should see CP lead").isNotNull().hasSize(1);
        Number assigned = (Number) content.get(0).get("assignedUserId");
        assertThat(assigned.longValue()).as("CP lead assigned to InsideSalesMember")
                .isEqualTo(insideSalesMemberId.longValue());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Isolation: Teams Cannot See Other Team's Leads
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(700)
    void isolation_salesMemberCantSeeOtherTeam() {
        EntityProcessorApi sm1Api = new EntityProcessorApi(givenAuth(salesMember1Token, clientCode, appCode));
        Response res = sm1Api.listTickets(0, 100);
        assertThat(res.statusCode()).as("SalesMember1 list for isolation check").isEqualTo(200);

        List<Map<String, Object>> content = res.body().path("content");
        if (content != null) {
            // SalesMember1 should only see leads assigned to themselves
            content.forEach(ticket -> {
                Number assigned = (Number) ticket.get("assignedUserId");
                assertThat(assigned.longValue())
                        .as("SalesMember1 should only see own leads, not other teams'")
                        .isEqualTo(salesMember1UserId.longValue());
            });
        }
    }

    @Test
    @Order(710)
    void isolation_insideSalesMemberCantSeeSalesLeads() {
        EntityProcessorApi ismApi = new EntityProcessorApi(givenAuth(insideSalesMemberToken, clientCode, appCode));
        Response res = ismApi.listTickets(0, 100);
        assertThat(res.statusCode()).as("InsideSalesMember list for isolation check").isEqualTo(200);

        List<Map<String, Object>> content = res.body().path("content");
        if (content != null) {
            // InsideSalesMember should only see leads assigned to themselves
            content.forEach(ticket -> {
                Number assigned = (Number) ticket.get("assignedUserId");
                assertThat(assigned.longValue())
                        .as("InsideSalesMember should only see own leads")
                        .isEqualTo(insideSalesMemberId.longValue());
            });
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Utility
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Map.of() doesn't allow null values and has a 10-entry limit.
     * This helper allows any number of entries including nulls.
     */
    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> mapOf(Object... keyValues) {
        Map<Object, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return (Map<K, V>) map;
    }
}
