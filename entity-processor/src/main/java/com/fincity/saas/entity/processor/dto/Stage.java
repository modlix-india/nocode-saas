package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.entity.processor.dto.base.BaseValueDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.StageType;
import java.io.Serial;
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
public class Stage extends BaseValueDto<Stage> {

    @Serial
    private static final long serialVersionUID = 6408080312498009507L;

    private StageType stageType = StageType.OPEN;
    private Boolean isSuccess;
    private Boolean isFailure;
    private Integer order;

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.STAGE;
    }

    public Stage setIsSuccess(Boolean isSuccess) {
        if (this.stageType.isHasSuccessFailure()) this.isSuccess = isSuccess;
        return this;
    }

    public Stage setIsFailure(Boolean isFailure) {
        if (this.stageType.isHasSuccessFailure()) this.isFailure = isFailure;
        return this;
    }
}
