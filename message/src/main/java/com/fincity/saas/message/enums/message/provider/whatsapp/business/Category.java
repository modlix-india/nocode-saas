package com.fincity.saas.message.enums.message.provider.whatsapp.business;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.saas.commons.exeception.GenericException;
import lombok.Getter;
import org.jooq.EnumType;
import org.springframework.http.HttpStatus;

@Getter
public enum Category implements EnumType {
    AUTHENTICATION("AUTHENTICATION", "AUTHENTICATION", 600L, 30L, 900L),
    UTILITY("UTILITY", "UTILITY", 2592000L, 30L, 43200L),
    MARKETING("MARKETING", "MARKETING", 2592000L, 43200L, 2592000L);

    private final String literal;
    private final String value;
    private final Long defaultTtlSec;
    private final Long minTtlSec;
    private final Long maxTtlSec;

    Category(String literal, String value, Long defaultTtlSec, Long minTtlSec, Long maxTtlSec) {
        this.literal = literal;
        this.value = value;
        this.defaultTtlSec = defaultTtlSec;
        this.minTtlSec = minTtlSec;
        this.maxTtlSec = maxTtlSec;
    }

    public static Category lookupLiteral(String literal) {
        return EnumType.lookupLiteral(Category.class, literal);
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @Override
    public String getLiteral() {
        return literal;
    }

    @Override
    public String getName() {
        return value;
    }

    public void validateTtl(Long ttlSeconds) {
        if (ttlSeconds == null) throw new GenericException(HttpStatus.BAD_REQUEST, "TTL cannot be null");

        if (ttlSeconds < minTtlSec || ttlSeconds > maxTtlSec)
            throw new GenericException(
                    HttpStatus.BAD_REQUEST,
                    StringFormatter.format(
                            "$ category TTL must be between $ and $ seconds. Provided: $",
                            this.name(),
                            minTtlSec,
                            maxTtlSec,
                            ttlSeconds));
    }

    public boolean isValidTtl(Long ttlSeconds) {
        return ttlSeconds != null && ttlSeconds >= minTtlSec && ttlSeconds <= maxTtlSec;
    }
}
