package com.fincity.saas.notification.enums.channel;

import com.fincity.saas.notification.model.message.NotificationMessage;
import com.fincity.saas.notification.model.message.channel.EmailMessage;
import com.fincity.saas.notification.model.message.channel.InAppMessage;
import com.fincity.saas.notification.model.message.channel.MobilePushMessage;
import com.fincity.saas.notification.model.message.channel.SmsMessage;
import com.fincity.saas.notification.model.message.channel.WebPushMessage;
import com.fincity.saas.notification.oserver.core.enums.ConnectionType;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum NotificationChannelType implements EnumType {
    DISABLED("DISABLED", null, null),
    EMAIL(
            "EMAIL",
            ConnectionType.MAIL,
            EmailMessage::new,
            NotificationRecipientType.FROM,
            NotificationRecipientType.TO,
            NotificationRecipientType.BCC,
            NotificationRecipientType.CC,
            NotificationRecipientType.REPLY_TO),
    IN_APP("IN_APP", ConnectionType.IN_APP, InAppMessage::new),
    MOBILE_PUSH("MOBILE_PUSH", ConnectionType.MOBILE_PUSH, MobilePushMessage::new),
    WEB_PUSH("WEB_PUSH", ConnectionType.WEB_PUSH, WebPushMessage::new),
    SMS("SMS", ConnectionType.TEXT, SmsMessage::new, NotificationRecipientType.TO);

    private static final Map<ConnectionType, NotificationChannelType> BY_CONNECTION_TYPE = new HashMap<>();

    static {
        for (NotificationChannelType notificationChannelType : values()) {
            BY_CONNECTION_TYPE.put(notificationChannelType.connectionType, notificationChannelType);
        }
    }

    private final String literal;
    private final ConnectionType connectionType;
    private final Supplier<? extends NotificationMessage<?>> messageCreator;
    private final Set<NotificationRecipientType> allowedRecipientTypes;

    NotificationChannelType(
            String literal,
            ConnectionType connectionType,
            Supplier<? extends NotificationMessage<?>> messageCreator,
            NotificationRecipientType... notificationRecipientTypes) {
        this.literal = literal;
        this.connectionType = connectionType;
        this.messageCreator = messageCreator;
        this.allowedRecipientTypes = notificationRecipientTypes == null ? Set.of() : Set.of(notificationRecipientTypes);
    }

    public static NotificationChannelType lookupLiteral(String literal) {
        return EnumType.lookupLiteral(NotificationChannelType.class, literal);
    }

    public static NotificationChannelType getByConnectionType(ConnectionType connectionType) {
        return BY_CONNECTION_TYPE.get(connectionType);
    }

    public static <T> Map<NotificationChannelType, T> getChannelTypeMap(Map<String, T> channelMap) {

        if (channelMap == null || channelMap.isEmpty()) return new EnumMap<>(NotificationChannelType.class);

        return channelMap.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> NotificationChannelType.lookupLiteral(e.getKey()),
                        Map.Entry::getValue,
                        (existing, replacement) -> replacement,
                        () -> new EnumMap<>(NotificationChannelType.class)));
    }

    public static <T> Map<String, T> getChannelMap(Map<NotificationChannelType, T> channelMap) {
        if (channelMap == null || channelMap.isEmpty()) return new HashMap<>();

        return channelMap.entrySet().stream()
                .collect(Collectors.toMap(t -> t.getKey().getLiteral(), Map.Entry::getValue));
    }

    @Override
    public String getLiteral() {
        return literal;
    }

    @Override
    public String getName() {
        return null;
    }

    public String getMqQueueName(String mqExchangeName) {
        return mqExchangeName + "." + this.getLiteral().toLowerCase();
    }

    public boolean hasRecipientType(NotificationRecipientType recipientType) {
        return this.allowedRecipientTypes.contains(recipientType);
    }

    public <T extends NotificationMessage<T>> Supplier<T> getMessageCreator() {
        return (Supplier<T>) this.messageCreator;
    }
}
