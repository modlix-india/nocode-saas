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
public class TicketRUUserDistribution extends BaseUserDistribution<TicketRUUserDistribution> {

    @Serial
    private static final long serialVersionUID = 6659011787175377491L;


}
