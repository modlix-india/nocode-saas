package com.fincity.saas.entity.collector.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.io.Serial;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class WebsiteDetails extends AbstractLeadBase {

    @Serial
    private static final long serialVersionUID = -126270115243553536L;

    private String utm_ad;
    private String utm_campaign;
    private String utm_adset;
    private String utm_source;
}