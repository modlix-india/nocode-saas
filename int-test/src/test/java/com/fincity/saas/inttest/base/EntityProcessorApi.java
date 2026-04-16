package com.fincity.saas.inttest.base;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Fluent helper for entity-processor REST API calls.
 * All methods return the raw Response so callers can assert status and extract fields.
 * Each method creates a fresh RequestSpecification copy to avoid mutation side effects.
 */
public class EntityProcessorApi {

    private static final String EP = "/api/entity/processor";

    private final RequestSpecification spec;

    public EntityProcessorApi(RequestSpecification spec) {
        this.spec = spec;
    }

    /** Create a fresh copy of the base spec to avoid mutation between calls. */
    private RequestSpecification req() {
        return given().spec(spec);
    }

    // ── Product Templates ──────────────────────────────────────────────

    public Response createProductTemplate(Map<String, Object> body) {
        return req().body(body).post(EP + "/products/templates/req");
    }

    public Response getProductTemplate(Object id) {
        return req().get(EP + "/products/templates/req/" + id);
    }

    // ── Stages ─────────────────────────────────────────────────────────

    public Response createStage(Map<String, Object> body) {
        return req().body(body).post(EP + "/stages/req");
    }

    public Response getStages(String platform, Object templateId) {
        return req().queryParam("platform", platform)
                .queryParam("productTemplateId", templateId)
                .get(EP + "/stages/values");
    }

    // ── Products ───────────────────────────────────────────────────────

    public Response createProduct(Map<String, Object> body) {
        return req().body(body).post(EP + "/products/req");
    }

    public Response getProduct(Object id) {
        return req().get(EP + "/products/" + id);
    }

    public Response getProductByCode(String code) {
        return req().get(EP + "/products/code/" + code);
    }

    public Response getProductsEager(Map<String, Object> body) {
        return req().body(body).post(EP + "/products/eager/query");
    }

    public Response getProductForms() {
        return req().get(EP + "/products/forms");
    }

    public Response getProductStageRules() {
        return req().get(EP + "/products/stages/rules");
    }

    // ── Creation Rules (c_rules) ───────────────────────────────────────

    public Response createCreationRule(Map<String, Object> body) {
        return req().body(body).post(EP + "/products/tickets/c/rules");
    }

    // ── User Distributions ─────────────────────────────────────────────

    public Response createUserDistribution(Map<String, Object> body) {
        return req().body(body).post(EP + "/tickets/c/users/distributions/req");
    }

    // ── Visibility Rules (ru_rules) ────────────────────────────────────

    public Response createVisibilityRule(Map<String, Object> body) {
        return req().body(body).post(EP + "/products/tickets/ru/rules/req");
    }

    // ── Expiration Rules ───────────────────────────────────────────────

    public Response createExpirationRule(Map<String, Object> body) {
        return req().body(body).post(EP + "/products/tickets/ex/rules");
    }

    // ── Duplication Rules ──────────────────────────────────────────────

    public Response createDuplicationRule(Map<String, Object> body) {
        return req().body(body).post(EP + "/tickets/duplicate/rules");
    }

    public Response getDuplicationRules() {
        return req().get(EP + "/tickets/duplicate/rules");
    }

    // ── Tickets ────────────────────────────────────────────────────────

    public Response createTicket(Map<String, Object> body) {
        return req().body(body).post(EP + "/tickets/req");
    }

    public Response getTicket(Object id) {
        return req().get(EP + "/tickets/" + id);
    }

    public Response getTicketEager(Object id) {
        return req().get(EP + "/tickets/" + id + "/eager");
    }

    /** List tickets visible to the current user (paginated eager endpoint). */
    public Response listTickets(int page, int size) {
        return req().queryParam("page", page).queryParam("size", size)
                .queryParam("sort", "id,desc")
                .get(EP + "/tickets/eager");
    }

    /** Query tickets with full body via POST /tickets/query. */
    public Response queryTickets(Map<String, Object> body) {
        return req().body(body).post(EP + "/tickets/query");
    }

    /** Query tickets with explicit sort via POST /tickets/query. */
    public Response queryTicketsSorted(int page, int size, String sortProperty, String sortDirection) {
        return req().body(Map.of(
                "page", page,
                "size", size,
                "sort", List.of(Map.of("direction", sortDirection.toUpperCase(), "property", sortProperty))
        )).post(EP + "/tickets/query");
    }

    /** Query tickets eager with explicit sort via POST /tickets/eager/query. */
    public Response queryTicketsEagerSorted(int page, int size, String sortProperty, String sortDirection) {
        return req().body(Map.of(
                "page", page,
                "size", size,
                "sort", List.of(Map.of("direction", sortDirection.toUpperCase(), "property", sortProperty))
        )).post(EP + "/tickets/eager/query");
    }

