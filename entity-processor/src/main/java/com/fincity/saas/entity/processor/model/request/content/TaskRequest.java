package com.fincity.saas.entity.processor.model.request.content;

import com.fincity.saas.entity.processor.enums.content.TaskPriority;
import com.fincity.saas.entity.processor.model.common.Identity;
import java.io.Serial;
import java.time.LocalDateTime;
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
    private boolean hasReminder;
    private LocalDateTime nextReminder;

    public static void main(String[] args) {
        Integer a = 127;
        Integer b = 127;
        Integer c = 128;
        Integer d = 128;

        System.out.println(a == b);
        System.out.println(c == d);
    }
}
