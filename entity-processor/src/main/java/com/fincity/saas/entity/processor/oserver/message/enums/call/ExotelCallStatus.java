package com.fincity.saas.entity.processor.oserver.message.enums.call;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.jooq.EnumType;

/**
 * Exotel's own call status, kept alongside the normalised {@link CallStatus} rather than folded into
 * it.
 *
 * <p>Mirrors the message service's enum of the same name, including the {@link JsonValue} on the
 * display name. That annotation is load-bearing: the deal profile renders {@code
 * Parent.exotelCallStatus} straight to the screen, so this serialises as {@code "in-progress"}, not
 * {@code "IN_PROGRESS"}, and dropping it would silently change what an agent reads.
 */
@Getter
public enum ExotelCallStatus implements EnumType {
    QUEUED("QUEUED", "queued"),
    IN_PROGRESS("IN_PROGRESS", "in-progress"),
    COMPLETED("COMPLETED", "completed"),
    FAILED("FAILED", "failed"),
    BUSY("BUSY", "busy"),
    NO_ANSWER("NO_ANSWER", "no-answer"),
    CANCELLED("CANCELLED", "cancelled");

    private final String literal;
    private final String displayName;

    ExotelCallStatus(String literal, String displayName) {
        this.literal = literal;
        this.displayName = displayName;
    }

    /** Resolves the database literal. Returns null for anything unrecognised rather than throwing. */
    public static ExotelCallStatus lookupLiteral(String literal) {
        return EnumType.lookupLiteral(ExotelCallStatus.class, literal);
    }

    /**
     * Resolves the wire value the provider sends, which is the hyphenated display form rather than
     * the literal. Falls back to a literal match so either spelling works.
     */
    public static ExotelCallStatus of(String value) {
        if (value == null || value.isBlank()) return null;

        String trimmed = value.trim();

        for (ExotelCallStatus status : values())
            if (status.displayName.equalsIgnoreCase(trimmed) || status.literal.equalsIgnoreCase(trimmed))
                return status;

        return null;
    }

    /** The provider-neutral status this maps to, which is what the rest of the CRM reasons about. */
    public CallStatus toCallStatus() {
        return switch (this) {
            case QUEUED -> CallStatus.QUEUED;
            case IN_PROGRESS -> CallStatus.ORIGINATE;
            case COMPLETED -> CallStatus.COMPLETE;
            case FAILED -> CallStatus.FAILED;
            case BUSY -> CallStatus.BUSY;
            case NO_ANSWER -> CallStatus.NO_ANSWER;
            case CANCELLED -> CallStatus.CANCELED;
        };
    }

    @Override
    public String getLiteral() {
        return literal;
    }

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}
