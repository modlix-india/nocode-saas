package com.fincity.saas.entity.processor.model.request.content;

import java.io.Serial;
import java.time.LocalDateTime;

import com.fincity.saas.entity.processor.enums.content.TaskPriority;
import com.fincity.saas.entity.processor.model.common.Identity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TaskRequest extends BaseContentRequest<TaskRequest> {

	@Serial
	private static final long serialVersionUID = 2672900530850838706L;

	private Identity taskTypeId;
	private LocalDateTime dueDate;
	private TaskPriority taskPriority;
	private Boolean hasReminder;
	private LocalDateTime nextReminder;
}
