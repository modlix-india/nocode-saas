package com.fincity.saas.entity.processor.model.request;

import com.fincity.saas.entity.processor.enums.AssignmentType;
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
public final class ProductTemplateWalkInFormRequest extends BaseRequest<ProductTemplateWalkInFormRequest> {

    @Serial
    private static final long serialVersionUID = 3964494193089067344L;

    private Identity productTemplateId;
    private Identity stageId;
    private Identity statusId;
    private AssignmentType assignmentType;
}
