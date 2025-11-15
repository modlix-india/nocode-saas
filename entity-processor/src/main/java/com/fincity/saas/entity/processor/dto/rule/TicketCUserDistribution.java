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
public class TicketCUserDistribution extends BaseUserDistributionDto<TicketCUserDistribution> {

    @Serial
    private static final long serialVersionUID = 8047182181351711797L;

    public TicketCUserDistribution() {
        super();
        this.relationsMap.put(Fields.ruleId, EntitySeries.TICKET_RU_USER_DISTRIBUTION.getTable());
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.TICKET_RU_USER_DISTRIBUTION;
    }
}
