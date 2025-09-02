package com.fincity.saas.commons.core.service;

import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Primary
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

    public static final String NOT_ABLE_TO_OPEN_FILE_ERROR = "not_able_to_open_file_error";

    public static final String NOT_ABLE_TO_READ_FILE_FORMAT = "not_able_to_read_file_format";

    public static final String FILE_FORMAT_INVALID = "file_format_invalid";

    public static final String NOT_ABLE_TO_DOWNLOAD_DATA = "not_able_to_download_data";

    public static final String BULK_UPLOAD_MESSAGE = "bulk_upload_message";

    public static final String NOT_ABLE_TO_READ_FROM_FILE = "not_able_to_read_from_file";

    public static final String TEMPLATE_DETAILS_MISSING = "template_details_missing";

    public static final String TEMPLATE_CONVERT_ERROR = "template_convert_error";

    public static final String FS_STREAM_ERROR = "fs_stream_error";

    public static final String UNABLE_TO_FETCH_INTERNAL_RESOURCE = "unable_to_fetch_internal_resource";

    public static final String UNABLE_TO_FETCH_EXTERNAL_RESOURCE = "unable_to_fetch_external_resource";

    public static final String MAIL_SEND_ERROR = "mail_send_error";

    public static final String STORAGE_IS_APP_LEVEL = "storage_is_app_level";

    public static final String ONLY_SCHEMA_OBJECT_TYPE = "only_schema_object_type";

    public static final String NO_STORAGE_FOUND_WITH_NAME = "no_storage_found_with_name";

    public static final String STORAGE_SCHEMA_ALWAYS_OBJECT = "storage_schema_always_object";

    public static final String STORAGE_SCHEMA_FIELD_ALREADY_EXISTS = "storage_schema_field_already_exists";

    public static final String STORAGE_RELATION_OBJECT_CREATION_ERROR = "storage_relation_object_creation_error";

    public static final String STORAGE_RELATION_DATA_TYPE_MISMATCH = "storage_relation_data_type_mismatch";

    public static final String INVALID_RELATION_DATA = "invalid_relation_data";

    public static final String CANNOT_DELETE_STORAGE_WITH_RESTRICT = "cannot_delete_storage_with_restrict";

    public static final String NOT_ABLE_TO_CREATE_TOKEN = "not_able_to_create_token";

    public static final String CANNOT_DELETE_TOKEN_WITH_CLIENT_CODE = "cannot_delete_token_with_client_code";

    public static final String CONNECTION_NOT_AVAILABLE = "connection_not_available";

    public static final String INVALID_USER_FOR_CLIENT = "invalid_user_for_client";
}
