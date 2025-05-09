package com.fincity.saas.entity.processor.model.request;

import com.fincity.saas.entity.processor.enums.ValueTemplateType;
import com.fincity.saas.entity.processor.model.base.BaseRequest;
import com.fincity.saas.entity.processor.model.common.Identity;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ValueTemplateRequest extends BaseRequest<ValueTemplateRequest> {

    @Serial
    private static final long serialVersionUID = 5482351537919948018L;

    private ValueTemplateType valueTemplateType;
    private Identity valueEntityId;
}
