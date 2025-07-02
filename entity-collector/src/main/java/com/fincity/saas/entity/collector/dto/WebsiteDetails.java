package com.fincity.saas.entity.collector.dto;

import lombok.Data;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import java.io.Serial;
import lombok.experimental.Accessors;


@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class WebsiteDetails extends AbstractLeadBase {

    @Serial
    private static final long serialVersionUID = -126270115243553536L;

    private String utmAd;
    private String utmCampaign;
    private String utmAdset;
    private String utmSource;
}
