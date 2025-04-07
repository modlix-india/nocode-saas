package com.fincity.sass.worker.service;

import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

@Service
public class WorkerMessageResourceService extends AbstractMessageService {

    public static final String OBJECT_NOT_FOUND_TO_UPDATE = "object_not_found_to_update";
    public static final String PARAMS_NOT_FOUND = "params_not_found";
    public static final String FORBIDDEN_CREATE = "forbidden_create";
    public static final String FORBIDDEN_UPDATE = "forbidden_update";
    public static final String FORBIDDEN_PERMISSION = "forbidden_permission";

    protected WorkerMessageResourceService() {
        super(Map.of(Locale.ENGLISH, ResourceBundle.getBundle("messages", Locale.ENGLISH)));
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
