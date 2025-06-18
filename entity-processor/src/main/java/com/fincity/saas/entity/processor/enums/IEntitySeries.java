package com.fincity.saas.entity.processor.enums;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface IEntitySeries {

    @JsonIgnore
    default EntitySeries getEntitySeries() {
        return EntitySeries.XXX;
    }

    @JsonIgnore
    default String getEntityName() {
        return this.getEntitySeries().getDisplayName();
    }

    @JsonIgnore
    default String getEntityPrefix(String appCode) {
        return this.getEntitySeries().getPrefix(appCode);
    }
}
