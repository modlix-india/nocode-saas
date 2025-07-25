package com.fincity.saas.entity.processor.dto.content;

import com.fincity.saas.entity.processor.dto.content.base.BaseContentDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.content.TaskPriority;
import com.fincity.saas.entity.processor.model.request.content.TaskRequest;
import java.io.Serial;
import java.time.LocalDateTime;
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
        return new Task()
                .setName(taskRequest.getName())
                .setContent(taskRequest.getContent())
                .setHasAttachment(taskRequest.getHasAttachment())
                .setOwnerId(
                        taskRequest.getOwnerId() != null
                                ? taskRequest.getOwnerId().getULongId()
                                : null)
                .setTicketId(
                        taskRequest.getTicketId() != null
                                ? taskRequest.getTicketId().getULongId()
                                : null)
                .setTaskTypeId(
                        taskRequest.getTaskTypeId() != null
                                ? taskRequest.getTaskTypeId().getULongId()
                                : null)
                .setDueDate(taskRequest.getDueDate())
                .setTaskPriority(taskRequest.getTaskPriority())
                .setHasReminder(taskRequest.isHasReminder())
                .setNextReminder(taskRequest.getNextReminder());
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.TASK;
    }
}
