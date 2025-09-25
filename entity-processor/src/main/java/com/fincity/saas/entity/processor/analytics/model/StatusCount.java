package com.fincity.saas.entity.processor.analytics.model;

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

    private ULong id;

    private String name;

    private CountPercentage totalCount;

    private Map<String, CountPercentage> perCount;

    public static StatusCount of(
            ULong entityId, String entityName, CountPercentage totalCount, Map<String, CountPercentage> perCount) {
        return new StatusCount().setId(entityId).setName(entityName).setTotalCount(totalCount).setPerCount(perCount);
    }
}
