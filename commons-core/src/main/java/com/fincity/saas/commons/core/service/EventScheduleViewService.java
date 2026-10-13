package com.fincity.saas.commons.core.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.document.EventDefinition;
import com.fincity.saas.commons.core.dto.EventRun;
import com.fincity.saas.commons.core.model.ClientSchedule;
import com.fincity.saas.commons.core.model.EventSchedule;
import com.fincity.saas.commons.core.model.ScheduleModel;
import com.fincity.saas.commons.core.model.SchedulePreview;
import com.fincity.saas.commons.core.scheduler.ScheduleConverter;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * What the appbuilder's schedule screens read, so they can be built from basic
 * components: the builder's conversions between a person's time zone and the
 * stored UTC cron, the zone list, and every client's view of one event's schedule.
 *
 * <p>
 * Kept out of EventDefinitionService and EventRunService because it needs both,
 * and EventRunService already depends on EventDefinitionService.
 */
@Service
public class EventScheduleViewService {

    private static final int NEXT_RUNS = 3;

    private final EventDefinitionService edService;
    private final EventRunService runService;
    private final IFeignSecurityService securityService;

    public EventScheduleViewService(EventDefinitionService edService, EventRunService runService,
            IFeignSecurityService securityService) {
        this.edService = edService;
        this.runService = runService;
        this.securityService = securityService;
    }

    // ---------------------------------------------------------------- builder

    /** A builder model read in {@code zone}, as the UTC cron to store, with what it will do. */
    @PreAuthorize("hasAuthority('Authorities.EventDefinition_READ')")
    public Mono<SchedulePreview> toCron(ScheduleModel model, String zone) {

        return Mono.fromCallable(() -> {
            ZoneId display = zoneOrUtc(zone);
            int offset = ScheduleConverter.offsetMinutes(display, Instant.now());
            String cron = ScheduleConverter.toUtcCron(normalise(model), offset);
            return this.preview(cron, ZoneId.of("UTC"), display,
                    cron.isEmpty() ? model : ScheduleConverter.fromCron(cron, offset));
        });
    }

    /**
     * A stored cron, read in {@code cronZone} (UTC when blank, which is what the
     * builder writes), as the builder shows it in {@code zone}.
     */
    @PreAuthorize("hasAuthority('Authorities.EventDefinition_READ')")
    public Mono<SchedulePreview> fromCron(String cron, String cronZone, String zone) {

        return Mono.fromCallable(() -> {
            ZoneId display = zoneOrUtc(zone);
            ZoneId stored = zoneOrUtc(cronZone);
            Instant now = Instant.now();
            int delta = ScheduleConverter.offsetMinutes(display, now) - ScheduleConverter.offsetMinutes(stored, now);
            return this.preview(cron == null ? "" : cron.trim(), stored, display,
                    ScheduleConverter.fromCron(cron, delta));
        });
    }

    /** Every zone the scheduler accepts, with its offset now, for a Dropdown. */
    public Mono<List<Map<String, String>>> zones() {

        return Mono.fromCallable(() -> {
            Instant now = Instant.now();
            List<Map<String, String>> out = new ArrayList<>();
            out.add(Map.of("zone", "UTC", "label", "UTC (UTC+00:00)"));
            ZoneId.getAvailableZoneIds().stream()
                    .filter(z -> z.contains("/") && !z.startsWith("Etc/") && !z.startsWith("SystemV/"))
                    .sorted()
                    .forEach(z -> out.add(Map.of("zone", z, "label", z + " ("
                            + ScheduleConverter.formatOffset(ScheduleConverter.offsetMinutes(ZoneId.of(z), now))
                            + ")")));
            return out;
        });
    }

    /**
     * The model with every field valid for its frequency. The screen keeps one model
     * across frequency changes, so switching from "every 5 minutes" to hours would
     * otherwise carry an "every 5" no hourly cron can express. The preview hands the
     * canonical model back, and the screen takes it.
     */
    static ScheduleModel normalise(ScheduleModel in) {

        ScheduleModel m = in == null ? new ScheduleModel() : in;
        if (m.getFrequency() == null)
            m.setFrequency("off");
        if (m.getMinute() == null || m.getMinute() < 0 || m.getMinute() > 59)
            m.setMinute(0);
        if (m.getHour() == null || m.getHour() < 0 || m.getHour() > 23)
            m.setHour(9);
        if ("minutes".equals(m.getFrequency()) && !ScheduleConverter.MINUTE_STEPS.contains(m.getEvery()))
            m.setEvery(5);
        if ("hours".equals(m.getFrequency())) {
            if (!ScheduleConverter.HOUR_STEPS.contains(m.getEvery()))
                m.setEvery(1);
            m.setHour(m.getHour() % m.getEvery());
        }
        if (m.getDays() == null || m.getDays().isEmpty())
            m.setDays(List.of(1, 2, 3, 4, 5));
        if (m.getDayOfMonth() == null || m.getDayOfMonth().isBlank())
            m.setDayOfMonth("1");
        return m;
    }

