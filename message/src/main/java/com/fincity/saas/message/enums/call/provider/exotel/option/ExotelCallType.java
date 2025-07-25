package com.fincity.saas.message.enums.call.provider.exotel.option;

import lombok.Getter;

@Getter
public enum ExotelCallType implements ExotelOption<ExotelCallType> {
    TRANS("TRANS", "trans", true);

    private final String literal;
    private final String exotelName;
    private final boolean isDefault;

    ExotelCallType(String literal, String exotelName, boolean isDefault) {
        this.literal = literal;
        this.exotelName = exotelName;
        this.isDefault = isDefault;
    }

    public static ExotelCallType getDefault() {
        return ExotelOption.getDefault(ExotelCallType.class);
    }

    public static ExotelCallType getByName(String name) {
        return ExotelOption.getByName(ExotelCallType.class, name);
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
