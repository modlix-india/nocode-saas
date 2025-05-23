package com.fincity.saas.entity.processor.dto.rule;

import com.fincity.saas.entity.processor.dto.base.BaseDto;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.enums.rule.DistributionType;
import com.fincity.saas.entity.processor.model.common.UserDistribution;
import com.fincity.saas.entity.processor.model.request.rule.RuleRequest;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;
import org.springframework.data.annotation.Version;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public abstract class Rule<T extends Rule<T>> extends BaseDto<T> implements IEntitySeries {

    @Serial
    private static final long serialVersionUID = 3634716140733876196L;

    @Version
    private int version = 1;

    private ULong stageId;
    private Integer order;
    private boolean isDefault = false;
    private boolean breakAtFirstMatch = false;
    private boolean isSimple = true;
    private boolean isComplex = false;

    private DistributionType userDistributionType;
    private UserDistribution userDistribution;
    private ULong lastAssignedUserId;

    public abstract ULong getEntityId();

    public abstract T setEntityId(ULong entityId);

    public abstract T of(RuleRequest ruleRequest);

    public T setIsDefault(boolean isDefault) {
        this.isDefault = isDefault;
        if (isDefault) this.stageId = null;
        return (T) this;
    }
}
