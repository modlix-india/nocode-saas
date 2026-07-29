package com.modlix.saas.adzump.platform;

/**
 * A discovered conversion pixel / tracking tag (Meta pixel, Google conversion source).
 *
 * @param id   platform pixel id.
 * @param name pixel name.
 */
public record PixelRef(String id, String name) {
}
