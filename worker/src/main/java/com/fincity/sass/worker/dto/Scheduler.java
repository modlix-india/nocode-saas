package com.fincity.sass.worker.dto;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.sass.worker.enums.SchedulerStatus;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jooq.types.ULong;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Scheduler extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 8014247343448800096L;

    private String name;
    private SchedulerStatus schedulerStatus;
    private String instanceId;
}
