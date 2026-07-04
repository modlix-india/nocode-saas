package com.modlix.saas.adzump.model.asset;

/**
 * A stored Modlix file reference returned by {@code AdzumpFilesClient} after an upload/generate: the
 * {@code fileKey} (the files-service path the bytes live at, persisted on {@link Asset#getFileKey()})
 * and the resolvable {@code url}. Kept platform-neutral so no files-service model type leaks into the
 * asset layer.
 */
public record StoredFile(String fileKey, String url) {
}
