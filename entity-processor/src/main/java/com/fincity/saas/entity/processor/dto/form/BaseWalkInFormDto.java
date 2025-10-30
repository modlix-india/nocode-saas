package com.fincity.saas.entity.processor.dto.form;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.commons.jooq.util.DbSchema;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.AssignmentType;
import com.fincity.saas.entity.processor.enums.EntitySeries;
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

    @SuppressWarnings("unchecked")
    public T update(String name, ULong stageId, ULong statusId, AssignmentType assignmentType) {
        super.setName(name);
        this.stageId = stageId;
        this.statusId = statusId;
        this.assignmentType = assignmentType;
        return (T) this;
    }

    @Override
    public void extendSchema(Schema schema) {

        super.extendSchema(schema);

        Map<String, Schema> props = schema.getProperties();

        props.put(Fields.stageId, DbSchema.ofNumberId(Fields.stageId));
        props.put(Fields.statusId, DbSchema.ofNumberId(Fields.statusId));
        props.put(Fields.assignmentType, DbSchema.ofEnum(Fields.assignmentType, AssignmentType.class));

        schema.setProperties(props);
    }
}
