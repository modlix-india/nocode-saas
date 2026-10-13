package com.modlix.saas.worker.feign;

import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "core", contextId = "workerCoreService")
public interface IFeignCoreService {

    @PostMapping("/api/core/internal/billing/meter")
    Boolean triggerBillingMetering();

    @PostMapping("/api/core/internal/billing/reconcile")
    Boolean reconcileBilling();

    /** The core half of the same sweep. See {@link IFeignUIService#cleanupTransports}. */
    @PostMapping("/api/core/transports/internal/cleanupOlderThan")
    Map<String, Integer> cleanupTransports(
            @RequestParam("retentionDays") int retentionDays, @RequestParam("limit") int limit);

    /**
     * Deletes event run log rows (core_event_runs): successful runs older than
     * {@code successRetentionDays}, every run older than {@code retentionDays}; and closes runs with
     * no outcome after {@code staleAfterHours}. Core enforces the floors and the per-call cap.
     */
    @PostMapping("/api/core/eventRuns/internal/cleanupOlderThan")
    Map<String, Integer> cleanupEventRuns(@RequestParam("retentionDays") int retentionDays,
            @RequestParam("successRetentionDays") int successRetentionDays,
            @RequestParam("staleAfterHours") int staleAfterHours, @RequestParam("limit") int limit);
}