    /** List tickets with sort via GET query param. */
    public Response listTicketsSorted(int page, int size, String sortProperty, String sortDirection) {
        return req().queryParam("page", page).queryParam("size", size)
                .queryParam("sort", sortProperty + "," + sortDirection.toLowerCase())
                .get(EP + "/tickets");
    }

    public Response queryUserTickets(Map<String, Object> body) {
        return req().body(body).post(EP + "/tickets/users/query");
    }

    public Response updateTicketStage(Object ticketId, Map<String, Object> body) {
        return req().body(body).patch(EP + "/tickets/req/" + ticketId + "/stage");
    }

    public Response updateTicketTag(Object ticketId, Map<String, Object> body) {
        return req().body(body).patch(EP + "/tickets/req/" + ticketId + "/tag");
    }

    public Response reassignTicket(Object ticketId, Map<String, Object> body) {
        return req().body(body).patch(EP + "/tickets/req/" + ticketId + "/reassign");
    }

    public Response updateTicketByCode(String code, Map<String, Object> body) {
        return req().body(body).put(EP + "/tickets/code/" + code);
    }

    public Response bulkReassign(Map<String, Object> body) {
        return req().body(body).patch(EP + "/tickets/bulk-reassign");
    }

    // ── Notes ──────────────────────────────────────────────────────────

    public Response createNote(Map<String, Object> body) {
        return req().body(body).post(EP + "/notes/req");
    }

    public Response getNote(Object id) {
        return req().get(EP + "/notes/" + id);
    }

    public Response updateNoteByCode(String noteCode, Map<String, Object> body) {
        return req().body(body).put(EP + "/notes/code/" + noteCode);
    }

    public Response deleteNote(Object noteId) {
        return req().delete(EP + "/notes/" + noteId);
    }

    public Response getNotesEager(Map<String, Object> queryParams) {
        RequestSpecification r = req();
        for (var e : queryParams.entrySet()) {
            r = r.queryParam(e.getKey(), e.getValue());
        }
        return r.get(EP + "/notes/eager");
    }

    // ── Tasks ──────────────────────────────────────────────────────────

    public Response createTask(Map<String, Object> body) {
        return req().body(body).post(EP + "/tasks/req");
    }

    public Response createTaskType(Map<String, Object> body) {
        return req().body(body).post(EP + "/tasks/types");
    }

    public Response getTaskTypes() {
        return req().get(EP + "/tasks/types");
    }

    public Response getTask(Object id) {
        return req().get(EP + "/tasks/" + id);
    }

    public Response updateTask(Object taskId, Map<String, Object> body) {
        return req().body(body).put(EP + "/tasks/req/" + taskId);
    }

    public Response updateTaskByCode(String taskCode, Map<String, Object> body) {
        return req().body(body).put(EP + "/tasks/code/" + taskCode);
    }

    public Response deleteTask(Object taskId) {
        return req().delete(EP + "/tasks/" + taskId);
    }

    // ── Activities ─────────────────────────────────────────────────────

    public Response logCallActivity(Map<String, Object> body) {
        return req().body(body).post(EP + "/activities/call-log");
    }

    public Response getTicketActivities(Object ticketId, int page, int size) {
        return req().queryParam("page", page)
                .queryParam("size", size)
                .queryParam("sort", "id,desc")
                .get(EP + "/activities/tickets/" + ticketId);
    }

    public Response getTicketActivitiesEager(Object ticketId, int page, int size) {
        return req().queryParam("page", page)
                .queryParam("size", size)
                .queryParam("sort", "id,desc")
                .get(EP + "/activities/tickets/" + ticketId + "/eager");
    }

    // ── Partners ───────────────────────────────────────────────────────

    public Response createPartner(Map<String, Object> body) {
        return req().body(body).post(EP + "/partners/req");
    }

    public Response getPartner(Object id) {
        return req().get(EP + "/partners/" + id);
    }

    public Response updatePartnerVerificationStatus(Object partnerId, String status) {
        return req().patch(EP + "/partners/req/" + partnerId + "/verification-status?status=" + status);
    }

    public Response togglePartnerDnc(Object partnerId) {
        return req().patch(EP + "/partners/" + partnerId + "/dnc");
    }

    public Response getMyPartner() {
        return req().get(EP + "/partners/me");
    }

    public Response getMyTeammates(int page, int size) {
        return req().body(Map.of("page", page, "size", size))
                .post(EP + "/partners/me/teammates");
    }

    public Response queryPartners(int page, int size) {
        return req().body(Map.of("page", page, "size", size))
                .post(EP + "/partners/query");
    }

    public Response queryPartnersSorted(int page, int size, String sortProperty, String sortDirection) {
        return req().body(Map.of(
                "page", page,
                "size", size,
                "sort", List.of(Map.of("direction", sortDirection.toUpperCase(), "property", sortProperty))
        )).post(EP + "/partners/query");
    }

