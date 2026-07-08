package com.modlix.saas.adzump.platform;

import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.model.Links;

/**
 * The outcome of {@link AdPlatform#launchPaused} for one platform. On success it carries the
 * platform ids as a {@link Links} fragment (only the matching platform's sub-object populated) that
 * J8 merges onto {@code plan.body.links}; the SPI does not own persistence. On failure {@code ok}
 * is false and {@code error} is set — J8 uses this for partial-failure handling when a plan fans out
 * to Meta + Google and one leg fails.
 *
 * @param platform the platform this result is for.
 * @param ok        whether the launch succeeded.
 * @param links     the platform-id fragment to merge onto the plan (null on failure).
 * @param error     the failure reason (null on success).
 */
public record LaunchResult(Platform platform, boolean ok, Links links, String error) {

    /** Success carrying the platform-id fragment to write onto {@code plan.links}. */
    public static LaunchResult ok(Platform platform, Links links) {
        return new LaunchResult(platform, true, links, null);
    }

    /** Failure with a reason; no ids to write back. Used by J8 partial-failure handling. */
    public static LaunchResult failed(Platform platform, String error) {
        return new LaunchResult(platform, false, null, error);
    }
}
