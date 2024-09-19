package com.fincity.saas.ui.service;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;

@Service
public class UIMessageResourceService extends AbstractMongoMessageResourceService {

	public static final String APP_NAME_MISMATCH = "app_name_mismatch";

	public static final String URI_STRING_NULL = "uri_path_string_null";

	public static final String URI_PATTERN_PATH_MISMATCH = "uri_pattern_path_mismatch";

	public static final String URI_STRING_PARAMETERS_INVALID = "uri_path_string_parameters_invalid";

	public static final String UNABLE_TO_RUN_KIRUN_FX = "uri_unable_to_execute_kirun_fx";

	public static final String INVALID_REDIRECTION_DEFINITION = "uri_invalid_redirection_definition";

	public static final String REDIRECTION_NOT_VALID = "uri_redirection_not_valid";
}
