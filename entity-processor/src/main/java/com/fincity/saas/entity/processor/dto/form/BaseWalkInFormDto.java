package com.fincity.saas.entity.processor.dto.form;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.AssignmentType;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.EnumSchemaUtil;
import java.io.Serial;
import java.util.Map;
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
public abstract class BaseWalkInFormDto<T extends BaseWalkInFormDto<T>> extends BaseUpdatableDto<T> {

    @Serial
    private static final long serialVersionUID = 90688420659672089L;

    private ULong stageId;
    private ULong statusId;
    private AssignmentType assignmentType;

    protected BaseWalkInFormDto() {
        super();
        this.relationsMap.put(Fields.stageId, EntitySeries.STAGE.getTable());
        this.relationsMap.put(Fields.statusId, EntitySeries.STAGE.getTable());
    }

    protected BaseWalkInFormDto(BaseWalkInFormDto<T> baseWalkInFormDto) {
        super(baseWalkInFormDto);
        this.stageId = baseWalkInFormDto.stageId;
        this.statusId = baseWalkInFormDto.statusId;
        this.assignmentType = baseWalkInFormDto.assignmentType;
    }

    public abstract ULong getProductId();

    public T update(String name, ULong stageId, ULong statusId, AssignmentType assignmentType) {
        super.setName(name);
        this.stageId = stageId;
        this.statusId = statusId;
        this.assignmentType = assignmentType;
        return (T) this;
    }

    @Override
    public Schema getSchema() {

        Schema schema = super.getSchema();

        Map<String, Schema> props = schema.getProperties();
        props.put(Fields.stageId, Schema.ofLong(Fields.stageId).setMinimum(1));
        props.put(Fields.statusId, Schema.ofLong(Fields.statusId).setMinimum(1));
        props.put(
                Fields.assignmentType,
                Schema.ofString(Fields.assignmentType).setEnums(EnumSchemaUtil.getSchemaEnums(AssignmentType.class)));

        schema.setProperties(props);
        return schema;
    }
}
