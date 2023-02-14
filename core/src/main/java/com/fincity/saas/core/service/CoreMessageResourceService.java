package com.fincity.saas.core.service;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;

@Service
public class CoreMessageResourceService extends AbstractMongoMessageResourceService {

	public static final String STORAGE_NOT_FOUND = "storage_not_found";

	public static final String CONNECTION_DETAILS_MISSING = "connection_details_missing";

	public static final String FORBIDDEN_CREATE_STORAGE = "forbidden_create_storage";

	public static final String FORBIDDEN_UPDATE_STORAGE = "forbidden_update_storage";

	public static final String FORBIDDEN_READ_STORAGE = "forbidden_read_storage";

	public static final String FORBIDDEN_DELETE_STORAGE = "forbidden_delete_storage";

	public static final String SCHEMA_VALIDATION_ERROR = "schema_validation_error";

	public static final String WORKFLOW_TRIGGER_MISSING = "workflow_trigger_missing";

	public static final String TEMPLATE_GENERATION_ERROR = "template_generation_error";
}
