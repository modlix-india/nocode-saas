package com.fincity.saas.entity.processor.enums.message;

/**
 * Kind of WhatsApp message, mirroring the message service's enum of the same values.
 *
 * <p>Duplicated rather than shared on purpose. This service stores and displays messages but never
 * constructs or interprets a Meta payload, so it needs the discriminator and nothing else. Pulling
 * the message service's model package across would drag dozens of provider classes into a service
 * that has no use for them.
 */
public enum WhatsappMessageType {
    AUDIO,
    BUTTON,
    CONTACTS,
    DOCUMENT,
    LOCATION,
    TEXT,
    TEMPLATE,
    IMAGE,
    INTERACTIVE,
    ORDER,
    REACTION,
    STICKER,
    SYSTEM,
    UNKNOWN,
    VIDEO,
    UNSUPPORTED;

    /** Whether the message carries a file, so the UI knows to look at {@code mediaFileDetail}. */
    public boolean isMedia() {
        return this == AUDIO || this == DOCUMENT || this == IMAGE || this == VIDEO || this == STICKER;
    }
}
