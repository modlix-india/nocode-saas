package com.modlix.saas.adzump.service.asset;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.google.ads.googleads.v24.common.ImageAsset;
import com.google.ads.googleads.v24.enums.AssetTypeEnum.AssetType;
import com.google.ads.googleads.v24.resources.Asset;
import com.google.ads.googleads.v24.services.AssetOperation;
import com.google.ads.googleads.v24.services.MutateGoogleAdsResponse;
import com.google.ads.googleads.v24.services.MutateOperation;
import com.google.ads.googleads.v24.services.MutateOperationResponse;
import com.google.protobuf.ByteString;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.jooq.enums.AdzumpAssetKind;
import com.modlix.saas.adzump.model.asset.PlatformAssetId;
import com.modlix.saas.adzump.platform.Token;
import com.modlix.saas.adzump.platform.google.GoogleAdsClientFacade;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.commons2.exception.GenericException;

/**
 * J16 §5.2 — the Google registrar. Google wants creative media as an <b>asset resource</b> under the
 * customer; a creative then references that resource name. This registrar builds a single
 * {@code AssetService.mutate} create-operation for an {@code ImageAsset} carrying the bytes and returns
 * the resource name Google mints — synchronously, so the result is {@code READY} at once (there is no
 * transcode wait as with Meta video).
 *
 * <p>Only image kinds are supported here (IMAGE and LOGO are both {@code ImageAsset}s). A Google video
 * asset is a {@code YouTubeVideoAsset} keyed by a YouTube id — not a byte upload — so VIDEO is refused
 * as unsupported on this platform (out of scope this slice).
 *
 * <p>The bytes come from the Modlix files service via {@link AdzumpFilesClient}; the wire is
 * {@link GoogleAdsClientFacade} (the same facade the J4 lifecycle uses). Both are <b>mocked in every
 * test</b> — no gRPC, no live account. The MCC / access-token validation is enforced inside the facade
 * when it builds the client.
 */
@Component
public class GoogleAssetRegistrar implements AssetRegistrar {

    private final GoogleAdsClientFacade facade;
    private final AdzumpFilesClient files;
    private final AdzumpMessageResourceService msgService;

    public GoogleAssetRegistrar(GoogleAdsClientFacade facade, AdzumpFilesClient files,
            AdzumpMessageResourceService msgService) {
        this.facade = facade;
        this.files = files;
        this.msgService = msgService;
    }

    @Override
    public Platform platform() {
        return Platform.GOOGLE;
    }

    @Override
    public PlatformAssetId register(com.modlix.saas.adzump.model.asset.Asset asset, Token token) {

        if (asset.getKind() == AdzumpAssetKind.VIDEO)
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.UNPROCESSABLE_ENTITY, msg),
                    AdzumpMessageResourceService.UNSUPPORTED_ASSET_KIND, asset.getKind(), Platform.GOOGLE);

        String customerId = requireCustomerId(token);

        byte[] bytes = this.files.fetch(asset.getClientCode(), asset.getFileKey());

        Asset imageAsset = Asset.newBuilder()
                .setName(assetName(asset))
                .setType(AssetType.IMAGE)
                .setImageAsset(ImageAsset.newBuilder().setData(ByteString.copyFrom(bytes)))
                .build();

        MutateOperation op = MutateOperation.newBuilder()
                .setAssetOperation(AssetOperation.newBuilder().setCreate(imageAsset).build())
                .build();

        MutateGoogleAdsResponse response = this.facade.mutate(token, customerId, List.of(op));

        String resourceName = extractAssetResourceName(response);
        return PlatformAssetId.ready(resourceName);
    }

    // --- helpers --------------------------------------------------------------------------------

    private String extractAssetResourceName(MutateGoogleAdsResponse response) {
        if (response != null)
            for (MutateOperationResponse r : response.getMutateOperationResponsesList())
                if (r.hasAssetResult() && !r.getAssetResult().getResourceName().isBlank())
                    return r.getAssetResult().getResourceName();
        return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_GATEWAY, msg),
                AdzumpMessageResourceService.GOOGLE_API_ERROR, "mutate response carried no asset result");
    }

    /** A stable, human-readable asset name; Google requires image-asset names to be unique per account. */
    private static String assetName(com.modlix.saas.adzump.model.asset.Asset asset) {
        return "adzump-" + (asset.getKind() == null ? "image" : asset.getKind().getLiteral().toLowerCase())
                + "-" + asset.getId();
    }

    /** The operating customer-id (digits only) the mutate targets; from the J2 token's accountId. */
    private String requireCustomerId(Token token) {
        String digits = token == null || token.accountId() == null ? "" : token.accountId().replaceAll("[^0-9]", "");
        if (digits.isEmpty())
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "Google customer-id (account)");
        return digits;
    }
}
