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
public class Task extends BaseContentDto<TaskRequest, Task> {

    @Serial
    private static final long serialVersionUID = 2984521500368594815L;

    private ULong taskTypeId;
    private LocalDateTime dueDate;
    private TaskPriority taskPriority;
    private Boolean hasReminder;
    private LocalDateTime nextReminder;

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.TASK;
    }

    @Override
    public Task of(TaskRequest taskRequest) {
        return new Task()
                .setContent(taskRequest.getContent())
                .setHasAttachment(taskRequest.getHasAttachment())
                .setOwnerId(taskRequest.getOwnerId().getULongId())
                .setTicketId(taskRequest.getTicketId().getULongId())
                .setTaskTypeId(taskRequest.getTaskTypeId().getULongId())
                .setDueDate(taskRequest.getDueDate())
                .setTaskPriority(taskRequest.getTaskPriority())
                .setHasReminder(taskRequest.getHasReminder())
                .setNextReminder(taskRequest.getNextReminder());
    }
}
