package com.modlix.saas.worker.feign;

import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.modlix.saas.worker.dto.CoreEventRaiseRequest;

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

    /**
     * Raises a core event for an app and client, run by its EventAction under the client's
     * anonymous event-runner context. Core accepts only the event names it lists for this path
     * (FILES_EXPIRED).
     */
    @PostMapping("/api/core/events/internal/raise")
    Boolean raiseEvent(@RequestBody CoreEventRaiseRequest request);
}
