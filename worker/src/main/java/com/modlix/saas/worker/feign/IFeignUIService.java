package com.modlix.saas.worker.feign;

import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "ui", contextId = "workerUIService")
public interface IFeignUIService {

    /**
     * Asks the ui service to drop import receipts past their retention window.
     *
     * <p>Both halves of a transport go: the transport document, which carries the
     * uploaded zip base64'd into a field, and the version row that the create
     * wrote holding a second copy of the same bytes.
     *
     * <p>Bounded per call and returns what it removed, so the worker can tell a
     * backlog from a finished sweep instead of this method deciding how much of a
     * live database to churn in one go.
     */
    @PostMapping("/api/ui/transports/internal/cleanupOlderThan")
    Map<String, Integer> cleanupTransports(
            @RequestParam("retentionDays") int retentionDays, @RequestParam("limit") int limit);
}