    public Response queryPartnersWithCondition(int page, int size, Map<String, Object> condition) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("page", page);
        body.put("size", size);
        body.put("condition", condition);
        return req().body(body).post(EP + "/partners/query");
    }

    public Response queryPartnersSortedWithCondition(int page, int size, String sortProperty, String sortDirection,
            Map<String, Object> condition) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("page", page);
        body.put("size", size);
        body.put("sort", List.of(Map.of("direction", sortDirection.toUpperCase(), "property", sortProperty)));
        if (condition != null) body.put("condition", condition);
        return req().body(body).post(EP + "/partners/query");
    }

    public static Response triggerPartnerDenorm(boolean delta) {
        return given()
                .contentType("application/json")
                .post("http://localhost:8080" + EP + "/partners/internal/denorm?delta=" + delta);
    }

    // ── Product Comms ──────────────────────────────────────────────────

    public Response createProductComm(Map<String, Object> body) {
        return req().body(body).post(EP + "/productComms/req");
    }

    public Response getProductComms(int page, int size) {
        return req().queryParam("page", page)
                .queryParam("size", size)
                .get(EP + "/productComms");
    }

    public Response getDefaultProductComm(Map<String, Object> queryParams) {
        RequestSpecification r = req();
        for (var e : queryParams.entrySet()) {
            r = r.queryParam(e.getKey(), e.getValue());
        }
        return r.get(EP + "/productComms/default");
    }

    public Response getProductCommByCode(String code) {
        return req().get(EP + "/productComms/code/" + code);
    }

    // ── Owners ─────────────────────────────────────────────────────────

    public Response getOwners(int page, int size) {
        return req().queryParam("page", page)
                .queryParam("size", size)
                .get(EP + "/owners");
    }

    // ── Campaigns ──────────────────────────────────────────────────────

    public Response getCampaigns() {
        return req().get(EP + "/campaigns");
    }

    public Response getCampaignAds() {
        return req().get(EP + "/campaigns/list/ads");
    }

    // ── Walk-in Forms ──────────────────────────────────────────────────

    public Response createWalkInForm(Map<String, Object> body) {
        return req().body(body).post(EP + "/products/forms/req");
    }

    // ── Analytics ──────────────────────────────────────────────────────

    public Response analyticsStageCounts_AssignedUsers(Map<String, Object> body) {
        return req().body(body).post(EP + "/analytics/tickets/stage-counts/assigned-users");
    }

    public Response analyticsStageCounts_Products(Map<String, Object> body) {
        return req().body(body).post(EP + "/analytics/tickets/stage-counts/products");
    }

    public Response analyticsStageCounts_SourcesAssignedUsers(Map<String, Object> body) {
        return req().body(body).post(EP + "/analytics/tickets/stage-counts/sources/assigned-users");
    }

    public Response analyticsDateCounts_Clients(Map<String, Object> body) {
        return req().body(body).post(EP + "/analytics/tickets/clients/dates");
    }

    public Response analyticsProductStages_ClientsMe(Map<String, Object> body) {
        return req().body(body).post(EP + "/analytics/tickets/products/stages/clients/me");
    }

    // ── Analytics (Status-based) ─────────────────────────────────────────

    public Response analyticsStatusCounts_AssignedUsers(Map<String, Object> body) {
        return req().body(body).post(EP + "/analytics/tickets/status-counts/assigned-users");
    }

    public Response analyticsStatusCounts_Products(Map<String, Object> body) {
        return req().body(body).post(EP + "/analytics/tickets/products/statuses");
    }

    public Response analyticsStatusCounts_CreatedBys(Map<String, Object> body) {
        return req().body(body).post(EP + "/analytics/tickets/status-counts/created-bys");
    }

    public Response analyticsStatusCounts_Clients(Map<String, Object> body) {
        return req().body(body).post(EP + "/analytics/tickets/status-counts/clients");
    }

    // ── Analytics (Stage-based — additional) ──────────────────────────────

    public Response analyticsStageCounts_CreatedBys(Map<String, Object> body) {
        return req().body(body).post(EP + "/analytics/tickets/stage-counts/created-bys");
    }

    public Response analyticsStageCounts_Clients(Map<String, Object> body) {
        return req().body(body).post(EP + "/analytics/tickets/stage-counts/clients");
    }

    public Response analyticsStageCounts_CreatedBysUniqueClientId(Map<String, Object> body) {
        return req().body(body).post(EP + "/analytics/tickets/stage-counts/created-bys/unique/client-id");
    }

    public Response analyticsProductsClients(Map<String, Object> body) {
        return req().body(body).post(EP + "/analytics/tickets/stage-counts/products/clients");
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
