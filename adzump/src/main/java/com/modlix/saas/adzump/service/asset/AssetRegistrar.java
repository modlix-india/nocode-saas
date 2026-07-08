package com.modlix.saas.adzump.service.asset;

import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.model.asset.Asset;
import com.modlix.saas.adzump.model.asset.PlatformAssetId;
import com.modlix.saas.adzump.platform.Token;

/**
 * J16 §5.2 — the per-platform seam that turns a stored Modlix file into a platform-real ad-asset id.
 * Each platform (Meta, Google) contributes one bean; {@link AssetService} resolves the registrar by
 * {@link #platform()} and drives the lazy/idempotent/async-aware {@code ensureRegistered} loop on top
 * of it. Kept off the pure J7 compiler (compilation stays I/O-free) — registration is the one asset
 * step that talks to a platform, so it lives here and takes the J2 {@link Token}.
 */
public interface AssetRegistrar {

    /** The platform this registrar handles; the {@link AssetService} indexes beans by this. */
    Platform platform();

    /**
     * Uploads {@code asset}'s media to the platform's asset library and returns the id + status. Images
     * (and logos) come back {@code READY} with the hash / resource name; a Meta video comes back
     * {@code PROCESSING} with its {@code video_id} (transcode is async) — the caller must poll
     * {@link #checkStatus} until {@code READY} before attaching it to an ad.
     */
    PlatformAssetId register(Asset asset, Token token);

    /**
     * Re-checks a still-{@code PROCESSING} registration without re-uploading (the async video gotcha),
     * returning the (possibly now {@code READY} or {@code FAILED}) status. Synchronous kinds have
     * nothing to poll, so the default returns {@code current} unchanged.
     */
    default PlatformAssetId checkStatus(Asset asset, PlatformAssetId current, Token token) {
        return current;
    }
}
