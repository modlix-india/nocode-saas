package com.fincity.sass.worker.dto;

import com.fincity.sass.worker.enums.SchedulerStatus;
import com.modlix.saas.commons2.model.dto.AbstractUpdatableDTO;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jooq.types.ULong;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ClientScheduleControl extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 8014247343448800097L;

    private String appCode;
    private String clientCode;
    private String name;
    private SchedulerStatus schedulerStatus;

    public String getJobGroup() {
        if (clientCode == null) return null;
        if (appCode == null || appCode.isBlank()) return clientCode;
        return appCode + "_" + clientCode;
    }
}
