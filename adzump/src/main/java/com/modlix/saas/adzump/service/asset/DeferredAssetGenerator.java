package com.modlix.saas.adzump.service.asset;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.modlix.saas.adzump.jooq.enums.AdzumpAssetKind;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.commons2.exception.GenericException;

/**
 * The offline / P1 production {@link AssetGenerator}: generation is not yet wired (the per-kind route —
 * MCP {@code generate_image} vs appbuilder — is an A4 open item proven at the P4.5 gate), so this
 * refuses with a clear message rather than silently returning nothing. It keeps {@link AssetService}
 * bootable and the {@code generate} orchestration + storage path real and testable (a test swaps in a
 * mock generator). Uploading media exercises the same store path today.
 */
@Component
public class DeferredAssetGenerator implements AssetGenerator {

    private final AdzumpMessageResourceService msgService;

    public DeferredAssetGenerator(AdzumpMessageResourceService msgService) {
        this.msgService = msgService;
    }

    @Override
    public GeneratedAsset generate(AdzumpAssetKind kind, String brief, String clientCode) {
        return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.SERVICE_UNAVAILABLE, msg),
                AdzumpMessageResourceService.ASSET_GENERATION_UNAVAILABLE);
    }
}
