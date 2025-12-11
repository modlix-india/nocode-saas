package com.fincity.saas.entity.processor.enums;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.entity.processor.functions.anntations.IgnoreServerFunc;
import com.fincity.saas.entity.processor.util.NameUtil;

@IgnoreServerFunc
public interface IEntitySeries {

    @JsonIgnore
    @IgnoreServerFunc
    default EntitySeries getEntitySeries() {
        return EntitySeries.XXX;
    }

    @JsonIgnore
    @IgnoreServerFunc
    default String getEntityName() {
        return this.getEntitySeries().getDisplayName();
    }

    @JsonIgnore
    @IgnoreServerFunc
    default String getEntityPrefix(String appCode) {
        return this.getEntitySeries().getPrefix(appCode);
    }

    @JsonIgnore
    @IgnoreServerFunc
    default String getEntityKey() {
        return NameUtil.decapitalize(this.getEntityName());
    }
}
