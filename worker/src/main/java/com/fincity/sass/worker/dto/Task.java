package com.fincity.sass.worker.dto;

import com.fincity.sass.worker.enums.TaskJobType;
import com.fincity.sass.worker.enums.TaskLastFireStatus;
import com.fincity.sass.worker.enums.TaskState;
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
    private String groupName;
    private TaskState taskState = TaskState.NORMAL;
    private ULong schedulerId;
    private String description;
    private Map<String, Object> jobData;
    private Boolean durable;
    private TaskJobType taskJobType = TaskJobType.SIMPLE;
    private LocalDateTime startTime = LocalDateTime.now();
    private LocalDateTime endTime;
    private String schedule;
    private Integer repeatInterval;
    private Boolean recoverable = true;
    private LocalDateTime nextFireTime;
    private LocalDateTime lastFireTime;
    private TaskLastFireStatus taskLastFireStatus;
    private String lastFireResult;

    public Boolean getRepeatForever() {
        return repeatInterval == null || repeatInterval == -1;
    }

    public Task setRepeatForever(Boolean repeatForever) {
        if (Boolean.TRUE.equals(repeatForever)) this.repeatInterval = -1;

        return this;
    }

    public Integer getRepeatCount() {
        return Boolean.TRUE.equals(getRepeatForever()) ? null : repeatInterval;
    }

    public Task setRepeatCount(Integer repeatCount) {
        this.repeatInterval = repeatCount;
        return this;
    }
}
