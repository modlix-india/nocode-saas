package com.fincity.saas.message.enums;

import com.fincity.saas.message.dto.call.Call;
import com.fincity.saas.message.dto.call.provider.exotel.ExotelCall;
import lombok.Getter;
import org.jooq.EnumType;
import org.jooq.Table;

@Getter
public enum MessageSeries implements EnumType {
    XXX("XXX", "Unknown", 11, "xxx", null),
    CALL("CALL", "Call", 1, "call", null),
    EXOTEL_CALL("EXOTEL_CALL", "Exotel Call", 2, "exotel_call", null);

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
        };
    }
}
