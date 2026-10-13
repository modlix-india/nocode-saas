package com.fincity.saas.core.controller;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.core.document.EventDefinition;
import com.fincity.saas.commons.core.model.ClientSchedule;
import com.fincity.saas.commons.core.model.ScheduleModel;
import com.fincity.saas.commons.core.model.SchedulePreview;
import com.fincity.saas.commons.core.repository.EventDefinitionRepository;
import com.fincity.saas.commons.core.service.EventDefinitionService;
import com.fincity.saas.commons.core.service.EventScheduleViewService;
import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/core/eventDefinitions")
public class EventDefinitionController
        extends AbstractOverridableDataController<EventDefinition, EventDefinitionRepository, EventDefinitionService> {

    private final EventScheduleViewService scheduleViewService;

    public EventDefinitionController(EventScheduleViewService scheduleViewService) {
        this.scheduleViewService = scheduleViewService;
    }

    /**
     * Every client's view of this event's schedule that the caller may read, with
     * times in {@code timezone}, one page at a time; {@code clientCode} narrows it to
     * one client. See EventScheduleViewService.clientSchedules.
     */
    @GetMapping("/schedules")
    public Mono<ResponseEntity<Page<ClientSchedule>>> clientSchedules(@RequestParam String appCode,
            @RequestParam String name, @RequestParam(required = false) String timezone,
            @RequestParam(required = false) String clientCode,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {

        return this.scheduleViewService.clientSchedules(appCode, name, timezone, clientCode,
                        PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100)))
                .map(ResponseEntity::ok);
    }

    /** A builder model read in {@code timezone}, as the UTC cron to store, with a preview. */
    @PostMapping("/schedule/toCron")
    public Mono<ResponseEntity<SchedulePreview>> toCron(@RequestParam(required = false) String timezone,
            @RequestBody ScheduleModel model) {

        return this.scheduleViewService.toCron(model, timezone).map(ResponseEntity::ok);
    }

    /**
     * A stored cron as the builder shows it in {@code timezone}. A body rather than
     * query parameters because a cron is full of characters a url mangles.
     * Body: {@code {cron, cronZone}}; cronZone is UTC when absent.
     */
    @PostMapping("/schedule/fromCron")
    public Mono<ResponseEntity<SchedulePreview>> fromCron(@RequestParam(required = false) String timezone,
            @RequestBody Map<String, String> body) {

        return this.scheduleViewService.fromCron(body.get("cron"), body.get("cronZone"), timezone)
                .map(ResponseEntity::ok);
    }

    /** Every zone the scheduler accepts, labelled with its offset now. */
    @GetMapping("/schedule/zones")
    public Mono<ResponseEntity<List<Map<String, String>>>> zones() {
        return this.scheduleViewService.zones().map(ResponseEntity::ok);
    }
}
