package com.fincity.saas.entity.processor.analytics.model;

import com.fincity.saas.entity.processor.analytics.util.DatePair;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class StatusDateCount implements Serializable {

    @Serial
    private static final long serialVersionUID = 6258224914925137629L;

    private IdAndValue<ULong, String> entity;

    private CountPercentage totalCount;

    private Map<DatePair, CountPercentage> perCount;

    public static StatusDateCount of(
            IdAndValue<ULong, String> entity, CountPercentage totalCount, Map<DatePair, CountPercentage> perCount) {
        return new StatusDateCount().setEntity(entity).setTotalCount(totalCount).setPerCount(perCount);
    }

    public static StatusDateCount of(
            ULong entityId,
            String entityName,
            CountPercentage totalCount,
            Map<DatePair, CountPercentage> statusCounts) {
        return of(IdAndValue.of(entityId, entityName), totalCount, statusCounts);
    }
}
