package com.fincity.saas.entity.processor.enums;

import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum EntityType implements EnumType {
    XXX,
    ENTITY,
    MODEL,
    PRODUCT,
    SOURCE,
    SUB_SOURCE,
    STATUS,
    SUB_STATUS;

    @Override
    public String getLiteral() {
        return "";
    }

    @Override
    public String getName() {
        return "";
    }
}
