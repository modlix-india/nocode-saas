package com.fincity.sass.worker.model;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.sass.worker.jooq.enums.WorkerSchedulerStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jooq.types.ULong;

import java.io.Serial;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WorkerScheduler extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 8014247343448800096L;

    private String name;
    private WorkerSchedulerStatus status;
    private String instanceId;
}