    private SchedulePreview preview(String cron, ZoneId cronZone, ZoneId display, ScheduleModel model) {

        SchedulePreview p = new SchedulePreview()
                .setCron(cron)
                .setTimezone(cronZone.getId())
                .setModel(model)
                .setDisplayZone(display.getId())
                .setObservesDst(ScheduleConverter.observesDst(display, LocalDate.now().getYear()))
                .setDescription(ScheduleConverter.describe(model, display.getId()))
                .setValid(true);

        if (cron.isEmpty())
            return p.setNextRuns(List.of());

        try {
            return p.setNextRuns(ScheduleConverter.nextRuns(cron, cronZone, display, Instant.now(), NEXT_RUNS));
        } catch (IllegalArgumentException e) {
            return p.setValid(false).setNextRuns(List.of())
                    .setError("The scheduler cannot read this cron: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- clients

    /**
     * Every client that runs, or could run, this event's schedule, and that the
     * caller may read: its own client and the clients it manages, when its own
     * client may read the app.
     *
     * <p>
     * The candidates are the scheduler's own: clients with a copy of the definition,
     * and clients with write access to the app (those a "run once per client"
     * schedule fans out to). Times are shown in {@code zone}.
     *
     * <p>
     * Paged, because a client that manages many tenants sees every one with write
     * access to the app, and each row costs reads: only the requested page is built.
     * Clients with a copy of their own come first, then the caller's, then by code.
     * {@code clientCode} narrows it to one client.
     */
    @PreAuthorize("hasAuthority('Authorities.EventDefinition_READ')")
    public Mono<Page<ClientSchedule>> clientSchedules(String appCode, String name, String zone, String clientCode,
            Pageable pageable) {

        ZoneId display = zoneOrUtc(zone);

        return FlatMapUtil.flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> this.edService.readAllCopies(name, appCode).collectList(),

                        (ca, copies) -> this.securityService.getWriteAccessClientCodes(appCode)
                                .onErrorResume(e -> Mono.just(List.of()))
                                .defaultIfEmpty(List.of()),

                        (ca, copies, writers) -> {
                            Set<String> candidates = new LinkedHashSet<>();
                            copies.forEach(c -> candidates.add(c.getClientCode()));
                            candidates.addAll(writers);
                            return this.runService.visibleAmong(ca, appCode, candidates, false);
                        },

                        (ca, copies, writers, visible) -> {
                            Set<String> holders = new java.util.HashSet<>();
                            copies.forEach(c -> holders.add(c.getClientCode()));
                            List<String> ordered = visible.stream()
                                    .filter(c -> StringUtil.safeIsBlank(clientCode) || c.equals(clientCode.trim()))
                                    .sorted(Comparator.comparing((String c) -> !holders.contains(c))
                                            .thenComparing(c -> !c.equals(ca.getClientCode()))
                                            .thenComparing(Comparator.naturalOrder()))
                                    .toList();
                            int from = (int) Math.min(pageable.getOffset(), ordered.size());
                            int to = Math.min(from + pageable.getPageSize(), ordered.size());
                            return Mono.just(new PageImpl<>(ordered.subList(from, to), pageable, ordered.size()));
                        },

                        (ca, copies, writers, visible, page) -> this.runService
                                .latestRuns(appCode, name, page.getContent()),

                        (ca, copies, writers, visible, page, lastRuns) -> this.rows(appCode, name, display, copies,
                                Set.copyOf(writers), page.getContent(), lastRuns, ca.getClientCode())
                                .map(rows -> (Page<ClientSchedule>) new PageImpl<>(rows, pageable,
                                        page.getTotalElements())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "EventScheduleViewService.clientSchedules"));
    }

    private Mono<List<ClientSchedule>> rows(String appCode, String name, ZoneId display, List<EventDefinition> copies,
            Set<String> writers, List<String> clients, Map<String, EventRun> lastRuns, String callerClient) {

        Map<String, EventDefinition> byClient = new HashMap<>();
        copies.forEach(c -> byClient.put(c.getClientCode(), c));

        // What a client without a copy is forked from: the caller's own standalone
        // definition when it has one, else any standalone one.
        List<EventDefinition> roots = copies.stream().filter(c -> StringUtil.safeIsBlank(c.getBaseClientCode()))
                .toList();
        String baseId = roots.stream().filter(c -> c.getClientCode().equals(callerClient)).findFirst()
                .or(() -> roots.stream().findFirst()).map(EventDefinition::getId).orElse(null);

        return Flux.fromIterable(clients)
                .flatMapSequential(client -> this.row(appCode, name, client, display, byClient.get(client), baseId,
                        writers.contains(client), lastRuns.get(client)), 8)
                .collectList();
    }

    private Mono<ClientSchedule> row(String appCode, String name, String client, ZoneId display,
            EventDefinition copy, String baseId, boolean writer, EventRun lastRun) {

        Mono<EventSchedule> live = this.edService.readEffective(name, appCode, client)
                .mapNotNull(EventDefinition::getSchedule)
                .onErrorResume(e -> Mono.empty());

        Mono<Map<String, Object>> draft = copy == null ? Mono.empty()
                : this.edService.readDraftWithVersion(copy.getId())
                        .filter(t -> t.getT2() != null && t.getT2() > 0)
                        .map(t -> {
                            Map<String, Object> m = new HashMap<>();
                            m.put("version", t.getT2());
                            if (t.getT1().getSchedule() != null)
                                m.put("schedule", t.getT1().getSchedule());
                            return m;
                        })
                        .onErrorResume(e -> Mono.empty());

        Mono<String> clientName = this.securityService.getClientByCode(client)
                .mapNotNull(c -> c.getName())
                .onErrorResume(e -> Mono.empty());

        return Mono.zip(live.map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty()),
                        draft.map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty()),
                        clientName.defaultIfEmpty(client))
                .map(t -> {
                    EventSchedule schedule = t.getT1().orElse(null);

                    ClientSchedule r = new ClientSchedule()
                            .setClientCode(client)
                            .setClientName(t.getT3())
                            .setOwner(copy != null && StringUtil.safeIsBlank(copy.getBaseClientCode()))
                            .setCopyId(copy == null ? null : copy.getId())
                            .setBaseClientCode(copy == null ? null : copy.getBaseClientCode())
                            .setBaseId(baseId)
                            .setOwnSchedule(copy == null ? null : copy.getSchedule())
                            .setLiveSchedule(schedule)
                            .setHasWriteAccess(writer)
                            .setLastRun(EventRunService.decorate(lastRun, display.getId()));

                    t.getT2().ifPresent(d -> {
                        r.setDraftVersion((Integer) d.get("version"));
                        EventSchedule ds = (EventSchedule) d.get("schedule");
                        r.setDraftSchedule(ds);
                        r.setDraftDescription(this.describe(ds, display));
                    });

                    boolean holder = copy != null && copy.getSchedule() != null;
                    this.state(r, schedule, holder, writer, display);
                    return r;
                });
    }

