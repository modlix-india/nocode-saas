package com.modlix.saas.adzump.service.integration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.modlix.saas.adzump.dto.MilestoneMapping;
import com.modlix.saas.adzump.model.leadzump.Product;
import com.modlix.saas.adzump.model.leadzump.ProductTemplatePipeline;
import com.modlix.saas.adzump.model.leadzump.Stage;
import com.modlix.saas.adzump.model.leadzump.Status;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.adzump.service.MilestoneMappingService;
import com.modlix.saas.adzump.service.campaign.CampaignService;
import com.modlix.saas.adzump.service.leadzump.LeadzumpClient;
import com.modlix.saas.adzump.vertical.VerticalRegistry;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.util.StringUtil;

/**
 * J22 - the re-runnable integration wizard. Reads the live entity-processor shape (products +
 * per-template pipelines) through J11, fingerprints it, and diffs it against the stored per-template
 * {@link MilestoneMapping}s so the operator maps leadzump stages/statuses onto vertical milestones
 * (J5), marks junk, and attributes existing campaigns - re-prompting for only what drifted while
 * preserving existing mappings. Blocking / imperative, built on the same collaborators as J8; it owns
 * no persistence of its own (writes go through {@link MilestoneMappingService} and, for campaign
 * attribution, {@link CampaignService#attributeExisting}).
 *
 * <p><b>Body shape.</b> {@code apply} persists the CONTRACT §4 body ({@code map[].leadzumpStages},
 * {@code junkStatuses}, {@code ignoredStages}, {@code verticalMilestones}) plus an {@code integration}
 * block ({@code fingerprint}, {@code productIds}) stored in-body per the P2 no-schema-change rule.
 *
 * <p><b>Tenancy.</b> The effective client is resolved by the delegated services (the
 * {@code targetClientCode} is forwarded to {@link MilestoneMappingService} and {@link CampaignService},
 * which apply the AutonomyConfig-style resolution + managed-client gate). The J11 product/pipeline
 * reads are still scoped to the caller's own client until those EP reads take a client argument
 * (integration-gate, P4.5); a cross-client {@code targetClientCode} therefore only re-scopes the
 * stored-mapping side this slice.
 */
@Service
public class IntegrationWizardService {

    private static final String EDIT = "hasAnyAuthority('Authorities.Campaign_MANAGE','Authorities.ROLE_Owner')";
    private static final String SCHEMA_VERSION = "1.0";
    private static final String VERTICAL_ATTR = "vertical";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LeadzumpClient leadzumpClient;
    private final MilestoneMappingService milestoneMappingService;
    private final CampaignService campaignService;
    private final DriftDetector driftDetector;
    private final VerticalRegistry verticalRegistry;
    private final AdzumpMessageResourceService msgService;

    public IntegrationWizardService(LeadzumpClient leadzumpClient, MilestoneMappingService milestoneMappingService,
            CampaignService campaignService, DriftDetector driftDetector, VerticalRegistry verticalRegistry,
            AdzumpMessageResourceService msgService) {

        this.leadzumpClient = leadzumpClient;
        this.milestoneMappingService = milestoneMappingService;
        this.campaignService = campaignService;
        this.driftDetector = driftDetector;
        this.verticalRegistry = verticalRegistry;
        this.msgService = msgService;
    }

    // =====================================================================================
    // Preview / drift (reads)
    // =====================================================================================

    /**
     * Reads the live EP shape, computes its fingerprint, and diffs it against the stored account-default
     * mappings, returning the snapshot + {@link DriftReport} + current mappings so the wizard UI shows
     * only gaps and changes. No {@code @PreAuthorize}: tenant-scoped through the delegated reads.
     */
    public IntegrationPreview preview(String targetClientCode) {

        IntegrationSnapshot snapshot = this.buildSnapshot();
        List<MilestoneMapping> stored = this.milestoneMappingService.listAccountDefaults(targetClientCode);
        DriftReport drift = this.driftDetector.diff(snapshot, DriftDetector.byTemplate(stored));

        return new IntegrationPreview(snapshot, drift, stored);
    }

