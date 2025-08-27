package com.fincity.saas.message.service;

import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Function;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Primary
@Service
public class MessageResourceService extends AbstractMessageService {

    public static final String MESSAGE_PREFIX = "MESSAGE_";

    public static final String UKNOWN_ERROR = "unknown_error";
    public static final String FORBIDDEN_CREATE = "forbidden_create";
    public static final String FORBIDDEN_UPDATE = "forbidden_update";
    public static final String TEMPLATE_DATA_NOT_FOUND = "template_data_not_found";
    public static final String CONNECTION_NOT_FOUND = "connection_not_found";
    public static final String MAIL_SEND_ERROR = "mail_send_error";
    public static final String PHONE_NUMBER_REQUIRED = "phone_number_required";

    public static final String INVALID_CONNECTION_TYPE = "invalid_connection_type";
    public static final String URL_CREATION_ERROR = "url_creation_error";
    public static final String MISSING_CALL_PARAMETERS = "missing_call_parameters";
    public static final String MISSING_MESSAGE_PARAMETERS = "missing_message_parameters";
    public static final String MISSING_CONNECTION_DETAILS = "missing_connection_details";

    public static final String VERSION_MISMATCH = "version_mismatch";
    public static final String FORBIDDEN_APP_ACCESS = "forbidden_app_access";
    public static final String LOGIN_REQUIRED = "login_required";
    public static final String NAME_MISSING = "name_missing";
    public static final String DUPLICATE_NAME_FOR_ENTITY = "duplicate_name_for_entity";
    public static final String INVALID_USER_FOR_CLIENT = "invalid_user_for_client";
    public static final String INVALID_USER_ACCESS = "invalid_user_access";
    public static final String IDENTITY_MISSING = "identity_missing";
    public static final String IDENTITY_WRONG = "identity_wrong";

    public static final String UNABLE_TO_FETCH_INTERNAL_RESOURCE = "unable_to_fetch_internal_resource";

    public static final String UNABLE_TO_FETCH_EXTERNAL_RESOURCE = "unable_to_fetch_external_resource";

    // WhatsApp Template validation messages
    public static final String TEMPLATE_NOT_EDITABLE_STATUS = "template_not_editable_status";
    public static final String TEMPLATE_DAILY_EDIT_LIMIT_EXCEEDED = "template_daily_edit_limit_exceeded";
    public static final String TEMPLATE_MONTHLY_EDIT_LIMIT_EXCEEDED = "template_monthly_edit_limit_exceeded";
    public static final String TEMPLATE_NAME_LENGTH_EXCEEDED = "template_name_length_exceeded";
    public static final String TEMPLATE_NOT_FOUND_IN_WHATSAPP = "template_not_found_in_whatsapp";

    protected MessageResourceService() {
        super(Map.of(Locale.ENGLISH, ResourceBundle.getBundle("messages", Locale.ENGLISH)));
    }

    @Override
    public Mono<String> getMessage(String messageId) {

        return SecurityContextUtil.getUsersLocale()
                .flatMap(locale -> Mono.justOrEmpty(this.findResourceBundle(locale)))
                .defaultIfEmpty(this.bundleMap.get(Locale.ENGLISH))
                .map(bundle ->
                        bundle.containsKey(messageId) ? bundle.getString(messageId) : bundle.getString(UKNOWN_ERROR));
    }

    public <T> Mono<T> throwStrMessage(Function<String, GenericException> genericExceptionFunction, String message) {
        return Mono.defer(() -> Mono.just(message).map(genericExceptionFunction).flatMap(Mono::error));
    }

    private ResourceBundle findResourceBundle(Locale locale) {

        ResourceBundle bundle = this.bundleMap.get(locale);

        return bundle == null ? this.bundleMap.get(Locale.forLanguageTag(locale.getLanguage())) : bundle;
    }
}
