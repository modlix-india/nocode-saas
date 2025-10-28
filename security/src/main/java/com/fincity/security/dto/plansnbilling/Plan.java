package com.fincity.security.dto.plansnbilling;

import java.io.Serial;
import java.util.List;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.security.dto.App;
import com.fincity.security.jooq.enums.SecurityPlanStatus;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Plan extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 1L;

    private ULong clientId;
    private String name;
    private String description;
    private String features;
    private SecurityPlanStatus status;
    private String planCode;
    private ULong fallBackPlanId;
    private boolean forRegistration;
    private int orderNumber;
    private boolean defaultPlan;
    private ULong forClientId;
    private boolean prepaid;
    private ULong appId;

    private App app;
    private List<PlanCycle> cycles;
    private List<PlanLimit> limits;
}
