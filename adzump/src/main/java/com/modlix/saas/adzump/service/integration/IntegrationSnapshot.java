package com.modlix.saas.adzump.service.integration;

import java.util.List;

import com.modlix.saas.adzump.model.leadzump.Product;
import com.modlix.saas.adzump.model.leadzump.ProductTemplatePipeline;

/**
 * What entity-processor currently exposes at read time (J22 §5.1): the products, their product
 * templates (each with ordered stages + statuses, reusing {@link ProductTemplatePipeline}), the
 * external campaigns, and a stable {@code fingerprint} of the whole shape used for drift detection.
 *
 * <p>The {@code fingerprint} is computed by {@link DriftDetector#fingerprint} over the product set and
 * each template's ordered stages + statuses; a change anywhere flips it.
 *
 * @param products    the leadzump products visible to the caller's client
 * @param templates   the distinct product-template pipelines (stages + statuses)
 * @param campaigns   external platform campaigns EP knows about (empty until the J11 links read lands)
 * @param fingerprint stable content hash of the product set + template shapes
 */
public record IntegrationSnapshot(List<Product> products, List<ProductTemplatePipeline> templates,
        List<ExternalCampaign> campaigns, String fingerprint) {
}
