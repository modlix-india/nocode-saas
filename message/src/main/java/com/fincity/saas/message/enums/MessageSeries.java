package com.fincity.saas.message.enums;

import static com.fincity.saas.message.jooq.Tables.MESSAGE_CALLS;
import static com.fincity.saas.message.jooq.Tables.MESSAGE_EXOTEL_CALLS;
import static com.fincity.saas.message.jooq.Tables.MESSAGE_MESSAGES;
import static com.fincity.saas.message.jooq.Tables.MESSAGE_WHATSAPP_MESSAGES;

import com.fincity.saas.message.dto.call.Call;
import com.fincity.saas.message.dto.call.provider.exotel.ExotelCall;
import com.fincity.saas.message.dto.message.Message;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappMessage;
import lombok.Getter;
import org.jooq.EnumType;
import org.jooq.Table;

@Getter
public enum MessageSeries implements EnumType {
    XXX("XXX", "Unknown", 11, "xxx", null),
    CALL("CALL", "Call", 1, "call", MESSAGE_CALLS),
    EXOTEL_CALL("EXOTEL_CALL", "Exotel Call", 2, "exotel_call", MESSAGE_EXOTEL_CALLS),
    MESSAGE("MESSAGE", "Message", 3, "message", MESSAGE_MESSAGES),
    WHATSAPP("WHATSAPP", "Whatsapp", 4, "whatsapp", MESSAGE_WHATSAPP_MESSAGES);

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
            case WHATSAPP -> WhatsappMessage.class;
        };
    }
}
