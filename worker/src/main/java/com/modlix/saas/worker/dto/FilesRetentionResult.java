package com.modlix.saas.worker.dto;

import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * One group of files the files service removed under a retention policy: the client and app whose
 * prefix they were under, which bucket, and their paths. The worker raises FILES_EXPIRED for each.
 */
@Data
@Accessors(chain = true)
public class FilesRetentionResult {

    private String clientCode;
    private String appCode;
    private String resourceType;
    private List<String> paths;
}
