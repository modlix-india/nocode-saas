package com.fincity.saas.entity.processor.model.request;

import com.fincity.saas.entity.processor.enums.Platform;
import com.fincity.saas.entity.processor.enums.StageType;
import com.fincity.saas.entity.processor.model.base.BaseProductTemplate;
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
public class StageRequest extends BaseProductTemplate<StageRequest> {

    @Serial
    private static final long serialVersionUID = 1704140784725077601L;

    private Identity id;
    private Platform platform;
    private StageType stageType;
    private Boolean isSuccess;
    private Boolean isFailure;

    public boolean isValid() {

        if (stageType == null) return false;

        if (stageType.isHasSuccessFailure()) return isSuccess != null || isFailure != null;

        return true;
    }

    public boolean isAllValid() {

        if (!this.isValid()) return false;

        return this.getChildren().values().stream().allMatch(StageRequest::isValid);
    }
}
