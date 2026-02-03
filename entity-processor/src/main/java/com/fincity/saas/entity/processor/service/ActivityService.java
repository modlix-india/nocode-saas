package com.fincity.saas.entity.processor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.functions.annotations.IgnoreGeneration;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.model.User;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.DifferenceExtractor;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.ActivityDAO;
import com.fincity.saas.entity.processor.dto.Activity;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.base.BaseDto;
import com.fincity.saas.entity.processor.dto.content.Note;
import com.fincity.saas.entity.processor.dto.content.Task;
import com.fincity.saas.entity.processor.dto.content.base.BaseContentDto;
import com.fincity.saas.entity.processor.enums.ActivityAction;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.Tag;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorActivitiesRecord;
import com.fincity.saas.entity.processor.model.common.ActivityObject;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.ticket.CallLogRequest;
import com.fincity.saas.entity.processor.service.base.BaseService;
import com.fincity.saas.entity.processor.util.CollectionUtil;
import com.fincity.saas.entity.processor.util.NameUtil;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
@IgnoreGeneration
public class ActivityService extends BaseService<EntityProcessorActivitiesRecord, Activity, ActivityDAO> {

    private static final List<String> sUpdatedFields = List.of(
            AbstractDTO.Fields.id,
            AbstractDTO.Fields.createdBy,
            AbstractDTO.Fields.createdAt,
            AbstractUpdatableDTO.Fields.updatedBy,
            AbstractUpdatableDTO.Fields.updatedAt,
            BaseDto.Fields.code);

    private static final JsonNodeFactory FACTORY = JsonNodeFactory.instance;

    private StageService stageService;
    private TicketService ticketService;

    private static boolean isValidId(ULong id) {
        return id != null && id.longValue() > 0;
    }

    @Lazy
    @Autowired
    private void setStageService(StageService stageService) {
        this.stageService = stageService;
    }

