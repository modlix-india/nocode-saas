package com.fincity.saas.message.service.message.provider.whatsapp;

import com.fincity.saas.message.configuration.WebClientConfig;
import com.fincity.saas.message.configuration.message.whatsapp.ApiVersion;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.service.message.provider.whatsapp.cloud.WhatsappBusinessCloudApi;
import com.fincity.saas.message.service.message.provider.whatsapp.business.WhatsappBusinessManagementApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class WhatsappApiFactory {

    private final WebClientConfig webClientConfig;

    public Mono<WhatsappBusinessCloudApi> newBusinessCloudApi(String appCode, String clientCode) {
        return webClientConfig.createWhatsappWebClient(appCode, clientCode).map(WhatsappBusinessCloudApi::new);
    }

    public Mono<WhatsappBusinessCloudApi> newBusinessCloudApi(
            String appCode, String clientCode, ApiVersion apiVersion) {
        return webClientConfig
                .createWhatsappWebClient(appCode, clientCode)
                .map(webClient -> new WhatsappBusinessCloudApi(webClient, apiVersion));
    }

    public Mono<WhatsappBusinessCloudApi> newBusinessCloudApiFromConnection(Connection connection) {
        return webClientConfig.createWhatsappWebClient(connection).map(WhatsappBusinessCloudApi::new);
    }

    public Mono<WhatsappBusinessCloudApi> newBusinessCloudApiFromConnection(
            Connection connection, ApiVersion apiVersion) {
        return webClientConfig
                .createWhatsappWebClient(connection)
                .map(webClient -> new WhatsappBusinessCloudApi(webClient, apiVersion));
    }

    public WhatsappBusinessCloudApi newBusinessCloudApi(WebClient webClient) {
        return new WhatsappBusinessCloudApi(webClient);
    }

    public WhatsappBusinessCloudApi newBusinessCloudApi(WebClient webClient, ApiVersion apiVersion) {
        return new WhatsappBusinessCloudApi(webClient, apiVersion);
    }

    public Mono<WhatsappBusinessManagementApi> newBusinessManagementApi(String appCode, String clientCode) {
        return webClientConfig.createWhatsappWebClient(appCode, clientCode).map(WhatsappBusinessManagementApi::new);
    }

    public Mono<WhatsappBusinessManagementApi> newBusinessManagementApi(
            String appCode, String clientCode, ApiVersion apiVersion) {
        return webClientConfig
                .createWhatsappWebClient(appCode, clientCode)
                .map(webClient -> new WhatsappBusinessManagementApi(webClient, apiVersion));
    }

    public Mono<WhatsappBusinessManagementApi> newBusinessManagementApiFromConnection(Connection connection) {
        return webClientConfig.createWhatsappWebClient(connection).map(WhatsappBusinessManagementApi::new);
    }

    public Mono<WhatsappBusinessManagementApi> newBusinessManagementApiFromConnection(
            Connection connection, ApiVersion apiVersion) {
        return webClientConfig
                .createWhatsappWebClient(connection)
                .map(webClient -> new WhatsappBusinessManagementApi(webClient, apiVersion));
    }

    public WhatsappBusinessManagementApi newBusinessManagementApi(WebClient webClient) {
        return new WhatsappBusinessManagementApi(webClient);
    }

    public WhatsappBusinessManagementApi newBusinessManagementApi(WebClient webClient, ApiVersion apiVersion) {
        return new WhatsappBusinessManagementApi(webClient, apiVersion);
    }
}
