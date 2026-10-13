package com.fincity.saas.commons.core.dao;

import static com.fincity.saas.commons.core.jooq.tables.CoreEventRuns.CORE_EVENT_RUNS;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.JSON;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.jooq.types.UInteger;
import org.jooq.types.ULong;
import org.jooq.types.UShort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.core.dto.EventRun;
import com.fincity.saas.commons.core.jooq.enums.CoreEventRunsStatus;
import com.fincity.saas.commons.core.jooq.enums.CoreEventRunsTriggerType;
import com.fincity.saas.commons.core.jooq.tables.records.CoreEventRunsRecord;
import com.fincity.saas.commons.core.model.EventRunTask;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * core_event_runs. A plain DAO rather than an AbstractUpdatableDAO: the table is a
 * log with no CREATED_BY / UPDATED_* columns, and nothing but EventRunService
 * writes it.
 */
@Component
public class EventRunDAO {

    private static final TypeReference<List<EventRunTask>> TASK_LIST = new TypeReference<>() {
    };

    private final DSLContext dslContext;
    private final ObjectMapper objectMapper;

    public EventRunDAO(DSLContext dslContext, ObjectMapper objectMapper) {
        this.dslContext = dslContext;
        this.objectMapper = objectMapper;
    }

    public Mono<ULong> insert(EventRun run) {

        CoreEventRunsRecord rec = this.dslContext.newRecord(CORE_EVENT_RUNS);
        rec.setAppCode(run.getAppCode());
        rec.setClientCode(run.getClientCode());
        rec.setEventName(run.getEventName());
        rec.setTriggerType(run.getTriggerType());
        rec.setStatus(run.getStatus());
        rec.setScheduleRunId(run.getScheduleRunId());
        rec.setScheduledFor(run.getScheduledFor());
        rec.setAttempt(run.getAttempt() == null ? null : UInteger.valueOf(run.getAttempt()));
        rec.setIsDraft(Boolean.TRUE.equals(run.getIsDraft()) ? (byte) 1 : (byte) 0);
        rec.setTriggeredBy(run.getTriggeredBy());
        rec.setTaskCount(UShort.valueOf(nonNegative(run.getTaskCount())));
        rec.setFailedTaskCount(UShort.valueOf(nonNegative(run.getFailedTaskCount())));
        rec.setTasks(this.toJson(run.getTasks()));
        rec.setError(run.getError());
        rec.setInstanceId(run.getInstanceId());
        rec.setStartedAt(run.getStartedAt());
        rec.setFinishedAt(run.getFinishedAt());
        rec.setDurationMs(run.getDurationMs() == null ? null : UInteger.valueOf(Math.max(0, run.getDurationMs())));

        return Mono.from(this.dslContext.insertInto(CORE_EVENT_RUNS).set(rec).returning(CORE_EVENT_RUNS.ID))
                .map(r -> r.get(CORE_EVENT_RUNS.ID));
    }

    /** Closes a RUNNING row. A row someone already closed (the stale sweep) is overwritten: the run did report. */
    public Mono<Boolean> finish(ULong id, CoreEventRunsStatus status, List<EventRunTask> tasks, int failedTasks,
            String error, LocalDateTime finishedAt, long durationMs) {

        return Mono.from(this.dslContext.update(CORE_EVENT_RUNS)
                .set(CORE_EVENT_RUNS.STATUS, status)
                .set(CORE_EVENT_RUNS.TASKS, this.toJson(tasks))
                .set(CORE_EVENT_RUNS.TASK_COUNT, UShort.valueOf(tasks == null ? 0 : tasks.size()))
                .set(CORE_EVENT_RUNS.FAILED_TASK_COUNT, UShort.valueOf(nonNegative(failedTasks)))
                .set(CORE_EVENT_RUNS.ERROR, error)
                .set(CORE_EVENT_RUNS.FINISHED_AT, finishedAt)
                .set(CORE_EVENT_RUNS.DURATION_MS, UInteger.valueOf(Math.max(0, durationMs)))
                .where(CORE_EVENT_RUNS.ID.eq(id)))
                .map(n -> n > 0);
    }

