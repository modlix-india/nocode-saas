package com.fincity.saas.entity.processor.model.request;

import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import com.fincity.saas.entity.processor.model.common.Identity;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class CampaignRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = -1420136147009444788L;

    private String campaignId;
    private String campaignName;
    private String campaignType;
    private CampaignPlatform campaignPlatform;
    private Identity productId;

    // Platform context (optional on create — MetricsSyncService.ensurePlatformContext
    // lazily backfills these on first sync if absent, so old UI clients keep working).
    private String platformAccountId; // Meta: ad_account_id;     Google: customer_id
    private String platformLoginId;   // Meta: n/a;               Google: MCC login_customer_id
    private String platformDatasetId; // Meta: Pixel/Dataset ID;  Google: n/a
}
