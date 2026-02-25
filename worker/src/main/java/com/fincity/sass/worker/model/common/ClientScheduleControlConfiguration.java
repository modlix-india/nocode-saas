package com.fincity.sass.worker.model.common;

import com.fincity.sass.worker.dto.ClientScheduleControl;
import com.fincity.sass.worker.enums.SchedulerStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ClientScheduleControlConfiguration {

    private String appCode;
    private String clientCode;
    private String name;
    private SchedulerStatus schedulerStatus = SchedulerStatus.STARTED;

    public static ClientScheduleControlConfiguration defaultConfiguration() {
        ClientScheduleControlConfiguration config = new ClientScheduleControlConfiguration();
        config.setAppCode("default");
        config.setClientCode("default");
        config.setName("Default");
        config.setSchedulerStatus(SchedulerStatus.STARTED);
        return config;
    }

    public ClientScheduleControl toDto() {
        ClientScheduleControl dto = new ClientScheduleControl();
        dto.setAppCode(this.appCode);
        dto.setClientCode(this.clientCode);
        dto.setName(this.name);
        dto.setSchedulerStatus(this.schedulerStatus);
        return dto;
    }
}
