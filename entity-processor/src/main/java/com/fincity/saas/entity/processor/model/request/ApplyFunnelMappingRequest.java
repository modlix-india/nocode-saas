package com.fincity.saas.entity.processor.model.request;

import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import com.fincity.saas.entity.processor.enums.FunnelStage;
import com.fincity.saas.entity.processor.model.common.Identity;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Body for the self-contained funnel-mapping page. For one product template +
 * platform, the admin assigns deal-stages to each funnel stage AND sets the
 * conversion event for that funnel. Applying it tags each chosen stage with the
 * funnel stage ({@code FUNNEL_STAGE}) and upserts a stage-level conversion
 * mapping for it.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class ApplyFunnelMappingRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1623094857120036114L;

    private Identity productTemplateId;
    private CampaignPlatform campaignPlatform;
    private Map<FunnelStage, FunnelMapping> funnels;

    @Data
    @Accessors(chain = true)
    @NoArgsConstructor
    public static class FunnelMapping implements Serializable {

        @Serial
        private static final long serialVersionUID = 5712098461530028471L;

        /** Deal-stages assigned to this funnel stage (parent stage ids). */
        private List<Identity> stageIds;
        /**
         * Google customer id (sub-account under MCC) this mapping targets. NULL
         * for Meta (pixel on the campaign already routes correctly) and for
         * legacy callers; required for Google when more than one customer under
         * an MCC has active campaigns.
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
