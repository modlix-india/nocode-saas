package com.modlix.saas.worker.feign;

import java.util.List;

import com.modlix.saas.worker.dto.FilesRetentionResult;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "files", contextId = "workerFilesService")
public interface IFeignFilesService {

    @PostMapping("/api/files/internal/billing/meter")
    Boolean triggerBillingMetering();

    @PostMapping("/api/files/internal/billing/reconcile")
    Boolean reconcileBilling();

    /**
     * Asks the files service to remove whatever has outlived its declared lifetime.
     *
     * <p>A cleanup rather than a delete, and the distinction is the whole design: the worker never
     * decides which files go. That was settled when each file was created, by whoever knew it was
     * temporary. A file created without a lifetime cannot be removed by this call at any age.
     */
    @PostMapping("/api/files/internal/{resourceType}/cleanupExpired")
    Integer cleanupExpired(@PathVariable("resourceType") String resourceType, @RequestParam("limit") int limit);

    /**
     * Asks the files service to remove what has outlived the retention policy of the app (or the
     * client's override) whose prefix it sits under. As with {@link #cleanupExpired}, the files
     * service decides; the worker only learns what went, grouped by client and app, so it can tell
     * each app.
     */
    @PostMapping("/api/files/internal/{resourceType}/applyRetention")
    List<FilesRetentionResult> applyRetention(
            @PathVariable("resourceType") String resourceType, @RequestParam("limit") int limit);
}
