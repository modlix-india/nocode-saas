package com.fincity.security.dto.plansnbilling;

import java.io.Serial;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.security.jooq.enums.SecurityPlanLimitName;
import com.fincity.security.jooq.enums.SecurityPlanLimitStatus;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PlanLimit extends AbstractUpdatableDTO<ULong, ULong> {
    
    @Serial
    private static final long serialVersionUID = 1L;

    private ULong planId;
    private SecurityPlanLimitName name;
    private String customName;
    private Integer limit;
    private SecurityPlanLimitStatus status;
}
