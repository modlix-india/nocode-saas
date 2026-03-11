package com.modlix.saas.worker.dto;

import com.modlix.saas.worker.enums.TaskJobType;
import com.modlix.saas.worker.enums.TaskLastFireStatus;
import com.modlix.saas.worker.enums.TaskState;
import com.modlix.saas.commons2.model.dto.AbstractUpdatableDTO;
import java.io.Serial;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Task extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = -8484383577414055084L;

    private String clientCode;
    private String appCode;
    private String name;
    private TaskState taskState = TaskState.NORMAL;
    private ULong clientScheduleControlId;
    private String description;
    private Map<String, Object> jobData;
    private Boolean durable;
    private TaskJobType taskJobType = TaskJobType.SSL_RENEWAL;
    private LocalDateTime startTime = LocalDateTime.now();
    private LocalDateTime endTime;
    private String schedule;
    private Boolean recoverable = true;
    private LocalDateTime nextFireTime;
    private LocalDateTime lastFireTime;
    private TaskLastFireStatus taskLastFireStatus;
    private String lastFireResult;

    public String getJobGroup() {
        if (clientCode == null) return null;
        if (appCode == null || appCode.isBlank()) return clientCode;
        return appCode + "_" + clientCode;
    }
}
