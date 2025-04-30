package com.fincity.saas.entity.collector.util;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.collector.dto.EntityIntegration;
import com.fincity.saas.entity.collector.dto.EntityResponse;
import com.fincity.saas.entity.collector.dto.LeadDetails;
import com.fincity.saas.entity.collector.dto.WebsiteDetails;
import com.fincity.saas.entity.collector.fiegn.IFeignCoreService;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

import static com.fincity.saas.entity.collector.util.EntityUtil.fetchOAuthToken;
import static com.fincity.saas.entity.collector.util.EntityUtil.populateStaticFields;
import static com.fincity.saas.entity.collector.util.MetaEntityUtil.buildCampaignDetails;

public class WebsiteEntityUtil {

    private static final String FORWARDED_HOST = "X-Forwarded-Host";


    public static String getHost(ServerHttpRequest request) {
        return request.getHeaders().getFirst(FORWARDED_HOST);
    }

    public static Mono<EntityResponse> handleWebsiteLeadNormalization(WebsiteDetails websiteDetails, EntityIntegration integration, IFeignCoreService coreService) {

        String adId = websiteDetails.getUtmAd();
        LeadDetails lead = new LeadDetails();
        websiteDetails.setLeadDetails(lead);

        populateStaticFields(lead, integration, websiteDetails.getPlatform(), "WEBSITE", "contactForm");

        if (adId == null || adId.isBlank()) {
            EntityResponse response = new EntityResponse();
            response.setLeadDetails(lead);
            return Mono.just(response);
        }
        return FlatMapUtil.flatMapMonoWithNull(
                () -> fetchOAuthToken(coreService, integration.getClientCode(), integration.getAppCode()),
                token -> buildCampaignDetails(adId, token),
                (token, campaignDetails) -> {
                    lead.setClientCode(integration.getClientCode());
                    lead.setAppCode(integration.getAppCode());
                    EntityResponse response = new EntityResponse();
                    response.setLeadDetails(lead);
                    response.setCampaignDetails(campaignDetails);
                    return Mono.just(response);
                }
        );
    }
}