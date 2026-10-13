package com.fincity.saas.core.controller;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.core.dto.EventRun;
import com.fincity.saas.commons.core.jooq.enums.CoreEventRunsStatus;
import com.fincity.saas.commons.core.jooq.enums.CoreEventRunsTriggerType;
import com.fincity.saas.commons.core.service.EventRunService;

import reactor.core.publisher.Mono;

/**
 * The event run log. Who may read what is decided in EventRunService: the caller's
 * own client, or a client it manages, with Authorities.EventAction_READ.
 */
@RestController
@RequestMapping("api/core/eventRuns")
public class EventRunController {

    private static final int MAX_PAGE_SIZE = 200;

    private final EventRunService service;

    public EventRunController(EventRunService service) {
        this.service = service;
    }

    /**
     * Runs of {@code appCode}, newest first. {@code clientCode} defaults to the
     * caller's own client; {@code allClients=true} reads every client the caller may
     * see that has a run of this event. {@code from} and {@code to} are epoch seconds,
     * matching how every time in the response is written.
     */
    @GetMapping
    public Mono<ResponseEntity<Page<EventRun>>> readPage(@RequestParam String appCode,
            @RequestParam(required = false) String clientCode,
            @RequestParam(required = false, defaultValue = "false") boolean allClients,
            @RequestParam(required = false) String eventName,
            @RequestParam(required = false) CoreEventRunsStatus status,
            @RequestParam(required = false) CoreEventRunsTriggerType triggerType,
            @RequestParam(required = false) Long from, @RequestParam(required = false) Long to,
            @RequestParam(required = false) String timezone, Pageable pageable) {

        Pageable page = PageRequest.of(pageable.getPageNumber(),
                Math.min(MAX_PAGE_SIZE, Math.max(1, pageable.getPageSize())));

        return this.service.readPage(appCode, clientCode, allClients, eventName, status, triggerType, time(from),
                        time(to), page, timezone)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<EventRun>> read(@PathVariable String id) {
        return Mono.fromCallable(() -> ULong.valueOf(id))
                .onErrorResume(NumberFormatException.class, e -> Mono.empty())
                .flatMap(this.service::read)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * The retention sweep, driven by the worker's EVENT_RUN_CLEANUP task.
     *
     * <p>
     * Unauthenticated, like /transports/internal/cleanupOlderThan: the worker calls
     * core directly through Eureka and carries no token, and nginx keeps internal
     * paths off the public edge. What makes that defensible is that the damage is
     * bounded by construction in EventRunService, not by the caller: it can only
     * delete run-log rows, never younger than 30 days (1 day for successes), at most
     * 200,000 per call.
     */
    @PostMapping("/internal/cleanupOlderThan")
    public Mono<ResponseEntity<Map<String, Integer>>> internalCleanupOlderThan(
            @RequestParam(required = false, defaultValue = "90") int retentionDays,
            @RequestParam(required = false, defaultValue = "7") int successRetentionDays,
            @RequestParam(required = false, defaultValue = "6") int staleAfterHours,
            @RequestParam(required = false, defaultValue = "50000") int limit) {

        return this.service.cleanupOlderThan(retentionDays, successRetentionDays, staleAfterHours, limit)
                .map(ResponseEntity::ok);
    }

    private static LocalDateTime time(Long epochSeconds) {
        return epochSeconds == null ? null : LocalDateTime.ofEpochSecond(epochSeconds, 0, ZoneOffset.UTC);
    }
}
