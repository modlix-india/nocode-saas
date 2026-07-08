package com.modlix.saas.adzump.platform;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A partial targeting mutation for {@link AdPlatform#mutateTargeting} (add/remove audiences,
 * placements, geo, demographics on an existing ad-set/ad-group). P1 keeps the delta as a neutral
 * {@link JsonNode} the caller builds and each impl interprets; J3/J4 will introduce a typed
 * add/remove field set (audience ids, placement enums, geo ids, negative-keyword lists) once the
 * real mutation surface is known.
 *
 * @param patch the neutral targeting delta.
 */
public record TargetingPatch(JsonNode patch) {
}
