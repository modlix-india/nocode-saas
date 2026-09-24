package com.fincity.saas.ui.service;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;

@Service
public class UIMessageResourceService extends AbstractMongoMessageResourceService {

    public static final String APP_NAME_MISMATCH = "app_name_mismatch";

    public static final String URI_STRING_NULL = "uri_path_string_null";

    public static final String URI_PATTERN_PATH_MISMATCH = "uri_pattern_path_mismatch";

    public static final String URI_INVALID_TYPE = "uri_invalid_type";

    public static final String URI_INVALID_METHOD = "uri_invalid_method";

    public static final String INTERNAL_ONLY = "internal_only";

    public static final String MOBILE_APP_BAD_REQUEST = "mobile_app_bad_request";

    public static final String MOBILE_APP_UNABLE_TO_GEN_KEYSTORE = "mobile_app_unable_to_gen_keystore";

    public static final String ANALYTICS_NOT_CONFIGURED = "analytics_not_configured";

    public static final String ANALYTICS_CODES_REQUIRED = "analytics_codes_required";

    public static final String ANALYTICS_NO_WRITE_ACCESS = "analytics_no_write_access";

    public static final String ANALYTICS_CLIENT_NOT_MANAGED = "analytics_client_not_managed";

    public static final String ANALYTICS_NOT_ENABLED = "analytics_not_enabled";

    public static final String ANALYTICS_UNKNOWN_TIMEZONE = "analytics_unknown_timezone";
}
