package com.fincity.sass.worker.model;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.sass.worker.jooq.enums.WorkerTaskJobType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;
import org.quartz.Trigger;

import java.io.Serial;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Task extends AbstractUpdatableDTO<ULong, ULong>{

    @Serial
    private static final long serialVersionUID = -8484383577414055084L;

    private String name;
    private String groupName;
    private ULong clientId;
    private ULong appId;
//    private Trigger.TriggerState status;
    private ULong schedulerId;
    private String description;
    private Boolean durable;
    private WorkerTaskJobType jobType;
    private LocalDateTime startTime = LocalDateTime.now();
    private LocalDateTime endTime;
    private String schedule;
    private Integer repeatCount; // only for simple job type
    private Boolean repeatForever;  // only for simple job type
    private LocalDateTime nextExecutionTime;
    private LocalDateTime lastExecutionTime;
    private String lastExecutionResult;

}