    /** The drift report alone, for the app "setup needed" badge (J22 §6). Tenant-scoped, no auth gate. */
    public DriftReport driftCheck(String targetClientCode) {

        IntegrationSnapshot snapshot = this.buildSnapshot();
        List<MilestoneMapping> stored = this.milestoneMappingService.listAccountDefaults(targetClientCode);

        return this.driftDetector.diff(snapshot, DriftDetector.byTemplate(stored));
    }

    // =====================================================================================
    // Apply (EDIT write)
    // =====================================================================================

    /**
     * Persists one template's mapping and attributes its campaigns. Validates that every mapped
     * milestone is in the vertical's funnel (J5) and that every live stage/status is mapped, junked, or
     * ignored (CONTRACT §6.7, no silent drops) before writing. The current live fingerprint + product
     * set are stored in-body so a later EP change surfaces as drift. Idempotent per
     * {@link MilestoneMappingService#applyTemplateMapping}.
     */
    @PreAuthorize(EDIT)
    public MilestoneMapping apply(MappingDraft draft, String targetClientCode) {

        if (draft == null || StringUtil.safeIsBlank(draft.productTemplateId()))
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "productTemplateId");

        Map<String, String> stageToMilestone = draft.stageToMilestone() == null ? Map.of() : draft.stageToMilestone();
        Set<String> junkStatuses = draft.junkStatuses() == null ? Set.of() : draft.junkStatuses();
        Set<String> ignoredStages = draft.ignoredStages() == null ? Set.of() : draft.ignoredStages();

        IntegrationSnapshot snapshot = this.buildSnapshot();
        ProductTemplatePipeline pipeline = this.pipelineFor(snapshot, draft.productTemplateId());
        List<String> verticalMilestones = this.resolveMilestoneKeys(draft.productTemplateId(), snapshot.products());

        this.assertMilestonesKnown(stageToMilestone, verticalMilestones);
        this.assertNoSilentDrop(pipeline, stageToMilestone.keySet(), junkStatuses, ignoredStages);

        JsonNode body = this.buildBody(draft.productTemplateId(), verticalMilestones, stageToMilestone, junkStatuses,
                ignoredStages, snapshot);

        MilestoneMapping saved = this.milestoneMappingService.applyTemplateMapping(draft.productTemplateId(), body,
                targetClientCode);

        // Campaign-product attribution rides through J8's single attribute action (writes the EP link).
        if (draft.attributions() != null)
            for (CampaignAttribution attribution : draft.attributions())
                if (attribution != null)
                    this.campaignService.attributeExisting(attribution.platform(), attribution.externalCampaignId(),
                            attribution.productId(), targetClientCode);

