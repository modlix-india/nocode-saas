package com.fincity.saas.entity.processor.dto.content;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.string.StringFormat;
import com.fincity.saas.entity.processor.dto.content.base.BaseContentDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.EnumSchemaUtil;
import com.fincity.saas.entity.processor.enums.content.TaskPriority;
import com.fincity.saas.entity.processor.model.request.content.TaskRequest;
import com.google.gson.JsonPrimitive;
import java.io.Serial;
import java.time.LocalDateTime;
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
public class Task extends BaseContentDto<Task> {

    @Serial
    private static final long serialVersionUID = 2984521500368594815L;

    private ULong taskTypeId;
    private LocalDateTime dueDate;
    private TaskPriority taskPriority;
    private boolean isCompleted;
    private LocalDateTime completedDate;
    private boolean isCancelled;
    private LocalDateTime cancelledDate;
    private boolean isDelayed;
    private boolean hasReminder;
    private LocalDateTime nextReminder;

    public Task() {
        super();
        this.relationsMap.put(Fields.taskTypeId, EntitySeries.TASK_TYPE.getTable());
    }

    public Task(Task task) {
        super(task);
        this.taskTypeId = task.taskTypeId;
        this.dueDate = task.dueDate;
        this.taskPriority = task.taskPriority;
        this.isCompleted = task.isCompleted;
        this.completedDate = task.completedDate;
        this.isCancelled = task.isCancelled;
        this.cancelledDate = task.cancelledDate;
        this.isDelayed = task.isDelayed;
        this.hasReminder = task.hasReminder;
        this.nextReminder = task.nextReminder;
    }

    public static Task of(TaskRequest taskRequest) {

        Task task = (Task) new Task()
                .setTaskTypeId(taskRequest.getTaskTypeId().getULongId())
                .setDueDate(taskRequest.getDueDate())
                .setTaskPriority(taskRequest.getTaskPriority())
                .setHasReminder(taskRequest.isHasReminder())
                .setNextReminder(taskRequest.getNextReminder())
                .setName(taskRequest.getName())
                .setContent(taskRequest.getContent())
                .setHasAttachment(taskRequest.getHasAttachment())
                .setContentEntitySeries(taskRequest.getContentEntitySeries());

        return switch (task.getContentEntitySeries()) {
            case OWNER -> task.setOwnerId(taskRequest.getOwnerId().getULongId());
            case TICKET -> task.setTicketId(taskRequest.getTicketId().getULongId());
            case USER -> task.setUserId(taskRequest.getUserId());
        };
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.TASK;
    }

    @Override
    public Schema getSchema() {

        Schema schema = super.getSchema();

        Map<String, Schema> props = schema.getProperties();
        props.put(Fields.taskTypeId, Schema.ofLong(Fields.taskTypeId).setMinimum(1));
        props.put(Fields.dueDate, Schema.ofString(Fields.dueDate).setFormat(StringFormat.DATETIME));
        props.put(
                Fields.taskPriority,
                Schema.ofString(Fields.taskPriority).setEnums(EnumSchemaUtil.getSchemaEnums(TaskPriority.class)));
        props.put(Fields.isCompleted, Schema.ofBoolean(Fields.isCompleted).setDefaultValue(new JsonPrimitive(false)));
        props.put(Fields.completedDate, Schema.ofString(Fields.completedDate).setFormat(StringFormat.DATETIME));
        props.put(Fields.isCancelled, Schema.ofBoolean(Fields.isCancelled).setDefaultValue(new JsonPrimitive(false)));
        props.put(Fields.cancelledDate, Schema.ofString(Fields.cancelledDate).setFormat(StringFormat.DATETIME));
        props.put(Fields.isDelayed, Schema.ofBoolean(Fields.isDelayed).setDefaultValue(new JsonPrimitive(false)));
        props.put(Fields.hasReminder, Schema.ofBoolean(Fields.hasReminder).setDefaultValue(new JsonPrimitive(false)));
        props.put(Fields.nextReminder, Schema.ofString(Fields.nextReminder).setFormat(StringFormat.DATETIME));

        schema.setProperties(props);
        return schema;
    }
}
