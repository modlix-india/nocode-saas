package com.modlix.saas.adzump.service.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.modlix.saas.adzump.dto.MilestoneMapping;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.jooq.enums.AdzumpMilestoneMappingScope;
import com.modlix.saas.adzump.model.leadzump.Product;
import com.modlix.saas.adzump.model.leadzump.ProductTemplatePipeline;
import com.modlix.saas.adzump.model.leadzump.Stage;
import com.modlix.saas.adzump.model.leadzump.Status;
import com.modlix.saas.adzump.dao.MilestoneMappingDao;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.adzump.service.CampaignPlanService;
import com.modlix.saas.adzump.service.MilestoneMappingService;
import com.modlix.saas.adzump.service.campaign.CampaignService;
import com.modlix.saas.adzump.service.leadzump.LeadzumpClient;
import com.modlix.saas.adzump.vertical.VerticalPlaybook;
import com.modlix.saas.adzump.vertical.VerticalRegistry;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.service.FeignAuthenticationService;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;

/**
 * J22 wizard tests, offline: J11 ({@link LeadzumpClient}) and the platform/attribution collaborators
 * are mocked, while a <b>real</b> {@link MilestoneMappingService} (over a mocked DAO) and a real
 * {@link DriftDetector} run so idempotent-merge, the fingerprint-in-body round-trip, and the
 * no-silent-drop guard exercise production logic rather than a double.
 *
 * <p>Covers the J22 P2 exit: preview detects added-stage / removed-stage / new-product drift; apply
 * refuses an uncovered stage (no silent drop) and an unknown milestone; apply persists idempotently
 * (unchanged keys preserved) with the current fingerprint round-tripped into the mapping body; and
 * campaign attributions route through J8.
 */
class IntegrationWizardServiceTest {

    private static final AdzumpMessageResourceService MSG = new AdzumpMessageResourceService();
    private static final JsonNodeFactory NF = JsonNodeFactory.instance;
    private static final String CLIENT = "CLI0";
    private static final String TEMPLATE = "tmpl-re";
    private static final List<String> RE_MILESTONES = List.of("lead", "qualified", "site_visit", "booking");

    private LeadzumpClient leadzump;
    private MilestoneMappingDao dao;
    private CampaignService campaignService;
    private DriftDetector driftDetector;
    private VerticalRegistry verticalRegistry;
    private MilestoneMappingService milestoneMappingService;
    private IntegrationWizardService wizard;

    private ContextAuthentication ca;
    private MockedStatic<SecurityContextUtil> securityCtx;

    @BeforeEach
    void setUp() {

        this.leadzump = mock(LeadzumpClient.class);
        this.dao = mock(MilestoneMappingDao.class);
        this.campaignService = mock(CampaignService.class);
        this.driftDetector = new DriftDetector();

        CampaignPlanService planService = mock(CampaignPlanService.class);
        FeignAuthenticationService security = mock(FeignAuthenticationService.class);
        this.milestoneMappingService = new MilestoneMappingService(this.dao, planService, security, MSG);

        VerticalPlaybook playbook = mock(VerticalPlaybook.class);
        when(playbook.milestoneKeys()).thenReturn(RE_MILESTONES);
        this.verticalRegistry = mock(VerticalRegistry.class);
        when(this.verticalRegistry.get(any())).thenReturn(playbook);

        this.wizard = new IntegrationWizardService(this.leadzump, this.milestoneMappingService, this.campaignService,
                this.driftDetector, this.verticalRegistry, MSG);

        this.ca = mock(ContextAuthentication.class);
        when(this.ca.getLoggedInFromClientCode()).thenReturn(CLIENT);
        this.securityCtx = Mockito.mockStatic(SecurityContextUtil.class);
        this.securityCtx.when(SecurityContextUtil::getUsersContextAuthentication).thenReturn(this.ca);
    }

    @AfterEach
    void tearDown() {
        this.securityCtx.close();
    }

    // =====================================================================================
    // apply — guards
    // =====================================================================================

