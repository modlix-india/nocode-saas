package com.fincity.saas.entity.processor.dto.content;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.commons.jooq.util.DbSchema;
import com.fincity.saas.entity.processor.dto.content.base.BaseContentDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.content.TaskPriority;
import com.fincity.saas.entity.processor.model.request.content.TaskRequest;
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
    public void extendSchema(Schema schema) {

        super.extendSchema(schema);

        Map<String, Schema> props = schema.getProperties();

        props.put(Fields.taskTypeId, DbSchema.ofNumberId(Fields.taskTypeId));
        props.put(Fields.dueDate, DbSchema.ofFutureEpochTime(Fields.dueDate));
        props.put(Fields.taskPriority, DbSchema.ofEnum(Fields.taskPriority, TaskPriority.class));
        props.put(Fields.isCompleted, DbSchema.ofBooleanFalse(Fields.isCompleted));
        props.put(Fields.completedDate, DbSchema.ofFutureEpochTime(Fields.completedDate));
        props.put(Fields.isCancelled, DbSchema.ofBooleanFalse(Fields.isCancelled));
        props.put(Fields.cancelledDate, DbSchema.ofFutureEpochTime(Fields.cancelledDate));
        props.put(Fields.isDelayed, DbSchema.ofBooleanFalse(Fields.isDelayed));
        props.put(Fields.hasReminder, DbSchema.ofBooleanFalse(Fields.hasReminder));
        props.put(Fields.nextReminder, DbSchema.ofFutureEpochTime(Fields.nextReminder));

        schema.setProperties(props);
    }
}
