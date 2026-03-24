package com.fincity.saas.inttest.base;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

/**
 * Fluent helper for entity-processor REST API calls.
 * All methods return the raw Response so callers can assert status and extract fields.
 */
public class EntityProcessorApi {

    private static final String EP = "/api/entity/processor";

    private final RequestSpecification spec;

    public EntityProcessorApi(RequestSpecification spec) {
        this.spec = spec;
    }

    // ── Product Templates ──────────────────────────────────────────────

    public Response createProductTemplate(Map<String, Object> body) {
        return spec.body(body).post(EP + "/products/templates/req");
    }

    public Response getProductTemplate(Object id) {
        return spec.get(EP + "/products/templates/req/" + id);
    }

    // ── Stages ─────────────────────────────────────────────────────────

    public Response createStage(Map<String, Object> body) {
        return spec.body(body).post(EP + "/stages/req");
    }

    public Response getStages(String platform, Object templateId) {
        return spec.queryParam("platform", platform)
                .queryParam("productTemplateId", templateId)
                .get(EP + "/stages/values");
    }

    // ── Products ───────────────────────────────────────────────────────

    public Response createProduct(Map<String, Object> body) {
        return spec.body(body).post(EP + "/products/req");
    }

    public Response getProduct(Object id) {
        return spec.get(EP + "/products/" + id);
    }

    public Response getProductByCode(String code) {
        return spec.get(EP + "/products/code/" + code);
    }

    public Response getProductsEager(Map<String, Object> body) {
        return spec.body(body).post(EP + "/products/eager/query");
    }

    public Response getProductForms() {
        return spec.get(EP + "/products/forms");
    }

    public Response getProductStageRules() {
        return spec.get(EP + "/products/stages/rules");
    }

    // ── Creation Rules (c_rules) ───────────────────────────────────────

    public Response createCreationRule(Map<String, Object> body) {
        return spec.body(body).post(EP + "/products/tickets/c/rules");
    }

    // ── User Distributions ─────────────────────────────────────────────

    public Response createUserDistribution(Map<String, Object> body) {
        return spec.body(body).post(EP + "/tickets/c/users/distributions/req");
    }

    // ── Visibility Rules (ru_rules) ────────────────────────────────────

    public Response createVisibilityRule(Map<String, Object> body) {
        return spec.body(body).post(EP + "/products/tickets/ru/rules/req");
    }

    // ── Expiration Rules ───────────────────────────────────────────────

    public Response createExpirationRule(Map<String, Object> body) {
        return spec.body(body).post(EP + "/products/tickets/ex/rules");
    }

    // ── Duplication Rules ──────────────────────────────────────────────

    public Response createDuplicationRule(Map<String, Object> body) {
        return spec.body(body).post(EP + "/tickets/duplicate/rules");
    }

    public Response getDuplicationRules() {
        return spec.get(EP + "/tickets/duplicate/rules");
    }

    // ── Tickets ────────────────────────────────────────────────────────

    public Response createTicket(Map<String, Object> body) {
        return spec.body(body).post(EP + "/tickets/req");
    }

    public Response getTicket(Object id) {
        return spec.get(EP + "/tickets/" + id);
    }

    public Response getTicketEager(Object id) {
        return spec.get(EP + "/tickets/" + id + "/eager");
    }

    public Response queryUserTickets(Map<String, Object> body) {
        return spec.body(body).post(EP + "/tickets/users/query");
    }

    public Response updateTicketStage(Object ticketId, Map<String, Object> body) {
        return spec.body(body).patch(EP + "/tickets/req/" + ticketId + "/stage");
    }

    public Response updateTicketTag(Object ticketId, Map<String, Object> body) {
        return spec.body(body).patch(EP + "/tickets/req/" + ticketId + "/tag");
    }

    public Response reassignTicket(Object ticketId, Map<String, Object> body) {
        return spec.body(body).patch(EP + "/tickets/req/" + ticketId + "/reassign");
    }

    // ── Notes ──────────────────────────────────────────────────────────

    public Response createNote(Map<String, Object> body) {
        return spec.body(body).post(EP + "/notes/req");
    }

    public Response getNote(Object id) {
        return spec.get(EP + "/notes/" + id);
    }

    public Response getNotesEager(Map<String, Object> queryParams) {
        RequestSpecification r = spec;
        for (var e : queryParams.entrySet()) {
            r = r.queryParam(e.getKey(), e.getValue());
        }
        return r.get(EP + "/notes/eager");
    }

    // ── Tasks ──────────────────────────────────────────────────────────

    public Response createTask(Map<String, Object> body) {
        return spec.body(body).post(EP + "/tasks/req");
    }

    public Response getTaskTypes() {
        return spec.get(EP + "/tasks/types");
    }

    public Response updateTask(Object taskId, Map<String, Object> body) {
        return spec.body(body).put(EP + "/tasks/req/" + taskId);
    }

    // ── Activities ─────────────────────────────────────────────────────

