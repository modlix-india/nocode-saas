package com.fincity.saas.entity.collector.util;

import static com.fincity.saas.entity.collector.util.EntityUtil.fetchOAuthToken;
import static com.fincity.saas.entity.collector.util.MetaEntityUtil.buildCampaignDetails;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.collector.dto.EntityIntegration;
import com.fincity.saas.entity.collector.dto.EntityResponse;
import com.fincity.saas.entity.collector.dto.WebsiteDetails;
import com.fincity.saas.entity.collector.fiegn.IFeignCoreService;
import com.fincity.saas.entity.collector.model.LeadDetails;
import com.fincity.saas.entity.collector.service.EntityCollectorLogService;
import com.fincity.saas.entity.collector.service.EntityCollectorMessageResourceService;
import org.jooq.types.ULong;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

public final class WebsiteEntityUtil {

    private static final String FORWARDED_HOST = "X-Forwarded-Host";

    public static String getHost(ServerHttpRequest request) {
        return request.getHeaders().getFirst(FORWARDED_HOST);
    }

    public static Mono<EntityResponse> normalizeWebsiteEntity(
            WebsiteDetails websiteDetails,
            EntityIntegration integration,
            IFeignCoreService coreService,
            EntityCollectorMessageResourceService messageService,
            EntityCollectorLogService logService,
            ULong logId) {

        LeadDetails lead = new LeadDetails().createLead(websiteDetails);

        EntityResponse response = new EntityResponse();
        response.setLeadDetails(lead);
        response.setAppCode(integration.getOutAppCode());
        response.setClientCode(integration.getClientCode());

        String adId = websiteDetails.getUtmAd();

        if (adId == null || adId.isBlank()) {
            return Mono.just(response);
        }

        return FlatMapUtil.flatMapMonoWithNull(
                        () -> fetchOAuthToken(coreService, integration.getClientCode(), integration.getInAppCode()),
                        token -> buildCampaignDetails(adId, token),
                        (token, campaignDetails) -> {
                            response.setCampaignDetails(campaignDetails);
                            return Mono.just(response);
                        })
                .switchIfEmpty(messageService
                        .getMessage(EntityCollectorMessageResourceService.FAILED_NORMALIZE_ENTITY)
                        .flatMap(msg -> logService.updateOnError(logId, msg).then(Mono.empty())));
    }
}
