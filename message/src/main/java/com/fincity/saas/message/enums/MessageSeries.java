package com.fincity.saas.message.enums;

import static com.fincity.saas.message.jooq.Tables.MESSAGE_CALLS;
import static com.fincity.saas.message.jooq.Tables.MESSAGE_EXOTEL_CALLS;
import static com.fincity.saas.message.jooq.Tables.MESSAGE_MESSAGES;
import static com.fincity.saas.message.jooq.Tables.MESSAGE_WHATSAPP_BUSINESS_ACCOUNTS;
import static com.fincity.saas.message.jooq.Tables.MESSAGE_WHATSAPP_MESSAGES;
import static com.fincity.saas.message.jooq.Tables.MESSAGE_WHATSAPP_PHONE_NUMBERS;
import static com.fincity.saas.message.jooq.Tables.MESSAGE_WHATSAPP_TEMPLATES;

import com.fincity.saas.message.dto.call.Call;
import com.fincity.saas.message.dto.call.provider.exotel.ExotelCall;
import com.fincity.saas.message.dto.message.Message;
import com.fincity.saas.message.dto.message.MessageWebhook;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappBusinessAccount;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappMessage;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappPhoneNumber;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappTemplate;
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
    WHATSAPP_MESSAGE("WHATSAPP_MESSAGE", "Whatsapp Message", 5, "whatsapp", MESSAGE_WHATSAPP_MESSAGES),
    WHATSAPP_PHONE_NUMBER(
            "WHATSAPP_PHONE_NUMBER",
            "Whatsapp Phone Number",
            4,
            "whatsapp_phone_number",
            MESSAGE_WHATSAPP_PHONE_NUMBERS),
    WHATSAPP_TEMPLATE("WHATSAPP_TEMPLATE", "Whatsapp Template", 6, "whatsapp_template", MESSAGE_WHATSAPP_TEMPLATES),
    WHATSAPP_BUSINESS_ACCOUNT(
            "WHATSAPP_BUSINESS_ACCOUNT",
            "Whatsapp Business Account",
            7,
            "whatsapp_business_account",
            MESSAGE_WHATSAPP_BUSINESS_ACCOUNTS);

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
            case WHATSAPP_MESSAGE -> WhatsappMessage.class;
            case WHATSAPP_PHONE_NUMBER -> WhatsappPhoneNumber.class;
            case WHATSAPP_TEMPLATE -> WhatsappTemplate.class;
            case WHATSAPP_BUSINESS_ACCOUNT -> WhatsappBusinessAccount.class;
        };
    }
}
