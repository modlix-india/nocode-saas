package com.fincity.saas.commons.mongo.service;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;

import reactor.core.publisher.Mono;

public abstract class AbstractMongoMessageResourceService extends AbstractMessageService {

    public static final String OBJECT_NOT_FOUND = "object_not_found";
    public static final String OBJECT_NOT_FOUND_TO_UPDATE = "object_not_found_to_update";
    public static final String FORBIDDEN_CREATE = "forbidden_create";
    public static final String FORBIDDEN_PERMISSION = "forbidden_permission";
    public static final String UNABLE_TO_DELETE = "unable_to_delete";
    public static final String OBJECT_NOT_UPDATABLE = "object_not_updatable";
    public static final String USER_CREDENTIALS_MISMATCHED = "user_credentials_mismatched";
    public static final String UNKNOWN_ERROR = "unknown_error";
    public static final String UNKNOWN_ERROR_WITH_ID = "unknown_error_with_id";
    public static final String UNKONWN_ERROR_INSERT = "unknown_error_insert";
    public static final String TOKEN_EXPIRED = "token_expired";
    public static final String UNKNOWN_TOKEN = "unknown_token";
    public static final String ALREADY_EXISTS = "already_exists";
    public static final String VERSION_MISMATCH = "version_mismatch";
    public static final String CANNOT_CHANGE_PREF = "cannot_change_pref";
    public static final String UNABLE_TO_CREATE_OBJECT = "unable_to_create_object";
    public static final String APPLICATION_NAME_REQUIRED = "application_name_required";
    public static final String FORBIDDEN_APP_ACCESS = "forbidden_app_access";
    public static final String NAME_MISSING = "name_missing";
    public static final String NAME_CHANGE = "name_change";
    public static final String FORBIDDEN_EXECUTION = "forbidden_execution";
    public static final String INTERNAL_ERROR_RECURSION = "internal_error_recursion";
    public static final String TRANSPORT_ERROR = "transport_error";

    protected AbstractMongoMessageResourceService() {

        super(Map.of(Locale.ENGLISH, ResourceBundle.getBundle("messages", Locale.ENGLISH)));
    }

    @Override
    public Mono<String> getMessage(String messageId) {

        Mono<Locale> locale = SecurityContextUtil.getUsersLocale();

        return locale.flatMap(l -> {
                    var x = this.bundleMap.get(l);

                    if (x == null)
                        x = this.bundleMap.get(Locale.forLanguageTag(l.getLanguage()));

                    return x == null ? Mono.empty() : Mono.just(x);
                })
                .defaultIfEmpty(this.bundleMap.get(Locale.ENGLISH))
                .map(e -> e.getString(e.containsKey(messageId) ? messageId : UKNOWN_ERROR));
    }
}