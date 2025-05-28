package com.fincity.saas.notification.enums;

import com.fincity.saas.commons.mongo.util.CloneUtil;
import com.fincity.saas.notification.enums.channel.NotificationChannelType;
import java.util.HashSet;
import java.util.List;
import java.util.function.UnaryOperator;
import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum PreferenceLevel implements EnumType {
    CHANNEL(
            "CHANNEL",
            "Channel",
            Boolean.FALSE,
            List.of(NotificationChannelType.DISABLED.getLiteral()),
            PreferenceLevel::toValidChannelList),
    NOTIFICATION(
            "NOTIFICATION", "Disabled Notification", Boolean.TRUE, List.of(), PreferenceLevel::toValidNotificationList);

    private final String literal;
    private final String displayName;
    private final boolean reverseSave;
    private final List<String> defaultList;
    private final UnaryOperator<List<String>> validator;

    PreferenceLevel(
            String literal,
            String displayName,
            Boolean reverseSave,
            List<String> defaultList,
            UnaryOperator<List<String>> validator) {
        this.literal = literal;
        this.displayName = displayName;
        this.reverseSave = reverseSave;
        this.defaultList = CloneUtil.cloneMapList(defaultList);
        this.validator = validator;
    }

    public static PreferenceLevel lookupLiteral(String literal) {
        return EnumType.lookupLiteral(PreferenceLevel.class, literal);
    }

    private static List<String> toValidChannelList(List<String> preferences) {
        if (preferences.contains(NotificationChannelType.DISABLED.getLiteral())) {
            if (preferences.size() > 1)
                throw new IllegalArgumentException(
                        "Invalid channel preferences, disabled channel can not be set with other channels");
            return List.of(NotificationChannelType.DISABLED.getLiteral());
        }

        return preferences;
    }

    private static List<String> toValidNotificationList(List<String> preferences) {
        return preferences;
    }

    @Override
    public String getLiteral() {
        return this.literal;
    }

    @Override
    public String getName() {
        return null;
    }

    public List<String> toValidList(List<String> preferences) {

        if (preferences == null || preferences.isEmpty()) return this.defaultList;

        return this.validator.apply(preferences);
    }

    public boolean isDefault(List<String> preferences) {
        return new HashSet<>(this.defaultList).containsAll(preferences);
    }
}
