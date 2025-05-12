package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.entity.processor.dto.base.BaseValueDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.StageType;
import com.fincity.saas.entity.processor.model.request.StageRequest;
import java.io.Serial;
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
public class Stage extends BaseValueDto<Stage> {

    @Serial
    private static final long serialVersionUID = 6408080312498009507L;

    private StageType stageType = StageType.OPEN;
    private Boolean isSuccess;
    private Boolean isFailure;
    private Integer order;

    public static Stage ofParent(StageRequest stageRequest) {
        return new Stage()
                .setIsParent(Boolean.TRUE)
                .setName(stageRequest.getName())
                .setDescription(stageRequest.getDescription())
                .setIsSuccess(stageRequest.getIsSuccess())
                .setIsFailure(stageRequest.getIsFailure());
    }

    public static Stage ofChild(StageRequest stageRequest, Integer order, ULong... parents) {
        return new Stage()
                .setIsParent(Boolean.FALSE)
                .setParentLevel0(parents[0])
                .setParentLevel1(parents[1])
                .setName(stageRequest.getName())
                .setDescription(stageRequest.getDescription())
                .setIsSuccess(stageRequest.getIsSuccess())
                .setIsFailure(stageRequest.getIsFailure())
                .setOrder(order);
    }

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
