package com.fincity.saas.entity.processor.enums;

public interface IEntityType {
    default EntityType getEntityType() {
        return EntityType.XXX;
    }
}
