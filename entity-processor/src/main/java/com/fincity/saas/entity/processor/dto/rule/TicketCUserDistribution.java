package com.fincity.saas.entity.processor.dto.rule;

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
public class TicketCUserDistribution extends BaseUserDistribution<TicketCUserDistribution> {

    @Serial
    private static final long serialVersionUID = 8047182181351711797L;
}
