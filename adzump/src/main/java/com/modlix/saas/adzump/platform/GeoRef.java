package com.modlix.saas.adzump.platform;

/**
 * A discovered geo-target returned by {@link AdPlatform#searchGeo}.
 *
 * @param id   platform geo id (Meta key, Google geo-target constant).
 * @param name display name.
 * @param type geo kind ("COUNTRY" / "REGION" / "CITY" / "POSTAL_CODE" / ...), platform-neutral.
 */
public record GeoRef(String id, String name, String type) {
}
