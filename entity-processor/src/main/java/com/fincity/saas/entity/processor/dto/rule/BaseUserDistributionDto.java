package com.fincity.saas.entity.processor.dto.rule;

import java.io.Serial;

import org.jooq.types.ULong;

import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.relations.resolvers.field.UserFieldResolver;

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
public abstract class BaseUserDistributionDto<T extends BaseUserDistributionDto<T>> extends BaseUpdatableDto<T> {

    @Serial
    private static final long serialVersionUID = 3292745048951337839L;

    private ULong ruleId;
    private ULong userId;
    private ULong roleId;
    private ULong profileId;
    private ULong designationId;
    private ULong departmentId;

    protected BaseUserDistributionDto() {
        super();
        this.relationsResolverMap.put(UserFieldResolver.class, Fields.userId);
    }

    protected BaseUserDistributionDto(BaseUserDistributionDto<T> userDistribution) {
        super(userDistribution);
        this.ruleId = userDistribution.ruleId;
        this.userId = userDistribution.userId;
        this.roleId = userDistribution.roleId;
        this.profileId = userDistribution.profileId;
        this.designationId = userDistribution.designationId;
        this.departmentId = userDistribution.departmentId;
    }
}
