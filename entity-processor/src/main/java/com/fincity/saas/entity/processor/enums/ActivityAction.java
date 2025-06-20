package com.fincity.saas.entity.processor.enums;

import com.fincity.saas.entity.processor.model.common.IdAndValue;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum ActivityAction implements EnumType {

    // Deal-related actions
    CREATE("CREATE", "$entity from $source created for $user on $date at $time", keys("entity", "source")),
    RE_INQUIRY("RE_INQUIRY", "$entity re-inquired from $source for $user on $date at $time", keys("entity", "source")),
    QUALIFY("QUALIFY", "$entity qualified by $user on $date at $time", keys("entity")),
    DISQUALIFY("DISQUALIFY", "$entity marked as disqualified by $user on $date at $time", keys("entity")),
    DISCARD("DISCARD", "$entity discarded by $user on $date at $time", keys("entity")),
    IMPORT("IMPORT", "$entity imported via '$source' by $user on $date at $time", keys("entity", "source")),
    STATUS_CREATE("STATUS_CREATE", "$status created by $user on $date at $time", keys("status")),
    STAGE_UPDATE(
            "STAGE_UPDATE",
            "Stage moved from '$oldStage' to '$newStage' by $user on $date at $time",
            keys("oldStage", "newStage")),

    // Task-related actions
    TASK_CREATE("TASK_CREATE", "Task '$task' was created by $user on $date at $time", keys("task")),
    TASK_COMPLETE("TASK_COMPLETE", "Task '$task' was marked as completed by $user on $date at $time", keys("task")),
    TASK_CANCELLED("TASK_CANCELLED", "Task '$task' was marked as cancelled by $user on $date at $time", keys("task")),
    TASK_DELETE("TASK_DELETE", "Task '$task' was deleted by $user on $date at $time", keys("task")),
    REMINDER_SET(
            "REMINDER_SET",
            "Reminder for date $reminderDate, set for $task by $user on $date at $time",
            keys("reminderDate", "task")),

    // Document actions
    DOCUMENT_UPLOAD("DOCUMENT_UPLOAD", "Document '$file' uploaded by $user on $date at $time", keys("file")),
    DOCUMENT_DOWNLOAD("DOCUMENT_DOWNLOAD", "Document '$file' downloaded by $user on $date at $time", keys("file")),
    DOCUMENT_DELETE("DOCUMENT_DELETE", "Document '$file' deleted by $user on $date at $time", keys("file")),

    // Note actions
    NOTE_ADD("NOTE_ADD", "Note $note added by $user on $date at $time", keys("note")),
    NOTE_DELETE("NOTE_DELETE", "Note $note deleted by $user on $date at $time", keys("note")),

    // Assignment actions
    ASSIGN("ASSIGN", "$entity was assigned to $newUser by $user on $date at $time", keys("entity", "newUser")),
    REASSIGN(
            "REASSIGN",
            "$entity was reassigned from $oldUser to $newUser by $user on $date at $time",
            keys("entity", "oldUser", "newUser")),
    REASSIGN_SYSTEM(
            "REASSIGN_SYSTEM",
            "$entity reassigned from $oldUser to $newUser due to availability rule by $user on $date at $time",
            keys("entity", "oldUser", "newUser")),
    OWNERSHIP_TRANSFER(
            "OWNERSHIP_TRANSFER",
            "Ownership transferred from $oldOwner to $newOwner by $user on $date at $time",
            keys("oldOwner", "newOwner")),

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
        StringBuilder msgBuilder = new StringBuilder(template);

        context.forEach((key, value) -> {
            int index;
            while ((index = msgBuilder.indexOf("$" + key)) != -1) {
                msgBuilder.replace(index, index + key.length() + 1, this.getValue(value));
            }
        });

        return msgBuilder.toString();
    }

    private String getValue(Object value) {
        return switch (value) {
            case Map<?, ?> map when map.size() <= 2 && !map.isEmpty() -> formatMapEntries(map);
            case IdAndValue<?, ?> idAndValue -> String.valueOf(idAndValue.getValue());
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
