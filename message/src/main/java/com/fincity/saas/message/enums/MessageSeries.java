package com.fincity.saas.message.enums;

import static com.fincity.saas.message.jooq.Tables.MESSAGE_CALLS;
import static com.fincity.saas.message.jooq.Tables.MESSAGE_EXOTEL_CALLS;
import static com.fincity.saas.message.jooq.Tables.MESSAGE_MESSAGES;
import static com.fincity.saas.message.jooq.Tables.MESSAGE_WHATSAPP_PHONE_NUMBERS;

import com.fincity.saas.message.dto.call.Call;
import com.fincity.saas.message.dto.call.provider.exotel.ExotelCall;
import com.fincity.saas.message.dto.message.Message;
import com.fincity.saas.message.dto.message.MessageWebhook;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappPhoneNumber;
import lombok.Getter;
import org.jooq.EnumType;
import org.jooq.Table;

@Getter
public enum MessageSeries implements EnumType {
    XXX("XXX", "Unknown", 11, "xxx", null),
    CALL("CALL", "Call", 1, "call", MESSAGE_CALLS),
    EXOTEL_CALL("EXOTEL_CALL", "Exotel Call", 2, "exotel_call", MESSAGE_EXOTEL_CALLS),
    MESSAGE("MESSAGE", "Message", 3, "message", MESSAGE_MESSAGES),
    MESSAGE_WEBHOOKS("MESSAGE_WEBHOOKS", "Message Webhooks", 4, "message_webhooks", null),
    WHATSAPP_PHONE_NUMBER(
            "WHATSAPP_PHONE_NUMBER",
            "Whatsapp Phone Number",
            4,
            "whatsapp_phone_number",
            MESSAGE_WHATSAPP_PHONE_NUMBERS);

    // WHATSAPP_MESSAGE (5), WHATSAPP_TEMPLATE (6) and WHATSAPP_BUSINESS_ACCOUNT (7) retired with the
    // Cloud API. Their ordinals are deliberately not reused: existing rows in the retired tables
    // still carry codes built from them, and handing 5 to something new would make two unrelated
    // entities share a code prefix.

    private final String literal;
    private final String displayName;
    private final int value;
    private final String prefix;
    private final Table<?> table;

    MessageSeries(String literal, String displayName, int value, String prefix, Table<?> table) {
        this.literal = literal;
        this.displayName = displayName;
        this.value = value;
        this.prefix = prefix;
        this.table = table;
    }

    public static MessageSeries lookupLiteral(String literal) {
        return EnumType.lookupLiteral(MessageSeries.class, literal);
    }

    @Override
    public String getLiteral() {
        return literal;
    }

    @Override
    public String getName() {
        return this.displayName;
    }

    public Class<?> getDtoClass() {
        return switch (this) {
            case XXX -> null;
            case CALL -> Call.class;
            case EXOTEL_CALL -> ExotelCall.class;
            case MESSAGE -> Message.class;
            case MESSAGE_WEBHOOKS -> MessageWebhook.class;
            case WHATSAPP_PHONE_NUMBER -> WhatsappPhoneNumber.class;
        };
    }
}