    @Lazy
    @Autowired
    private void setTicketService(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Override
    protected Mono<ULong> getLoggedInUserId() {
        return SecurityContextUtil.getUsersContextUser().map(ContextUser::getId).map(ULong::valueOf);
    }

    @Override
    public Mono<Activity> create(Activity activity) {
        return super.hasAccess()
                .flatMap(access -> super.createInternal(access, activity))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.create"));
    }

    public Mono<Page<Activity>> readPageFilter(Pageable pageable, Identity ticket, AbstractCondition condition) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.ticketService.checkAndUpdateIdentityWithAccess(access, ticket),
                        (access, uTicket) -> super.readPageFilter(
                                pageable, addTicketToCondition(access, condition, uTicket.getULongId())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.readPageFilter"));
    }

    public Mono<Page<Map<String, Object>>> readPageFilterEager(
            Pageable pageable,
            Identity ticket,
            AbstractCondition condition,
            List<String> tableFields,
            MultiValueMap<String, String> queryParams) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.ticketService.checkAndUpdateIdentityWithAccess(access, ticket),
                        (access, uTicket) -> super.readPageFilterEager(
                                pageable,
                                addTicketToCondition(access, condition, uTicket.getULongId()),
                                tableFields,
                                queryParams))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.readPageFilterEager"));
    }

    private AbstractCondition addTicketToCondition(
            ProcessorAccess access, AbstractCondition condition, ULong ticketId) {
        return ComplexCondition.and(
                super.addAppCodeAndClientCodeToCondition(access, condition),
                FilterCondition.make(Activity.Fields.ticketId, ticketId).setOperator(FilterConditionOperator.EQUALS));
    }

    private Mono<Void> createActivityInternal(ActivityAction action, String comment, Map<String, Object> context) {
        return this.hasAccess().flatMap(access -> this.createActivityInternal(access, action, null, comment, context));
    }

    private Mono<Void> createActivityInternal(
            ProcessorAccess access,
            ActivityAction action,
            LocalDateTime createdOn,
            String comment,
            Map<String, Object> context) {

        ULong ticketId = context.containsKey(Activity.Fields.ticketId)
                ? ULongUtil.valueOf(context.get(Activity.Fields.ticketId))
                : null;
        ULong ownerId = context.containsKey(Activity.Fields.ownerId)
                ? ULongUtil.valueOf(context.get(Activity.Fields.ownerId))
                : null;
        ULong userId = context.containsKey(Activity.Fields.userId)
                ? ULongUtil.valueOf(context.get(Activity.Fields.userId))
                : null;

        if (isValidId(ticketId))
            return this.createActivityForTicket(access, action, createdOn, comment, context, ticketId);

        if (isValidId(ownerId))
            return this.createActivityForOwner(access, action, createdOn, comment, context, ownerId);

        if (isValidId(userId)) return this.createActivityForUser(access, action, createdOn, comment, context, userId);

        return Mono.empty();
    }

    private Map<String, Object> prepareActivityContext(
            ProcessorAccess access,
            Map<String, Object> context,
            EntitySeries parentEntitySeries,
            LocalDateTime createdOn) {
        Map<String, Object> mutableContext = new HashMap<>(context);
        mutableContext.put("entity", parentEntitySeries.getPrefix(access.getAppCode()));
        if (!mutableContext.containsKey("user"))
            mutableContext.put("user", IdAndValue.of(access.getUserId(), access.getUserName()));

        LocalDateTime activityDate = createdOn != null ? createdOn : LocalDateTime.now();
        if (!mutableContext.containsKey("dateTime")) mutableContext.put("dateTime", activityDate);

        return mutableContext;
    }

    private Mono<Void> createActivityForTicket(
            ProcessorAccess access,
            ActivityAction action,
            LocalDateTime createdOn,
            String comment,
            Map<String, Object> context,
            ULong ticketId) {
        Map<String, Object> mutableContext =
                this.prepareActivityContext(access, context, EntitySeries.TICKET, createdOn);
        LocalDateTime activityDate = (LocalDateTime) mutableContext.get("dateTime");
        ActivityObject activityObject = ActivityObject.ofTicket(ticketId, comment, mutableContext);
        Activity activity = Activity.of(ticketId, null, null, action, activityObject)
                .setActivityDate(activityDate)
                .setDescription(action.formatMessage(mutableContext))
                .setActorId(access.getUserId());
        this.updateActivityIds(activity, mutableContext, action.isDelete());
        return this.createInternal(access, activity)
                .then()
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.createActivityForTicket"));
    }

    private Mono<Void> createActivityForOwner(
            ProcessorAccess access,
            ActivityAction action,
            LocalDateTime createdOn,
            String comment,
            Map<String, Object> context,
            ULong ownerId) {
        Map<String, Object> mutableContext =
                this.prepareActivityContext(access, context, EntitySeries.OWNER, createdOn);
        LocalDateTime activityDate = (LocalDateTime) mutableContext.get("dateTime");
        ActivityObject activityObject = ActivityObject.ofOwner(ownerId, comment, mutableContext);
        Activity activity = Activity.of(null, ownerId, null, action, activityObject)
                .setActivityDate(activityDate)
                .setDescription(action.formatMessage(mutableContext))
                .setActorId(access.getUserId());
        this.updateActivityIds(activity, mutableContext, action.isDelete());
        return this.createInternal(access, activity)
                .then()
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.createActivityForOwner"));
    }

    private Mono<Void> createActivityForUser(
            ProcessorAccess access,
            ActivityAction action,
            LocalDateTime createdOn,
            String comment,
            Map<String, Object> context,
            ULong userId) {
        Map<String, Object> mutableContext = this.prepareActivityContext(access, context, EntitySeries.XXX, createdOn);
        LocalDateTime activityDate = (LocalDateTime) mutableContext.get("dateTime");
        ActivityObject activityObject = ActivityObject.ofUser(userId, comment, mutableContext);
        Activity activity = Activity.of(null, null, userId, action, activityObject)
                .setActivityDate(activityDate)
                .setDescription(action.formatMessage(mutableContext))
                .setActorId(access.getUserId());
        this.updateActivityIds(activity, mutableContext, action.isDelete());
        return this.createInternal(access, activity)
                .then()
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.createActivityForUser"));
    }

    public Mono<Void> acCreate(Ticket ticket) {
        return this.hasAccess()
                .flatMap(access -> this.acCreate(access, ticket, null))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acCreate"));
    }

    public Mono<Void> acCreate(ProcessorAccess access, Ticket ticket, String comment) {
        return this.createActivityInternal(
                        access,
                        ActivityAction.CREATE,
                        null,
                        comment,
                        Map.of(
                                Activity.Fields.ticketId,
                                ticket.getId(),
                                Ticket.Fields.source,
                                ticket.getSubSource() != null
                                        ? Map.of(
                                                Ticket.Fields.source,
                                                ticket.getSource(),
                                                Ticket.Fields.subSource,
                                                ticket.getSubSource())
                                        : Map.of(Ticket.Fields.source, ticket.getSource())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acCreate"));
    }

    public Mono<Void> acDcrmImport(
            ProcessorAccess access, Ticket ticket, String comment, Map<String, Object> metadata) {
        return this.createActivityInternal(
                access,
                ActivityAction.DCRM_IMPORT,
                null,
                comment,
                Map.of(Activity.Fields.ticketId, ticket.getId(), "activities_dcrm", metadata));
    }

    public Mono<Void> acReInquiry(Ticket ticket, String source, String subSource) {
        return super.hasAccess()
                .flatMap(access -> this.acReInquiry(access, ticket, null, source, subSource))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acReInquiry"));
    }

    public Mono<Void> acReInquiry(
            ProcessorAccess access, Ticket ticket, String comment, String source, String subSource) {
        return this.createActivityInternal(
                        access,
                        ActivityAction.RE_INQUIRY,
                        null,
                        comment,
                        Map.of(
                                Activity.Fields.ticketId,
                                ticket.getId(),
                                Ticket.Fields.source,
                                subSource != null
                                        ? Map.of(Ticket.Fields.source, source, Ticket.Fields.subSource, subSource)
                                        : Map.of(Ticket.Fields.source, source)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acReInquiry"));
    }

    public Mono<Void> acQualify(ULong ticketId, String comment) {
        return this.createActivityInternal(ActivityAction.QUALIFY, comment, Map.of(Activity.Fields.ticketId, ticketId))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acQualify"));
    }

    public Mono<Void> acDisqualify(ULong ticketId, String comment) {
        return this.createActivityInternal(
                        ActivityAction.DISQUALIFY, comment, Map.of(Activity.Fields.ticketId, ticketId))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acDisqualify"));
    }

    public Mono<Void> acDiscard(ULong ticketId, String comment) {
        return this.createActivityInternal(ActivityAction.DISCARD, comment, Map.of(Activity.Fields.ticketId, ticketId))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acDiscard"));
    }

    public Mono<Void> acImport(ULong ticketId, String comment, String source) {
        return this.createActivityInternal(
                        ActivityAction.IMPORT,
                        comment,
                        Map.of(
                                Activity.Fields.ticketId, ticketId,
                                Ticket.Fields.source, source))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acImport"));
    }

    public Mono<Void> acStageStatus(ProcessorAccess access, Ticket ticket, String comment, ULong oldStageId) {

        if (oldStageId == null || ticket.getStage().equals(oldStageId))
            return this.acStatusCreate(access, ticket, comment);

        return Mono.when(
                        this.acStatusCreate(access, ticket, comment),
                        this.acStageUpdate(access, ticket, comment, oldStageId))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acStageStatus"));
    }

    private Mono<Void> acStatusCreate(ProcessorAccess access, Ticket ticket, String comment) {

        if (ticket.getStatus() == null) return Mono.empty();

        return FlatMapUtil.flatMapMono(
                        () -> this.stageService.readById(access, ticket.getStatus()),
                        status -> this.createActivityInternal(
                                access,
                                ActivityAction.STATUS_CREATE,
                                null,
                                comment,
                                Map.of(
                                        Activity.Fields.ticketId,
                                        ticket.getId(),
                                        Ticket.Fields.status,
                                        IdAndValue.of(status.getId(), status.getName()))))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acStatusCreate"));
    }

    private Mono<Void> acStageUpdate(ProcessorAccess access, Ticket ticket, String comment, ULong oldStageId) {
        return FlatMapUtil.flatMapMono(
                        () -> Mono.zip(
                                this.stageService.readById(access, oldStageId),
                                this.stageService.readById(access, ticket.getStage())),
                        stages -> this.createActivityInternal(
                                access,
                                ActivityAction.STAGE_UPDATE,
                                null,
                                comment,
                                Map.of(
                                        Activity.Fields.ticketId,
                                        ticket.getId(),
                                        ActivityAction.getOldName(Ticket.Fields.stage),
                                        IdAndValue.of(
                                                stages.getT1().getId(),
                                                stages.getT1().getName()),
                                        Ticket.Fields.stage,
                                        IdAndValue.of(
                                                stages.getT2().getId(),
                                                stages.getT2().getName()))))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acStageUpdate"));
    }

    public Mono<Void> acWalkIn(ProcessorAccess access, Ticket ticket, String comment) {
        Map<String, Object> context = Map.of(
                Activity.Fields.ticketId,
                ticket.getId(),
                "user",
                IdAndValue.of(access.getUserId(), access.getUserName()));
        return this.createActivityInternal(access, ActivityAction.WALK_IN, null, comment, context)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acWalkIn"));
    }

    public <T extends BaseContentDto<T>> Mono<Void> acContentCreate(ProcessorAccess access, T content) {
        return this.acContentCreate(access, content, null)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acContentCreate[T]"));
    }

    public <T extends BaseContentDto<T>> Mono<Void> acContentCreate(ProcessorAccess access, T content, String comment) {
        if (content instanceof Note note)
            return this.acNoteAdd(access, note, comment)
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acContentCreate[T, String]"));

        if (content instanceof Task task)
            return this.acTaskCreate(access, task, comment)
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acContentCreate[T, String]"));

        return Mono.<Void>empty()
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acContentCreate[T, String]"));
    }

    public <T extends BaseContentDto<T>> Mono<Void> acContentUpdate(ProcessorAccess access, T content, T updated) {
        return this.acContentUpdate(access, content, updated, null)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acContentCreate[T, T]"));
    }

    public <T extends BaseContentDto<T>> Mono<Void> acContentUpdate(
            ProcessorAccess access, T content, T updated, String comment) {
        if (content instanceof Note note && updated instanceof Note updatedNote)
            return this.acNoteUpdate(access, note, updatedNote, comment)
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acContentUpdate[T, T, String]"));

        if (content instanceof Task task && updated instanceof Task updatedTask)
            return this.acTaskUpdate(access, task, updatedTask, comment)
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acContentUpdate[T, T, String]"));

        return Mono.<Void>empty()
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acContentUpdate[T, T, String]"));
    }

    private Map<String, Object> buildContentContext(BaseContentDto<?> content) {
        return switch (content.getContentEntitySeries()) {
            case TICKET -> Map.of(Activity.Fields.ticketId, content.getTicketId());
            case OWNER -> Map.of(Activity.Fields.ownerId, content.getOwnerId());
            case USER -> Map.of(Activity.Fields.userId, content.getUserId());
        };
    }

    public Mono<Void> acTaskCreate(ProcessorAccess access, Task task, String comment) {
        return this.createActivityInternal(
                        access,
                        ActivityAction.TASK_CREATE,
                        task.getCreatedAt(),
                        comment,
                        this.buildTaskActivityContext(task))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acTaskCreate"));
    }

    private Map<String, Object> buildTaskActivityContext(Task task) {
        return CollectionUtil.merge(
                this.buildContentContext(task),
                Map.of(Activity.Fields.taskId, task.getId(), ActivityAction.getClassName(Task.class), task));
    }

    private Map<String, Object> buildReminderSetContext(Task task) {
        return CollectionUtil.merge(
                this.buildContentContext(task),
                Map.of(
                        Activity.Fields.taskId,
                        task.getId(),
                        Task.Fields.nextReminder,
                        task.getNextReminder(),
                        ActivityAction.getClassName(Task.class),
                        task));
    }

    private Map<String, Object> buildTaskUpdateContext(Task task, Object updatedNode, Object oldNode, Object diffNode) {
        return CollectionUtil.merge(
                this.buildContentContext(task),
                Map.of(
                        Activity.Fields.taskId,
                        task.getId(),
                        ActivityAction.getClassName(Task.class),
                        updatedNode,
                        ActivityAction.getOldName(Task.class),
                        oldNode,
                        ActivityAction.getDiffName(Task.class),
                        diffNode));
    }

    private Map<String, Object> buildNoteContentContext(Note note) {
        return CollectionUtil.merge(
                this.buildContentContext(note),
                Map.of(Activity.Fields.noteId, note.getId(), ActivityAction.getClassName(Note.class), note));
    }

    private Map<String, Object> buildNoteUpdateContext(Note note, Object updatedNode, Object oldNode, Object diffNode) {
        return CollectionUtil.merge(
                this.buildContentContext(note),
                Map.of(
                        Activity.Fields.noteId,
                        note.getId(),
                        ActivityAction.getClassName(Note.class),
                        updatedNode,
                        ActivityAction.getOldName(Note.class),
                        oldNode,
                        ActivityAction.getDiffName(Note.class),
                        diffNode));
    }

    public Mono<Void> acTaskUpdate(ProcessorAccess access, Task task, Task updated, String comment) {
        return FlatMapUtil.flatMapMono(
                () -> Mono.zip(updated.toJsonNodeAsync(), task.toJsonNodeAsync()),
                uTask -> this.extractDifference(uTask.getT1(), uTask.getT2())
                        .switchIfEmpty(Mono.just(FACTORY.objectNode())),
                (uTask, dTask) -> this.createActivityInternal(
                        access,
                        ActivityAction.TASK_UPDATE,
                        updated.getUpdatedAt(),
                        comment,
                        this.buildTaskUpdateContext(task, uTask.getT1(), uTask.getT2(), dTask)));
    }

    public Mono<Void> acTaskComplete(Task task) {
        return this.hasAccess()
                .flatMap(access -> this.acTaskComplete(task, null, access))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acTaskComplete"));
    }

    public Mono<Void> acTaskComplete(Task task, String comment, ProcessorAccess access) {
        return this.createActivityInternal(
                        access,
                        ActivityAction.TASK_COMPLETE,
                        task.getCompletedDate(),
                        comment,
                        this.buildTaskActivityContext(task))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acTaskComplete"));
    }

    public Mono<Void> acTaskCancelled(Task task) {
        return this.hasAccess()
                .flatMap(access -> this.acTaskCancelled(task, null, access))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acTaskCancelled"));
    }

    public Mono<Void> acTaskCancelled(Task task, String comment, ProcessorAccess access) {
        return this.createActivityInternal(
                        access,
                        ActivityAction.TASK_CANCELLED,
                        task.getCancelledDate(),
                        comment,
                        this.buildTaskActivityContext(task))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acTaskCancelled"));
    }

    public <T extends BaseContentDto<T>> Mono<Void> acContentDelete(T content, LocalDateTime deletedDate) {
        return this.acContentDelete(content, null, deletedDate)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acContentDelete"));
    }

    public <T extends BaseContentDto<T>> Mono<Void> acContentDelete(
            T content, String comment, LocalDateTime deletedDate) {
        if (content instanceof Note note) return this.acNoteDelete(note, comment, deletedDate);

        if (content instanceof Task task) return this.acTaskDelete(task, comment, deletedDate);

        return Mono.<Void>empty().contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acContentDelete"));
    }

    public Mono<Void> acTaskDelete(Task task, String comment, LocalDateTime deletedDate) {
        return this.hasAccess()
                .flatMap(access -> this.createActivityInternal(
                        access, ActivityAction.TASK_DELETE, deletedDate, comment, this.buildTaskActivityContext(task)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acTaskDelete"));
    }

    public Mono<Void> acReminderSet(Task task) {
        return this.hasAccess()
                .flatMap(access -> this.acReminderSet(task, null, access))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acReminderSet"));
    }

    public Mono<Void> acReminderSet(Task task, String comment, ProcessorAccess access) {
        return this.createActivityInternal(
                        access,
                        ActivityAction.REMINDER_SET,
                        task.getNextReminder(),
                        comment,
                        this.buildReminderSetContext(task))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acReminderSet"));
    }

    public Mono<Void> acNoteAdd(ProcessorAccess access, Note note, String comment) {
        return this.createActivityInternal(
                        access,
                        ActivityAction.NOTE_ADD,
                        note.getCreatedAt(),
                        comment,
                        this.buildNoteContentContext(note))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acNoteAdd"));
    }

    public Mono<Void> acNoteUpdate(ProcessorAccess access, Note note, Note updated, String comment) {
        return FlatMapUtil.flatMapMonoWithNull(
                () -> Mono.zip(updated.toJsonNodeAsync(), note.toJsonNodeAsync()),
                uNote -> this.extractDifference(uNote.getT1(), uNote.getT2())
                        .switchIfEmpty(Mono.just(FACTORY.objectNode())),
                (uNote, dNote) -> this.createActivityInternal(
                        access,
                        ActivityAction.NOTE_UPDATE,
                        updated.getUpdatedAt(),
                        comment,
                        this.buildNoteUpdateContext(note, uNote.getT1(), uNote.getT2(), dNote)));
    }

    public Mono<Void> acNoteDelete(Note note, String comment, LocalDateTime deletedDate) {
        return this.hasAccess()
                .flatMap(access -> this.createActivityInternal(
                        access, ActivityAction.NOTE_DELETE, deletedDate, comment, this.buildNoteContentContext(note)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acNoteDelete"));
    }

    public Mono<Void> acDocumentUpload(ULong ticketId, String comment, String file) {
        return this.createActivityInternal(
                        ActivityAction.DOCUMENT_UPLOAD,
                        comment,
                        Map.of(Activity.Fields.ticketId, ticketId, "file", file))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acDocumentUpload"));
    }

    public Mono<Void> acDocumentDownload(ULong ticketId, String comment, String file) {
        return this.createActivityInternal(
                        ActivityAction.DOCUMENT_DOWNLOAD,
                        comment,
                        Map.of(Activity.Fields.ticketId, ticketId, "file", file))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acDocumentDownload"));
    }

    public Mono<Void> acDocumentDelete(ULong ticketId, String comment, String file) {
        return this.createActivityInternal(
                        ActivityAction.DOCUMENT_DELETE,
                        comment,
                        Map.of(Activity.Fields.ticketId, ticketId, "file", file))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acDocumentDelete"));
    }

    public Mono<Void> acAssign(ULong ticketId, String comment, String newUser) {
        return this.createActivityInternal(
                        ActivityAction.ASSIGN,
                        comment,
                        Map.of(
                                Activity.Fields.ticketId, ticketId,
                                Ticket.Fields.assignedUserId, newUser))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acAssign"));
    }

    public Mono<Void> acReassign(
            ProcessorAccess access, ULong ticketId, String comment, ULong oldUser, ULong newUser, boolean isAutomatic) {
        return isAutomatic
                ? this.acReassignSystem(access, ticketId, comment, oldUser, newUser)
                : this.acReassign(access, ticketId, comment, oldUser, newUser);
    }

    private Mono<Void> acReassign(
            ProcessorAccess access, ULong ticketId, String comment, ULong oldUser, ULong newUser) {

        return FlatMapUtil.flatMapMono(
                        () -> Mono.zip(
                                super.securityService
                                        .getUserInternal(oldUser.toBigInteger(), null)
                                        .map(this::getUserIdAndValue),
                                super.securityService
                                        .getUserInternal(newUser.toBigInteger(), null)
                                        .map(this::getUserIdAndValue)),
                        users -> this.createActivityInternal(
                                access,
                                ActivityAction.REASSIGN,
                                null,
                                comment,
                                Map.of(
                                        Activity.Fields.ticketId,
                                        ticketId,
                                        ActivityAction.getOldName(Ticket.Fields.assignedUserId),
                                        users.getT1(),
                                        Ticket.Fields.assignedUserId,
                                        users.getT2())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acReassign"));
    }

    private Mono<Void> acReassignSystem(
            ProcessorAccess access, ULong ticketId, String comment, ULong oldUser, ULong newUser) {
        return FlatMapUtil.flatMapMono(
                        () -> Mono.zip(
                                oldUser != null
                                        ? super.securityService
                                                .getUserInternal(oldUser.toBigInteger(), null)
                                                .map(this::getUserIdAndValue)
                                        : Mono.just(IdAndValue.of(oldUser, "")),
                                newUser != null
                                        ? super.securityService
                                                .getUserInternal(newUser.toBigInteger(), null)
                                                .map(this::getUserIdAndValue)
                                        : Mono.just(IdAndValue.of(newUser, ""))),
                        users -> this.createActivityInternal(
                                access,
                                ActivityAction.REASSIGN_SYSTEM,
                                null,
                                comment,
                                Map.of(
                                        Activity.Fields.ticketId,
                                        ticketId,
                                        ActivityAction.getOldName(Ticket.Fields.assignedUserId),
                                        users.getT1(),
                                        Ticket.Fields.assignedUserId,
                                        users.getT2())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acReassignSystem"));
    }

    public Mono<Void> acOwnerShipTransfer(ULong ticketId, String comment, String oldOwner, String newOwner) {
        return this.createActivityInternal(
                        ActivityAction.OWNERSHIP_TRANSFER,
                        comment,
                        Map.of(Activity.Fields.ticketId, ticketId, "oldOwner", oldOwner, "newOwner", newOwner))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acOwnerShipTransfer"));
    }

    public Mono<Void> acCallLog(ProcessorAccess access, Ticket ticket, String comment) {
        return this.createActivityInternal(
                        access,
                        ActivityAction.CALL_LOG,
                        null,
                        comment,
                        Map.of(Activity.Fields.ticketId, ticket.getId(), "customer", ticket.getName()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acCallLog"));
    }

    public Mono<Void> createCallLog(CallLogRequest callLogRequest) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.ticketService.readByIdentity(access, callLogRequest.getTicketId()),
                        (access, ticket) -> {
                            Map<String, Object> context = this.buildCallLogContext(ticket, callLogRequest);
                            return this.createActivityInternal(
                                    access,
                                    ActivityAction.CALL_LOG,
                                    callLogRequest.getCallDate(),
                                    callLogRequest.getComment(),
                                    context);
                        })
                .then()
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.createCallLog"));
    }

    private Map<String, Object> buildCallLogContext(Ticket ticket, CallLogRequest request) {
        Map<String, Object> base = Map.of(Activity.Fields.ticketId, ticket.getId(), "customer", ticket.getName());
        if (request.getIsOutbound() == null
                && request.getCallStatus() == null
                && request.getCallDate() == null
                && request.getCallDuration() == null) {
            return base;
        }
        Map<String, Object> context = new HashMap<>(base);
        if (request.getIsOutbound() != null) context.put("isOutbound", request.getIsOutbound());
        if (request.getCallStatus() != null)
            context.put("callStatus", request.getCallStatus().getLiteral());
        if (request.getCallDate() != null) context.put("callDate", request.getCallDate());
        if (request.getCallDuration() != null) context.put("callDuration", request.getCallDuration());
        return context;
    }

    public Mono<Void> acWhatsapp(ULong ticketId, String comment, String customer) {
        return this.createActivityInternal(
                        ActivityAction.WHATSAPP,
                        comment,
                        Map.of(Activity.Fields.ticketId, ticketId, "customer", customer))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acWhatsapp"));
    }

    public Mono<Void> acEmailSent(ULong ticketId, String comment, String email) {
        return this.createActivityInternal(
                        ActivityAction.EMAIL_SENT, comment, Map.of(Activity.Fields.ticketId, ticketId, "email", email))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acEmailSent"));
    }

    public Mono<Void> acSmsSent(ULong ticketId, String comment, String customer) {
        return this.createActivityInternal(
                        ActivityAction.SMS_SENT,
                        comment,
                        Map.of(Activity.Fields.ticketId, ticketId, "customer", customer))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acSmsSent"));
    }

    public Mono<Void> acFieldUpdate(ULong ticketId, String comment, String fields) {
        return this.createActivityInternal(
                        ActivityAction.FIELD_UPDATE,
                        comment,
                        Map.of(Activity.Fields.ticketId, ticketId, "fields", fields))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acFieldUpdate"));
    }

    public Mono<Void> acCustomFieldUpdate(ULong ticketId, String comment, String field, String value) {
        return this.createActivityInternal(
                        ActivityAction.CUSTOM_FIELD_UPDATE,
                        comment,
                        Map.of(Activity.Fields.ticketId, ticketId, "field", field, "value", value))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acCustomFieldUpdate"));
    }

    public Mono<Void> acLocationUpdate(ULong ticketId, String comment, String location) {
        return this.createActivityInternal(
                        ActivityAction.LOCATION_UPDATE,
                        comment,
                        Map.of(Activity.Fields.ticketId, ticketId, "location", location))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acLocationUpdate"));
    }

    public Mono<Void> acOther(ULong ticketId, String comment, String action) {
        return this.createActivityInternal(
                        ActivityAction.OTHER, comment, Map.of(Activity.Fields.ticketId, ticketId, "action", action))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acOther"));
    }

    private void updateActivityIds(Activity activity, Map<String, Object> context, boolean isDelete) {

        if (isDelete) return;

        this.updateIdFromContext(Activity.Fields.taskId, context, activity::setTaskId);
        this.updateIdFromContext(Activity.Fields.noteId, context, activity::setNoteId);
        this.updateIdFromContext(Ticket.Fields.stage, context, activity::setStageId);
        this.updateIdFromContext(Ticket.Fields.status, context, activity::setStatusId);
    }

    private void updateIdFromContext(String fieldName, Map<String, Object> context, Consumer<ULong> consumer) {
        if (!context.containsKey(fieldName)) return;

        Object fieldValue = context.get(fieldName);

        if (fieldValue instanceof IdAndValue<?, ?> idAndValue) {
            consumer.accept(ULongUtil.valueOf(idAndValue.getId()));
        } else if (fieldValue instanceof ULong uLongValue) {
            consumer.accept(uLongValue);
        }
    }

    private IdAndValue<ULong, String> getUserIdAndValue(User user) {
        return IdAndValue.of(
                ULongUtil.valueOf(user.getId()),
                NameUtil.assembleFullName(user.getFirstName(), user.getMiddleName(), user.getLastName()));
    }

    private Mono<JsonNode> extractDifference(JsonNode incoming, JsonNode existing) {

        if (!incoming.isObject() && !existing.isObject()) return DifferenceExtractor.extract(incoming, existing);

        ObjectNode iObject = incoming.deepCopy();
        ObjectNode eObject = existing.deepCopy();

        sUpdatedFields.forEach(key -> {
            iObject.remove(key);
            eObject.remove(key);
        });

        return DifferenceExtractor.extract(iObject, eObject);
    }

    public Mono<Void> acTagChange(ProcessorAccess access, Ticket ticket, String comment, Tag oldTagEnum) {

        if (oldTagEnum == null) {
            return this.acTagCreate(access, ticket, comment)
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acTagChange"));
        }
        return this.acTagUpdate(access, ticket, comment, oldTagEnum)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acTagChange"));
    }

    private Mono<Void> acTagCreate(ProcessorAccess access, Ticket ticket, String comment) {

        if (ticket.getTag() == null) return Mono.empty();

        return this.createActivityInternal(
                        access,
                        ActivityAction.TAG_CREATE,
                        null,
                        comment,
                        Map.of(Activity.Fields.ticketId, ticket.getId(), Ticket.Fields.tag, ticket.getTag()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acTagCreate"));
    }

    private Mono<Void> acTagUpdate(ProcessorAccess access, Ticket ticket, String comment, Tag oldTag) {

        return this.createActivityInternal(
                        access,
                        ActivityAction.TAG_UPDATE,
                        null,
                        comment,
                        Map.of(
                                Activity.Fields.ticketId,
                                ticket.getId(),
                                ActivityAction.getOldName(Ticket.Fields.tag),
                                oldTag,
                                Ticket.Fields.tag,
                                ticket.getTag()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acTagUpdate"));
    }
}
