package com.fincity.saas.message.enums.call.provider.exotel.option;

import lombok.Getter;

@Getter
public enum ExotelRecordingChannel implements ExotelOption<ExotelRecordingChannel> {
    SINGLE("SINGLE", "single", true),
    DUAL("DUAL", "dual", false);

    private final String literal;
    private final String exotelName;
    private final boolean isDefault;

    ExotelRecordingChannel(String literal, String exotelName, boolean isDefault) {
        this.literal = literal;
        this.exotelName = exotelName;
        this.isDefault = isDefault;
    }

    public static ExotelRecordingChannel getDefault() {
        return ExotelOption.getDefault(ExotelRecordingChannel.class);
    }

    public static ExotelRecordingChannel getByName(String name) {
        return ExotelOption.getByName(ExotelRecordingChannel.class, name);
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
