package com.fincity.saas.entity.processor.enums.content;

import org.jooq.EnumType;

import lombok.Getter;

@Getter
public enum TaskPriority implements EnumType {
    LOW("LOW"),
    MEDIUM("MEDIUM"),
    HIGH("HIGH");

    private final String literal;

    TaskPriority(String literal) {
        this.literal = literal;
    }

    public static TaskPriority lookupLiteral(String literal) {
        return EnumType.lookupLiteral(TaskPriority.class, literal);
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
