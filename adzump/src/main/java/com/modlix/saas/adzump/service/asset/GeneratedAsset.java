package com.modlix.saas.adzump.service.asset;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The raw output of an {@link AssetGenerator}: the produced media bytes plus enough metadata for
 * {@link AssetService} to store them like any uploaded asset (J16 §5.3 — generation is orchestration;
 * the result becomes a normal {@code Asset} so it is classified, attributed and registered
 * identically). {@code attributes} carries any generator-side classification to seed the asset row.
 */
public record GeneratedAsset(byte[] content, String contentType, String fileName, JsonNode attributes) {
}
