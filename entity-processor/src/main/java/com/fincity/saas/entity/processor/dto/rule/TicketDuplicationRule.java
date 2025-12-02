package com.fincity.saas.entity.processor.dto.rule;

import com.fincity.saas.entity.processor.enums.EntitySeries;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class TicketDuplicationRule extends BaseRuleDto<NoOpUserDistribution, TicketDuplicationRule> {

    @Serial
    private static final long serialVersionUID = 4886380833032613269L;

    private String source;
    private String subSource;
    private ULong maxStageId;

    public TicketDuplicationRule() {
        super();
    }

    public TicketDuplicationRule(TicketDuplicationRule ticketDuplicationRule) {
        super(ticketDuplicationRule);
        this.source = ticketDuplicationRule.getSource();
        this.subSource = ticketDuplicationRule.getSubSource();
        this.maxStageId = ticketDuplicationRule.getMaxStageId();
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.TICKET_DUPLICATION_RULES;
    }
}
