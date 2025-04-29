package com.fincity.saas.entity.collector.util;

import com.fincity.saas.entity.collector.dto.EntityIntegration;
import com.fincity.saas.entity.collector.dto.EntityResponse;
import com.fincity.saas.entity.collector.dto.LeadDetails;
import com.fincity.saas.entity.collector.fiegn.IFeignCoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Slf4j
public class EntityUtil {

    private static final WebClient webClient = WebClient.create();

    public static final String CONNECTION_NAME = "meta_facebook_connection";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";

    public static Mono<Void> sendEntityToTarget(EntityIntegration integration, EntityResponse leadData) {
        if (integration.getPrimaryTarget() == null || integration.getPrimaryTarget().isBlank()) {
            return Mono.error(new RuntimeException("Primary target URL is not configured in entity integration."));
        }

        Mono<Void> primarySend = sendToUrl(integration.getPrimaryTarget(), leadData);
        Mono<Void> secondarySend = Optional.ofNullable(integration.getSecondaryTarget())
                .filter(s -> !s.isBlank())
                .map(url -> sendToUrl(url, leadData))
                .orElse(Mono.empty());

        return Mono.when(primarySend, secondarySend);
    }

    private static Mono<Void> sendToUrl(String url, EntityResponse body) {
        return EntityUtil.webClient.post()
                .uri(url)
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .then();
    }

    public static Mono<String> fetchOAuthToken(IFeignCoreService coreService, String clientCode, String appCode) {
        return coreService.getConnectionOAuth2Token(
                "",
                "",
                "",
                clientCode,
                appCode,
                CONNECTION_NAME
        );
    }

    public static void populateStaticFields(LeadDetails lead, EntityIntegration integration, String platform, String source, String subSource) {
        lead.setClientCode(integration.getClientCode());
        lead.setAppCode(integration.getAppCode());
        lead.setPlatform(platform);
        lead.setSource(source);
        lead.setSubSource(subSource);
    }
}
