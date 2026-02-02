package com.fincity.saas.message.enums.call.provider.exotel.option;

import lombok.Getter;

@Getter
public enum ExotelRecordingFormat implements ExotelOption<ExotelRecordingFormat> {
    MP3("MP3", "mp3", true),
    MP3_HQ("MP3_HQ", "mp3-hq", false);

    private final String literal;
    private final String exotelName;
    private final boolean isDefault;

    ExotelRecordingFormat(String literal, String exotelName, boolean isDefault) {
        this.literal = literal;
        this.exotelName = exotelName;
        this.isDefault = isDefault;
    }

    public static ExotelRecordingFormat getDefault() {
        return ExotelOption.getDefault(ExotelRecordingFormat.class);
    }

    public static ExotelRecordingFormat getByName(String name) {
        return ExotelOption.getByName(ExotelRecordingFormat.class, name);
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
