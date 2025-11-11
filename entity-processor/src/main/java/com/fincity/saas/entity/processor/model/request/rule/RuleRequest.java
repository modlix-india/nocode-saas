package com.fincity.saas.entity.processor.model.request.rule;

import com.fincity.saas.entity.processor.dto.rule.BaseRuleDto;
import com.fincity.saas.entity.processor.dto.rule.BaseUserDistributionDto;
import java.util.List;
import java.util.stream.Stream;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class RuleRequest<U extends BaseUserDistributionDto<U>, D extends BaseRuleDto<U, D>> {

    private D entity;
    private List<ULong> userIds;
    private List<ULong> roleIds;
    private List<ULong> profileIds;
    private List<ULong> designationIds;
    private List<ULong> departmentIds;

    public boolean areDistributionEmpty() {
        return Stream.of(this.userIds, this.roleIds, this.profileIds, this.designationIds, this.departmentIds)
                .noneMatch(list -> list != null && !list.isEmpty());
    }
}
