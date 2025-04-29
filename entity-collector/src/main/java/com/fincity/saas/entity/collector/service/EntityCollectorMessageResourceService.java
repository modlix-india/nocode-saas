package com.fincity.saas.entity.collector.service;

import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

@Service
public class EntityCollectorMessageResourceService extends AbstractMessageService {

    public static final String VERIFICATION_FAILED = "verification_failed";
    public static final String SUCCESS_ENTITY_MESSAGE = "success_entity";
    public static final String INTEGRATION_FOUND_MESSAGE = "integration_found_message";
    public static final String MESSAGES = "messages";


    protected EntityCollectorMessageResourceService() {
        super(Map.of(Locale.ENGLISH, ResourceBundle.getBundle(MESSAGES, Locale.ENGLISH)));
    }

    @Override
    public Mono<String> getMessage(String messageId) {

        return SecurityContextUtil.getUsersLocale()
                .flatMap(locale -> Mono.justOrEmpty(this.findResourceBundle(locale)))
                .defaultIfEmpty(
                        this.bundleMap.get(Locale.ENGLISH))
                .map(bundle -> bundle.containsKey(messageId) ? bundle.getString(messageId)
                        : bundle.getString(UKNOWN_ERROR));
    }

    private ResourceBundle findResourceBundle(Locale locale) {

        ResourceBundle bundle = this.bundleMap.get(locale);

        return bundle == null ? this.bundleMap.get(Locale.forLanguageTag(locale.getLanguage())) : bundle;
    }
}