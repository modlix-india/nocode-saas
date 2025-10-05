package com.fincity.saas.message.service.message.provider.whatsapp.business;

import com.fincity.saas.message.configuration.message.whatsapp.ApiVersion;
import com.fincity.saas.message.model.message.whatsapp.business.BusinessAccount;
import com.fincity.saas.message.model.message.whatsapp.business.SubscribedApp;
import com.fincity.saas.message.model.message.whatsapp.business.WebhookConfig;
import com.fincity.saas.message.model.message.whatsapp.business.WebhookOverride;
import com.fincity.saas.message.model.message.whatsapp.config.CommerceDataItem;
import com.fincity.saas.message.model.message.whatsapp.data.FbData;
import com.fincity.saas.message.model.message.whatsapp.data.FbPagingData;
import com.fincity.saas.message.model.message.whatsapp.phone.PhoneNumber;
import com.fincity.saas.message.model.message.whatsapp.phone.PhoneNumberWebhookConfig;
import com.fincity.saas.message.model.message.whatsapp.phone.RequestCode;
import com.fincity.saas.message.model.message.whatsapp.phone.VerifyCode;
import com.fincity.saas.message.model.message.whatsapp.response.Response;
import com.fincity.saas.message.model.message.whatsapp.templates.MessageTemplate;
import com.fincity.saas.message.model.message.whatsapp.templates.response.Template;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.message.provider.whatsapp.api.AbstractWhatsappApi;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public class WhatsappBusinessManagementApi extends AbstractWhatsappApi {

    private final WhatsappBusinessManagementApiService apiService;

    public WhatsappBusinessManagementApi(WebClient webClient) {
        super(webClient);
        this.apiService = (WhatsappBusinessManagementApiService) createApiService();
    }

    public WhatsappBusinessManagementApi(WebClient webClient, ApiVersion apiVersion) {
        super(webClient, apiVersion);
        this.apiService = (WhatsappBusinessManagementApiService) createApiService();
    }

    public WhatsappBusinessManagementApi(WebClient webClient, MessageResourceService messageResourceService) {
        super(webClient, messageResourceService);
        this.apiService = (WhatsappBusinessManagementApiService) createApiService();
    }

    public WhatsappBusinessManagementApi(
            WebClient webClient, ApiVersion apiVersion, MessageResourceService messageResourceService) {
        super(webClient, apiVersion, messageResourceService);
        this.apiService = (WhatsappBusinessManagementApiService) createApiService();
    }

    @Override
    protected Object createApiService() {
        return new WhatsappBusinessManagementApiServiceImpl(webClient, messageResourceService);
    }

    public Mono<BusinessAccount> getBusinessAccount(String whatsappBusinessAccountId) {
        return apiService.getBusinessAccount(apiVersion.getValue(), whatsappBusinessAccountId);
    }

    public Mono<Response> overrideBusinessWebhook(String whatsappBusinessAccountId, WebhookOverride webhookOverride) {
        return apiService.overrideBusinessWebhook(apiVersion.getValue(), whatsappBusinessAccountId, webhookOverride);
    }

    public Mono<FbData<SubscribedApp>> getSubscribedApp(String whatsappBusinessAccountId) {
        return apiService.getSubscribedApp(apiVersion.getValue(), whatsappBusinessAccountId);
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

    public Mono<FbPagingData<Template>> retrieveTemplates(String whatsappBusinessAccountId) {
        return apiService.retrieveTemplates(apiVersion.getValue(), whatsappBusinessAccountId);
    }

    public Mono<FbPagingData<Template>> retrieveTemplates(String whatsappBusinessAccountId, int limit) {
        Map<String, Object> filters = new HashMap<>();
        filters.put("limit", Optional.of(limit));
        return apiService.retrieveTemplates(apiVersion.getValue(), whatsappBusinessAccountId, filters);
    }

    public Mono<FbPagingData<Template>> retrieveTemplates(String whatsappBusinessAccountId, String templateName) {
        Map<String, Object> filters = new HashMap<>();
        filters.put("name", templateName);
        return apiService.retrieveTemplates(apiVersion.getValue(), whatsappBusinessAccountId, filters);
    }

    public Mono<FbPagingData<Template>> retrieveTemplates(String whatsappBusinessAccountId, int limit, String after) {
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

    public Mono<FbPagingData<PhoneNumber>> retrievePhoneNumbers(String whatsappBusinessAccountId) {
        return apiService.retrievePhoneNumbers(apiVersion.getValue(), whatsappBusinessAccountId);
    }

    public Mono<Response> overridePhoneNumberWebhook(String phoneNumberId, WebhookConfig webhookConfig) {
        return apiService.overridePhoneNumberWebhook(apiVersion.getValue(), phoneNumberId, webhookConfig);
    }

    public Mono<PhoneNumberWebhookConfig> retrievePhoneNumberWebhookConfig(String phoneNumberId) {
        return apiService.retrievePhoneNumberWebhookConfig(apiVersion.getValue(), phoneNumberId);
    }

    public Mono<Response> requestCode(String phoneNumberId, RequestCode requestCode) {
        return apiService.requestCode(apiVersion.getValue(), phoneNumberId, requestCode);
    }

    public Mono<Response> verifyCode(String phoneNumberId, VerifyCode verifyCode) {
        return apiService.verifyCode(apiVersion.getValue(), phoneNumberId, verifyCode);
    }

    public Mono<FbData<CommerceDataItem>> getWhatsappCommerceSettings(String phoneNumberId, String... fields) {
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

        private Mono<Throwable> handleWhatsappApiError(ClientResponse clientResponse) {
            return AbstractWhatsappApi.handleWhatsappApiError(clientResponse, this.msgService);
        }

        @Override
        public Mono<BusinessAccount> getBusinessAccount(String apiVersion, String whatsappBusinessAccountId) {
            return webClient
                    .get()
                    .uri("/{api-version}/{whatsapp-business-account-ID}", apiVersion, whatsappBusinessAccountId)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            this::handleWhatsappApiError)
                    .bodyToMono(BusinessAccount.class);
        }

        @Override
        public Mono<FbData<SubscribedApp>> getSubscribedApp(String apiVersion, String whatsappBusinessAccountId) {
            return webClient
                    .get()
                    .uri(
                            "/{api-version}/{whatsapp-business-account-ID}/subscribed_apps",
                            apiVersion,
                            whatsappBusinessAccountId)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            this::handleWhatsappApiError)
                    .bodyToMono(new ParameterizedTypeReference<FbData<SubscribedApp>>() {});
        }

        @Override
        public Mono<Response> overrideBusinessWebhook(
                String apiVersion, String whatsappBusinessAccountId, WebhookOverride webhookOverride) {
            return webClient
                    .post()
                    .uri(
                            "/{api-version}/{whatsapp-business-account-ID}/subscribed_apps",
                            apiVersion,
                            whatsappBusinessAccountId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(webhookOverride)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            this::handleWhatsappApiError)
                    .bodyToMono(Response.class);
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
        public Mono<FbPagingData<Template>> retrieveTemplates(String apiVersion, String whatsappBusinessAccountId) {
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
                    .bodyToMono(new ParameterizedTypeReference<FbPagingData<Template>>() {});
        }

        @Override
        public Mono<FbPagingData<Template>> retrieveTemplates(
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
                    .bodyToMono(new ParameterizedTypeReference<FbPagingData<Template>>() {});
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
        public Mono<FbPagingData<PhoneNumber>> retrievePhoneNumbers(
                String apiVersion, String whatsappBusinessAccountId) {
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
                    .bodyToMono(new ParameterizedTypeReference<FbPagingData<PhoneNumber>>() {});
        }

        @Override
        public Mono<Response> overridePhoneNumberWebhook(
                String apiVersion, String phoneNumberId, WebhookConfig webhookConfig) {
            return webClient
                    .post()
                    .uri("/{api-version}/{phone-number-ID}", apiVersion, phoneNumberId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(webhookConfig)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            this::handleWhatsappApiError)
                    .bodyToMono(Response.class);
        }

        @Override
        public Mono<PhoneNumberWebhookConfig> retrievePhoneNumberWebhookConfig(
                String apiVersion, String phoneNumberId) {
            return webClient
                    .get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/{api-version}/{phone-number-ID}");
                        uriBuilder.queryParam("fields", "webhook_configuration");
                        return uriBuilder.build(apiVersion, phoneNumberId);
                    })
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            this::handleWhatsappApiError)
                    .bodyToMono(PhoneNumberWebhookConfig.class);
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
        public Mono<FbData<CommerceDataItem>> getWhatsappCommerceSettings(
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
                    .bodyToMono(new ParameterizedTypeReference<FbData<CommerceDataItem>>() {});
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
