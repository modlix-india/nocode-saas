package com.fincity.sass.worker.model;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.sass.worker.jooq.enums.WorkerTaskStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

import java.io.Serial;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Task extends AbstractUpdatableDTO<ULong, ULong>{

    @Serial
    private static final long serialVersionUID = -8484383577414055084L;

    private String jobName;
    private String cronExpression;
    private LocalDateTime nextExecutionTime;
    private LocalDateTime lastExecutionTime;
    private WorkerTaskStatus status;
    private String lastExecutionResult;

}