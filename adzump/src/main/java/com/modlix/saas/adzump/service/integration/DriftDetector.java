package com.modlix.saas.adzump.service.integration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.modlix.saas.adzump.dto.MilestoneMapping;
import com.modlix.saas.adzump.model.leadzump.Product;
import com.modlix.saas.adzump.model.leadzump.ProductTemplatePipeline;
import com.modlix.saas.adzump.model.leadzump.Stage;
import com.modlix.saas.adzump.model.leadzump.Status;

/**
 * The re-runnable core of J22: computes a stable fingerprint of the EP integration shape and diffs a
 * live {@link IntegrationSnapshot} against the stored per-template {@link MilestoneMapping}s to produce
 * a {@link DriftReport}. Pure logic - no I/O, no Spring collaborators - so it is exercised directly in
 * unit tests.
 *
 * <p>The stored mapping body is the CONTRACT §4 shape ({@code map[].leadzumpStages}, {@code junkStatuses},
 * {@code ignoredStages}) plus an {@code integration} block ({@code fingerprint}, {@code productIds}) that
 * J22 persists in-body (no new table). Fingerprint mismatch on any stored mapping, or any structural
 * add/remove/unmapped-template, marks the report drifted.
 */
@Component
public class DriftDetector {

    public static final String INTEGRATION = "integration";
    public static final String FINGERPRINT = "fingerprint";
    public static final String PRODUCT_IDS = "productIds";

    static final String MAP = "map";
    static final String LEADZUMP_STAGES = "leadzumpStages";
    static final String JUNK_STATUSES = "junkStatuses";
    static final String IGNORED_STAGES = "ignoredStages";

    /**
     * A stable content hash over the product id set and each template's ordered stage keys + status
     * keys. Canonicalized (products sorted, templates sorted by id, stages ordered by {@code order}
     * then key, statuses sorted) so equal shapes always hash equal regardless of read order.
     */
    public String fingerprint(List<Product> products, List<ProductTemplatePipeline> templates) {

        StringBuilder sb = new StringBuilder();

        List<String> productIds = products == null ? List.of()
                : products.stream().map(Product::getId).filter(Objects::nonNull).sorted().toList();
        sb.append("P|").append(String.join(",", productIds)).append('\n');

        List<ProductTemplatePipeline> ordered = templates == null ? List.of()
                : templates.stream()
                        .filter(t -> t != null && t.getTemplateId() != null)
                        .sorted(Comparator.comparing(ProductTemplatePipeline::getTemplateId))
                        .toList();

        for (ProductTemplatePipeline t : ordered) {
            sb.append("T|").append(t.getTemplateId());
            sb.append("|S|").append(String.join(",", orderedStageKeys(t)));
            sb.append("|X|").append(String.join(",", sortedStatusKeys(t)));
            sb.append('\n');
        }

        return sha256Hex(sb.toString());
    }

    /**
     * Diffs the live shape against the stored mappings. For each live template: an absent stored
     * mapping makes the whole template unmapped (all its keys are additions); otherwise, live keys not
     * covered by the stored mapping are additions and covered keys absent from the live pipeline are
     * removals. Products are diffed against the {@code integration.productIds} baseline stored in-body.
     */
    public DriftReport diff(IntegrationSnapshot live, Map<String, MilestoneMapping> storedByTemplate) {

        List<Change> added = new ArrayList<>();
        List<Change> removed = new ArrayList<>();
        List<String> unmapped = new ArrayList<>();

        List<ProductTemplatePipeline> templates = live.templates() == null ? List.of() : live.templates();

        for (ProductTemplatePipeline t : templates) {

            MilestoneMapping stored = storedByTemplate.get(t.getTemplateId());
            JsonNode body = stored == null ? null : stored.getBody();

            if (body == null || !body.isObject()) {
                unmapped.add(t.getTemplateId());
                for (Stage s : safeStages(t))
                    added.add(Change.stage(t.getTemplateId(), s));
                for (Status s : safeStatuses(t))
                    added.add(Change.status(t.getTemplateId(), s));
                continue;
            }

            Set<String> covered = coveredKeys(body);
            Set<String> junk = stringSet(body.get(JUNK_STATUSES));
            Set<String> liveKeys = new HashSet<>();

            for (Stage s : safeStages(t)) {
                liveKeys.add(s.getKey());
                if (!covered.contains(s.getKey()))
                    added.add(Change.stage(t.getTemplateId(), s));
            }
            for (Status s : safeStatuses(t)) {
                liveKeys.add(s.getKey());
                if (!covered.contains(s.getKey()))
                    added.add(Change.status(t.getTemplateId(), s));
            }

            for (String key : covered)
                if (!liveKeys.contains(key))
                    removed.add(Change.removed(junk.contains(key) ? Change.Kind.STATUS : Change.Kind.STAGE,
                            t.getTemplateId(), key));
        }

        diffProducts(live, storedByTemplate.values(), added, removed);

        boolean structural = !(added.isEmpty() && removed.isEmpty() && unmapped.isEmpty());
        boolean fingerprintChanged = fingerprintChanged(live.fingerprint(), storedByTemplate.values());

        return new DriftReport(structural || fingerprintChanged, List.copyOf(added), List.copyOf(removed),
                List.copyOf(unmapped));
    }