    public Mono<EventRun> read(ULong id) {
        return Mono.from(this.dslContext.selectFrom(CORE_EVENT_RUNS).where(CORE_EVENT_RUNS.ID.eq(id)))
                .map(this::toRun);
    }

    /**
     * Newest first, for the given clients. Null filters are ignored; appCode and at
     * least one client are required. One client walks K1 (CLIENT, APP, EVENT,
     * STARTED); several walk K5 (APP, EVENT, STARTED, CLIENT) backwards and filter
     * the client in the index, so a page stops after its rows instead of sorting
     * every client's history.
     */
    public Mono<Page<EventRun>> readPage(String appCode, List<String> clientCodes, String eventName,
            CoreEventRunsStatus status, CoreEventRunsTriggerType triggerType, LocalDateTime from,
            LocalDateTime to, Pageable pageable) {

        if (clientCodes == null || clientCodes.isEmpty())
            return Mono.just(new PageImpl<>(List.of(), pageable, 0));

        List<Condition> conditions = new ArrayList<>();
        conditions.add(clientCodes.size() == 1 ? CORE_EVENT_RUNS.CLIENT_CODE.eq(clientCodes.getFirst())
                : CORE_EVENT_RUNS.CLIENT_CODE.in(clientCodes));
        conditions.add(CORE_EVENT_RUNS.APP_CODE.eq(appCode));
        if (eventName != null && !eventName.isBlank())
            conditions.add(CORE_EVENT_RUNS.EVENT_NAME.eq(eventName));
        if (status != null)
            conditions.add(CORE_EVENT_RUNS.STATUS.eq(status));
        if (triggerType != null)
            conditions.add(CORE_EVENT_RUNS.TRIGGER_TYPE.eq(triggerType));
        if (from != null)
            conditions.add(CORE_EVENT_RUNS.STARTED_AT.ge(from));
        if (to != null)
            conditions.add(CORE_EVENT_RUNS.STARTED_AT.lt(to));

        Condition where = DSL.and(conditions);

        Mono<Integer> count = Mono.from(this.dslContext.selectCount().from(CORE_EVENT_RUNS).where(where))
                .map(r -> r.value1());

        Mono<List<EventRun>> rows = Flux.from(this.dslContext.selectFrom(CORE_EVENT_RUNS)
                        .where(where)
                        .orderBy(CORE_EVENT_RUNS.STARTED_AT.desc(), CORE_EVENT_RUNS.ID.desc())
                        .limit(pageable.getPageSize())
                        .offset(pageable.getOffset()))
                .map(this::toRun)
                .collectList();

        return Mono.zip(rows, count).map(t -> new PageImpl<>(t.getT1(), pageable, t.getT2()));
    }

    /** The clients that have any run of this event, at most {@code limit} of them. */
    public Mono<List<String>> distinctClients(String appCode, String eventName, int limit) {

        Condition where = CORE_EVENT_RUNS.APP_CODE.eq(appCode);
        if (eventName != null && !eventName.isBlank())
            where = where.and(CORE_EVENT_RUNS.EVENT_NAME.eq(eventName));

        return Flux.from(this.dslContext.selectDistinct(CORE_EVENT_RUNS.CLIENT_CODE)
                        .from(CORE_EVENT_RUNS)
                        .where(where)
                        .limit(limit))
                .map(r -> trim(r.value1()))
                .collectList();
    }

