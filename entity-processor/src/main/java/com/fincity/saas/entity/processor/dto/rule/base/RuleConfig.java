package com.fincity.saas.entity.processor.dto.rule.base;

import java.io.Serial;
import java.util.Map;

import org.jooq.types.ULong;

import com.fincity.saas.entity.processor.dto.base.BaseDto;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.enums.rule.DistributionType;
import com.fincity.saas.entity.processor.model.common.UserDistribution;

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
public abstract class RuleConfig<T extends RuleConfig<T>> extends BaseDto<T> implements IEntitySeries {

    @Serial
    private static final long serialVersionUID = 1844345864104376762L;

    private ULong stageId;
    private boolean breakAtFirstMatch = false;

    private Map<Integer, ULong> rules;
    private DistributionType userDistributionType;
    private Map<ULong, UserDistribution> userDistributions;
    private ULong lastUsedUserId;

    public abstract ULong getEntityId();

    public abstract T setEntityId(ULong entityId);
}
