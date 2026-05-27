package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
public class CampaignSyncState {
    private ULong id;
    private String appCode;
    private String clientCode;
    private ULong campaignId;
    private CampaignPlatform platform;
    private LocalDateTime lastSyncAt;
    private LocalDate lastSyncedTo;
    private LocalDate syncStartDate;
    private String syncStatus; // IDLE, IN_PROGRESS, FAILED
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
