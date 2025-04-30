package com.fincity.saas.entity.collector.dto;

import lombok.Data;

@Data
public class WebsiteDetails {

    private LeadDetails leadDetails;
    private String utmAd;
    private String utmCampaign;
    private String utmMedium;
    private String utmSource;
    private String platform;

}