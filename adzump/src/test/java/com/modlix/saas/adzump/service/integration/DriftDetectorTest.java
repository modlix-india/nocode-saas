package com.modlix.saas.adzump.service.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.modlix.saas.adzump.dto.MilestoneMapping;
import com.modlix.saas.adzump.model.leadzump.Product;
import com.modlix.saas.adzump.model.leadzump.ProductTemplatePipeline;
import com.modlix.saas.adzump.model.leadzump.Stage;
import com.modlix.saas.adzump.model.leadzump.Status;

/**
 * Pure unit tests for the J22 drift core: the fingerprint is a stable, order-independent content hash
 * that changes when the EP shape changes, and the diff surfaces added/removed stages, unmapped
 * templates, and new products against the stored per-template mappings.
 */
class DriftDetectorTest {

    private static final JsonNodeFactory NF = JsonNodeFactory.instance;
    private static final String TEMPLATE = "tmpl-re";

    private final DriftDetector detector = new DriftDetector();

    // =====================================================================================
    // Fingerprint
    // =====================================================================================

    @Test
    void fingerprint_stable_regardlessOfReadOrder() {

        List<Product> products = List.of(product("p2", TEMPLATE), product("p1", TEMPLATE));
        ProductTemplatePipeline a = rerPipeline(); // stages/statuses in canonical order
        ProductTemplatePipeline b = new ProductTemplatePipeline()
                .setTemplateId(TEMPLATE)
                .setStages(List.of(stage("BOOKING", 4), stage("LEAD", 1), stage("SITE_VISIT", 3), stage("QUALIFIED", 2)))
                .setStatuses(List.of(status("JUNK"), status("NEW"), status("NOT_INTERESTED"), status("FOLLOW_UP")));

        String fpA = detector.fingerprint(products, List.of(a));
        String fpB = detector.fingerprint(List.of(product("p1", TEMPLATE), product("p2", TEMPLATE)), List.of(b));

        assertEquals(fpA, fpB, "reordering products/stages/statuses must not change the fingerprint");
    }

    @Test
    void fingerprint_changesWhenStageAdded() {

        List<Product> products = List.of(product("p1", TEMPLATE));
        String before = detector.fingerprint(products, List.of(rerPipeline()));

        ProductTemplatePipeline withExtra = rerPipeline();
        withExtra.getStages().add(stage("NEGOTIATION", 5));
        String after = detector.fingerprint(products, List.of(withExtra));

        assertTrue(!before.equals(after), "adding a stage must change the fingerprint");
    }

    @Test
    void fingerprint_changesWhenProductAdded() {

        String before = detector.fingerprint(List.of(product("p1", TEMPLATE)), List.of(rerPipeline()));
        String after = detector.fingerprint(List.of(product("p1", TEMPLATE), product("p2", TEMPLATE)),
                List.of(rerPipeline()));

        assertTrue(!before.equals(after), "adding a product must change the fingerprint");
    }

    // =====================================================================================
    // Diff
    // =====================================================================================

    @Test
    void diff_unmappedTemplate_whenNoStoredMapping() {

        IntegrationSnapshot live = snapshot(List.of(product("p1", TEMPLATE)), List.of(rerPipeline()));

        DriftReport report = detector.diff(live, Map.of());

        assertTrue(report.drifted());
        assertEquals(List.of(TEMPLATE), report.unmappedTemplates());
        // every stage + status is an addition (nothing mapped yet)
        assertEquals(Set.of("LEAD", "QUALIFIED", "SITE_VISIT", "BOOKING", "NEW", "FOLLOW_UP", "NOT_INTERESTED", "JUNK"),
                keys(report.added()));
    }

    @Test
    void diff_addedAndRemovedStages_againstStoredMapping() {

        // Live gains NEGOTIATION; the stored mapping still covers a since-retired LOST key.
        ProductTemplatePipeline live = rerPipeline();
        live.getStages().add(stage("NEGOTIATION", 5));

        MilestoneMapping stored = mapping(fullyCoveringBody("stale-fingerprint", List.of("p1"), List.of("LOST")));
        IntegrationSnapshot snapshot = snapshot(List.of(product("p1", TEMPLATE)), List.of(live));

        DriftReport report = detector.diff(snapshot, DriftDetector.byTemplate(List.of(stored)));

        assertTrue(report.drifted());
        assertTrue(keys(report.added()).contains("NEGOTIATION"), "new EP stage must be flagged as added");
        assertTrue(keys(report.removed()).contains("LOST"), "stored key gone from EP must be flagged as removed");
        assertTrue(report.unmappedTemplates().isEmpty());
    }

