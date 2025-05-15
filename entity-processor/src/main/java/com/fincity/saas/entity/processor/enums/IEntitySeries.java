package com.fincity.saas.entity.processor.enums;

public interface IEntitySeries {
    default EntitySeries getEntitySeries() {
        return EntitySeries.XXX;
    }

    default String getEntityName() {
        return this.getEntitySeries().getDisplayName();
    }
}
