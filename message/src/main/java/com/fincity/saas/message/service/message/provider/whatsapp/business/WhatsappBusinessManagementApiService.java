package com.fincity.saas.message.service.message.provider.whatsapp.business;

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
import java.util.Map;
import reactor.core.publisher.Mono;

public interface WhatsappBusinessManagementApiService {

    Mono<BusinessAccount> getBusinessAccount(String apiVersion, String whatsappBusinessAccountId);

    Mono<FbData<SubscribedApp>> getSubscribedApp(String apiVersion, String whatsappBusinessAccountId);

    Mono<Response> overrideBusinessWebhook(
            String apiVersion, String whatsappBusinessAccountId, WebhookOverride webhookOverride);

    Mono<Template> createMessageTemplate(
            String apiVersion, String whatsappBusinessAccountId, MessageTemplate messageTemplate);

    Mono<Template> updateMessageTemplate(
            String apiVersion,
            String whatsappBusinessAccountId,
            String messageTemplateId,
            MessageTemplate messageTemplate);

    Mono<Response> deleteMessageTemplate(String apiVersion, String whatsappBusinessAccountId, String name);

    Mono<FbPagingData<Template>> retrieveTemplates(String apiVersion, String whatsappBusinessAccountId);

    Mono<FbPagingData<Template>> retrieveTemplates(
            String apiVersion, String whatsappBusinessAccountId, Map<String, Object> filters);

    Mono<PhoneNumber> retrievePhoneNumber(String apiVersion, String phoneNumberId, Map<String, String> queryParams);

    Mono<FbPagingData<PhoneNumber>> retrievePhoneNumbers(
            String apiVersion, String whatsappBusinessAccountId, Map<String, String> queryParams);

    Mono<Response> overridePhoneNumberWebhook(String apiVersion, String phoneNumberId, WebhookConfig webhookConfig);

    Mono<PhoneNumberWebhookConfig> retrievePhoneNumberWebhookConfig(String apiVersion, String phoneNumberId);

    Mono<Response> requestCode(String apiVersion, String phoneNumberId, RequestCode requestCode);

    Mono<Response> verifyCode(String apiVersion, String phoneNumberId, VerifyCode verifyCode);

    Mono<FbData<CommerceDataItem>> getWhatsappCommerceSettings(
            String apiVersion, String phoneNumberId, Map<String, String> queryParams);

    Mono<Response> updateWhatsappCommerceSettings(
            String apiVersion, String phoneNumberId, CommerceDataItem commerceDataItem);

    // TODO: ADD create phone number and update phone number methods
}
