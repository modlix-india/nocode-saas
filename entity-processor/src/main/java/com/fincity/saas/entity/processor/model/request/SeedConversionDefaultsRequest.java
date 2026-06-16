package com.fincity.saas.entity.processor.model.request;

import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import com.fincity.saas.entity.processor.enums.FunnelStage;
import com.fincity.saas.entity.processor.model.common.Identity;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Bulk-seed conversion-action mappings for every tagged stage under a product
 * template, for a single ad platform. The caller supplies one default per funnel
 * tag (LEAD/MQL/SQL/...); the service walks tagged stages and creates a mapping
 * row for each, skipping any that already have an active mapping.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class SeedConversionDefaultsRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = -3094820018349573711L;

    private Identity productTemplateId;
    private CampaignPlatform campaignPlatform;
    private Map<FunnelStage, FunnelStageDefault> defaults;

    @Data
    @Accessors(chain = true)
    @NoArgsConstructor
    public static class FunnelStageDefault implements Serializable {

        @Serial
        private static final long serialVersionUID = -8437228294817223610L;

        /**
         * Google customer id (sub-account under MCC) this mapping targets. NULL
         * for Meta and legacy callers.
         */
        private String platformAccountId;

        private String eventName;
        private String platformActionId;
        private BigDecimal defaultValue;
        private String currency;
        private String valueFieldPath;
        private String testEventCode;
    }
}
