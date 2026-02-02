package com.fincity.saas.message.enums.call.provider.exotel.option;

import lombok.Getter;

@Getter
public enum ExotelStartPlaybackTo implements ExotelOption<ExotelStartPlaybackTo> {
    CALLEE("CALLEE", "callee", true),
    BOTH("BOTH", "both", false);

    private final String literal;
    private final String exotelName;
    private final boolean isDefault;

    ExotelStartPlaybackTo(String literal, String exotelName, boolean isDefault) {
        this.literal = literal;
        this.exotelName = exotelName;
        this.isDefault = isDefault;
    }

    public static ExotelStartPlaybackTo getDefault() {
        return ExotelOption.getDefault(ExotelStartPlaybackTo.class);
    }

    public static ExotelStartPlaybackTo getByName(String name) {
        return ExotelOption.getByName(ExotelStartPlaybackTo.class, name);
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
