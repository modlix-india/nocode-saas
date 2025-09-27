package com.fincity.saas.entity.processor.analytics.model;

import com.fincity.saas.entity.processor.analytics.model.base.BaseStatusCount;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class StatusEntityCount extends BaseStatusCount<StatusEntityCount> implements Serializable {

    @Serial
    private static final long serialVersionUID = 6036698402832983531L;

    private ULong id;

    private String name;

    public static StatusEntityCount of(
            ULong entityId,
            String entityName,
            CountPercentage totalCount,
            List<IdAndValue<String, CountPercentage>> perCount) {
        return new StatusEntityCount()
                .setId(entityId)
                .setName(entityName)
                .setTotalCount(totalCount)
                .setPerCount(perCount);
    }
}
