package com.modlix.saas.adzump.platform;

import com.fasterxml.jackson.databind.JsonNode;

import com.modlix.saas.adzump.enums.CreativeFormat;

/**
 * A single compiled creative for {@link AdPlatform#upsertCreative} — the format plus the
 * platform-neutral payload tree J7 produced for it (RSA headlines/descriptions, image/video asset
 * refs, carousel cards, lead-form ref). P1 keeps the payload a structured {@link JsonNode}; J3/J4
 * read it and map onto each platform's creative/ad objects.
 *
 * @param format  the creative format.
 * @param payload the compiled creative tree (J3/J4 read this).
 */
public record CompiledCreative(CreativeFormat format, JsonNode payload) {
}
