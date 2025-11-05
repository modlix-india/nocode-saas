package com.fincity.saas.entity.processor.dto.rule;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.util.CloneUtil;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.rule.DistributionType;
import com.fincity.saas.entity.processor.model.common.UserDistribution;
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
public abstract class BaseRuleDto<T extends BaseRuleDto<T>> extends BaseUpdatableDto<T> {

    @Serial
    private static final long serialVersionUID = 3634716140733876196L;

    private ULong stageId;
    private Integer order;
    private boolean isDefault = false;
    private boolean breakAtFirstMatch = true;

    private DistributionType userDistributionType;
    private UserDistribution userDistribution;

    @JsonIgnore
    private ULong lastAssignedUserId;

    private AbstractCondition condition;

    protected BaseRuleDto() {
        super();
        this.relationsMap.put(Fields.stageId, EntitySeries.STAGE.getTable());
    }

    protected BaseRuleDto(BaseRuleDto<T> baseRuleDto) {
        super(baseRuleDto);
        this.stageId = baseRuleDto.stageId;
        this.order = baseRuleDto.order;
        this.isDefault = baseRuleDto.isDefault;
        this.breakAtFirstMatch = baseRuleDto.breakAtFirstMatch;
        this.userDistributionType = baseRuleDto.userDistributionType;
        this.userDistribution = CloneUtil.cloneObject(baseRuleDto.userDistribution);
        this.lastAssignedUserId = baseRuleDto.lastAssignedUserId;
        this.condition = CloneUtil.cloneObject(baseRuleDto.condition);
    }

    public boolean isSimple() {
        return this.condition instanceof FilterCondition;
    }

    public boolean isComplex() {
        return this.condition instanceof ComplexCondition;
    }

    public abstract ULong getEntityId();

    public abstract T setEntityId(ULong entityId);

    public T setIsDefault(boolean isDefault) {
        this.isDefault = isDefault;
        if (isDefault) this.stageId = null;
        return (T) this;
    }
}
