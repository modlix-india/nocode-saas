package com.modlix.saas.adzump.model.asset;

import java.time.LocalDateTime;

/**
 * The cached result of registering an {@link Asset} on one platform (J16 §5.1): the platform-real id
 * (a Meta image {@code hash} / {@code video_id}, or a Google asset resource name), its
 * {@link RegStatus}, and when it was last written. The same source file maps to a <b>different id per
 * platform</b>, so {@link Asset#getPlatformIds()} holds one of these per platform and J16 never
 * re-uploads an asset that is already {@code READY}.
 *
 * <p>A record (immutable value): a fresh instance is produced on every state change and swapped into
 * the asset's {@code platformIds} map, which keeps the cache-hit check ({@code status == READY}) and
 * the "did anything change?" comparison trivial value-equality.
 */
public record PlatformAssetId(String id, RegStatus status, LocalDateTime registeredAt) {

    public static PlatformAssetId ready(String id) {
        return new PlatformAssetId(id, RegStatus.READY, LocalDateTime.now());
    }

    public static PlatformAssetId processing(String id) {
        return new PlatformAssetId(id, RegStatus.PROCESSING, LocalDateTime.now());
    }

    public static PlatformAssetId failed(String id) {
        return new PlatformAssetId(id, RegStatus.FAILED, LocalDateTime.now());
    }

    public boolean isReady() {
        return this.status == RegStatus.READY;
    }

    public boolean isProcessing() {
        return this.status == RegStatus.PROCESSING;
    }
}
