package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.model.UserResponse;
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
import com.fincity.saas.entity.processor.enums.content.ContentEntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorActivitiesRecord;
import com.fincity.saas.entity.processor.model.common.ActivityObject;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.ticket.TicketRequest;
import com.fincity.saas.entity.processor.service.base.BaseService;
import com.fincity.saas.entity.processor.util.NameUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ActivityService extends BaseService<EntityProcessorActivitiesRecord, Activity, ActivityDAO> {

    private static final List<String> sUpdatedFields = List.of(
            AbstractDTO.Fields.id,
            AbstractDTO.Fields.createdBy,
            AbstractDTO.Fields.createdAt,
            AbstractUpdatableDTO.Fields.updatedBy,
            AbstractUpdatableDTO.Fields.updatedAt,
            BaseDto.Fields.code);

    private StageService stageService;
    private TicketService ticketService;

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

    private Mono<IdAndValue<ULong, String>> getLoggedInUser() {
        return SecurityContextUtil.getUsersContextUser()
                .map(user -> IdAndValue.of(
                        ULongUtil.valueOf(user.getId()),
                        NameUtil.assembleFullName(user.getFirstName(), user.getMiddleName(), user.getLastName())));
    }

    private Mono<IdAndValue<ULong, String>> getActorName(ULong actorId) {

        if (actorId == null || actorId.longValue() <= 0) return this.getLoggedInUser();

        return this.securityService
                .getUserInternal(actorId.toBigInteger())
                .map(this::getUserIdAndValue)
                .switchIfEmpty(this.getLoggedInUser());
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
            Boolean eager,
            List<String> eagerFields) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.ticketService.checkAndUpdateIdentityWithAccess(access, ticket),
                        (access, uTicket) -> super.readPageFilterEager(
                                pageable,
                                addTicketToCondition(access, condition, uTicket.getULongId()),
                                tableFields,
                                eager,
                                eagerFields))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.readPageFilterEager"));
    }

    private AbstractCondition addTicketToCondition(
            ProcessorAccess access, AbstractCondition condition, ULong ticketId) {
        return ComplexCondition.and(
                super.addAppCodeAndClientCodeToCondition(access, condition),
                FilterCondition.make(Activity.Fields.ticketId, ticketId).setOperator(FilterConditionOperator.EQUALS));
    }

    private Mono<Void> createActivityInternal(ActivityAction action, String comment, Map<String, Object> context) {
        return this.hasAccess().flatMap(access -> this.createActivityInternal(action, null, comment, context, access));
    }

    private Mono<Void> createActivityInternal(
            ActivityAction action, LocalDateTime createOn, String comment, Map<String, Object> context, ProcessorAccess access) {
        if (!context.containsKey(Activity.Fields.ticketId)) return Mono.empty();
        ULong ticketId = ULongUtil.valueOf(context.get(Activity.Fields.ticketId));
        if (ticketId == null || ticketId.longValue() <= 0) return Mono.empty();

        Map<String, Object> mutableContext = new HashMap<>(context);

        return FlatMapUtil.flatMapMono(
                        () -> {
                            mutableContext.put("entity", EntitySeries.TICKET.getPrefix(access.getAppCode()));
                            return Mono.just(mutableContext);
                        },
                        uContext -> this.getActorName(
                                ULongUtil.valueOf(uContext.getOrDefault(Activity.Fields.actorId, null))),
                        (uContext, actor) -> {
                            LocalDateTime activityDate = createOn != null ? createOn : LocalDateTime.now();

                            if (!mutableContext.containsKey("user")) mutableContext.put("user", actor);
                            if (!mutableContext.containsKey("dateTime")) mutableContext.put("dateTime", activityDate);
                            String message = action.formatMessage(mutableContext);

                            Activity activity = Activity.of(
                                            ticketId,
                                            action,
                                            ActivityObject.ofTicket(ticketId, comment, mutableContext))
                                    .setActivityDate(activityDate)
                                    .setDescription(message)
                                    .setActorId(actor.getId());
                            this.updateActivityIds(activity, mutableContext, action.isDelete());
                            return this.createInternal(access, activity).then();
                        })
                .then()
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.createActivityInternal"));
    }

    public Mono<Void> acCreate(Ticket ticket) {
        return this.hasAccess().flatMap( access -> this.acCreate(ticket, null, access)).contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acCreate"));
    }

    public Mono<Void> acCreate(Ticket ticket, String comment, ProcessorAccess access) {
        return this.createActivityInternal(
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
                                        : Map.of(Ticket.Fields.source, ticket.getSource())), access)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acCreate"));
    }

    public Mono<Void> acReInquiry(Ticket ticket, TicketRequest ticketRequest) {
        return this.acReInquiry(ticket, null, ticketRequest)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acReInquiry"));
    }

    public Mono<Void> acReInquiry(Ticket ticket, String comment, TicketRequest ticketRequest) {
        return this.createActivityInternal(
                        ActivityAction.RE_INQUIRY,
                        comment,
                        Map.of(
                                Activity.Fields.ticketId,
                                ticket.getId(),
                                Ticket.Fields.source,
                                ticketRequest.getSubSource() != null
                                        ? Map.of(
                                                Ticket.Fields.source,
                                                ticketRequest.getSource(),
                                                Ticket.Fields.subSource,
                                                ticketRequest.getSubSource())
                                        : Map.of(Ticket.Fields.source, ticketRequest.getSource())))
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

    public Mono<Void> acStageStatus(Ticket ticket, String comment, ULong oldStageId) {

        if (oldStageId == null || ticket.getStage().equals(oldStageId)) return this.acStatusCreate(ticket, comment);

        return Mono.when(this.acStatusCreate(ticket, comment), this.acStageUpdate(ticket, comment, oldStageId))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acStageStatus"));
    }

    public Mono<Void> acStatusCreate(Ticket ticket, String comment) {
        return FlatMapUtil.flatMapMono(
                        () -> this.stageService.readByIdInternal(ticket.getStatus()),
                        status -> this.createActivityInternal(
                                ActivityAction.STATUS_CREATE,
                                comment,
                                Map.of(
                                        Activity.Fields.ticketId,
                                        ticket.getId(),
                                        Ticket.Fields.status,
                                        IdAndValue.of(status.getId(), status.getName()))))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acStatusCreate"));
    }

    public Mono<Void> acStageUpdate(Ticket ticket, String comment, ULong oldStageId) {
        return FlatMapUtil.flatMapMono(
                        () -> Mono.zip(
                                this.stageService.readByIdInternal(oldStageId),
                                this.stageService.readByIdInternal(ticket.getStage())),
                        stages -> this.createActivityInternal(
                                ActivityAction.STAGE_UPDATE,
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

    public <T extends BaseContentDto<T>> Mono<Void> acContentCreate(T content) {

        if (!content.getContentEntitySeries().equals(ContentEntitySeries.TICKET)) return Mono.empty();

        return this.acContentCreate(content, null)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acContentCreate[T]"));
    }

    public <T extends BaseContentDto<T>> Mono<Void> acContentCreate(T content, String comment) {

        if (!content.getContentEntitySeries().equals(ContentEntitySeries.TICKET)) return Mono.empty();

        if (content instanceof Note note)
            return this.acNoteAdd(note, comment)
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acContentCreate[T, String]"));

        if (content instanceof Task task)
            return this.acTaskCreate(task, comment)
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acContentCreate[T, String]"));

        return Mono.<Void>empty()
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acContentCreate[T, String]"));
    }

    public <T extends BaseContentDto<T>> Mono<Void> acContentUpdate(T content, T updated) {

        if (!content.getContentEntitySeries().equals(ContentEntitySeries.TICKET)) return Mono.empty();

        return this.acContentUpdate(content, updated, null)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acContentCreate[T, T]"));
    }

    public <T extends BaseContentDto<T>> Mono<Void> acContentUpdate(T content, T updated, String comment) {

        if (!content.getContentEntitySeries().equals(ContentEntitySeries.TICKET)) return Mono.empty();

        if (content instanceof Note note && updated instanceof Note updatedNote)
            return this.acNoteUpdate(note, updatedNote, comment)
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acContentUpdate[T, T, String]"));

        if (content instanceof Task task && updated instanceof Task updatedTask)
            return this.acTaskUpdate(task, updatedTask, comment)
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acContentUpdate[T, T, String]"));

        return Mono.<Void>empty()
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acContentUpdate[T, T, String]"));
    }

    public Mono<Void> acTaskCreate(Task task, String comment) {
        return this.hasAccess().flatMap(access -> this.createActivityInternal(
                        ActivityAction.TASK_CREATE,
                        task.getCreatedAt(),
                        comment,
                        Map.of(
                                Activity.Fields.ticketId, task.getTicketId(),
                                Activity.Fields.taskId, task.getId()), access))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acTaskCreate"));
    }

    public Mono<Void> acTaskUpdate(Task task, Task updated, String comment) {
        return FlatMapUtil.flatMapMono(
                this::hasAccess,
                access -> Mono.zip(updated.toJsonAsync(), task.toJsonAsync()),
                (access, uTask) ->
                        this.extractDifference(uTask.getT1(), uTask.getT2()).switchIfEmpty(Mono.just(new JsonObject())),
                (access, uTask, dTask) -> this.createActivityInternal(
                        ActivityAction.TASK_UPDATE,
                        updated.getUpdatedAt(),
                        comment,
                        Map.of(
                                Activity.Fields.ticketId,
                                task.getTicketId(),
                                Activity.Fields.taskId,
                                task.getId(),
                                ActivityAction.getClassName(Task.class),
                                uTask.getT1(),
                                ActivityAction.getOldName(Task.class),
                                uTask.getT2(),
                                ActivityAction.getDiffName(Task.class),
                                dTask), access));
    }

    public Mono<Void> acTaskComplete(Task task) {
        return this.hasAccess().flatMap(access -> this.acTaskComplete(task, null, access))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acTaskComplete"));
    }

    public Mono<Void> acTaskComplete(Task task, String comment, ProcessorAccess access) {
        return this.createActivityInternal(
                        ActivityAction.TASK_COMPLETE,
                        task.getCompletedDate(),
                        comment,
                        Map.of(
                                Activity.Fields.ticketId, task.getTicketId(),
                                Activity.Fields.taskId, task.getId()), access)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acTaskComplete"));
    }

    public Mono<Void> acTaskCancelled(Task task) {
        return this.hasAccess().flatMap(access -> this.acTaskCancelled(task, null, access))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acTaskCancelled"));
    }

    public Mono<Void> acTaskCancelled(Task task, String comment, ProcessorAccess access) {
        return this.createActivityInternal(
                        ActivityAction.TASK_CANCELLED,
                        task.getCancelledDate(),
                        comment,
                        Map.of(
                                Activity.Fields.ticketId, task.getTicketId(),
                                Activity.Fields.taskId, task.getId()), access)
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
        return this.hasAccess().flatMap(access -> this.createActivityInternal(
                        ActivityAction.TASK_DELETE,
                        deletedDate,
                        comment,
                        Map.of(
                                Activity.Fields.ticketId,
                                task.getTicketId(),
                                Activity.Fields.taskId,
                                task.getId(),
                                EntitySeries.TASK.getDisplayName(),
                                task), access))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acTaskDelete"));
    }

    public Mono<Void> acReminderSet(Task task) {
        return this.hasAccess().flatMap(access -> this.acReminderSet(task, null, access))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acReminderSet"));
    }

    public Mono<Void> acReminderSet(Task task, String comment, ProcessorAccess access) {
        return this.createActivityInternal(
                        ActivityAction.REMINDER_SET,
                        task.getNextReminder(),
                        comment,
                        Map.of(
                                Activity.Fields.ticketId, task.getTicketId(),
                                Activity.Fields.taskId, task.getId(),
                                Task.Fields.nextReminder, task.getNextReminder()), access)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acReminderSet"));
    }

    public Mono<Void> acNoteAdd(Note note, String comment) {
        return this.hasAccess().flatMap(access -> this.createActivityInternal(
                        ActivityAction.NOTE_ADD,
                        note.getCreatedAt(),
                        comment,
                        Map.of(
                                Activity.Fields.ticketId, note.getTicketId(),
                                Activity.Fields.noteId, note.getId()), access))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acNoteAdd"));
    }

    public Mono<Void> acNoteUpdate(Note note, Note updated, String comment) {
        return FlatMapUtil.flatMapMonoWithNull(
                this::hasAccess,
                access -> Mono.zip(updated.toJsonAsync(), note.toJsonAsync()),
                (access, uNote) ->
                        this.extractDifference(uNote.getT1(), uNote.getT2()).switchIfEmpty(Mono.just(new JsonObject())),
                (access, uNote, dNote) -> this.createActivityInternal(
                        ActivityAction.NOTE_UPDATE,
                        updated.getUpdatedAt(),
                        comment,
                        Map.of(
                                Activity.Fields.ticketId,
                                note.getTicketId(),
                                Activity.Fields.noteId,
                                note.getId(),
                                ActivityAction.getClassName(Note.class),
                                uNote.getT1(),
                                ActivityAction.getOldName(Note.class),
                                uNote.getT2(),
                                ActivityAction.getDiffName(Note.class),
                                dNote), access));
    }

    public Mono<Void> acNoteDelete(Note note, String comment, LocalDateTime deletedDate) {
        return this.hasAccess().flatMap( access -> this.createActivityInternal(
                        ActivityAction.NOTE_DELETE,
                        deletedDate,
                        comment,
                        Map.of(
                                Activity.Fields.ticketId,
                                note.getTicketId(),
                                Activity.Fields.noteId,
                                note.getId(),
                                EntitySeries.NOTE.getDisplayName(),
                                note), access))
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

    public Mono<Void> acReassign(ULong ticketId, String comment, ULong oldUser, ULong newUser) {

        return FlatMapUtil.flatMapMono(
                        () -> Mono.zip(
                                super.securityService
                                        .getUserInternal(oldUser.toBigInteger())
                                        .map(this::getUserIdAndValue),
                                super.securityService
                                        .getUserInternal(newUser.toBigInteger())
                                        .map(this::getUserIdAndValue)),
                        users -> this.createActivityInternal(
                                ActivityAction.REASSIGN,
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

    public Mono<Void> acReassignSystem(ULong ticketId, String comment, String oldUser, String newUser) {
        return this.createActivityInternal(
                        ActivityAction.REASSIGN_SYSTEM,
                        comment,
                        Map.of(
                                Activity.Fields.ticketId,
                                ticketId,
                                ActivityAction.getOldName(Ticket.Fields.assignedUserId),
                                oldUser,
                                Ticket.Fields.assignedUserId,
                                newUser))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acReassignSystem"));
    }

    public Mono<Void> acOwnerShipTransfer(ULong ticketId, String comment, String oldOwner, String newOwner) {
        return this.createActivityInternal(
                        ActivityAction.OWNERSHIP_TRANSFER,
                        comment,
                        Map.of(Activity.Fields.ticketId, ticketId, "oldOwner", oldOwner, "newOwner", newOwner))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acOwnerShipTransfer"));
    }

    public Mono<Void> acCallLog(ULong ticketId, String comment, String customer) {
        return this.createActivityInternal(
                        ActivityAction.CALL_LOG,
                        comment,
                        Map.of(Activity.Fields.ticketId, ticketId, "customer", customer))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActivityService.acCallLog"));
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

    private IdAndValue<ULong, String> getUserIdAndValue(UserResponse user) {
        return IdAndValue.of(
                ULongUtil.valueOf(user.getId()),
                NameUtil.assembleFullName(user.getFirstName(), user.getMiddleName(), user.getLastName()));
    }

    private Mono<JsonElement> extractDifference(JsonElement incoming, JsonElement existing) {

        if (!incoming.isJsonObject() && !existing.isJsonObject())
            return DifferenceExtractor.extract(incoming, existing);

        JsonObject iObject = incoming.deepCopy().getAsJsonObject();
        JsonObject eObject = existing.deepCopy().getAsJsonObject();

        sUpdatedFields.forEach(key -> {
            if (iObject.has(key)) iObject.remove(key);
            if (eObject.has(key)) eObject.remove(key);
        });

        return DifferenceExtractor.extract(iObject, eObject);
    }
}
