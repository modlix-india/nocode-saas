package com.fincity.saas.entity.processor.analytics.model;

import com.fincity.saas.entity.processor.analytics.model.base.BaseStatusCount;
import java.io.Serial;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class StatusNameCount extends BaseStatusCount<StatusNameCount> {

    @Serial
    private static final long serialVersionUID = 6036698402832983531L;

    private String name;

    public static StatusNameCount of(
            String entityName, CountPercentage totalCount, Map<String, CountPercentage> perCount) {
        return new StatusNameCount()
                .setName(entityName)
                .setTotalCount(totalCount)
                .setPerCount(perCount);
    }
}
