package com.fincity.saas.entity.collector.util;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.collector.dto.EntityIntegration;
import com.fincity.saas.entity.collector.dto.EntityResponse;
import com.fincity.saas.entity.collector.enums.LeadSource;
import com.fincity.saas.entity.collector.enums.LeadSubSource;
import com.fincity.saas.entity.collector.model.LeadDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
public final class EntityUtil {

    private static final WebClient webClient = WebClient.create();

    public static final String META_CONNECTION_NAME = "META_API";
    public static final String GOOGLE_CONNECTION_NAME = "GOOGLE_API";
    public static final String GOOGLE_UTM_SOURCE = "google";
    public static final String META_UTM_SOURCE = "facebook";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";
    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final String UNKNOWN = "UNKNOWN";
    private static final String FORWARDED_HOST = "X-Forwarded-Host";


    public static String getHost(ServerHttpRequest request) {
        return request.getHeaders().getFirst(FORWARDED_HOST);
    }

    public static Mono<Map<String, Object>> sendEntityToTarget(EntityIntegration integration, EntityResponse leadData) {

        return FlatMapUtil.flatMapMono(

                () -> sendToUrl(integration.getPrimaryTarget(), leadData),

                result -> {
                    String secondary = integration.getSecondaryTarget();
                    return (secondary != null && !secondary.isBlank()) ? sendToUrl(secondary, leadData) : Mono.just(result);
                });
    }

    private static Mono<Map<String, Object>> sendToUrl(String url, EntityResponse body) {
        return EntityUtil.webClient
                .post()
                .uri(url)
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<>() {});
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
