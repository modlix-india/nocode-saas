package com.fincity.saas.entity.processor.analytics.model;

import com.fincity.saas.entity.processor.model.common.IdAndValue;
import java.io.Serializable;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class StatusCount implements Serializable {

    private IdAndValue<ULong, String> entity;

    private CountPercentage totalCount;

    private Map<String, CountPercentage> statusCounts;

    public static StatusCount of(
            IdAndValue<ULong, String> entity, CountPercentage totalCount, Map<String, CountPercentage> statusCounts) {
        return new StatusCount().setEntity(entity).setTotalCount(totalCount).setStatusCounts(statusCounts);
    }

    public static StatusCount of(
            ULong entityId, String entityName, CountPercentage totalCount, Map<String, CountPercentage> statusCounts) {
        return of(IdAndValue.of(entityId, entityName), totalCount, statusCounts);
    }
}
