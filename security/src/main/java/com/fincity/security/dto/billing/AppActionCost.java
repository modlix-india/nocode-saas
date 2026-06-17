package com.fincity.security.dto.billing;

import java.io.Serial;
import java.math.BigDecimal;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.security.jooq.enums.SecurityAppActionCostActionClassOverride;
import com.fincity.security.jooq.enums.SecurityAppActionCostStatus;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Per-app override of an action's credit cost and class. Falls back to the
 * {@link ActionCatalog} default when absent.
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AppActionCost extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 1L;

    private ULong appId;
    private ULong clientId;
    private String actionKey;
    private BigDecimal creditCost;
    private SecurityAppActionCostActionClassOverride actionClassOverride;
    private BigDecimal freeQuota;
    private SecurityAppActionCostStatus status;
}
