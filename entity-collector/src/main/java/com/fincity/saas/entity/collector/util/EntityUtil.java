package com.fincity.saas.entity.collector.util;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.collector.dto.EntityIntegration;
import com.fincity.saas.entity.collector.dto.EntityResponse;
import com.fincity.saas.entity.collector.enums.LeadSource;
import com.fincity.saas.entity.collector.enums.LeadSubSource;
import com.fincity.saas.entity.collector.fiegn.IFeignCoreService;
import com.fincity.saas.entity.collector.model.LeadDetails;
import com.fincity.saas.entity.collector.service.EntityCollectorLogService;
import com.fincity.saas.entity.collector.service.EntityCollectorMessageResourceService;
import lombok.extern.slf4j.Slf4j;
import org.jooq.types.ULong;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
public final class EntityUtil {

    private static final WebClient webClient = WebClient.create();

    public static final String CONNECTION_NAME = "META_API";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";
    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final String UNKNOWN = "UNKNOWN";
    private static final String CACHE_NAME_REST_OAUTH2 = "RestOAuthToken";

    public static Mono<Void> sendEntityToTarget(EntityIntegration integration, EntityResponse leadData) {
        return FlatMapUtil.flatMapMono(() -> sendToUrl(integration.getPrimaryTarget(), leadData), result -> {
            String secondary = integration.getSecondaryTarget();
            return (secondary != null && !secondary.isBlank()) ? sendToUrl(secondary, leadData) : Mono.empty();
        });
    }

    private static Mono<Void> sendToUrl(String url, EntityResponse body) {
        return EntityUtil.webClient
                .post()
                .uri(url)
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .then();
    }

    public static Mono<String> fetchOAuthToken(IFeignCoreService coreService, String clientCode, String appCode) {
        return coreService.getConnectionOAuth2Token(clientCode, appCode, CONNECTION_NAME);
    }

    public static Mono<String> fetchOAuthToken(
            IFeignCoreService coreService,
            String clientCode,
            String appCode,
            EntityCollectorMessageResourceService messageService,
            EntityCollectorLogService logService,
            ULong logId) {

        return coreService
                .getConnectionOAuth2Token( clientCode, appCode, CONNECTION_NAME)
                .onErrorResume(error ->
                        logService.updateOnError(logId, error.getMessage()).then(Mono.empty()));
    }

    public static void populateStaticFields(
            LeadDetails lead,
            String platform,
            LeadSource source,
            LeadSubSource subSource) {
        lead.setPlatform(platform);
        lead.setSource(source);
        lead.setSubSource(subSource);
    }

    public static String getClientIpAddress(ServerHttpRequest request) {
        String ipFromHeader = request.getHeaders().getFirst(FORWARDED_FOR);

        if (ipFromHeader != null && !ipFromHeader.isBlank()) {
            return ipFromHeader.split(",")[0].trim();
        }
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        return UNKNOWN;
    }
}
