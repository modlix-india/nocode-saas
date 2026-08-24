package com.fincity.saas.entity.processor.enums.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;
import lombok.Getter;
import org.jooq.EnumType;

/**
 * Which channel a stored message is written for.
 *
 * <p>Separate from {@link com.fincity.saas.entity.processor.enums.MessageChannelType}, which is
 * about how a <em>stage rule</em> reaches a lead and still carries Cloud API leftovers like {@code
 * WHATS_APP_TEMPLATE}. This one describes the content itself, and a template-versus-free-form
 * distinction has no meaning on the linked-device protocol.
 *
 * <p>Only {@link #WHATSAPP} is wired. The other two exist because the same library is the obvious
 * home for email and SMS bodies later, and adding them then would otherwise mean a schema change.
 */
@Getter
public enum MessageTemplateChannel implements EnumType {
    WHATSAPP("whatsapp"),
    EMAIL("email"),
    SMS("sms");

    private final String literal;

    MessageTemplateChannel(String literal) {
        this.literal = literal;
    }

    @JsonValue
    public String getValue() {
        return this.literal;
    }

    @JsonCreator
    public static MessageTemplateChannel fromValue(String value) {
        if (value == null || value.isBlank()) return WHATSAPP;
        return MessageTemplateChannel.valueOf(value.toUpperCase(Locale.ROOT));
    }

    @Override
    public String getLiteral() {
        return this.name();
    }

    @Override
    public String getName() {
        return null;
    }
}
