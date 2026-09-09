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

    public static final String EXOTEL_REQUEST_FAILED = "exotel_request_failed";
    public static final String EXOTEL_APP_NOT_RETURNED = "exotel_app_not_returned";
    public static final String EXOTEL_APP_NOT_DELETED = "exotel_app_not_deleted";
    public static final String EXOTEL_IDENTITY_CLAIMED = "exotel_identity_claimed";
    public static final String EXOTEL_MAPPING_NOT_READABLE = "exotel_mapping_not_readable";
    public static final String EXOTEL_VIRTUAL_NUMBER_NOT_APPLIED = "exotel_virtual_number_not_applied";
    public static final String EXOTEL_MAPPING_READ_FAILED = "exotel_mapping_read_failed";
    public static final String EXOTEL_AGENT_NOT_DIAL_READY = "exotel_agent_not_dial_ready";
    public static final String EXOTEL_NO_SIP_IDENTITY = "exotel_no_sip_identity";
    public static final String EXOTEL_NO_CALL_SID = "exotel_no_call_sid";

    public static final String EXOTEL_REVOCATION_FAILED = "exotel_revocation_failed";

    public static final String EXOTEL_UNREADABLE_RESPONSE = "exotel_unreadable_response";

    public static final String EXOTEL_INVALID_PHONE_NUMBER = "exotel_invalid_phone_number";
    public static final String FORBIDDEN_CREATE = "forbidden_create";
    public static final String FORBIDDEN_UPDATE = "forbidden_update";
    public static final String CONNECTION_NOT_FOUND = "connection_not_found";
    public static final String CONNECTION_TOKEN_NOT_FOUND = "connection_token_not_found";
    public static final String MAIL_SEND_ERROR = "mail_send_error";
    public static final String PHONE_NUMBER_REQUIRED = "phone_number_required";

    public static final String INVALID_CONNECTION_TYPE = "invalid_connection_type";
    public static final String URL_CREATION_ERROR = "url_creation_error";
    public static final String MISSING_CALL_PARAMETERS = "missing_call_parameters";
    public static final String MISSING_CONNECTION_DETAILS = "missing_connection_details";
    public static final String BROWSER_CALLING_NOT_SUPPORTED = "browser_calling_not_supported";
    public static final String CALL_APP_NOT_INITIALIZED = "call_app_not_initialized";
    public static final String AGENT_NOT_PROVISIONED = "agent_not_provisioned";
    public static final String AGENT_HAS_NO_EMAIL = "agent_has_no_email";
    public static final String DUPLICATE_CALL_SID = "duplicate_call_sid";
    public static final String CALL_NOT_FOUND = "call_not_found";

    public static final String VERSION_MISMATCH = "version_mismatch";
    public static final String FORBIDDEN_APP_ACCESS = "forbidden_app_access";
    public static final String LOGIN_REQUIRED = "login_required";
    public static final String NAME_MISSING = "name_missing";
    public static final String DUPLICATE_NAME_FOR_ENTITY = "duplicate_name_for_entity";
    public static final String INVALID_USER_FOR_CLIENT = "invalid_user_for_client";
    public static final String INVALID_USER_ACCESS = "invalid_user_access";
    public static final String IDENTITY_MISSING = "identity_missing";
    public static final String IDENTITY_WRONG = "identity_wrong";
    public static final String UNABLE_TO_UPDATE = "unable_to_update";

    public static final String UNABLE_TO_FETCH_INTERNAL_RESOURCE = "unable_to_fetch_internal_resource";

    public static final String UNABLE_TO_FETCH_EXTERNAL_RESOURCE = "unable_to_fetch_external_resource";

    // WhatsApp Template validation messages

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
