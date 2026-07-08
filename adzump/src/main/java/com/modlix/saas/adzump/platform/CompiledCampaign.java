package com.modlix.saas.adzump.platform;

import com.fasterxml.jackson.databind.JsonNode;

import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.model.Objective;

/**
 * The type-tagged payload tree J7 produces from the IR (pure, no I/O). {@code launchPaused}
 * dispatches on {@link #type()} — a PMax asset-group tree differs from a Search ad-group/keyword
 * tree — so the type is a first-class header field, not buried in the payload.
 *
 * <p>P1 keeps the compiled tree as a structured {@link JsonNode} the platform TypeCompilers build;
 * J3/J4 read {@link #payload()} and map it onto real platform create-calls. The small typed header
 * ({@code name}, {@code objective}) is surfaced so callers can log/label a launch without parsing
 * the payload.
 *
 * @param platform  the platform this payload targets.
 * @param type      the campaign type (drives launch dispatch).
 * @param name      the campaign name (header).
 * @param objective the campaign objective (header).
 * @param payload   the compiled, platform-neutral payload tree (J3/J4 read this).
 */
public record CompiledCampaign(
        Platform platform,
        CampaignType type,
        String name,
        Objective objective,
        JsonNode payload) {
}