    @Test
    void apply_noSilentDrop_rejectsUncoveredStatus() {

        stubEpShape(rePipeline());

        // NEW and FOLLOW_UP are neither mapped, junk, nor ignored -> must reject.
        MappingDraft draft = new MappingDraft(TEMPLATE,
                Map.of("LEAD", "lead", "QUALIFIED", "qualified", "SITE_VISIT", "site_visit", "BOOKING", "booking"),
                Set.of("JUNK", "NOT_INTERESTED"), Set.of(), List.of());

        assertThrows(GenericException.class, () -> wizard.apply(draft, null));

        verify(this.dao, never()).create(any());
        verify(this.dao, never()).update(any(MilestoneMapping.class));
        verify(this.campaignService, never()).attributeExisting(any(), anyString(), anyString(), any());
    }

    @Test
    void apply_unknownMilestone_rejected() {

        stubEpShape(rePipeline());

        MappingDraft draft = fullDraft().withMapping("LEAD", "not_a_milestone").draft();

        assertThrows(GenericException.class, () -> wizard.apply(draft, null));

        verify(this.dao, never()).create(any());
        verify(this.dao, never()).update(any(MilestoneMapping.class));
    }

    // =====================================================================================
    // apply — persistence
    // =====================================================================================

    @Test
    void apply_firstTime_roundTripsFingerprintAndProductIdsInBody() {

        stubEpShape(rePipeline());
        when(this.dao.findByTemplate(eq(CLIENT), eq(TEMPLATE), eq(AdzumpMilestoneMappingScope.ACCOUNT_DEFAULT),
                isNull())).thenReturn(null);
        when(this.dao.create(any())).thenAnswer(inv -> inv.getArgument(0));

        String expectedFingerprint = this.driftDetector.fingerprint(List.of(product("p1")), List.of(rePipeline()));

        wizard.apply(fullDraft().draft(), null);

        ArgumentCaptor<MilestoneMapping> captor = ArgumentCaptor.forClass(MilestoneMapping.class);
        verify(this.dao).create(captor.capture());
        MilestoneMapping created = captor.getValue();

        assertEquals(CLIENT, created.getClientCode());
        assertEquals(AdzumpMilestoneMappingScope.ACCOUNT_DEFAULT, created.getScope());
        assertEquals(TEMPLATE, created.getProductTemplateId());

        JsonNode body = created.getBody();
        assertEquals(expectedFingerprint, body.at("/integration/fingerprint").asText(),
                "the live fingerprint must round-trip into the milestone_mapping body");
        assertTrue(textValues(body.at("/integration/productIds")).contains("p1"));
        // CONTRACT §4 shape: stageToMilestone inverted into map[].leadzumpStages
        assertTrue(mappedStagesFor(body, "lead").containsAll(Set.of("LEAD", "NEW")));
    }

    @Test
    void apply_idempotent_preservesUnrelatedKeysAndReappliesCleanly() {

        stubEpShape(rePipeline());
        String liveFingerprint = this.driftDetector.fingerprint(List.of(product("p1")), List.of(rePipeline()));

        // A prior mapping already confirmed against the same shape, carrying an unrelated custom key.
        ObjectNode existingBody = fullBody(liveFingerprint);
        existingBody.put("customNote", "keep-me");
        MilestoneMapping existing = new MilestoneMapping()
                .setClientCode(CLIENT)
                .setScope(AdzumpMilestoneMappingScope.ACCOUNT_DEFAULT)
                .setProductTemplateId(TEMPLATE)
                .setBody(existingBody);
        existing.setId(org.jooq.types.ULong.valueOf(5));

        when(this.dao.findByTemplate(eq(CLIENT), eq(TEMPLATE), eq(AdzumpMilestoneMappingScope.ACCOUNT_DEFAULT),
                isNull())).thenReturn(existing);
        when(this.dao.update(any(MilestoneMapping.class))).thenAnswer(inv -> inv.getArgument(0));

        wizard.apply(fullDraft().draft(), null);

        ArgumentCaptor<MilestoneMapping> captor = ArgumentCaptor.forClass(MilestoneMapping.class);
        verify(this.dao).update(captor.capture());
        JsonNode merged = captor.getValue().getBody();

        // Unrelated key survives the merge (idempotent re-apply does not clobber it)...
        assertEquals("keep-me", merged.at("/customNote").asText());
        // ...and the mapping content + fingerprint are unchanged (no drift introduced).
        assertEquals(liveFingerprint, merged.at("/integration/fingerprint").asText());
        assertTrue(mappedStagesFor(merged, "qualified").containsAll(Set.of("QUALIFIED", "FOLLOW_UP")));
        verify(this.dao, never()).create(any());
    }

