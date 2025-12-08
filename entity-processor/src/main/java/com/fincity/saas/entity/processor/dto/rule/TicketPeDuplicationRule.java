package com.fincity.saas.entity.processor.dto.rule;

import java.io.Serial;

import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.PhoneNumberAndEmailType;

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
public class TicketPeDuplicationRule extends BaseUpdatableDto<TicketPeDuplicationRule> {

    @Serial
    private static final long serialVersionUID = 7461568812916675058L;

    private PhoneNumberAndEmailType phoneNumberAndEmailType = PhoneNumberAndEmailType.PHONE_NUMBER_OR_EMAIL;

	public TicketPeDuplicationRule() {
		super();
	}

	public TicketPeDuplicationRule(TicketPeDuplicationRule ticketPeDuplicationRule) {
		super(ticketPeDuplicationRule);
		this.phoneNumberAndEmailType = ticketPeDuplicationRule.phoneNumberAndEmailType;
	}

	@Override
	public EntitySeries getEntitySeries() {
		return EntitySeries.TICKET_PE_DUPLICATION_RULES;
	}
}
