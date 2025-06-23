package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.entity.processor.dao.ActivityDAO;
import com.fincity.saas.entity.processor.dto.Activity;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.content.Note;
import com.fincity.saas.entity.processor.dto.content.Task;
import com.fincity.saas.entity.processor.dto.content.base.BaseContentDto;
import com.fincity.saas.entity.processor.enums.ActivityAction;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorActivitiesRecord;
import com.fincity.saas.entity.processor.model.common.ActivityObject;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.TicketRequest;
import com.fincity.saas.entity.processor.service.base.BaseService;
import com.fincity.saas.entity.processor.util.NameUtil;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

@Service
public class ActivityService extends BaseService<EntityProcessorActivitiesRecord, Activity, ActivityDAO> {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("hh:mm a");

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
                .map(user -> IdAndValue.of(
                        ULongUtil.valueOf(user.get("id")),
                        NameUtil.assembleFullName(user.get("firstName"), user.get("middleName"), user.get("lastName"))))
                .switchIfEmpty(this.getLoggedInUser());
    }

    private String formatDate(LocalDateTime dateTime) {
        return dateTime.format(DATE_FORMAT);
    }

    private String formatTime(LocalDateTime dateTime) {
        return dateTime.format(TIME_FORMAT);
    }

    @Override
    public Mono<Activity> create(Activity activity) {
        return this.createInternal(activity);
    }

    public Mono<Page<Activity>> readPageFilter(Pageable pageable, Identity ticket, AbstractCondition condition) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> this.ticketService.checkAndUpdateIdentityWithAccess(access, ticket),
                (access, uTicket) ->
                        super.readPageFilter(pageable, addTicketToCondition(access, condition, uTicket.getULongId())));
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
                        eagerFields));
    }

    private AbstractCondition addTicketToCondition(
            ProcessorAccess access, AbstractCondition condition, ULong ticketId) {
        return ComplexCondition.and(
                super.addAppCodeAndClientCodeToCondition(access, condition),
                FilterCondition.make(Activity.Fields.ticketId, ticketId).setOperator(FilterConditionOperator.EQUALS));
    }

    protected Mono<Activity> createInternal(Activity activity) {
        return super.hasAccess().flatMap(access -> {
            activity.setAppCode(access.getAppCode());
            activity.setClientCode(access.getClientCode());
            return super.create(activity);
        });
    }

    private Mono<Void> createActivityInternal(ActivityAction action, String comment, Map<String, Object> context) {
        return this.createActivityInternal(action, null, comment, context);
    }

    private Mono<Void> createActivityInternal(
            ActivityAction action, LocalDateTime createOn, String comment, Map<String, Object> context) {
        if (!context.containsKey(Activity.Fields.ticketId)) return Mono.empty();
        ULong ticketId = ULongUtil.valueOf(context.get(Activity.Fields.ticketId));
        if (ticketId == null || ticketId.longValue() <= 0) return Mono.empty();

        Map<String, Object> mutableContext = new HashMap<>(context);

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> {
                            mutableContext.put("entity", EntitySeries.TICKET.getPrefix(access.getAppCode()));
                            return Mono.just(mutableContext);
                        },
                        (access, uContext) -> this.getActorName(
                                ULongUtil.valueOf(uContext.getOrDefault(Activity.Fields.actorId, null))),
                        (access, uContext, actor) -> {
                            LocalDateTime activityDate = createOn != null ? createOn : LocalDateTime.now();

                            if (!mutableContext.containsKey("user")) mutableContext.put("user", actor);
                            if (!mutableContext.containsKey("date"))
                                mutableContext.put("date", this.formatDate(activityDate));
                            if (!mutableContext.containsKey("time"))
                                mutableContext.put("time", this.formatTime(activityDate));
                            String message = action.formatMessage(mutableContext);

                            Activity activity = Activity.of(
                                            ticketId,
                                            action,
                                            ActivityObject.ofTicket(ticketId, comment, mutableContext))
                                    .setActivityDate(activityDate)
                                    .setDescription(message)
                                    .setActorId(actor.getId());
                            this.updateActivityIds(activity, mutableContext);
                            return this.create(activity).then();
                        })
                .then();
    }

    public Mono<Void> acCreate(Ticket ticket) {
        return this.acCreate(ticket, null);
    }

    public Mono<Void> acCreate(Ticket ticket, String comment) {
        return this.createActivityInternal(
                ActivityAction.CREATE,
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
                                : Map.of(Ticket.Fields.source, ticket.getSource())));
    }

    public Mono<Void> acReInquiry(Ticket ticket, TicketRequest ticketRequest) {
        return this.acReInquiry(ticket, null, ticketRequest);
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
                                : Map.of(Ticket.Fields.source, ticketRequest.getSource())));
    }

    public Mono<Void> acQualify(ULong ticketId, String comment) {
        return this.createActivityInternal(ActivityAction.QUALIFY, comment, Map.of(Activity.Fields.ticketId, ticketId));
    }

    public Mono<Void> acDisqualify(ULong ticketId, String comment) {
        return this.createActivityInternal(
                ActivityAction.DISQUALIFY, comment, Map.of(Activity.Fields.ticketId, ticketId));
    }

    public Mono<Void> acDiscard(ULong ticketId, String comment) {
        return this.createActivityInternal(ActivityAction.DISCARD, comment, Map.of(Activity.Fields.ticketId, ticketId));
    }

    public Mono<Void> acImport(ULong ticketId, String comment, String source) {
        return this.createActivityInternal(
                ActivityAction.IMPORT,
                comment,
                Map.of(
                        Activity.Fields.ticketId, ticketId,
                        Ticket.Fields.source, source));
    }

    public Mono<Void> acStageStatus(Ticket ticket, String comment, ULong oldStageId) {

        if (oldStageId == null || ticket.getStage().equals(oldStageId)) return this.acStatusCreate(ticket, comment);

        return Mono.when(this.acStatusCreate(ticket, comment), this.acStageUpdate(ticket, comment, oldStageId));
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
                                IdAndValue.of(status.getId(), status.getName()))));
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
                                        stages.getT1().getId(), stages.getT1().getName()),
                                Ticket.Fields.stage,
                                IdAndValue.of(
                                        stages.getT2().getId(), stages.getT2().getName()))));
    }

    public <T extends BaseContentDto<T>> Mono<Void> acContentCreate(T content) {
        return this.acContentCreate(content, null);
    }

    public <T extends BaseContentDto<T>> Mono<Void> acContentCreate(T content, String comment) {
        if (content instanceof Note note) return this.acNoteAdd(note, comment);

        if (content instanceof Task task) return this.acTaskCreate(task, comment);

        return Mono.empty();
    }

    public Mono<Void> acTaskCreate(Task task, String comment) {
        return this.createActivityInternal(
                ActivityAction.TASK_CREATE,
                task.getCreatedAt(),
                comment,
                Map.of(
                        Activity.Fields.ticketId, task.getTicketId(),
                        Activity.Fields.taskId, task.getId()));
    }

    public Mono<Void> acTaskComplete(Task task) {
        return this.acTaskComplete(task, null);
    }

    public Mono<Void> acTaskComplete(Task task, String comment) {
        return this.createActivityInternal(
                ActivityAction.TASK_COMPLETE,
                task.getCompletedDate(),
                comment,
                Map.of(
                        Activity.Fields.ticketId, task.getTicketId(),
                        Activity.Fields.taskId, task.getId()));
    }

    public Mono<Void> acTaskCancelled(Task task) {
        return this.acTaskCancelled(task, null);
    }

    public Mono<Void> acTaskCancelled(Task task, String comment) {
        return this.createActivityInternal(
                ActivityAction.TASK_CANCELLED,
                task.getCancelledDate(),
                comment,
                Map.of(
                        Activity.Fields.ticketId, task.getTicketId(),
                        Activity.Fields.taskId, task.getId()));
    }

    public <T extends BaseContentDto<T>> Mono<Void> acContentDelete(T content, LocalDateTime deletedDate) {
        return this.acContentDelete(content, null, deletedDate);
    }

    public <T extends BaseContentDto<T>> Mono<Void> acContentDelete(
            T content, String comment, LocalDateTime deletedDate) {
        if (content instanceof Note note) return this.acNoteDelete(note, comment, deletedDate);

        if (content instanceof Task task) return this.acTaskDelete(task, comment, deletedDate);

        return Mono.empty();
    }

    public Mono<Void> acTaskDelete(Task task, String comment, LocalDateTime deletedDate) {
        return this.createActivityInternal(
                ActivityAction.TASK_DELETE,
                deletedDate,
                comment,
                Map.of(
                        Activity.Fields.ticketId, task.getTicketId(),
                        Activity.Fields.taskId, task.getId()));
    }

    public Mono<Void> acReminderSet(Task task) {
        return this.acReminderSet(task, null);
    }

    public Mono<Void> acReminderSet(Task task, String comment) {
        return this.createActivityInternal(
                ActivityAction.REMINDER_SET,
                task.getNextReminder(),
                comment,
                Map.of(
                        Activity.Fields.ticketId, task.getTicketId(),
                        Activity.Fields.taskId, task.getId(),
                        Task.Fields.nextReminder, task.getNextReminder()));
    }

    public Mono<Void> acNoteAdd(Note note, String comment) {
        return this.createActivityInternal(
                ActivityAction.NOTE_ADD,
                note.getCreatedAt(),
                comment,
                Map.of(
                        Activity.Fields.ticketId, note.getTicketId(),
                        Activity.Fields.noteId, note.getId()));
    }

    public Mono<Void> acNoteDelete(Note note, String comment, LocalDateTime deletedDate) {
        return this.createActivityInternal(
                ActivityAction.NOTE_DELETE,
                deletedDate,
                comment,
                Map.of(
                        Activity.Fields.ticketId, note.getTicketId(),
                        Activity.Fields.noteId, note.getId()));
    }

    public Mono<Void> acDocumentUpload(ULong ticketId, String comment, String file) {
        return this.createActivityInternal(
                ActivityAction.DOCUMENT_UPLOAD, comment, Map.of(Activity.Fields.ticketId, ticketId, "file", file));
    }

    public Mono<Void> acDocumentDownload(ULong ticketId, String comment, String file) {
        return this.createActivityInternal(
                ActivityAction.DOCUMENT_DOWNLOAD, comment, Map.of(Activity.Fields.ticketId, ticketId, "file", file));
    }

    public Mono<Void> acDocumentDelete(ULong ticketId, String comment, String file) {
        return this.createActivityInternal(
                ActivityAction.DOCUMENT_DELETE, comment, Map.of(Activity.Fields.ticketId, ticketId, "file", file));
    }

    public Mono<Void> acAssign(ULong ticketId, String comment, String newUser) {
        return this.createActivityInternal(
                ActivityAction.ASSIGN,
                comment,
                Map.of(
                        Activity.Fields.ticketId, ticketId,
                        Ticket.Fields.assignedUserId, newUser));
    }

    public Mono<Void> acReassign(ULong ticketId, String comment, String oldUser, String newUser) {
        return this.createActivityInternal(
                ActivityAction.REASSIGN,
                comment,
                Map.of(
                        Activity.Fields.ticketId,
                        ticketId,
                        ActivityAction.getOldName(Ticket.Fields.assignedUserId),
                        oldUser,
                        Ticket.Fields.assignedUserId,
                        newUser));
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
                        newUser));
    }

    public Mono<Void> acOwnerShipTransfer(ULong ticketId, String comment, String oldOwner, String newOwner) {
        return this.createActivityInternal(
                ActivityAction.OWNERSHIP_TRANSFER,
                comment,
                Map.of(Activity.Fields.ticketId, ticketId, "oldOwner", oldOwner, "newOwner", newOwner));
    }

    public Mono<Void> acCallLog(ULong ticketId, String comment, String customer) {
        return this.createActivityInternal(
                ActivityAction.CALL_LOG, comment, Map.of(Activity.Fields.ticketId, ticketId, "customer", customer));
    }

    public Mono<Void> acWhatsapp(ULong ticketId, String comment, String customer) {
        return this.createActivityInternal(
                ActivityAction.WHATSAPP, comment, Map.of(Activity.Fields.ticketId, ticketId, "customer", customer));
    }

    public Mono<Void> acEmailSent(ULong ticketId, String comment, String email) {
        return this.createActivityInternal(
                ActivityAction.EMAIL_SENT, comment, Map.of(Activity.Fields.ticketId, ticketId, "email", email));
    }

    public Mono<Void> acSmsSent(ULong ticketId, String comment, String customer) {
        return this.createActivityInternal(
                ActivityAction.SMS_SENT, comment, Map.of(Activity.Fields.ticketId, ticketId, "customer", customer));
    }

    public Mono<Void> acFieldUpdate(ULong ticketId, String comment, String fields) {
        return this.createActivityInternal(
                ActivityAction.FIELD_UPDATE, comment, Map.of(Activity.Fields.ticketId, ticketId, "fields", fields));
    }

    public Mono<Void> acCustomFieldUpdate(ULong ticketId, String comment, String field, String value) {
        return this.createActivityInternal(
                ActivityAction.CUSTOM_FIELD_UPDATE,
                comment,
                Map.of(Activity.Fields.ticketId, ticketId, "field", field, "value", value));
    }

    public Mono<Void> acLocationUpdate(ULong ticketId, String comment, String location) {
        return this.createActivityInternal(
                ActivityAction.LOCATION_UPDATE,
                comment,
                Map.of(Activity.Fields.ticketId, ticketId, "location", location));
    }

    public Mono<Void> acOther(ULong ticketId, String comment, String action) {
        return this.createActivityInternal(
                ActivityAction.OTHER, comment, Map.of(Activity.Fields.ticketId, ticketId, "action", action));
    }

    private void updateActivityIds(Activity activity, Map<String, Object> context) {
        this.updateIdFromContext(Activity.Fields.taskId, context, activity::setTaskId);
        this.updateIdFromContext(Activity.Fields.noteId, context, activity::setNoteId);
    }

    private void updateIdFromContext(String fieldName, Map<String, Object> context, Consumer<ULong> consumer) {
        if (!context.containsKey(fieldName)) return;

        Object fieldValue = context.get(fieldName);

        if (fieldValue instanceof IdAndValue<?, ?> idAndValue) {
            consumer.accept(ULongUtil.valueOf(idAndValue.getId()));
        } else if (fieldValue instanceof ULong ulongValue) {
            consumer.accept(ulongValue);
        }
    }
}
