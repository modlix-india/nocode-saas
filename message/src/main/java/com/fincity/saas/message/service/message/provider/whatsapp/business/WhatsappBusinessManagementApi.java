package com.fincity.saas.message.service.message.provider.whatsapp.business;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.message.configuration.message.whatsapp.ApiVersion;
import com.fincity.saas.message.configuration.message.whatsapp.WhatsappApiConfig;
import com.fincity.saas.message.model.message.whatsapp.config.CommerceDataItem;
import com.fincity.saas.message.model.message.whatsapp.config.GraphCommerceSettings;
import com.fincity.saas.message.model.message.whatsapp.errors.WhatsappApiError;
import com.fincity.saas.message.model.message.whatsapp.phone.PhoneNumber;
import com.fincity.saas.message.model.message.whatsapp.phone.PhoneNumbers;
import com.fincity.saas.message.model.message.whatsapp.phone.RequestCode;
import com.fincity.saas.message.model.message.whatsapp.phone.VerifyCode;
import com.fincity.saas.message.model.message.whatsapp.response.Response;
import com.fincity.saas.message.model.message.whatsapp.templates.MessageTemplate;
import com.fincity.saas.message.model.message.whatsapp.templates.response.MessageTemplates;
import com.fincity.saas.message.model.message.whatsapp.templates.response.Template;
import com.fincity.saas.message.service.MessageResourceService;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public class WhatsappBusinessManagementApi {

    private final WhatsappBusinessManagementApiService apiService;
    private final ApiVersion apiVersion;
    private final WebClient webClient;
    private final MessageResourceService messageResourceService;

    public WhatsappBusinessManagementApi(WebClient webClient) {
        this(webClient, WhatsappApiConfig.API_VERSION, null);
    }

    public WhatsappBusinessManagementApi(WebClient webClient, ApiVersion apiVersion) {
        this(webClient, apiVersion, null);
    }

    public WhatsappBusinessManagementApi(WebClient webClient, MessageResourceService messageResourceService) {
        this(webClient, WhatsappApiConfig.API_VERSION, messageResourceService);
    }

    public WhatsappBusinessManagementApi(
            WebClient webClient, ApiVersion apiVersion, MessageResourceService messageResourceService) {
        this.apiVersion = apiVersion;
        this.webClient = webClient;
        this.messageResourceService = messageResourceService;
        this.apiService = new WhatsappBusinessManagementApiServiceImpl(webClient, messageResourceService);
    }

    public Mono<Template> createMessageTemplate(String whatsappBusinessAccountId, MessageTemplate messageTemplate) {
        return apiService.createMessageTemplate(apiVersion.getValue(), whatsappBusinessAccountId, messageTemplate);
    }

    public Mono<Template> updateMessageTemplate(
            String whatsappBusinessAccountId, String messageTemplateId, MessageTemplate messageTemplate) {
        return apiService.updateMessageTemplate(
                apiVersion.getValue(), whatsappBusinessAccountId, messageTemplateId, messageTemplate);
    }

    public Mono<Response> deleteMessageTemplate(String whatsappBusinessAccountId, String name) {
        return apiService.deleteMessageTemplate(apiVersion.getValue(), whatsappBusinessAccountId, name);
    }

    public Mono<MessageTemplates> retrieveTemplates(String whatsappBusinessAccountId) {
        return apiService.retrieveTemplates(apiVersion.getValue(), whatsappBusinessAccountId);
    }

    public Mono<MessageTemplates> retrieveTemplates(String whatsappBusinessAccountId, int limit) {
        Map<String, Object> filters = new HashMap<>();
        filters.put("limit", Optional.of(limit));
        return apiService.retrieveTemplates(apiVersion.getValue(), whatsappBusinessAccountId, filters);
    }

    public Mono<MessageTemplates> retrieveTemplates(String whatsappBusinessAccountId, String templateName) {
        Map<String, Object> filters = new HashMap<>();
        filters.put("name", templateName);
        return apiService.retrieveTemplates(apiVersion.getValue(), whatsappBusinessAccountId, filters);
    }

    public Mono<MessageTemplates> retrieveTemplates(String whatsappBusinessAccountId, int limit, String after) {
        Map<String, Object> filters = new HashMap<>();
        filters.put("limit", Optional.of(limit));
        filters.put("after", after);
        return apiService.retrieveTemplates(apiVersion.getValue(), whatsappBusinessAccountId, filters);
    }

    public Mono<PhoneNumber> retrievePhoneNumber(String phoneNumberId) {
        return apiService.retrievePhoneNumber(apiVersion.getValue(), phoneNumberId, new HashMap<>());
    }

    public Mono<PhoneNumber> retrievePhoneNumber(String phoneNumberId, String... fields) {
        Objects.requireNonNull(fields, "Fields cannot be null");
        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("fields", String.join(",", fields));
        return apiService.retrievePhoneNumber(apiVersion.getValue(), phoneNumberId, queryParams);
    }

    public Mono<PhoneNumbers> retrievePhoneNumbers(String whatsappBusinessAccountId) {
        return apiService.retrievePhoneNumbers(apiVersion.getValue(), whatsappBusinessAccountId);
    }

    public Mono<Response> requestCode(String phoneNumberId, RequestCode requestCode) {
        return apiService.requestCode(apiVersion.getValue(), phoneNumberId, requestCode);
    }

    public Mono<Response> verifyCode(String phoneNumberId, VerifyCode verifyCode) {
        return apiService.verifyCode(apiVersion.getValue(), phoneNumberId, verifyCode);
    }

    public Mono<GraphCommerceSettings> getWhatsappCommerceSettings(String phoneNumberId, String... fields) {
        Objects.requireNonNull(fields, "Fields cannot be null");
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("fields", String.join(",", fields));
        return apiService.getWhatsappCommerceSettings(apiVersion.getValue(), phoneNumberId, queryParams);
    }

    public Mono<Response> updateWhatsappCommerceSettings(String phoneNumberId, CommerceDataItem commerceDataItem) {
        return apiService.updateWhatsappCommerceSettings(apiVersion.getValue(), phoneNumberId, commerceDataItem);
    }

    private record WhatsappBusinessManagementApiServiceImpl(WebClient webClient, MessageResourceService msgService)
            implements WhatsappBusinessManagementApiService {

        private static final Logger logger = LoggerFactory.getLogger(WhatsappBusinessManagementApiServiceImpl.class);

        private Mono<Throwable> handleWhatsappApiError(ClientResponse clientResponse) {
            return clientResponse.bodyToMono(WhatsappApiError.class).flatMap(errorBody -> {
                logger.error("Error response received from WhatsApp API: {}", errorBody);

                return this.msgService.throwStrMessage(
                        msg -> new GenericException(
                                HttpStatus.valueOf(clientResponse.statusCode().value()), msg),
                        errorBody.getError().getMessage());
            });
        }

        @Override
        public Mono<Template> createMessageTemplate(
                String apiVersion, String whatsappBusinessAccountId, MessageTemplate messageTemplate) {
            return webClient
                    .post()
                    .uri(
                            "/{api-version}/{whatsapp-business-account-ID}/message_templates",
                            apiVersion,
                            whatsappBusinessAccountId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(messageTemplate)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            this::handleWhatsappApiError)
                    .bodyToMono(Template.class);
        }

        @Override
        public Mono<Template> updateMessageTemplate(
                String apiVersion,
                String whatsappBusinessAccountId,
                String messageTemplateId,
                MessageTemplate messageTemplate) {
            return webClient
                    .post()
                    .uri(
                            "/{api-version}/{whatsapp-business-account-ID}/message_templates/{message-template-id}",
                            apiVersion,
                            whatsappBusinessAccountId,
                            messageTemplateId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(messageTemplate)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            this::handleWhatsappApiError)
                    .bodyToMono(Template.class);
        }

        @Override
        public Mono<Response> deleteMessageTemplate(String apiVersion, String whatsappBusinessAccountId, String name) {
            return webClient
                    .delete()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{api-version}/{whatsapp-business-account-ID}/message_templates")
                            .queryParam("name", name)
                            .build(apiVersion, whatsappBusinessAccountId))
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            this::handleWhatsappApiError)
                    .bodyToMono(Response.class);
        }

        @Override
        public Mono<MessageTemplates> retrieveTemplates(String apiVersion, String whatsappBusinessAccountId) {
            return webClient
                    .get()
                    .uri(
                            "/{api-version}/{whatsapp-business-account-ID}/message_templates",
                            apiVersion,
                            whatsappBusinessAccountId)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            this::handleWhatsappApiError)
                    .bodyToMono(MessageTemplates.class);
        }

        @Override
        public Mono<MessageTemplates> retrieveTemplates(
                String apiVersion, String whatsappBusinessAccountId, Map<String, Object> filters) {
            return webClient
                    .get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/{api-version}/{whatsapp-business-account-ID}/message_templates");
                        filters.forEach(uriBuilder::queryParam);
                        return uriBuilder.build(apiVersion, whatsappBusinessAccountId);
                    })
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            this::handleWhatsappApiError)
                    .bodyToMono(MessageTemplates.class);
        }

        @Override
        public Mono<PhoneNumber> retrievePhoneNumber(
                String apiVersion, String phoneNumberId, Map<String, Object> queryParams) {
            return webClient
                    .get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/{api-version}/{phone-number-ID}");
                        queryParams.forEach(uriBuilder::queryParam);
                        return uriBuilder.build(apiVersion, phoneNumberId);
                    })
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            this::handleWhatsappApiError)
                    .bodyToMono(PhoneNumber.class);
        }

        @Override
        public Mono<PhoneNumbers> retrievePhoneNumbers(String apiVersion, String whatsappBusinessAccountId) {
            return webClient
                    .get()
                    .uri(
                            "/{api-version}/{whatsapp-business-account-ID}/phone_numbers",
                            apiVersion,
                            whatsappBusinessAccountId)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            this::handleWhatsappApiError)
                    .bodyToMono(PhoneNumbers.class);
        }

        @Override
        public Mono<Response> requestCode(String apiVersion, String phoneNumberId, RequestCode requestCode) {
            return webClient
                    .post()
                    .uri("/{api-version}/{phone-number-ID}/request_code", apiVersion, phoneNumberId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestCode)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            this::handleWhatsappApiError)
                    .bodyToMono(Response.class);
        }

        @Override
        public Mono<Response> verifyCode(String apiVersion, String phoneNumberId, VerifyCode verifyCode) {
            return webClient
                    .post()
                    .uri("/{api-version}/{phone-number-ID}/verify_code", apiVersion, phoneNumberId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(verifyCode)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            this::handleWhatsappApiError)
                    .bodyToMono(Response.class);
        }

        @Override
        public Mono<GraphCommerceSettings> getWhatsappCommerceSettings(
                String apiVersion, String phoneNumberId, Map<String, String> queryParams) {
            return webClient
                    .get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/{api-version}/{phone-number-ID}/whatsapp_commerce_settings");
                        queryParams.forEach(uriBuilder::queryParam);
                        return uriBuilder.build(apiVersion, phoneNumberId);
                    })
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            this::handleWhatsappApiError)
                    .bodyToMono(GraphCommerceSettings.class);
        }

        @Override
        public Mono<Response> updateWhatsappCommerceSettings(
                String apiVersion, String phoneNumberId, CommerceDataItem commerceDataItem) {
            return webClient
                    .post()
                    .uri("/{api-version}/{phone-number-ID}/whatsapp_commerce_settings", apiVersion, phoneNumberId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(commerceDataItem)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            this::handleWhatsappApiError)
                    .bodyToMono(Response.class);
        }
    }
}
