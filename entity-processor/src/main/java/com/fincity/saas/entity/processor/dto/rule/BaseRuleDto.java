package com.fincity.saas.entity.processor.dto.rule;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.util.CloneUtil;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.rule.DistributionType;
import java.io.Serial;
import java.math.BigInteger;
import java.util.List;
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
public abstract class BaseRuleDto<U extends BaseUserDistributionDto<U>, T extends BaseRuleDto<U, T>>
        extends BaseUpdatableDto<T> {

    public static final int DEFAULT_ORDER = BigInteger.ZERO.intValue();

    @Serial
    private static final long serialVersionUID = 3634716140733876196L;

    private ULong productId;
    private ULong productTemplateId;

    private Integer order;
    private DistributionType userDistributionType = DistributionType.ROUND_ROBIN;

    @JsonIgnore
    private ULong lastAssignedUserId;

    private AbstractCondition condition;

    private List<U> userDistributions;

    protected BaseRuleDto() {
        super();
        this.relationsMap.put(Fields.productId, EntitySeries.PRODUCT.getTable());
        this.relationsMap.put(Fields.productTemplateId, EntitySeries.PRODUCT_TEMPLATE.getTable());
    }

    protected BaseRuleDto(BaseRuleDto<U, T> baseRuleDto) {
        super(baseRuleDto);
        this.productId = baseRuleDto.productId;
        this.productTemplateId = baseRuleDto.productTemplateId;
        this.order = baseRuleDto.order;
        this.userDistributionType = baseRuleDto.userDistributionType;
        this.lastAssignedUserId = baseRuleDto.lastAssignedUserId;
        this.condition = CloneUtil.cloneObject(baseRuleDto.condition);
        this.userDistributions = CloneUtil.cloneMapList(baseRuleDto.userDistributions);
    }

    public boolean isSimple() {
        return this.condition instanceof FilterCondition;
    }

    public boolean isComplex() {
        return this.condition instanceof ComplexCondition;
    }

    public boolean isDefault() {
        return this.order == DEFAULT_ORDER;
    }

    public boolean areDistributionEmpty() {
        return this.userDistributions == null || this.userDistributions.isEmpty();
    }

    public boolean areDistributionValid() {
        return this.userDistributions.stream().allMatch(BaseUserDistributionDto::isDistributionValid);
    }

    public AbstractCondition getConditionWithProduct() {
        return ComplexCondition.and(FilterCondition.make(Fields.productId, this.getProductId()), getCondition());
    }
}
