package com.fincity.saas.entity.processor.enums;

import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.entity.processor.dto.Activity;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.content.Task;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import org.jooq.EnumType;
import org.jooq.types.ULong;

@Getter
public enum ActivityAction implements EnumType {

    // Deal-related actions
    CREATE(
            "CREATE",
            "$entity from $%s created for $user on $date at $time".formatted(Ticket.Fields.source),
            keys("entity", Ticket.Fields.source)),
    RE_INQUIRY(
            "RE_INQUIRY",
            "$entity re-inquired from $%s for $user on $date at $time".formatted(Ticket.Fields.source),
            keys("entity", Ticket.Fields.source)),
    QUALIFY("QUALIFY", "$entity qualified by $user on $date at $time", keys("entity")),
    DISQUALIFY("DISQUALIFY", "$entity marked as disqualified by $user on $date at $time", keys("entity")),
    DISCARD("DISCARD", "$entity discarded by $user on $date at $time", keys("entity")),
    IMPORT(
            "IMPORT",
            "$entity imported via '$%s' by $user on $date at $time".formatted(Ticket.Fields.source),
            keys("entity", Ticket.Fields.source)),
    STATUS_CREATE(
            "STATUS_CREATE",
            "$%s created by $user on $date at $time".formatted(Ticket.Fields.status),
            keys(Ticket.Fields.status)),
    STAGE_UPDATE(
            "STAGE_UPDATE",
            "Stage moved from '$%s' to '$%s' by $user on $date at $time"
                    .formatted(getOldName(Ticket.Fields.stage), Ticket.Fields.stage),
            keys(getOldName(Ticket.Fields.stage), Ticket.Fields.stage)),

    // Task-related actions
    TASK_CREATE(
            "TASK_CREATE",
            "Task '$%s' was created by $user on $date at $time".formatted(Activity.Fields.taskId),
            keys(Activity.Fields.taskId)),
    TASK_COMPLETE(
            "TASK_COMPLETE",
            "Task '$%s' was marked as completed by $user on $date at $time".formatted(Activity.Fields.taskId),
            keys(Activity.Fields.taskId)),
    TASK_CANCELLED(
            "TASK_CANCELLED",
            "Task '$%s' was marked as cancelled by $user on $date at $time".formatted(Activity.Fields.taskId),
            keys(Activity.Fields.taskId)),
    TASK_DELETE(
            "TASK_DELETE",
            "Task '$%s' was deleted by $user on $date at $time".formatted(Activity.Fields.taskId),
            keys(Activity.Fields.taskId)),
    REMINDER_SET(
            "REMINDER_SET",
            "Reminder for date $%s, set for $%s by $user on $date at $time"
                    .formatted(Task.Fields.nextReminder, Activity.Fields.taskId),
            keys(Task.Fields.nextReminder, Activity.Fields.taskId)),

    // Document actions
    DOCUMENT_UPLOAD("DOCUMENT_UPLOAD", "Document '$file' uploaded by $user on $date at $time", keys("file")),
    DOCUMENT_DOWNLOAD("DOCUMENT_DOWNLOAD", "Document '$file' downloaded by $user on $date at $time", keys("file")),
    DOCUMENT_DELETE("DOCUMENT_DELETE", "Document '$file' deleted by $user on $date at $time", keys("file")),

    // Note actions
    NOTE_ADD(
            "NOTE_ADD",
            "Note $%s added by $user on $date at $time".formatted(Activity.Fields.noteId),
            keys(Activity.Fields.noteId)),
    NOTE_DELETE(
            "NOTE_DELETE",
            "Note $%s deleted by $user on $date at $time".formatted(Activity.Fields.noteId),
            keys(Activity.Fields.noteId)),

