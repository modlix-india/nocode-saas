package com.fincity.saas.entity.processor.model.request.rule;

import com.fincity.saas.entity.processor.dto.rule.BaseRuleDto;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class RuleRequest<D extends BaseRuleDto<D>> {

    private D entity;
    private List<ULong> userId;
    private List<ULong> roleId;
    private List<ULong> profileId;
    private List<ULong> designationId;
}
