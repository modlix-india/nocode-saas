package com.modlix.saas.adzump.model.asset;

/**
 * The registration state of an {@link Asset} on a single ad platform (J16 §5.1). Images register
 * synchronously ({@code READY} the moment the platform returns a hash / resource name); Meta video
 * upload returns immediately with {@code PROCESSING} and only becomes {@code READY} once the platform
 * finishes transcoding, so the launch path (J8) must block on {@code READY} before attaching the video
 * to an ad. {@code FAILED} records a terminal upload/transcode failure so the next
 * {@code ensureRegistered} retries rather than serving a bad cached id.
 */
public enum RegStatus {
    READY,
    PROCESSING,
    FAILED
}
