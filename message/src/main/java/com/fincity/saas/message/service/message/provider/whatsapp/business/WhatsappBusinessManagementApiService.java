package com.fincity.saas.message.service.message.provider.whatsapp.business;

import com.fincity.saas.message.model.message.whatsapp.config.CommerceDataItem;
import com.fincity.saas.message.model.message.whatsapp.config.GraphCommerceSettings;
import com.fincity.saas.message.model.message.whatsapp.phone.PhoneNumber;
import com.fincity.saas.message.model.message.whatsapp.phone.PhoneNumbers;
import com.fincity.saas.message.model.message.whatsapp.phone.RequestCode;
import com.fincity.saas.message.model.message.whatsapp.phone.VerifyCode;
import com.fincity.saas.message.model.message.whatsapp.response.Response;
import com.fincity.saas.message.model.message.whatsapp.templates.MessageTemplate;
import com.fincity.saas.message.model.message.whatsapp.templates.response.MessageTemplates;
import com.fincity.saas.message.model.message.whatsapp.templates.response.Template;
import java.util.Map;
import reactor.core.publisher.Mono;

public interface WhatsappBusinessManagementApiService {

    Mono<Template> createMessageTemplate(
            String apiVersion, String whatsappBusinessAccountId, MessageTemplate messageTemplate);

    Mono<Template> updateMessageTemplate(
            String apiVersion,
            String whatsappBusinessAccountId,
            String messageTemplateId,
            MessageTemplate messageTemplate);

    Mono<Response> deleteMessageTemplate(String apiVersion, String whatsappBusinessAccountId, String name);

    Mono<MessageTemplates> retrieveTemplates(String apiVersion, String whatsappBusinessAccountId);

    Mono<MessageTemplates> retrieveTemplates(
            String apiVersion, String whatsappBusinessAccountId, Map<String, Object> filters);

    Mono<PhoneNumber> retrievePhoneNumber(String apiVersion, String phoneNumberId, Map<String, Object> queryParams);

    Mono<PhoneNumbers> retrievePhoneNumbers(String apiVersion, String whatsappBusinessAccountId);

    Mono<Response> requestCode(String apiVersion, String phoneNumberId, RequestCode requestCode);

    Mono<Response> verifyCode(String apiVersion, String phoneNumberId, VerifyCode verifyCode);

    Mono<GraphCommerceSettings> getWhatsappCommerceSettings(
            String apiVersion, String phoneNumberId, Map<String, String> queryParams);

    Mono<Response> updateWhatsappCommerceSettings(
            String apiVersion, String phoneNumberId, CommerceDataItem commerceDataItem);
}
