package com.modlix.saas.files.model.retention;

import java.io.Serializable;
import java.util.List;

/**
 * A dry run: what a retention of {@code days} under {@code <appCode>/<path>} would remove now for
 * one client. {@code days} null (keep forever, or a path no registered prefix covers) removes
 * nothing. {@code samplePaths} holds up to 20 of the oldest files, full paths within the client.
 */
public record RetentionPreview(
        String appCode,
        String clientCode,
        String resourceType,
        String path,
        Integer days,
        String source,
        long count,
        long totalBytes,
        List<String> samplePaths) implements Serializable {
}
