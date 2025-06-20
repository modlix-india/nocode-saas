package com.fincity.saas.entity.processor.service;

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
public class ProcessorMessageResourceService extends AbstractMessageService {

    public static final String VERSION_MISMATCH = "version_mismatch";
    public static final String FORBIDDEN_APP_ACCESS = "forbidden_app_access";
    public static final String LOGIN_REQUIRED = "login_required";
    public static final String NAME_MISSING = "name_missing";
    public static final String DUPLICATE_NAME_FOR_ENTITY = "duplicate_name_for_entity";
    public static final String INVALID_USER_FOR_CLIENT = "invalid_user_for_client";
    public static final String IDENTITY_MISSING = "identity_missing";
    public static final String IDENTITY_WRONG = "identity_wrong";
    public static final String PRODUCT_FORBIDDEN_ACCESS = "product_forbidden_access";
    public static final String PRODUCT_TEMPLATE_FORBIDDEN_ACCESS = "product_template_forbidden_access";
    public static final String OWNER_NOT_CREATED = "owner_not_created";
    public static final String DUPLICATE_TICKET = "duplicate_ticket";
    public static final String INVALID_TICKET_OWNER = "invalid_ticket_owner";
    public static final String USER_DISTRIBUTION_MISSING = "user_distribution_missing";
    public static final String USER_DISTRIBUTION_INVALID = "user_distribution_invalid";
    public static final String PRODUCT_TEMPLATE_TYPE_MISSING = "product_template_type_missing";
    public static final String STAGE_MISSING = "stage_missing";
    public static final String INVALID_STAGE_STATUS = "invalid_stage_status";
    public static final String TEMPLATE_STAGE_MISSING = "template_stage_missing";
    public static final String TEMPLATE_STAGE_INVALID = "template_stage_invalid";
    public static final String DEFAULT_RULE_MISSING = "default_rule_missing";
    public static final String NO_VALUES_FOUND = "no_values_found";
    public static final String CONTENT_MISSING = "content_missing";
    public static final String DATE_IN_PAST = "date_in_past";
    public static final String TASK_FORBIDDEN_ACCESS = "task_forbidden_access";
    public static final String TASK_ALREADY_COMPLETED = "task_already_completed";
    public static final String TASK_ALREADY_CANCELLED = "task_already_cancelled";

    protected ProcessorMessageResourceService() {
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
