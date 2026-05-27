package com.fincity.saas.entity.processor.enums;

import lombok.Getter;
import org.jooq.EnumType;

/**
 * Per Meta CAPI doc Part 6.2: must be {@code website} for browser-form events
 * (matched by fbp/fbc cookies) and {@code system_generated} for Meta native
 * lead-form events (matched by lead_id). Wrong value → incorrect attribution.
 */
@Getter
public enum ConversionActionSource implements EnumType {
    WEBSITE("WEBSITE", "Website", "website"),
    SYSTEM_GENERATED("SYSTEM_GENERATED", "Lead form / system generated", "system_generated");

    private final String literal;
    private final String displayName;
    /** Lower-case value sent in the CAPI payload (per spec). */
    private final String wireValue;

    ConversionActionSource(String literal, String displayName, String wireValue) {
        this.literal = literal;
        this.displayName = displayName;
        this.wireValue = wireValue;
    }

    public static ConversionActionSource lookupLiteral(String literal) {
        return EnumType.lookupLiteral(ConversionActionSource.class, literal);
    }

    @Override
    public String getLiteral() {
        return literal;
    }

    @Override
    public String getName() {
        return null;
    }
}
