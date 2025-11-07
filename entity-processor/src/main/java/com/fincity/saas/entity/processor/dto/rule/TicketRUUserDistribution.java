package com.fincity.saas.entity.processor.dto.rule;

import com.fincity.saas.entity.processor.enums.EntitySeries;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class TicketRUUserDistribution extends BaseUserDistributionDto<TicketRUUserDistribution> {

    @Serial
    private static final long serialVersionUID = 6659011787175377491L;

    public TicketRUUserDistribution() {
        super();
        this.relationsMap.put(Fields.ruleId, EntitySeries.TICKET_C_USER_DISTRIBUTION.getTable());
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.TICKET_C_USER_DISTRIBUTION;
    }
}
