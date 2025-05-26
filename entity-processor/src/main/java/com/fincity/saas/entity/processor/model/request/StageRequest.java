package com.fincity.saas.entity.processor.model.request;

import com.fincity.saas.entity.processor.enums.Platform;
import com.fincity.saas.entity.processor.enums.StageType;
import com.fincity.saas.entity.processor.model.base.BaseProductTemplate;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class StageRequest extends BaseProductTemplate<StageRequest> {

    @Serial
    private static final long serialVersionUID = 1704140784725077601L;

    private Platform platform;
    private StageType stageType;
    private Boolean isSuccess;
    private Boolean isFailure;
}
