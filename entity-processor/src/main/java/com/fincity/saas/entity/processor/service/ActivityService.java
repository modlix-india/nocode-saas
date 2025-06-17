package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.entity.processor.dao.ActivityDAO;
import com.fincity.saas.entity.processor.dto.Activity;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.content.Note;
import com.fincity.saas.entity.processor.dto.content.Task;
import com.fincity.saas.entity.processor.enums.ActivityAction;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorActivitiesRecord;
import com.fincity.saas.entity.processor.model.common.ActivityObject;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.model.request.TicketRequest;
import com.fincity.saas.entity.processor.service.base.BaseService;
import com.fincity.saas.entity.processor.util.NameUtil;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ActivityService extends BaseService<EntityProcessorActivitiesRecord, Activity, ActivityDAO> {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("hh:mm a");
    private StageService stageService;

    @Lazy
    @Autowired
    private void setStageService(StageService stageService) {
        this.stageService = stageService;
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
        if (!context.containsKey("ticketId")) return Mono.empty();
        ULong ticketId = ULongUtil.valueOf(context.get("ticketId"));
        if (ticketId == null || ticketId.longValue() <= 0) return Mono.empty();

        Map<String, Object> mutableContext = new HashMap<>(context);

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> {
                            mutableContext.put("entity", EntitySeries.TICKET.getPrefix(access.getAppCode()));
                            return Mono.just(mutableContext);
                        },
                        (access, uContext) ->
                                this.getActorName(ULongUtil.valueOf(uContext.getOrDefault("actorId", null))),
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
                        "ticketId",
                        ticket.getId(),
                        "source",
                        ticket.getSubSource() != null
                                ? Map.of("source", ticket.getSource(), "subSource", ticket.getSubSource())
                                : Map.of("source", ticket.getSource())));
    }

    public Mono<Void> acReInquiry(Ticket ticket, TicketRequest ticketRequest) {
        return this.acReInquiry(ticket, null, ticketRequest);
    }

    public Mono<Void> acReInquiry(Ticket ticket, String comment, TicketRequest ticketRequest) {
        return this.createActivityInternal(
                ActivityAction.RE_INQUIRY,
                comment,
                Map.of(
                        "ticketId",
                        ticket.getId(),
                        "source",
                        ticketRequest.getSubSource() != null
                                ? Map.of("source", ticketRequest.getSource(), "subSource", ticketRequest.getSubSource())
                                : Map.of("source", ticketRequest.getSource())));
    }

    public Mono<Void> acQualify(ULong ticketId, String comment) {
        return this.createActivityInternal(ActivityAction.QUALIFY, comment, Map.of("ticketId", ticketId));
    }

    public Mono<Void> acDisqualify(ULong ticketId, String comment) {
        return this.createActivityInternal(ActivityAction.DISQUALIFY, comment, Map.of("ticketId", ticketId));
    }

    public Mono<Void> acDiscard(ULong ticketId, String comment) {
        return this.createActivityInternal(ActivityAction.DISCARD, comment, Map.of("ticketId", ticketId));
    }

    public Mono<Void> acImport(ULong ticketId, String comment, String source) {
        return this.createActivityInternal(
                ActivityAction.IMPORT,
                comment,
                Map.of(
                        "ticketId", ticketId,
                        "source", source));
    }

    public Mono<Void> acStatusCreate(ULong ticketId, String comment, ULong statusId) {
        return FlatMapUtil.flatMapMono(
                () -> this.stageService.readByIdInternal(statusId),
                status -> this.createActivityInternal(
                        ActivityAction.STATUS_CREATE,
                        comment,
                        Map.of("ticketId", ticketId, "status", IdAndValue.of(status.getId(), status.getName()))));
    }

    public Mono<Void> acStageUpdate(ULong ticketId, String comment, ULong oldStageId, ULong newStageId) {
        return FlatMapUtil.flatMapMono(
                () -> Mono.zip(
                        this.stageService.readByIdInternal(oldStageId), this.stageService.readByIdInternal(newStageId)),
                stages -> this.createActivityInternal(
                        ActivityAction.STAGE_UPDATE,
                        comment,
                        Map.of(
                                "ticketId", ticketId,
                                "oldStage",
                                        IdAndValue.of(
                                                stages.getT1().getId(),
                                                stages.getT1().getName()),
                                "newStage",
                                        IdAndValue.of(
                                                stages.getT2().getId(),
                                                stages.getT2().getName()))));
    }

    public Mono<Void> acTaskCreate(Task task, String comment) {
        return this.createActivityInternal(
                ActivityAction.TASK_CREATE,
                task.getCreatedAt(),
                comment,
                Map.of(
                        "ticketId", task.getTicketId(),
                        "task", IdAndValue.of(task.getId(), task.getName())));
    }

    public Mono<Void> acTaskComplete(Task task, String comment) {
        return this.createActivityInternal(
                ActivityAction.TASK_COMPLETE,
                task.getCompletedDate(),
                comment,
                Map.of(
                        "ticketId", task.getTicketId(),
                        "task", IdAndValue.of(task.getId(), task.getName())));
    }

    public Mono<Void> acTaskDelete(Task task, String comment, LocalDateTime deletedDate) {
        return this.createActivityInternal(
                ActivityAction.TASK_DELETE,
                deletedDate,
                comment,
                Map.of(
                        "ticketId", task.getTicketId(),
                        "task", IdAndValue.of(task.getId(), task.getName())));
    }

    public Mono<Void> acReminderSet(Task task, String comment) {
        return this.createActivityInternal(
                ActivityAction.REMINDER_SET,
                task.getNextReminder(),
                comment,
                Map.of(
                        "ticketId", task.getTicketId(),
                        "reminderDate", task.getNextReminder(),
                        "task", IdAndValue.of(task.getId(), task.getName())));
    }

    public Mono<Void> acDocumentUpload(ULong ticketId, String comment, String file) {
        return this.createActivityInternal(
                ActivityAction.DOCUMENT_UPLOAD,
                comment,
                Map.of(
                        "ticketId", ticketId,
                        "file", file));
    }

    public Mono<Void> acDocumentDownload(ULong ticketId, String comment, String file) {
        return this.createActivityInternal(
                ActivityAction.DOCUMENT_DOWNLOAD,
                comment,
                Map.of(
                        "ticketId", ticketId,
                        "file", file));
    }

    public Mono<Void> acDocumentDelete(ULong ticketId, String comment, String file) {
        return this.createActivityInternal(
                ActivityAction.DOCUMENT_DELETE,
                comment,
                Map.of(
                        "ticketId", ticketId,
                        "file", file));
    }

    public Mono<Void> acNoteAdd(Note note, String comment) {
        return this.createActivityInternal(
                ActivityAction.NOTE_ADD,
                note.getCreatedAt(),
                comment,
                Map.of(
                        "ticketId", note.getTicketId(),
                        "note", IdAndValue.of(note.getId(), note.getName())));
    }

    public Mono<Void> acNoteDelete(Note note, String comment, LocalDateTime deletedDate) {
        return this.createActivityInternal(
                ActivityAction.NOTE_DELETE,
                deletedDate,
                comment,
                Map.of(
                        "ticketId", note.getTicketId(),
                        "note", IdAndValue.of(note.getId(), note.getName())));
    }

    public Mono<Void> acAssign(ULong ticketId, String comment, String newUser) {
        return this.createActivityInternal(
                ActivityAction.ASSIGN,
                comment,
                Map.of(
                        "ticketId", ticketId,
                        "newUser", newUser));
    }

    public Mono<Void> acReassign(ULong ticketId, String comment, String oldUser, String newUser) {
        return this.createActivityInternal(
                ActivityAction.REASSIGN,
                comment,
                Map.of(
                        "ticketId", ticketId,
                        "oldUser", oldUser,
                        "newUser", newUser));
    }

    public Mono<Void> acReassignSystem(ULong ticketId, String comment, String oldUser, String newUser) {
        return this.createActivityInternal(
                ActivityAction.REASSIGN_SYSTEM,
                comment,
                Map.of(
                        "ticketId", ticketId,
                        "oldUser", oldUser,
                        "newUser", newUser));
    }

    public Mono<Void> acOwnerShipTransfer(ULong ticketId, String comment, String oldOwner, String newOwner) {
        return this.createActivityInternal(
                ActivityAction.OWNERSHIP_TRANSFER,
                comment,
                Map.of(
                        "ticketId", ticketId,
                        "oldOwner", oldOwner,
                        "newOwner", newOwner));
    }

    public Mono<Void> acCallLog(ULong ticketId, String comment, String customer) {
        return this.createActivityInternal(
                ActivityAction.CALL_LOG,
                comment,
                Map.of(
                        "ticketId", ticketId,
                        "customer", customer));
    }

    public Mono<Void> acWhatsapp(ULong ticketId, String comment, String customer) {
        return this.createActivityInternal(
                ActivityAction.WHATSAPP,
                comment,
                Map.of(
                        "ticketId", ticketId,
                        "customer", customer));
    }

    public Mono<Void> acEmailSent(ULong ticketId, String comment, String email) {
        return this.createActivityInternal(
                ActivityAction.EMAIL_SENT,
                comment,
                Map.of(
                        "ticketId", ticketId,
                        "email", email));
    }

    public Mono<Void> acSmsSent(ULong ticketId, String comment, String customer) {
        return this.createActivityInternal(
                ActivityAction.SMS_SENT,
                comment,
                Map.of(
                        "ticketId", ticketId,
                        "customer", customer));
    }

    public Mono<Void> acFieldUpdate(ULong ticketId, String comment, String fields) {
        return this.createActivityInternal(
                ActivityAction.FIELD_UPDATE,
                comment,
                Map.of(
                        "ticketId", ticketId,
                        "fields", fields));
    }

    public Mono<Void> acCustomFieldUpdate(ULong ticketId, String comment, String field, String value) {
        return this.createActivityInternal(
                ActivityAction.CUSTOM_FIELD_UPDATE,
                comment,
                Map.of(
                        "ticketId", ticketId,
                        "field", field,
                        "value", value));
    }

    public Mono<Void> acLocationUpdate(ULong ticketId, String comment, String location) {
        return this.createActivityInternal(
                ActivityAction.LOCATION_UPDATE,
                comment,
                Map.of(
                        "ticketId", ticketId,
                        "location", location));
    }

    public Mono<Void> acOther(ULong ticketId, String comment, String action) {
        return this.createActivityInternal(
                ActivityAction.OTHER,
                comment,
                Map.of(
                        "ticketId", ticketId,
                        "action", action));
    }
}
