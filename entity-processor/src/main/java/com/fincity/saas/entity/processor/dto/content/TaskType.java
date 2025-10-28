package com.fincity.saas.entity.processor.dto.content;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.model.request.content.TaskTypeRequest;
import java.io.Serial;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TaskType extends BaseUpdatableDto<TaskType> {

    @Serial
    private static final long serialVersionUID = 3217238190792368556L;

    public TaskType() {
        super();
    }

    public TaskType(TaskType taskType) {
        super(taskType);
    }

    public static TaskType of(TaskTypeRequest taskTypeRequest) {
        return new TaskType().setName(taskTypeRequest.getName()).setDescription(taskTypeRequest.getDescription());
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.TASK_TYPE;
    }

    @Override
    public Schema getSchema() {

        Schema schema = super.getSchema();

        Map<String, Schema> props = schema.getProperties();

        schema.setProperties(props);
        return schema;
    }
}
