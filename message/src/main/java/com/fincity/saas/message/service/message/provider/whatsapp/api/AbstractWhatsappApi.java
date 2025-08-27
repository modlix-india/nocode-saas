package com.fincity.saas.message.service.message.provider.whatsapp.api;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.message.configuration.message.whatsapp.ApiVersion;
import com.fincity.saas.message.configuration.message.whatsapp.WhatsappApiConfig;
import com.fincity.saas.message.model.message.whatsapp.errors.WhatsappApiError;
import com.fincity.saas.message.service.MessageResourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public abstract class AbstractWhatsappApi {

    protected final ApiVersion apiVersion;
    protected final WebClient webClient;
    protected final MessageResourceService messageResourceService;

    protected AbstractWhatsappApi(WebClient webClient) {
        this(webClient, WhatsappApiConfig.API_VERSION, null);
    }

    protected AbstractWhatsappApi(WebClient webClient, ApiVersion apiVersion) {
        this(webClient, apiVersion, null);
    }

    protected AbstractWhatsappApi(WebClient webClient, MessageResourceService messageResourceService) {
        this(webClient, WhatsappApiConfig.API_VERSION, messageResourceService);
    }

    protected AbstractWhatsappApi(
            WebClient webClient, ApiVersion apiVersion, MessageResourceService messageResourceService) {
        this.apiVersion = apiVersion;
        this.webClient = webClient;
        this.messageResourceService = messageResourceService;
    }

    protected static Mono<Throwable> handleWhatsappApiError(
            ClientResponse clientResponse, MessageResourceService msgService) {
        Logger logger = LoggerFactory.getLogger(AbstractWhatsappApi.class);

        return clientResponse.bodyToMono(WhatsappApiError.class).flatMap(errorBody -> {
            logger.error("Error response received from WhatsApp API: {}", errorBody);

            return msgService.throwStrMessage(
                    msg -> new GenericException(
                            HttpStatus.valueOf(clientResponse.statusCode().value()), msg),
                    errorBody.getError().getMessage()
                            + (errorBody.getError().getErrorUserSubtitle() != null
                                    ? ". " + errorBody.getError().getErrorUserSubtitle()
                                    : "")
                            + (errorBody.getError().getErrorUserMsg() != null
                                    ? ". " + errorBody.getError().getErrorUserMsg()
                                    : ""));
        });
    }

    protected abstract Object createApiService();
}
