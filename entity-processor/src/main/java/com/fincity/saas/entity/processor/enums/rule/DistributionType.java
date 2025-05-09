package com.fincity.saas.entity.processor.enums.rule;

import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum DistributionType implements EnumType {
    ROUND_ROBIN("ROUND_ROBIN"),
    PERCENTAGE("PERCENTAGE"),
    WEIGHTED("WEIGHTED"),
    LOAD_BALANCED("LOAD_BALANCED"),
    PRIORITY_QUEUE("PRIORITY_QUEUE"),
    RANDOM("RANDOM"),
    HYBRID("HYBRID");

    private final String literal;

    DistributionType(String literal) {
        this.literal = literal;
    }

    public static DistributionType lookupLiteral(String literal) {
        return EnumType.lookupLiteral(DistributionType.class, literal);
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
