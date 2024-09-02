package com.fincity.saas.ui.service;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;

@Service
public class UIMessageResourceService extends AbstractMongoMessageResourceService {

	public static final String APP_NAME_MISMATCH = "app_name_mismatch";

	public static final String URI_STRING_NULL = "uri_string_null";

	public static final String URI_STRING_INVALID = "uri_string_invalid";
}