    @Test
    void diff_newProduct_againstStoredProductIdBaseline() {

        MilestoneMapping stored = mapping(fullyCoveringBody("stale-fingerprint", List.of("p1"), List.of()));
        IntegrationSnapshot snapshot = snapshot(
                List.of(product("p1", TEMPLATE), product("p2", TEMPLATE)), List.of(rerPipeline()));

        DriftReport report = detector.diff(snapshot, DriftDetector.byTemplate(List.of(stored)));

        assertTrue(report.drifted());
        assertTrue(report.added().stream()
                .anyMatch(c -> c.kind() == Change.Kind.PRODUCT && "p2".equals(c.key())),
                "the newly-added product must be flagged");
    }

    @Test
    void diff_noDrift_whenShapeMatchesAndFingerprintCurrent() {

        List<Product> products = List.of(product("p1", TEMPLATE));
        List<ProductTemplatePipeline> templates = List.of(rerPipeline());
        String currentFingerprint = detector.fingerprint(products, templates);

        MilestoneMapping stored = mapping(fullyCoveringBody(currentFingerprint, List.of("p1"), List.of()));
        IntegrationSnapshot snapshot = snapshot(products, templates);

        DriftReport report = detector.diff(snapshot, DriftDetector.byTemplate(List.of(stored)));

        assertFalse(report.drifted(), "identical shape + current fingerprint must not drift");
        assertTrue(report.added().isEmpty());
        assertTrue(report.removed().isEmpty());
        assertTrue(report.unmappedTemplates().isEmpty());
    }

    // =====================================================================================
    // Fixtures
    // =====================================================================================

    private static Set<String> keys(List<Change> changes) {
        return changes.stream().map(Change::key).collect(Collectors.toSet());
    }

    private static IntegrationSnapshot snapshot(List<Product> products, List<ProductTemplatePipeline> templates) {
        DriftDetector d = new DriftDetector();
        return new IntegrationSnapshot(products, templates, List.of(), d.fingerprint(products, templates));
    }

    private static Product product(String id, String templateId) {
        return new Product().setId(id).setName(id).setTemplateId(templateId);
    }

    private static Stage stage(String key, int order) {
        return new Stage().setKey(key).setName(key).setOrder(order);
    }

    private static Status status(String key) {
        return new Status().setKey(key).setName(key);
    }

    /** A fresh RE pipeline (mutable stage list, so tests can append). */
    private static ProductTemplatePipeline rerPipeline() {
        return new ProductTemplatePipeline()
                .setTemplateId(TEMPLATE)
                .setStages(new java.util.ArrayList<>(List.of(
                        stage("LEAD", 1), stage("QUALIFIED", 2), stage("SITE_VISIT", 3), stage("BOOKING", 4))))
                .setStatuses(new java.util.ArrayList<>(List.of(
                        status("NEW"), status("FOLLOW_UP"), status("NOT_INTERESTED"), status("JUNK"))));
    }

    private static MilestoneMapping mapping(ObjectNode body) {
        return new MilestoneMapping().setProductTemplateId(TEMPLATE).setBody(body);
    }

    /** CONTRACT §4 body covering every RE stage/status, plus optional extra ignored keys + the in-body fingerprint. */
    private static ObjectNode fullyCoveringBody(String fingerprint, List<String> productIds, List<String> extraIgnored) {

        ObjectNode body = NF.objectNode();
        ArrayNode map = body.putArray("map");
        mapEntry(map, "lead", "LEAD", "NEW");
        mapEntry(map, "qualified", "QUALIFIED", "FOLLOW_UP");
        mapEntry(map, "site_visit", "SITE_VISIT");
        mapEntry(map, "booking", "BOOKING");

        ArrayNode junk = body.putArray("junkStatuses");
        junk.add("NOT_INTERESTED");
        junk.add("JUNK");

        ArrayNode ignored = body.putArray("ignoredStages");
        extraIgnored.forEach(ignored::add);

        ObjectNode integration = body.putObject("integration");
        integration.put("fingerprint", fingerprint);
        ArrayNode ids = integration.putArray("productIds");
        productIds.forEach(ids::add);

        return body;
    }

    private static void mapEntry(ArrayNode map, String milestone, String... stages) {
        ObjectNode row = map.addObject();
        row.put("milestone", milestone);
        ArrayNode ls = row.putArray("leadzumpStages");
        for (String s : stages)
            ls.add(s);
    }
}
