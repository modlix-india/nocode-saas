package com.fincity.saas.entity.processor.enums;

import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.entity.processor.dto.Activity;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.content.Task;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
            "$entity from $%s created for $user.".formatted(Ticket.Fields.source),
            keys("entity", Ticket.Fields.source)),
    RE_INQUIRY(
            "RE_INQUIRY",
            "$entity re-inquired from $%s for $user.".formatted(Ticket.Fields.source),
            keys("entity", Ticket.Fields.source)),
    QUALIFY("QUALIFY", "$entity qualified by $user.", keys("entity")),
    DISQUALIFY("DISQUALIFY", "$entity marked as disqualified by $user.", keys("entity")),
    DISCARD("DISCARD", "$entity discarded by $user.", keys("entity")),
    IMPORT(
            "IMPORT",
            "$entity imported via $%s by $user.".formatted(Ticket.Fields.source),
            keys("entity", Ticket.Fields.source)),
    STATUS_CREATE("STATUS_CREATE", "$%s created by $user.".formatted(Ticket.Fields.status), keys(Ticket.Fields.status)),
    STAGE_UPDATE(
            "STAGE_UPDATE",
            "Stage moved from $%s to $%s by $user.".formatted(getOldName(Ticket.Fields.stage), Ticket.Fields.stage),
            keys(getOldName(Ticket.Fields.stage), Ticket.Fields.stage)),

    // Task-related actions
    TASK_CREATE(
            "TASK_CREATE",
            "Task $%s was created by $user.".formatted(Activity.Fields.taskId),
            keys(Activity.Fields.taskId)),
    TASK_COMPLETE(
            "TASK_COMPLETE",
            "Task $%s was marked as completed by $user.".formatted(Activity.Fields.taskId),
            keys(Activity.Fields.taskId)),
    TASK_CANCELLED(
            "TASK_CANCELLED",
            "Task $%s was marked as cancelled by $user.".formatted(Activity.Fields.taskId),
            keys(Activity.Fields.taskId)),
    TASK_DELETE(
            "TASK_DELETE",
            "Task $%s was deleted by $user.".formatted(Activity.Fields.taskId),
            keys(Activity.Fields.taskId)),
    REMINDER_SET(
            "REMINDER_SET",
            "Reminder for date $%s, set for $%s by $user.".formatted(Task.Fields.nextReminder, Activity.Fields.taskId),
            keys(Task.Fields.nextReminder, Activity.Fields.taskId)),

    // Document actions
    DOCUMENT_UPLOAD("DOCUMENT_UPLOAD", "Document $file uploaded by $user.", keys("file")),
    DOCUMENT_DOWNLOAD("DOCUMENT_DOWNLOAD", "Document $file downloaded by $user.", keys("file")),
    DOCUMENT_DELETE("DOCUMENT_DELETE", "Document $file deleted by $user.", keys("file")),

    // Note actions
    NOTE_ADD("NOTE_ADD", "Note $%s added by $user.".formatted(Activity.Fields.noteId), keys(Activity.Fields.noteId)),
    NOTE_DELETE(
            "NOTE_DELETE",
            "Note $%s deleted by $user.".formatted(Activity.Fields.noteId),
            keys(Activity.Fields.noteId)),

    // Assignment actions
    ASSIGN(
            "ASSIGN",
            "$entity was assigned to $%s by $user.".formatted(Ticket.Fields.assignedUserId),
            keys("entity", Ticket.Fields.assignedUserId)),
    REASSIGN(
            "REASSIGN",
            "$entity was reassigned from $%s to $%s by $user."
                    .formatted(getOldName(Ticket.Fields.assignedUserId), Ticket.Fields.assignedUserId),
            keys("entity", getOldName(Ticket.Fields.assignedUserId), Ticket.Fields.assignedUserId)),
    REASSIGN_SYSTEM(
            "REASSIGN_SYSTEM",
            "$entity reassigned from $%s to $%s due to availability rule by $user."
                    .formatted(getOldName(Ticket.Fields.assignedUserId), Ticket.Fields.assignedUserId),
            keys("entity", getOldName(Ticket.Fields.assignedUserId), Ticket.Fields.assignedUserId)),
    OWNERSHIP_TRANSFER(
            "OWNERSHIP_TRANSFER",
            "Ownership transferred from $%s to $%s by $user."
                    .formatted(getOldName(AbstractDTO.Fields.createdBy), AbstractDTO.Fields.createdBy),
            keys(getOldName(AbstractDTO.Fields.createdBy), AbstractDTO.Fields.createdBy)),

    // Communication actions
    CALL_LOG("CALL_LOG", "Call with $customer logged by $user.", keys("customer")),
    WHATSAPP("WHATSAPP", "WhatsApp message sent to $customer by $user.", keys("customer")),
    EMAIL_SENT("EMAIL_SENT", "Email sent to $email by $user.", keys("email")),
    SMS_SENT("SMS_SENT", "SMS sent to $customer by $user.", keys("customer")),

    // Field update actions
    FIELD_UPDATE("FIELD_UPDATE", "$fields by $user.", keys("fields")),
    CUSTOM_FIELD_UPDATE(
            "CUSTOM_FIELD_UPDATE", "Custom field $field updated to $value by $user.", keys("field", "value")),
    LOCATION_UPDATE("LOCATION_UPDATE", "Location updated to $location by $user.", keys("location")),

    // Other
    OTHER("OTHER", "$action performed on $entity by $user.", keys("action", "entity"));

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

        final Set<String> commonKeys = Set.of("user");

        if (specific == null || specific.length == 0) return commonKeys;

        Set<String> result = new HashSet<>(commonKeys);
        result.addAll(Set.of(specific));
        return Set.copyOf(result);
    }

    public static String getOldName(String fieldName) {
        return "_" + fieldName;
    }

    public List<ActivityAction> getDeleteActions() {
        return List.of(TASK_DELETE, DOCUMENT_DELETE, NOTE_DELETE);
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

    public boolean isDelete() {
        return this.getDeleteActions().contains(this);
    }

    public String formatMessage(Map<String, Object> context) {
        String formattedMessage = template;

        for (Map.Entry<String, Object> entry : context.entrySet())
            formattedMessage = formattedMessage.replace(
                    "$" + entry.getKey(), this.formatMarkdown(entry.getKey(), this.getValue(entry.getValue())));

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

    private String formatMarkdown(String key, String value) {
        if (key.contains("id")) return this.mdCode(value);

        if (key.equals("user")) return this.mdItalics(this.mdBold(value));

        return this.mdBold(value);
    }

    private String mdCode(String value) {
        return "`" + value + "`";
    }

    private String mdBold(String value) {
        return "**" + value + "**";
    }

    private String mdItalics(String value) {
        return "*" + value + "*";
    }
}
