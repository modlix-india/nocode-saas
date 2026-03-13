package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
public class CampaignMetric {
    private ULong id;
    private String code;
    private String appCode;
    private String clientCode;
    private ULong campaignId;
    private ULong adsetId;
    private ULong adId;
    private LocalDate metricDate;
    private long impressions;
    private long clicks;
    private BigDecimal spend;
    private long platformWL;
    private long platformFL;
    private String currency;
    private CampaignPlatform platform;
}
