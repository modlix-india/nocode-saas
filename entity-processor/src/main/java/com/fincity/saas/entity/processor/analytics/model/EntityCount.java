package com.fincity.saas.entity.processor.analytics.model;

import com.fincity.saas.entity.processor.analytics.model.base.BaseCount;
import com.fincity.saas.entity.processor.analytics.model.common.CountPercentage;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EntityCount extends BaseCount<EntityCount> {

    @Serial
    private static final long serialVersionUID = 6036698402832983532L;

    private ULong id;

    private String name;

    public static EntityCount of(ULong entityId, String entityName, CountPercentage totalCount) {
        return new EntityCount().setId(entityId).setName(entityName).setTotalCount(totalCount);
    }
}