    /**
     * Convenience: index account-default mappings by their {@code productTemplateId} (skipping rows
     * with none), the shape {@link #diff} consumes.
     */
    public static Map<String, MilestoneMapping> byTemplate(List<MilestoneMapping> mappings) {

        Map<String, MilestoneMapping> byTemplate = new LinkedHashMap<>();
        if (mappings != null)
            for (MilestoneMapping m : mappings)
                if (m != null && m.getProductTemplateId() != null)
                    byTemplate.put(m.getProductTemplateId(), m);
        return byTemplate;
    }

    // =====================================================================================
    // Internals
    // =====================================================================================

    private void diffProducts(IntegrationSnapshot live, Collection<MilestoneMapping> stored, List<Change> added,
            List<Change> removed) {

        Set<String> baseline = storedProductIds(stored);
        if (baseline == null) // no stored baseline yet -> cannot diff products (added ones surface via fingerprint)
            return;

        Set<String> liveIds = new HashSet<>();
        for (Product p : live.products() == null ? List.<Product>of() : live.products()) {
            if (p.getId() == null)
                continue;
            liveIds.add(p.getId());
            if (!baseline.contains(p.getId()))
                added.add(Change.product(p.getTemplateId(), p.getId(), p.getName()));
        }

        for (String id : baseline)
            if (!liveIds.contains(id))
                removed.add(Change.product(null, id, null));
    }

    /** Union of the CONTRACT §4 mapped stage keys, junk statuses, and explicitly ignored keys. */
    private Set<String> coveredKeys(JsonNode body) {

        Set<String> keys = new HashSet<>();
        JsonNode map = body.get(MAP);
        if (map != null && map.isArray())
            for (JsonNode entry : map)
                keys.addAll(stringSet(entry.get(LEADZUMP_STAGES)));
        keys.addAll(stringSet(body.get(JUNK_STATUSES)));
        keys.addAll(stringSet(body.get(IGNORED_STAGES)));
        return keys;
    }

    private Set<String> storedProductIds(Collection<MilestoneMapping> mappings) {

        for (MilestoneMapping m : mappings) {
            if (m == null || m.getBody() == null)
                continue;
            JsonNode integration = m.getBody().get(INTEGRATION);
            if (integration != null && integration.has(PRODUCT_IDS))
                return stringSet(integration.get(PRODUCT_IDS));
        }
        return null;
    }

    private boolean fingerprintChanged(String liveFingerprint, Collection<MilestoneMapping> mappings) {

        for (MilestoneMapping m : mappings) {
            if (m == null || m.getBody() == null)
                continue;
            JsonNode integration = m.getBody().get(INTEGRATION);
            if (integration != null && integration.hasNonNull(FINGERPRINT)
                    && !integration.get(FINGERPRINT).asText().equals(liveFingerprint))
                return true;
        }
        return false;
    }

    private static List<String> orderedStageKeys(ProductTemplatePipeline t) {
        return safeStages(t).stream()
                .filter(s -> s.getKey() != null)
                .sorted(Comparator.comparingInt(Stage::getOrder).thenComparing(Stage::getKey))
                .map(Stage::getKey)
                .toList();
    }

    private static List<String> sortedStatusKeys(ProductTemplatePipeline t) {
        return safeStatuses(t).stream()
                .filter(s -> s.getKey() != null)
                .map(Status::getKey)
                .sorted()
                .toList();
    }

    private static List<Stage> safeStages(ProductTemplatePipeline t) {
        return t.getStages() == null ? List.of() : t.getStages();
    }

    private static List<Status> safeStatuses(ProductTemplatePipeline t) {
        return t.getStatuses() == null ? List.of() : t.getStatuses();
    }

    private static Set<String> stringSet(JsonNode array) {

        if (array == null || !array.isArray())
            return Set.of();
        Set<String> values = new HashSet<>();
        for (JsonNode n : array)
            if (n != null && !n.isNull())
                values.add(n.asText());
        return values;
    }

    private static String sha256Hex(String value) {

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandated JRE algorithm; unreachable on a standard runtime.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
