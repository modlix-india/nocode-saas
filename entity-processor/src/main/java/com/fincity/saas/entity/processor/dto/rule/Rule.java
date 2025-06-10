package com.fincity.saas.entity.processor.dto.rule;

import java.io.Serial;
import java.util.Map;

import org.jooq.Table;
import org.jooq.types.ULong;
import org.springframework.data.annotation.Version;

import com.fincity.saas.entity.processor.dto.base.BaseDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.enums.rule.DistributionType;
import com.fincity.saas.entity.processor.model.common.UserDistribution;
import com.fincity.saas.entity.processor.model.request.rule.RuleInfoRequest;
import com.fincity.saas.entity.processor.model.request.rule.RuleRequest;

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
public abstract class Rule<T extends Rule<T>> extends BaseDto<T> implements IEntitySeries {

    public static final Map<String, Table<?>> relationsMap = Map.of(Fields.stageId, EntitySeries.STAGE.getTable());

    @Serial
    private static final long serialVersionUID = 3634716140733876196L;

    @Version
    private int version = 1;

    private ULong stageId;
    private Integer order;
    private boolean isDefault = false;
    private boolean breakAtFirstMatch = true;
    private boolean isSimple = true;
    private boolean isComplex = false;

    private DistributionType userDistributionType;
    private UserDistribution userDistribution;
    private ULong lastAssignedUserId;

    public abstract ULong getEntityId();

    public abstract T setEntityId(ULong entityId);

    public T of(RuleRequest ruleRequest) {

        RuleInfoRequest ruleInfoRequest = ruleRequest.getRule();

        if (ruleInfoRequest == null) return (T) this;

        this.setName(ruleInfoRequest.getName())
                .setDescription(ruleInfoRequest.getDescription())
                .setStageId(
                        ruleInfoRequest.getStageId() != null
                                ? ruleInfoRequest.getStageId().getULongId()
                                : null)
                .setIsDefault(ruleInfoRequest.isDefault())
                .setBreakAtFirstMatch(ruleInfoRequest.isBreakAtFirstMatch())
                .setSimple(ruleRequest.isSimple())
                .setComplex(ruleRequest.isComplex())
                .setUserDistributionType(ruleInfoRequest.getUserDistributionType())
                .setUserDistribution(ruleInfoRequest.getUserDistribution());

        return (T) this;
    }

    public T setIsDefault(boolean isDefault) {
        this.isDefault = isDefault;
        if (isDefault) this.stageId = null;
        return (T) this;
    }

    public T setComplex(boolean isComplex) {
        this.isComplex = isComplex;
        if (isComplex) this.isSimple = false;
        return (T) this;
    }

    public T setSimple(boolean isSimple) {
        this.isSimple = isSimple;
        if (isSimple) this.isComplex = false;
        return (T) this;
    }
}
