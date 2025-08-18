package com.fincity.saas.multi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.multi.fiegn.IFeignCoreService;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class RestUtilService {

    private final IFeignCoreService coreService;

    public RestUtilService(IFeignCoreService coreService) {
        this.coreService = coreService;
    }

    public Mono<JsonNode> metaDebugToken(String forwardedHost, String forwardedPort, String clientCode, String headerAppCode,
        String connectionName) {

        WebClient webClient = WebClient.create();

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> coreService.getConnectionOAuth2Token(clientCode, headerAppCode, connectionName),

                (ca, token) -> webClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .scheme("https")
                                .host("graph.facebook.com")
                                .path("/debug_token")
                                .queryParam("input_token", token)
                                .build())
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .bodyToMono(JsonNode.class)

        );
    }
}
