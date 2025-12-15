package com.fincity.saas.entity.processor.enums;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.entity.processor.functions.annotations.IgnoreGeneration;
import com.fincity.saas.entity.processor.util.NameUtil;

@IgnoreGeneration
public interface IEntitySeries {

    @JsonIgnore
    @IgnoreGeneration
    default EntitySeries getEntitySeries() {
        return EntitySeries.XXX;
    }

    @JsonIgnore
    @IgnoreGeneration
    default String getEntityName() {
        return this.getEntitySeries().getDisplayName();
    }

    @JsonIgnore
    @IgnoreGeneration
    default String getEntityPrefix(String appCode) {
        return this.getEntitySeries().getPrefix(appCode);
    }

    @JsonIgnore
    @IgnoreGeneration
    default String getEntityKey() {
        return NameUtil.decapitalize(this.getEntityName());
    }
}
