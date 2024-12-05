package com.fincity.security.service;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;

import reactor.core.publisher.Mono;

@Service
public class SecurityMessageResourceService extends AbstractMessageService {

	public static final String OBJECT_NOT_FOUND_TO_UPDATE = "object_not_found_to_update";
	public static final String FORBIDDEN_CREATE = "forbidden_create";
	public static final String FORBIDDEN_PERMISSION = "forbidden_permission";
	public static final String FORBIDDEN_CREATE_INVALID_PASS = "forbidden_create_user_invalid_password";
	public static final String UNABLE_TO_DELETE = "unable_to_delete";
	public static final String OBJECT_NOT_UPDATABLE = "object_not_updatable";
	public static final String USER_CREDENTIALS_MISMATCHED = "user_credentials_mismatched";
	public static final String USER_PASSWORD_INVALID = "user_password_invalid";
	public static final String USER_PIN_INVALID = "user_pin_invalid";
	public static final String USER_ACCOUNT_BLOCKED = "user_account_blocked";
	public static final String UNKNOWN_ERROR = "unknown_error";
	public static final String UNKONWN_ERROR_INSERT = "unkonwn_error_insert";
	public static final String TOKEN_EXPIRED = "token_expired";
	public static final String UNKNOWN_TOKEN = "unknown_token";
	public static final String ALREADY_EXISTS = "already_exists";
	public static final String ROLE_FORBIDDEN = "role_forbidden_for_selected_user";
	public static final String ASSIGN_PERMISSION_ERROR = "assign_permission_error";
	public static final String ASSIGN_PACKAGE_ERROR = "assign_package_error";
	public static final String ASSIGN_ROLE_ERROR = "assign_role_error";
	public static final String REMOVE_PERMISSION_ERROR = "remove_permission_error";
	public static final String ROLE_REMOVE_ERROR = "role_remove_error";
	public static final String ASSIGN_PERMISSION_ERROR_FOR_ROLE = "assign_permission_error_for_role";
	public static final String REMOVE_PERMISSION_FROM_ROLE_ERROR = "remove_permission_from_role_error";
	public static final String REMOVE_PACKAGE_ERR0R = "remove_package_error";
	public static final String ROLE_REMOVE_FROM_PACKAGE_ERROR = "role_remove_from_package_error";
	public static final String CLIENT_PASSWORD_POLICY_ERROR = "client_password_policy_error";
	public static final String CAPTIAL_LETTERS_MISSING = "capital_letters_missing";
	public static final String SMALL_LETTERS_MISSING = "small_letters_missing";
	public static final String NUMBERS_MISSING = "numbers_missing";
	public static final String SPECIAL_CHARACTERS_MISSING = "special_characters_missing";
	public static final String SPACES_MISSING = "spaces_missing";
	public static final String REGEX_MISMATCH = "regex_mismatch";
	public static final String MIN_LENGTH_ERROR = "min_length_error";
	public static final String MAX_LENGTH_ERROR = "max_length_error";
	public static final String USER_NOT_ACTIVE = "user_not_active";
	public static final String OLD_NEW_PASSWORD_MATCH = "old_new_password_match";
	public static final String NEW_PASSWORD_MISSING = "new_password_missing";
	public static final String PASSWORD_USER_ERROR = "password_used_error";
	public static final String DELETE_PERMISSION_ERROR = "delete_permission_error";
	public static final String DELETE_PACKAGE_ERROR = "delete_package_error";
	public static final String DELETE_ROLE_ERROR = "delete_role_error";
	public static final String UNKNOWN_CLIENT = "unknown_client";
	public static final String APP_CODE_NO_SPL_CHAR = "app_code_no_spl_char";
	public static final String CLIENT_REGISTRATION_ERROR = "client_registration_error";
	public static final String USER_ALREADY_EXISTS = "user_already_exists";
	public static final String FETCH_PACKAGE_ERROR = "fetch_package_error";
	public static final String FETCH_ROLE_ERROR = "fetch_role_error";
	public static final String FETCH_PERMISSION_ERROR = "fetch_permission_error";
	public static final String FETCH_PERMISSION_ERROR_FOR_USER = "fetch_permission_error_for_user";
	public static final String FETCH_ROLE_ERROR_FOR_USER = "fetch_role_error_for_user";
	public static final String PASS_RESET_REQ_ERROR = "pass_reset_req_error";
	public static final String ASSIGN_PACKAGE_TO_CLIENT_AND_APP = "assign_package_to_client_and_app";
	public static final String APPLICATION_ACCESS_ERROR = "application_access_error";
	public static final String NO_PACKAGE_ASSIGNED_TO_APP = "no_package_assigned_to_app";
	public static final String REMOVE_PACKAGE_FROM_APP_ERROR = "remove_package_from_app_error";
	public static final String ASSIGN_ROLE_TO_APP_ERROR = "assign_role_to_app_error";
	public static final String REMOVE_ROLE_FROM_APP_ERROR = "remove_role_from_app_error";
	public static final String NO_ROLE_ASSIGNED_TO_APP = "no_role_assigned_to_app";
	public static final String REQUEST_EXISTING = "request_exisiting";
	public static final String BAD_CERT_REQUEST = "bad_cert_request";
	public static final String MISMATCH_DOMAINS = "mismatch_domains";
	public static final String ERROR_KEY_CSR = "error_key_csr";
	public static final String LETS_ENCRYPT_CREDENTIALS = "lets_encrypt_credentials";
	public static final String LETS_ENCRYPT_ISSUE = "lets_encrypt_issue";
	public static final String TRIGGER_FAILED = "trigger_failed";
	public static final String CERTIFICATE_PROBLEM = "certificate_problem";
	public static final String ONLY_SYS_USER_CERTS = "only_sys_user_certs";
	public static final String MANDATORY_APP_ID_CODE = "mandatory_app_id_code";
	public static final String MANDATORY_APP_ID_NAME = "mandatory_app_id_name";
	public static final String NO_REGISTRATION_AVAILABLE = "no_registration_available";
	public static final String CANNOT_DELETE_APP = "cannot_delete_app";
	public static final String MAIL_CANNOT_BE_TRIGGERED = "mail_cannot_be_triggered";
	public static final String ACCESS_CODE_INCORRECT = "access_code_incorrect";
	public static final String USER_ALREADY_CREATED = "user_already_created";
	public static final String MISSING_PASSWORD = "missing_password";
	public static final String FIELDS_MISSING = "fields_missing";
	public static final String MANDATORY_APP_CODE = "mandatory_app_code";
	public static final String MANDATORY_CLIENT_CODE = "mandatory_client_code";
	public static final String CLIENT_CODE_OR_ID_ONLY_ONE = "client_code_or_id_only_one";
	public static final String FORBIDDEN_APP_REG_OBJECTS = "forbidden_app_reg_objects";
	public static final String SUBDOMAIN_ALREADY_EXISTS = "subdomain_already_exists";
	public static final String APP_DEPENDENCY_SAME_APP_CODE = "app_dependency_same_app_code";
	public static final String FORBIDDEN_COPY_ROLE_PERMISSION = "forbidden_copying_role_permission";
	public static final String ACTIVE_INACTIVE_ERROR = "active_inactive_error";
	public static final String HIERARCHY_ERROR = "hierarchy_error";
	public static final String SMS_OTP_ERROR = "sms_otp_error";
	public static final String INVALID_APP_PROP = "invalid_app_prop";
	public static final String SESSION_EXPIRED = "session_expired";
	public static final String SOCIAL_LOGIN_FAILED = "social_login_failed";
	public static final String UNSUPPORTED_PLATFORM = "unsupported_platform";
	public static final String CRT_KEY_ISSUE = "crt_key_issue";

	public SecurityMessageResourceService() {

		super(Map.of(Locale.ENGLISH, ResourceBundle.getBundle("messages", Locale.ENGLISH)));
	}

	@Override
	public Mono<String> getMessage(String messageId) {

		Mono<Locale> locale = SecurityContextUtil.getUsersLocale();

		return locale.flatMap(l -> {
			var x = this.bundleMap.get(l);

			if (x == null)
				x = this.bundleMap.get(Locale.forLanguageTag(l.getLanguage()));

			return x == null ? Mono.empty() : Mono.just(x);
		}).defaultIfEmpty(this.bundleMap.get(Locale.ENGLISH))
				.map(e -> e.getString(e.containsKey(messageId) ? messageId : UKNOWN_ERROR));

	}
}
