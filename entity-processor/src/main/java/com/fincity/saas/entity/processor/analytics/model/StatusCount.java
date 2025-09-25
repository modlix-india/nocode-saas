package com.fincity.saas.entity.processor.analytics.model;

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
public class StatusCount implements Serializable {

    @Serial
    private static final long serialVersionUID = 6036698402832983531L;

    private IdAndValue<ULong, String> entity;

    private CountPercentage totalCount;

    private Map<String, CountPercentage> perCount;

    public static StatusCount of(
            IdAndValue<ULong, String> entity, CountPercentage totalCount, Map<String, CountPercentage> perCount) {
        return new StatusCount().setEntity(entity).setTotalCount(totalCount).setPerCount(perCount);
    }

    public static StatusCount of(
            ULong entityId, String entityName, CountPercentage totalCount, Map<String, CountPercentage> perCount) {
        return of(IdAndValue.of(entityId, entityName), totalCount, perCount);
    }
}
