package com.modlix.saas.adzump.platform;

/**
 * A discovered audience / interest returned by {@link AdPlatform#searchInterests} (Meta detailed
 * targeting, Google audience segments).
 *
 * @param id   platform audience/interest id.
 * @param name display name.
 * @param type audience kind ("INTEREST" / "CUSTOM" / "IN_MARKET" / "AFFINITY" / ...), platform-neutral.
 */
public record AudienceRef(String id, String name, String type) {
}
