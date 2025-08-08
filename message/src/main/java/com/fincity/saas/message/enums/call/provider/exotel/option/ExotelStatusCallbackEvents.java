package com.fincity.saas.message.enums.call.provider.exotel.option;

import lombok.Getter;

@Getter
public enum ExotelStatusCallbackEvents implements ExotelOption<ExotelStatusCallbackEvents> {
    TERMINAL("TERMINAL", "terminal", true),
    ANSWERED("ANSWERED", "answered", false);

    private final String literal;
    private final String exotelName;
    private final boolean isDefault;

    ExotelStatusCallbackEvents(String literal, String exotelName, boolean isDefault) {
        this.literal = literal;
        this.exotelName = exotelName;
        this.isDefault = isDefault;
    }

    public static ExotelStatusCallbackEvents getDefault() {
        return ExotelOption.getDefault(ExotelStatusCallbackEvents.class);
    }

    public static ExotelStatusCallbackEvents getByName(String name) {
        return ExotelOption.getByName(ExotelStatusCallbackEvents.class, name);
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
