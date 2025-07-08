package com.fincity.saas.message.service;

import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Primary
@Service
public class MessageResourceService extends AbstractMessageService {

    public static final String VERSION_MISMATCH = "version_mismatch";
    public static final String FORBIDDEN_APP_ACCESS = "forbidden_app_access";
    public static final String LOGIN_REQUIRED = "login_required";
    public static final String NAME_MISSING = "name_missing";
    public static final String DUPLICATE_NAME_FOR_ENTITY = "duplicate_name_for_entity";
    public static final String INVALID_USER_FOR_CLIENT = "invalid_user_for_client";
    public static final String INVALID_USER_ACCESS = "invalid_user_access";
    public static final String IDENTITY_MISSING = "identity_missing";
    public static final String IDENTITY_WRONG = "identity_wrong";

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

    private ResourceBundle findResourceBundle(Locale locale) {

        ResourceBundle bundle = this.bundleMap.get(locale);

        return bundle == null ? this.bundleMap.get(Locale.forLanguageTag(locale.getLanguage())) : bundle;
    }
}
