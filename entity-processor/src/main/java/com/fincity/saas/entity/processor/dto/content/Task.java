package com.fincity.saas.entity.processor.dto.content;

import java.io.Serial;
import java.time.LocalDateTime;

import org.jooq.types.ULong;

import com.fincity.saas.entity.processor.dto.content.base.BaseContentDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.content.TaskPriority;

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
public class Task extends BaseContentDto<Task> {

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
}
