package com.fincity.sass.worker.model;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jooq.types.ULong;

import java.io.Serial;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WorkerScheduler extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 8014247343448800096L;

    private String name;
    private String status;
    private boolean isRunning;
    private boolean isStandbyMode;
    private boolean isShutdown;
    private LocalDateTime startTime;



}