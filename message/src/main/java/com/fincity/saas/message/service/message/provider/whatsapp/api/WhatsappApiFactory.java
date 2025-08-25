package com.fincity.saas.message.service.message.provider.whatsapp.api;

import com.fincity.saas.message.configuration.WebClientConfig;
import com.fincity.saas.message.configuration.message.whatsapp.ApiVersion;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.message.provider.whatsapp.business.WhatsappBusinessManagementApi;
import com.fincity.saas.message.service.message.provider.whatsapp.cloud.WhatsappBusinessCloudApi;
import com.fincity.saas.message.service.message.provider.whatsapp.graph.ResumableUploadApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class WhatsappApiFactory {

    private final WebClientConfig webClientConfig;
    private final MessageResourceService messageResourceService;

    public Mono<WhatsappBusinessCloudApi> newBusinessCloudApiFromConnection(Connection connection) {
        return webClientConfig
                .createWhatsappWebClient(connection)
                .map(wc -> new WhatsappBusinessCloudApi(wc, messageResourceService));
    }

    public Mono<WhatsappBusinessCloudApi> newBusinessCloudApiFromConnection(
            Connection connection, ApiVersion apiVersion) {
        return webClientConfig
                .createWhatsappWebClient(connection)
                .map(webClient -> new WhatsappBusinessCloudApi(webClient, apiVersion, messageResourceService));
    }

    public WhatsappBusinessCloudApi newBusinessCloudApi(WebClient webClient) {
        return new WhatsappBusinessCloudApi(webClient, messageResourceService);
    }

    public WhatsappBusinessCloudApi newBusinessCloudApi(WebClient webClient, ApiVersion apiVersion) {
        return new WhatsappBusinessCloudApi(webClient, apiVersion, messageResourceService);
    }

    public Mono<WhatsappBusinessManagementApi> newBusinessManagementApiFromConnection(Connection connection) {
        return webClientConfig
                .createWhatsappWebClient(connection)
                .map(wc -> new WhatsappBusinessManagementApi(wc, messageResourceService));
    }

    public Mono<WhatsappBusinessManagementApi> newBusinessManagementApiFromConnection(
            Connection connection, ApiVersion apiVersion) {
        return webClientConfig
                .createWhatsappWebClient(connection)
                .map(webClient -> new WhatsappBusinessManagementApi(webClient, apiVersion, messageResourceService));
    }

    public WhatsappBusinessManagementApi newBusinessManagementApi(WebClient webClient) {
        return new WhatsappBusinessManagementApi(webClient, messageResourceService);
    }

    public WhatsappBusinessManagementApi newBusinessManagementApi(WebClient webClient, ApiVersion apiVersion) {
        return new WhatsappBusinessManagementApi(webClient, apiVersion, messageResourceService);
    }

    public Mono<ResumableUploadApi> newResumableUploadApiFromConnection(Connection connection) {
        return webClientConfig
                .createWhatsappWebClient(connection)
                .map(wc -> new ResumableUploadApi(wc, messageResourceService));
    }

    public Mono<ResumableUploadApi> newResumableUploadApiFromConnection(Connection connection, ApiVersion apiVersion) {
        return webClientConfig
                .createWhatsappWebClient(connection)
                .map(webClient -> new ResumableUploadApi(webClient, apiVersion, messageResourceService));
    }

    public ResumableUploadApi newResumableUploadApi(WebClient webClient) {
        return new ResumableUploadApi(webClient, messageResourceService);
    }

    public ResumableUploadApi newResumableUploadApi(WebClient webClient, ApiVersion apiVersion) {
        return new ResumableUploadApi(webClient, apiVersion, messageResourceService);
    }
}
