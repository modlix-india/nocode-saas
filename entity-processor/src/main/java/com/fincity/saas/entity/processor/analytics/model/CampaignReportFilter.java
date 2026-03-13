package com.fincity.saas.entity.processor.analytics.model;

import com.fincity.saas.entity.processor.analytics.model.base.BaseFilter;
import java.io.Serial;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CampaignReportFilter extends BaseFilter<CampaignReportFilter> {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<ULong> campaignIds;
    private List<ULong> productIds;
    private List<String> platforms;
}