    /** Mirrors EventScheduler.runClient, so "runs" here means the scheduler picks it. */
    private void state(ClientSchedule r, EventSchedule s, boolean holder, boolean writer, ZoneId display) {

        r.setDescription(this.describe(s, display));

        if (s == null || StringUtil.safeIsBlank(s.getCron())) {
            r.setState(ClientSchedule.State.NOT_SCHEDULED).setStateReason("No cron is set.");
            return;
        }

        ZoneId zone;
        try {
            zone = ZoneId.of(s.effectiveTimezone());
            ScheduleConverter.parse(s.getCron());
        } catch (RuntimeException e) {
            r.setState(ClientSchedule.State.INVALID)
                    .setStateReason("The scheduler cannot read this schedule: " + e.getMessage());
            return;
        }

        if (Boolean.FALSE.equals(s.getEnabled())) {
            r.setState(ClientSchedule.State.PAUSED).setStateReason("Paused.");
            return;
        }

        if (s.isForEachClient() ? !writer : !holder) {
            r.setState(ClientSchedule.State.NOT_RUNNING).setStateReason(s.isForEachClient()
                    ? "Runs once per client, but this client has no write access to the app."
                    : "Run once per client is off, so only clients whose own copy sets a schedule run it.");
            return;
        }

        r.setState(ClientSchedule.State.RUNS)
                .setNextRuns(ScheduleConverter.nextRuns(s.getCron(), zone, display, Instant.now(), NEXT_RUNS));
    }

    private String describe(EventSchedule s, ZoneId display) {

        if (s == null || StringUtil.safeIsBlank(s.getCron()))
            return "Not scheduled";

        ZoneId stored = zoneOrUtc(s.getTimezone());
        Instant now = Instant.now();
        int delta = ScheduleConverter.offsetMinutes(display, now) - ScheduleConverter.offsetMinutes(stored, now);
        return ScheduleConverter.describe(ScheduleConverter.fromCron(s.getCron(), delta), display.getId());
    }

    private static ZoneId zoneOrUtc(String zone) {
        if (StringUtil.safeIsBlank(zone))
            return ZoneId.of("UTC");
        try {
            return ZoneId.of(zone.trim());
        } catch (RuntimeException e) {
            return ZoneId.of("UTC");
        }
    }
}