        return saved;
    }

    // =====================================================================================
    // Snapshot + guards + body
    // =====================================================================================

    /** Reads products (J11), then the pipeline of each distinct template, and fingerprints the shape. */
    private IntegrationSnapshot buildSnapshot() {

        List<Product> products = this.leadzumpClient.listProducts();

        List<String> templateIds = products.stream()
                .map(Product::getTemplateId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .sorted()
                .toList();

        List<ProductTemplatePipeline> templates = new ArrayList<>();
        for (String templateId : templateIds)
            templates.add(this.leadzumpClient.getPipeline(templateId));

        String fingerprint = this.driftDetector.fingerprint(products, templates);

        // External campaigns are empty this slice: the J11 campaign-links read is not wired yet (P4.5,
        // the integration gate). The field is populated once IFeignEntityProcessorService exposes it.
        return new IntegrationSnapshot(products, templates, List.of(), fingerprint);
    }

    private ProductTemplatePipeline pipelineFor(IntegrationSnapshot snapshot, String templateId) {

        return snapshot.templates().stream()
                .filter(t -> StringUtil.safeEquals(t.getTemplateId(), templateId))
                .findFirst()
                .orElseGet(() -> this.leadzumpClient.getPipeline(templateId));
    }

    /** The J5 funnel vocabulary for the template's vertical (deduced from the products on it). */
    private List<String> resolveMilestoneKeys(String templateId, List<Product> products) {

        String vertical = products.stream()
                .filter(p -> StringUtil.safeEquals(p.getTemplateId(), templateId))
                .map(p -> p.getAttributes() == null ? null : p.getAttributes().get(VERTICAL_ATTR))
                .filter(Objects::nonNull)
                .map(v -> v.toString().toLowerCase(Locale.ROOT))
                .findFirst()
                .orElse(null);

        return this.verticalRegistry.get(vertical).milestoneKeys();
    }

    private void assertMilestonesKnown(Map<String, String> stageToMilestone, List<String> verticalMilestones) {

        for (String milestone : stageToMilestone.values())
            if (!verticalMilestones.contains(milestone))
                this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        AdzumpMessageResourceService.MILESTONE_UNKNOWN, milestone);
    }

    /**
     * CONTRACT §6.7: every leadzump stage + status in the live pipeline must be mapped, marked junk, or
     * explicitly ignored. Any live key covered by none of the three is a silent drop and rejects the
     * draft (so a status that starts receiving tickets surfaces as drift, never as lost outcomes).
     */
    private void assertNoSilentDrop(ProductTemplatePipeline pipeline, Set<String> mapped, Set<String> junk,
            Set<String> ignored) {

        Set<String> covered = new HashSet<>(mapped);
        covered.addAll(junk);
        covered.addAll(ignored);

        for (String key : sourceKeys(pipeline))
            if (!covered.contains(key))
                this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        AdzumpMessageResourceService.STAGE_UNMAPPED, key);
    }

    private static Set<String> sourceKeys(ProductTemplatePipeline pipeline) {

        Set<String> keys = new HashSet<>();
        if (pipeline.getStages() != null)
            for (Stage s : pipeline.getStages())
                if (s != null && s.getKey() != null)
                    keys.add(s.getKey());
        if (pipeline.getStatuses() != null)
            for (Status s : pipeline.getStatuses())
                if (s != null && s.getKey() != null)
                    keys.add(s.getKey());
        return keys;
    }

    /**
     * Builds the CONTRACT §4 mapping body (inverting {@code stageToMilestone} into
     * {@code map[].leadzumpStages}, ordered by the vertical funnel) plus the in-body {@code integration}
     * fingerprint block. Deterministic (sorted) so re-applying the same draft over the same EP shape
     * yields an identical body.
     */
    private JsonNode buildBody(String templateId, List<String> verticalMilestones, Map<String, String> stageToMilestone,
            Set<String> junkStatuses, Set<String> ignoredStages, IntegrationSnapshot snapshot) {

        ObjectNode body = MAPPER.createObjectNode();
        body.put("schemaVersion", SCHEMA_VERSION);
        body.put("productTemplateId", templateId);

        ArrayNode milestones = body.putArray("verticalMilestones");
        verticalMilestones.forEach(milestones::add);

        // milestone -> [stage keys], iterated in funnel order, then any milestone not in the funnel list.
        Map<String, List<String>> byMilestone = new LinkedHashMap<>();
        for (String milestone : verticalMilestones)
            byMilestone.put(milestone, new ArrayList<>());
        for (Map.Entry<String, String> entry : stageToMilestone.entrySet())
            byMilestone.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());

        ArrayNode map = body.putArray("map");
        for (Map.Entry<String, List<String>> entry : byMilestone.entrySet()) {
            if (entry.getValue().isEmpty())
                continue;
            ObjectNode row = map.addObject();
            row.put("milestone", entry.getKey());
            ArrayNode stages = row.putArray("leadzumpStages");
            entry.getValue().stream().sorted().forEach(stages::add);
        }

        ArrayNode junk = body.putArray("junkStatuses");
        junkStatuses.stream().sorted().forEach(junk::add);

        ArrayNode ignored = body.putArray("ignoredStages");
        ignoredStages.stream().sorted().forEach(ignored::add);

        ObjectNode integration = body.putObject(DriftDetector.INTEGRATION);
        integration.put(DriftDetector.FINGERPRINT, snapshot.fingerprint());
        ArrayNode productIds = integration.putArray(DriftDetector.PRODUCT_IDS);
        snapshot.products().stream()
                .map(Product::getId)
                .filter(Objects::nonNull)
                .sorted()
                .forEach(productIds::add);

        return body;
    }
}
