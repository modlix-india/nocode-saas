package com.fincity.saas.commons.core.enums;

import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum NotificationType implements EnumType {
    ALERT("alert", "Alert", "High-priority notifications requiring immediate attention"),
    INFO("info", "Information", "General informational messages"),
    WARNING("warning", "Warning", "Cautionary messages for potential issues"),
    ERROR("error", "Error", "Error notifications for system failures or issues"),
    SUCCESS("success", "Success", "Confirmation messages for completed actions"),
    REMINDER("reminder", "Reminder", "Time-sensitive reminder notifications"),
    SYSTEM("system", "System", "Automated system-generated notifications"),
    PROMOTIONAL("promo", "Promotional", "Marketing or promotional content messages"),
    UPDATE("update", "Update", "System updates or changes notifications"),
    SECURITY("security", "Security", "Security-related alerts and notifications");

    private final String literal;
    private final String displayName;
    private final String description;

    NotificationType(String literal, String displayName, String description) {
        this.literal = literal;
        this.displayName = displayName;
        this.description = description;
    }

    @Override
    public String getLiteral() {
        return this.literal;
    }

    @Override
    public String getName() {
        return this.name();
    }

    public static NotificationType lookupLiteral(String literal) {
        return EnumType.lookupLiteral(NotificationType.class, literal);
    }

    public static boolean isLiteralValid(String literal) {
        return EnumType.lookupLiteral(NotificationType.class, literal) == null;
    }
}
