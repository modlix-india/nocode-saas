package com.fincity.saas.message.enums.channel;

import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.service.MessageResourceService;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum MessageChannelType implements EnumType {
    DISABLED("DISABLED"),
    CALL("CALL"),
    WHATS_APP("WHATS_APP", MessageRecipientType.PHONE_NUMBER),
    WHATS_APP_TEMPLATE("WHATS_APP_TEMPLATE", MessageRecipientType.PHONE_NUMBER),
    IN_APP("IN_APP"),
    SMS("TEXT", MessageRecipientType.PHONE_NUMBER);

    private final String literal;
    private final Set<MessageRecipientType> allowedRecipientTypes;

    MessageChannelType(String literal, MessageRecipientType... messageRecipientTypes) {
        this.literal = literal;
        this.allowedRecipientTypes = messageRecipientTypes == null ? Set.of() : Set.of(messageRecipientTypes);
    }

    public static MessageChannelType lookupLiteral(String literal) {
        return EnumType.lookupLiteral(MessageChannelType.class, literal);
    }

    public static MessageChannelType getFromConnectionSubType(ConnectionSubType connectionSubType) {

        String name = connectionSubType.name();

        if (!name.startsWith(MessageResourceService.MESSAGE_PREFIX)) return null;

        name = name.substring(MessageResourceService.MESSAGE_PREFIX.length());

        return MessageChannelType.valueOf(name.split("_")[0].toUpperCase());
    }

    public static <T> Map<MessageChannelType, T> getChannelTypeMap(Map<String, T> channelMap) {

        if (channelMap == null || channelMap.isEmpty()) return new EnumMap<>(MessageChannelType.class);

        return channelMap.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> MessageChannelType.lookupLiteral(e.getKey()),
                        Map.Entry::getValue,
                        (existing, replacement) -> replacement,
                        () -> new EnumMap<>(MessageChannelType.class)));
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

    public boolean hasRecipientType(MessageRecipientType recipientType) {
        return this.allowedRecipientTypes.contains(recipientType);
    }
}
