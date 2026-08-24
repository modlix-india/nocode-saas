package com.fincity.saas.entity.processor.enums.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Kind of WhatsApp message, mirroring the message service's enum of the same values.
 *
 * <p>Duplicated rather than shared on purpose. This service stores and displays messages but never
 * constructs or interprets a Meta payload, so it needs the discriminator and nothing else. Pulling
 * the message service's model package across would drag dozens of provider classes into a service
 * that has no use for them.
 *
 * <p><b>Mirroring means the JSON too, which is the whole point of {@link #getValue()}.</b> The
 * message service publishes these lowercase ({@code "text"}, {@code "image"}) through a
 * {@code @JsonValue} on its own copy, while storing them uppercase. Taking over the endpoint
 * without carrying that across changed the wire contract from {@code "text"} to {@code "TEXT"},
 * and the deal profile's chat decides what to draw with about 150 expressions of the form
 * {@code Parent.messageType = "text"}. Every one of them silently went false: the thread rendered
 * the right number of bubbles, all of them blank, with no console error. Same failure mode as the
 * {@code isOutbound}/{@code outbound} getter-naming trap documented on the DTO, and found the same
 * way - by looking at the screen, because nothing else complains.
 *
 * <p>Uppercase would be the more conventional JSON, but it is not worth a breaking change to a
 * page this size. The storage move was meant to be invisible to the UI.
 */
public enum WhatsappMessageType {
    AUDIO("audio"),
    BUTTON("button"),
    CONTACTS("contacts"),
    DOCUMENT("document"),
    LOCATION("location"),
    TEXT("text"),
    TEMPLATE("template"),
    IMAGE("image"),
    INTERACTIVE("interactive"),
    ORDER("order"),
    REACTION("reaction"),
    STICKER("sticker"),
    SYSTEM("system"),
    UNKNOWN("unknown"),
    VIDEO("video"),
    UNSUPPORTED("unsupported");

    private final String value;

    WhatsappMessageType(String value) {
        this.value = value;
    }

    /**
     * Accepts either case, so a payload written against either contract still parses.
     *
     * <p>Deliberately lenient about an unrecognised value: a type Meta adds later must not fail the
     * whole read. {@link #UNKNOWN} is exactly what it is for.
     */
    @JsonCreator
    public static WhatsappMessageType of(String value) {
        if (value == null || value.isBlank()) return TEXT;
        try {
            return WhatsappMessageType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

    /**
     * The wire form, lowercase to match the message service.
     *
     * <p>Jackson only. jOOQ converts through {@code EnumConverter}, which reads {@link #name()},
     * so the column keeps its uppercase literals and no migration is implied by this.
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /** Whether the message carries a file, so the UI knows to look at {@code mediaFileDetail}. */
    public boolean isMedia() {
        return this == AUDIO || this == DOCUMENT || this == IMAGE || this == VIDEO || this == STICKER;
    }
}
