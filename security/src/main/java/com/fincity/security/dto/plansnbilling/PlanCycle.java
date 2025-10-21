package com.fincity.security.dto.plansnbilling;

import java.io.Serial;
import java.math.BigDecimal;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.security.jooq.enums.SecurityPlanCycleStatus;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PlanCycle extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 1L;

    private String name;
    private String description;
    private ULong planId;
    private BigDecimal cost;
    private String currency;

    private BigDecimal tax1;
    private String tax1Name;
    private BigDecimal tax2;
    private String tax2Name;
    private BigDecimal tax3;
    private String tax3Name;
    private BigDecimal tax4;
    private String tax4Name;
    private BigDecimal tax5;
    private String tax5Name;

    private Integer interval;
    private SecurityPlanCycleStatus status;
}