    @Test
    void apply_attributesCampaigns_viaCampaignService() {

        stubEpShape(rePipeline());
        when(this.dao.findByTemplate(any(), any(), any(), any())).thenReturn(null);
        when(this.dao.create(any())).thenAnswer(inv -> inv.getArgument(0));

        CampaignAttribution attribution = new CampaignAttribution(Platform.META, "ext-123", "p1");
        MappingDraft draft = fullDraft().withAttribution(attribution).draft();

        wizard.apply(draft, null);

        verify(this.campaignService).attributeExisting(eq(Platform.META), eq("ext-123"), eq("p1"), isNull());
    }

    // =====================================================================================
    // preview — drift end-to-end
    // =====================================================================================

    @Test
    void preview_detectsAddedStage_removedStage_andNewProduct() {

        // Live: gains a NEGOTIATION stage and a second product p2.
        ProductTemplatePipeline live = rePipeline();
        live.getStages().add(new Stage().setKey("NEGOTIATION").setName("Negotiation").setOrder(5));
        when(this.leadzump.listProducts()).thenReturn(List.of(product("p1"), product("p2")));
        when(this.leadzump.getPipeline(TEMPLATE)).thenReturn(live);

        // Stored: confirmed against p1 only, still covers a since-retired LOST key, stale fingerprint.
        ObjectNode storedBody = fullBody("stale-fingerprint");
        ((ArrayNode) storedBody.get("ignoredStages")).add("LOST");
        MilestoneMapping stored = new MilestoneMapping().setProductTemplateId(TEMPLATE).setBody(storedBody);
        when(this.dao.readAll(any())).thenReturn(List.of(stored));

        IntegrationPreview preview = wizard.preview(null);
        DriftReport drift = preview.drift();

        assertTrue(drift.drifted());
        assertTrue(changeKeys(drift.added()).contains("NEGOTIATION"), "new EP stage surfaces as added");
        assertTrue(drift.added().stream().anyMatch(c -> c.kind() == Change.Kind.PRODUCT && "p2".equals(c.key())),
                "new product surfaces as added");
        assertTrue(changeKeys(drift.removed()).contains("LOST"), "retired stored key surfaces as removed");
        assertEquals(1, preview.currentMappings().size());
        assertTrue(preview.snapshot().fingerprint() != null && !preview.snapshot().fingerprint().isBlank());
    }

    // =====================================================================================
    // Fixtures / helpers
    // =====================================================================================

    private void stubEpShape(ProductTemplatePipeline pipeline) {
        when(this.leadzump.listProducts()).thenReturn(List.of(product("p1")));
        when(this.leadzump.getPipeline(TEMPLATE)).thenReturn(pipeline);
    }

    private static Product product(String id) {
        return new Product().setId(id).setName(id).setTemplateId(TEMPLATE)
                .setAttributes(Map.of("vertical", "REAL_ESTATE"));
    }