    /** The newest run of this event for each of the given clients. */
    public Mono<List<EventRun>> latestPerClient(String appCode, String eventName, List<String> clientCodes) {

        return Flux.fromIterable(clientCodes)
                .flatMapSequential(client -> Mono.from(this.dslContext.selectFrom(CORE_EVENT_RUNS)
                        .where(CORE_EVENT_RUNS.CLIENT_CODE.eq(client))
                        .and(CORE_EVENT_RUNS.APP_CODE.eq(appCode))
                        .and(CORE_EVENT_RUNS.EVENT_NAME.eq(eventName))
                        .orderBy(CORE_EVENT_RUNS.STARTED_AT.desc(), CORE_EVENT_RUNS.ID.desc())
                        .limit(1)).map(this::toRun), 8)
                .collectList();
    }

    /**
     * Deletes up to {@code limit} rows started before {@code before}, only those in
     * {@code status} when it is not null; answers how many went. With a status the
     * delete walks K3 (STATUS, STARTED_AT), without one K4 (STARTED_AT).
     */
    public Mono<Integer> deleteStartedBefore(LocalDateTime before, CoreEventRunsStatus status, int limit) {

        Condition where = CORE_EVENT_RUNS.STARTED_AT.lt(before);
        if (status != null)
            where = CORE_EVENT_RUNS.STATUS.eq(status).and(where);

        return Mono.from(this.dslContext.deleteFrom(CORE_EVENT_RUNS).where(where).limit(limit));
    }

    /** Closes RUNNING rows started before {@code before} as FAILED, with {@code error}. */
    public Mono<Integer> failRunningStartedBefore(LocalDateTime before, String error, LocalDateTime now) {
        return Mono.from(this.dslContext.update(CORE_EVENT_RUNS)
                .set(CORE_EVENT_RUNS.STATUS, CoreEventRunsStatus.FAILED)
                .set(CORE_EVENT_RUNS.ERROR, error)
                .set(CORE_EVENT_RUNS.FINISHED_AT, now)
                .where(CORE_EVENT_RUNS.STATUS.eq(CoreEventRunsStatus.RUNNING))
                .and(CORE_EVENT_RUNS.STARTED_AT.lt(before)));
    }

    private EventRun toRun(Record r) {
        CoreEventRunsRecord rec = r.into(CORE_EVENT_RUNS);
        return new EventRun()
                .setId(rec.getId())
                .setAppCode(trim(rec.getAppCode()))
                .setClientCode(trim(rec.getClientCode()))
                .setEventName(rec.getEventName())
                .setTriggerType(rec.getTriggerType())
                .setStatus(rec.getStatus())
                .setScheduleRunId(rec.getScheduleRunId())
                .setScheduledFor(rec.getScheduledFor())
                .setAttempt(rec.getAttempt() == null ? null : rec.getAttempt().intValue())
                .setIsDraft(rec.getIsDraft() != null && rec.getIsDraft() != 0)
                .setTriggeredBy(rec.getTriggeredBy())
                .setTaskCount(rec.getTaskCount() == null ? 0 : rec.getTaskCount().intValue())
                .setFailedTaskCount(rec.getFailedTaskCount() == null ? 0 : rec.getFailedTaskCount().intValue())
                .setTasks(this.fromJson(rec.getTasks()))
                .setError(rec.getError())
                .setInstanceId(rec.getInstanceId())
                .setStartedAt(rec.getStartedAt())
                .setFinishedAt(rec.getFinishedAt())
                .setDurationMs(rec.getDurationMs() == null ? null : rec.getDurationMs().longValue());
    }

    private JSON toJson(List<EventRunTask> tasks) {
        if (tasks == null || tasks.isEmpty())
            return null;
        try {
            return JSON.valueOf(this.objectMapper.writeValueAsString(tasks));
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private List<EventRunTask> fromJson(JSON json) {
        if (json == null || json.data() == null || json.data().isBlank())
            return List.of();
        try {
            return this.objectMapper.readValue(json.data(), TASK_LIST);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private static int nonNegative(Integer n) {
        return n == null || n < 0 ? 0 : n;
    }

    // CHAR columns come back padded on some drivers.
    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
