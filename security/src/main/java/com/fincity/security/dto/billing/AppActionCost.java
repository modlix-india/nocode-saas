package com.fincity.security.dto.billing;

import java.io.Serial;
import java.math.BigDecimal;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.security.jooq.enums.SecurityAppActionCostActionClass;
import com.fincity.security.jooq.enums.SecurityAppActionCostStatus;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Per-action credit cost owned by a billing config (one config per app+client).
 * There is no global catalog: valid action keys are code constants, and an
 * action with no row under a config is free for that app+client.
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AppActionCost extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 1L;

    private ULong billingConfigId;
    private String actionKey;
    private BigDecimal creditCost;
    private SecurityAppActionCostActionClass actionClass;
    private BigDecimal freeQuota;
    private SecurityAppActionCostStatus status;
}
