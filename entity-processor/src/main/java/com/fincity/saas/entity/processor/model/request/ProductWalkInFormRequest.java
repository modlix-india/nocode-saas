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
public final class ProductWalkInFormRequest extends BaseRequest<ProductWalkInFormRequest> {

    @Serial
    private static final long serialVersionUID = -3348592870179377228L;

    private Identity productId;
    private Identity stageId;
    private Identity statusId;
    private AssignmentType assignmentType;
}
