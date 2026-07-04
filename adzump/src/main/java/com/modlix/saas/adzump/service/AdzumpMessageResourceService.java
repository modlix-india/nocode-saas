package com.modlix.saas.adzump.service;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.springframework.stereotype.Service;

import com.modlix.saas.commons2.configuration.service.AbstractMessageService;

@Service
public class AdzumpMessageResourceService extends AbstractMessageService {

    public static final String PLAN_NOT_FOUND = "plan_not_found";
    public static final String FORBIDDEN_PERMISSION = "forbidden_permission";
    public static final String IDS_NOT_FETCHED = "ids_not_fetched";
    public static final String FIELDS_MISSING = "fields_missing";
    public static final String INVALID_SCOPE = "invalid_scope";
    public static final String OBJECT_NOT_FOUND = "object_not_found";
    public static final String CONNECTION_NOT_FOUND = "connection_not_found";
    public static final String PRODUCT_NOT_FOUND = "product_not_found";
    public static final String PLATFORM_NOT_AVAILABLE = "platform_not_available";
    public static final String META_API_ERROR = "meta_api_error";
    public static final String GOOGLE_API_ERROR = "google_api_error";
    public static final String SPECIAL_CATEGORY_REQUIRED = "special_category_required";
    public static final String UNSUPPORTED_LAUNCH_TYPE = "unsupported_launch_type";
    public static final String LIVE_LAUNCH_DISABLED = "live_launch_disabled";
    public static final String MILESTONE_UNKNOWN = "milestone_unknown";
    public static final String STAGE_UNMAPPED = "stage_unmapped";

    public AdzumpMessageResourceService() {

        super(Map.of(Locale.ENGLISH, ResourceBundle.getBundle("messages", Locale.ENGLISH)));
    }
}
