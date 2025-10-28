package com.fincity.saas.entity.processor.dto;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.entity.processor.dto.base.BaseValueDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.EnumSchemaUtil;
import com.fincity.saas.entity.processor.enums.Platform;
import com.fincity.saas.entity.processor.enums.StageType;
import com.fincity.saas.entity.processor.model.request.StageRequest;
import java.io.Serial;
import java.util.Map;
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

    public Stage() {
        super();
        this.relationsMap.put(
                BaseValueDto.Fields.parentLevel0, this.getEntitySeries().getTable());
        this.relationsMap.put(
                BaseValueDto.Fields.parentLevel1, this.getEntitySeries().getTable());
    }

    public Stage(Stage stage) {
        super(stage);
        this.stageType = stage.stageType;
        this.isSuccess = stage.isSuccess;
        this.isFailure = stage.isFailure;
    }

    public static Stage ofParent(StageRequest stageRequest) {
        return new Stage()
                .setProductTemplateId(stageRequest.getProductTemplateId().getULongId())
                .setIsParent(Boolean.TRUE)
                .setName(stageRequest.getName())
                .setDescription(stageRequest.getDescription())
                .setStageType(stageRequest.getStageType())
                .setIsSuccess(stageRequest.getIsSuccess())
                .setIsFailure(stageRequest.getIsFailure())
                .setPlatform(stageRequest.getPlatform())
                .setOrder(stageRequest.getOrder());
    }

    public static Stage ofChild(
            StageRequest stageRequest, Integer order, Platform platform, StageType stageType, Stage... parents) {
        return (Stage) new Stage()
                .setProductTemplateId(stageRequest.getProductTemplateId().getULongId())
                .setIsParent(Boolean.FALSE)
                .setParentLevel0(parents[0].getId())
                .setParentLevel1(parents.length > 1 ? parents[1].getParentLevel0() : null)
                .setName(stageRequest.getName())
                .setDescription(stageRequest.getDescription())
                .setStageType(stageType)
                .setIsSuccess(
                        stageRequest.getIsSuccess() != null ? stageRequest.getIsSuccess() : parents[0].getIsSuccess())
                .setIsFailure(
                        stageRequest.getIsFailure() != null ? stageRequest.getIsFailure() : parents[0].getIsFailure())
                .setOrder(order)
                .setPlatform(platform);
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

    @Override
    public Schema getSchema() {

        Schema schema = super.getSchema();

        Map<String, Schema> props = schema.getProperties();
        props.put(
                Fields.stageType,
                Schema.ofString(Fields.stageType).setEnums(EnumSchemaUtil.getSchemaEnums(StageType.class)));
        props.put(Fields.isSuccess, Schema.ofBoolean(Fields.isSuccess));
        props.put(Fields.isFailure, Schema.ofBoolean(Fields.isFailure));

        schema.setProperties(props);
        return schema;
    }
}
