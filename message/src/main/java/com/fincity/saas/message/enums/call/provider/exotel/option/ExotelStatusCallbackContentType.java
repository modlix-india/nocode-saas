package com.fincity.saas.message.enums.call.provider.exotel.option;

import lombok.Getter;

@Getter
public enum ExotelStatusCallbackContentType implements ExotelOption<ExotelStatusCallbackContentType> {
    FORM("FORM", "multipart/form-data", true),
    JSON("JSON", "application/json", false);

    private final String literal;
    private final String exotelName;
    private final boolean isDefault;

    ExotelStatusCallbackContentType(String literal, String exotelName, boolean isDefault) {
        this.literal = literal;
        this.exotelName = exotelName;
        this.isDefault = isDefault;
    }

    public static ExotelStatusCallbackContentType getDefault() {
        return ExotelOption.getDefault(ExotelStatusCallbackContentType.class);
    }

    public static ExotelStatusCallbackContentType getByName(String name) {
        return ExotelOption.getByName(ExotelStatusCallbackContentType.class, name);
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
