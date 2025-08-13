package com.fincity.saas.message.enums.call.provider.exotel.option;

import lombok.Getter;

@Getter
public enum ExotelDirection implements ExotelOption<ExotelDirection> {
    INBOUND("INBOUND", "inbound", false),
    OUTBOUND_DIAL("OUTBOUND_DIAL", "outbound-dial", false),
    OUTBOUND_API("OUTBOUND_API", "outbound-api", true);

    private final String literal;
    private final String exotelName;
    private final boolean isDefault;

    ExotelDirection(String literal, String exotelName, boolean isDefault) {
        this.literal = literal;
        this.exotelName = exotelName;
        this.isDefault = isDefault;
    }

    public static ExotelDirection getDefault() {
        return ExotelOption.getDefault(ExotelDirection.class);
    }

    public static ExotelDirection getByName(String name) {
        return ExotelOption.getByName(ExotelDirection.class, name);
    }

    public boolean isOutbound() {
        return this == OUTBOUND_DIAL || this == OUTBOUND_API;
    }

    @Override
    public String getLiteral() {
        return literal;
    }

    @Override
    public String getName() {
        return this.exotelName;
    }

    @Override
    public boolean isDefault() {
        return this.isDefault;
    }
}
