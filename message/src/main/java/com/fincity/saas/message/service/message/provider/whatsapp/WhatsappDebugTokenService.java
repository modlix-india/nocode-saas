package com.fincity.saas.message.service.message.provider.whatsapp;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.message.service.RestConnectionService;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.Map;

@Service
public class WhatsappDebugTokenService extends RestConnectionService {

    public Mono<Map<String, Object>> debugToken(String connectionName) {

        WebClient webClient = WebClient.create();

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.getConnection(ca.getUrlAppCode(), ca.getClientCode(), connectionName),

                (ca, connection) ->
                        this.getConnectionOAuth2Token(ca.getUrlAppCode(), ca.getClientCode(), connectionName),

                (ca, connection, token) -> {

                    @SuppressWarnings("unchecked")
                    Map<String, Object> tokenDetails = (Map<String, Object>)
                            connection.getConnectionDetails().get("tokenDetails");

                    @SuppressWarnings("unchecked")
                    Map<String, Object> queryParams = (Map<String, Object>) tokenDetails.get("queryParams");

                    String clientId = (String) queryParams.get("client_id");
                    String clientSecret = (String) queryParams.get("client_secret");
                    String appAccessToken = clientId + "|" + clientSecret;

                    return webClient
                            .get()
                            .uri(uriBuilder -> uriBuilder
                                    .scheme("https")
                                    .host("graph.facebook.com")
                                    .path("/debug_token")
                                    .queryParam("input_token", token)
                                    .queryParam( "access_token", appAccessToken)
                                    .build())
                            .retrieve()
                            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {});
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappTemplateService.createTemplate"));

    }
}