    // Assignment actions
    ASSIGN(
            "ASSIGN",
            "$entity was assigned to $%s by $user on $date at $time".formatted(Ticket.Fields.assignedUserId),
            keys("entity", Ticket.Fields.assignedUserId)),
    REASSIGN(
            "REASSIGN",
            "$entity was reassigned from $%s to $%s by $user on $date at $time"
                    .formatted(getOldName(Ticket.Fields.assignedUserId), Ticket.Fields.assignedUserId),
            keys("entity", getOldName(Ticket.Fields.assignedUserId), Ticket.Fields.assignedUserId)),
    REASSIGN_SYSTEM(
            "REASSIGN_SYSTEM",
            "$entity reassigned from $%s to $%s due to availability rule by $user on $date at $time"
                    .formatted(getOldName(Ticket.Fields.assignedUserId), Ticket.Fields.assignedUserId),
            keys("entity", getOldName(Ticket.Fields.assignedUserId), Ticket.Fields.assignedUserId)),
    OWNERSHIP_TRANSFER(
            "OWNERSHIP_TRANSFER",
            "Ownership transferred from $%s to $%s by $user on $date at $time"
                    .formatted(getOldName(AbstractDTO.Fields.createdBy), AbstractDTO.Fields.createdBy),
            keys(getOldName(AbstractDTO.Fields.createdBy), AbstractDTO.Fields.createdBy)),

    // Communication actions
    CALL_LOG("CALL_LOG", "Call with $customer logged by $user on $date at $time", keys("customer")),
    WHATSAPP("WHATSAPP", "WhatsApp message sent to $customer by $user on $date at $time", keys("customer")),
    EMAIL_SENT("EMAIL_SENT", "Email sent to $email by $user on $date at $time", keys("email")),
    SMS_SENT("SMS_SENT", "SMS sent to $customer by $user on $date at $time", keys("customer")),

    // Field update actions
    FIELD_UPDATE("FIELD_UPDATE", "$fields by $user on $date at $time", keys("fields")),
    CUSTOM_FIELD_UPDATE(
            "CUSTOM_FIELD_UPDATE",
            "Custom field '$field' updated to '$value' by $user on $date at $time",
            keys("field", "value")),
    LOCATION_UPDATE("LOCATION_UPDATE", "Location updated to '$location' by $user on $date at $time", keys("location")),

    // Other
    OTHER("OTHER", "$action performed on $entity by $user on $date at $time", keys("action", "entity"));

    private final String literal;
    private final String template;
    private final Set<String> contextKeys;

    private final Map<String, String> keyAccessors = new HashMap<>();

    ActivityAction(String literal, String template, Set<String> contextKeys) {
        this.literal = literal;
        this.template = template;
        this.contextKeys = contextKeys;
    }

    private static Set<String> keys(String... specific) {

        final Set<String> commonKeys = Set.of("user", "date", "time");

        if (specific == null || specific.length == 0) return commonKeys;

        Set<String> result = new HashSet<>(commonKeys);
        result.addAll(Set.of(specific));
        return Set.copyOf(result);
    }

    public static String getOldName(String fieldName) {
        return "_" + fieldName;
    }

    @Override
    public String getLiteral() {
        return literal;
    }

    @Override
    public String getName() {
        return null;
    }

    public boolean hasEntityKey() {
        return this.contextKeys.contains("entity");
    }

    public String formatMessage(Map<String, Object> context) {
        String formattedMessage = template;

        for (Map.Entry<String, Object> entry : context.entrySet()) {
            String key = entry.getKey();
            String value = this.getValue(entry.getValue());
            formattedMessage = formattedMessage.replace("$" + key, value);
        }

        return formattedMessage;
    }

    private String getValue(Object value) {
        return switch (value) {
            case Map<?, ?> map when map.size() <= 2 && !map.isEmpty() -> formatMapEntries(map);
            case IdAndValue<?, ?> idAndValue -> String.valueOf(idAndValue.getValue());
            case ULong ulong -> String.valueOf(ulong.longValue());
            default -> String.valueOf(value);
        };
    }

    private String formatMapEntries(Map<?, ?> map) {
        return map.entrySet().stream()
                .limit(2)
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining(", "));
    }
}