    /** A fresh RE pipeline with a mutable stage list. */
    private static ProductTemplatePipeline rePipeline() {
        return new ProductTemplatePipeline()
                .setTemplateId(TEMPLATE)
                .setStages(new ArrayList<>(List.of(
                        new Stage().setKey("LEAD").setName("Lead").setOrder(1),
                        new Stage().setKey("QUALIFIED").setName("Qualified").setOrder(2),
                        new Stage().setKey("SITE_VISIT").setName("Site Visit").setOrder(3),
                        new Stage().setKey("BOOKING").setName("Booking").setOrder(4))))
                .setStatuses(new ArrayList<>(List.of(
                        new Status().setKey("NEW").setName("New"),
                        new Status().setKey("FOLLOW_UP").setName("Follow up"),
                        new Status().setKey("NOT_INTERESTED").setName("Not interested"),
                        new Status().setKey("JUNK").setName("Junk"))));
    }

    /** A draft that covers every RE stage/status (guard passes). Fluent so tests can perturb it. */
    private static DraftBuilder fullDraft() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("LEAD", "lead");
        map.put("NEW", "lead");
        map.put("QUALIFIED", "qualified");
        map.put("FOLLOW_UP", "qualified");
        map.put("SITE_VISIT", "site_visit");
        map.put("BOOKING", "booking");
        return new DraftBuilder(map, Set.of("NOT_INTERESTED", "JUNK"), Set.of(), new ArrayList<>());
    }

    /** The persisted CONTRACT §4 body a fullDraft produces, with a chosen in-body fingerprint. */
    private static ObjectNode fullBody(String fingerprint) {
        ObjectNode body = NF.objectNode();
        body.put("schemaVersion", "1.0");
        body.put("productTemplateId", TEMPLATE);
        ArrayNode milestones = body.putArray("verticalMilestones");
        RE_MILESTONES.forEach(milestones::add);

        ArrayNode map = body.putArray("map");
        mapEntry(map, "lead", "LEAD", "NEW");
        mapEntry(map, "qualified", "FOLLOW_UP", "QUALIFIED");
        mapEntry(map, "site_visit", "SITE_VISIT");
        mapEntry(map, "booking", "BOOKING");

        body.putArray("junkStatuses").add("JUNK").add("NOT_INTERESTED");
        body.putArray("ignoredStages");

        ObjectNode integration = body.putObject("integration");
        integration.put("fingerprint", fingerprint);
        integration.putArray("productIds").add("p1");
        return body;
    }

    private static void mapEntry(ArrayNode map, String milestone, String... stages) {
        ObjectNode row = map.addObject();
        row.put("milestone", milestone);
        ArrayNode ls = row.putArray("leadzumpStages");
        for (String s : stages)
            ls.add(s);
    }

    private static Set<String> changeKeys(List<Change> changes) {
        return changes.stream().map(Change::key).collect(Collectors.toSet());
    }

    private static Set<String> textValues(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).collect(Collectors.toSet());
    }

    /** The leadzump stage keys mapped to a milestone in the persisted body. */
    private static Set<String> mappedStagesFor(JsonNode body, String milestone) {
        for (JsonNode row : body.get("map"))
            if (milestone.equals(row.get("milestone").asText()))
                return textValues(row.get("leadzumpStages"));
        return Set.of();
    }

    /** Small mutable builder so each test tweaks one facet of an otherwise-valid draft. */
    private static final class DraftBuilder {
        private final Map<String, String> stageToMilestone;
        private final Set<String> junk;
        private final Set<String> ignored;
        private final List<CampaignAttribution> attributions;

        private DraftBuilder(Map<String, String> stageToMilestone, Set<String> junk, Set<String> ignored,
                List<CampaignAttribution> attributions) {
            this.stageToMilestone = new LinkedHashMap<>(stageToMilestone);
            this.junk = junk;
            this.ignored = ignored;
            this.attributions = attributions;
        }

        DraftBuilder withMapping(String stageKey, String milestone) {
            this.stageToMilestone.put(stageKey, milestone);
            return this;
        }

        DraftBuilder withAttribution(CampaignAttribution attribution) {
            this.attributions.add(attribution);
            return this;
        }

        MappingDraft draft() {
            return new MappingDraft(TEMPLATE, this.stageToMilestone, this.junk, this.ignored, this.attributions);
        }
    }
}
