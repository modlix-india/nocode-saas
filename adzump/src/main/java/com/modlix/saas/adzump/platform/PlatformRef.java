package com.modlix.saas.adzump.platform;

/**
 * A platform-real handle to an entity that already exists on the ad account. {@code type} is the
 * entity kind ("campaign" / "adSet" / "adGroup" / "ad" / "assetGroup"), {@code id} the
 * platform-assigned id. Mutations (setStatus/updateBudget/...) take a ref; the SPI never owns
 * persistence, so the caller (J8) writes ids back onto {@code plan.links}.
 *
 * @param type entity kind.
 * @param id   platform-assigned id.
 */
public record PlatformRef(String type, String id) {
}
