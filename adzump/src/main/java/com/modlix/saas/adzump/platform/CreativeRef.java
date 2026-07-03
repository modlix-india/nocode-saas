package com.modlix.saas.adzump.platform;

/**
 * A platform-real handle to a creative/ad returned by {@link AdPlatform#upsertCreative}.
 *
 * @param id platform-assigned creative/ad id.
 */
public record CreativeRef(String id) {
}
