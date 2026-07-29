package com.modlix.saas.adzump.service.asset;

import com.modlix.saas.adzump.jooq.enums.AdzumpAssetKind;

/**
 * The seam for image/creative <b>generation</b> (J16 §5.3). {@link AssetService#generate} owns the
 * orchestration + storage; the actual generation backend (MCP {@code generate_image} vs appbuilder
 * delegation — an A4 open item, per kind) lives behind this interface so it can be swapped without
 * touching the store-as-a-normal-asset path. Offline this slice the production bean is a deferred stub;
 * tests inject a mock that returns canned bytes.
 */
public interface AssetGenerator {

    /**
     * Produces the media for {@code brief} of the given {@code kind}, on behalf of {@code clientCode}.
     * Implementations do no persistence; {@link AssetService} stores the returned bytes via the files
     * service and creates the asset row.
     */
    GeneratedAsset generate(AdzumpAssetKind kind, String brief, String clientCode);
}