    public Response logCallActivity(Map<String, Object> body) {
        return spec.body(body).post(EP + "/activities/call-log");
    }

    public Response getTicketActivities(Object ticketId, int page, int size) {
        return spec.queryParam("page", page)
                .queryParam("size", size)
                .queryParam("sort", "id,desc")
                .get(EP + "/activities/tickets/" + ticketId);
    }

    public Response getTicketActivitiesEager(Object ticketId, int page, int size) {
        return spec.queryParam("page", page)
                .queryParam("size", size)
                .queryParam("sort", "id,desc")
                .get(EP + "/activities/tickets/" + ticketId + "/eager");
    }

    // ── Partners ───────────────────────────────────────────────────────

    public Response createPartner(Map<String, Object> body) {
        return spec.body(body).post(EP + "/partners/req");
    }

    public Response getPartner(Object id) {
        return spec.get(EP + "/partners/" + id);
    }

    public Response updatePartnerVerificationStatus(Object partnerId, String status) {
        return spec.patch(EP + "/partners/req/" + partnerId + "/verification-status?status=" + status);
    }

    public Response togglePartnerDnc(Object partnerId) {
        return spec.patch(EP + "/partners/" + partnerId + "/dnc");
    }

    public Response getMyPartner() {
        return spec.get(EP + "/partners/me");
    }

    public Response getMyTeammates(int page, int size) {
        return spec.body(Map.of("page", page, "size", size))
                .post(EP + "/partners/me/teammates");
    }

    public Response getPartnerClients(int page, int size) {
        return spec.body(Map.of("page", page, "size", size))
                .post(EP + "/partners/clients");
    }

    // ── Product Comms ──────────────────────────────────────────────────

    public Response createProductComm(Map<String, Object> body) {
        return spec.body(body).post(EP + "/productComms/req");
    }

    public Response getProductComms(int page, int size) {
        return spec.queryParam("page", page)
                .queryParam("size", size)
                .get(EP + "/productComms");
    }

    public Response getDefaultProductComm(Map<String, Object> queryParams) {
        RequestSpecification r = spec;
        for (var e : queryParams.entrySet()) {
            r = r.queryParam(e.getKey(), e.getValue());
        }
        return r.get(EP + "/productComms/default");
    }

    public Response getProductCommByCode(String code) {
        return spec.get(EP + "/productComms/code/" + code);
    }

    // ── Owners ─────────────────────────────────────────────────────────

    public Response getOwners(int page, int size) {
        return spec.queryParam("page", page)
                .queryParam("size", size)
                .get(EP + "/owners");
    }

    // ── Campaigns ──────────────────────────────────────────────────────

    public Response getCampaigns() {
        return spec.get(EP + "/campaigns");
    }

    public Response getCampaignAds() {
        return spec.get(EP + "/campaigns/list/ads");
    }

    // ── Walk-in Forms ──────────────────────────────────────────────────

    public Response createWalkInForm(Map<String, Object> body) {
        return spec.body(body).post(EP + "/products/forms/req");
    }

    // ── Analytics ──────────────────────────────────────────────────────

    public Response analyticsStageCounts_AssignedUsers(Map<String, Object> body) {
        return spec.body(body).post(EP + "/analytics/tickets/stage-counts/assigned-users");
    }

    public Response analyticsStageCounts_Products(Map<String, Object> body) {
        return spec.body(body).post(EP + "/analytics/tickets/stage-counts/products");
    }

    public Response analyticsStageCounts_SourcesAssignedUsers(Map<String, Object> body) {
        return spec.body(body).post(EP + "/analytics/tickets/stage-counts/sources/assigned-users");
    }

    public Response analyticsDateCounts_Clients(Map<String, Object> body) {
        return spec.body(body).post(EP + "/analytics/tickets/clients/dates");
    }

    public Response analyticsProductStages_ClientsMe(Map<String, Object> body) {
        return spec.body(body).post(EP + "/analytics/tickets/products/stages/clients/me");
    }

    // ── Open API (no auth) ─────────────────────────────────────────────

    public static Response submitWebsiteLead(String baseHost, String productCode, Map<String, Object> body) {
        String host = baseHost.replaceFirst("https?://", "");
        return io.restassured.RestAssured.given()
                .baseUri(baseHost)
                .contentType("application/json")
                .header("X-Forwarded-Host", host)
                .header("X-Real-IP", "127.0.0.1")
                .body(body)
                .post(EP + "/open/tickets/req/website/" + productCode);
    }

    public static Response submitCampaignLead(String baseHost, Map<String, Object> body) {
        String host = baseHost.replaceFirst("https?://", "");
        return io.restassured.RestAssured.given()
                .baseUri(baseHost)
                .contentType("application/json")
                .header("X-Forwarded-Host", host)
                .header("X-Real-IP", "127.0.0.1")
                .body(body)
                .post(EP + "/open/tickets/req/campaigns");
    }
}
