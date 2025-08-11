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

    public static final String MULTIPLE_GEN_STATUS = "multiple_gen_status";

    public static final String INTERNAL_ONLY = "internal_only";
}
