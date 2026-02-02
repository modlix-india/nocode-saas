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
    public static final String PARAMS_NOT_FOUND = "params_not_found";
    public static final String FORBIDDEN_CREATE = "forbidden_create";
    public static final String FORBIDDEN_UPDATE = "forbidden_update";
    public static final String FORBIDDEN_PERMISSION = "forbidden_permission";
    public static final String OBJECT_NOT_UPDATABLE = "object_not_updatable";
    public static final String USER_IDENTIFICATION_NOT_FOUND = "user_identification_not_found";
    public static final String USER_CREDENTIALS_MISMATCHED = "user_credentials_mismatched";
    public static final String USER_PASSWORD_INVALID_ATTEMPTS = "user_password_invalid_attempts";
    public static final String USER_PASSWORD_INVALID = "user_password_invalid";
    public static final String USER_ACCOUNT_BLOCKED = "user_account_blocked";
    public static final String USER_ACCOUNT_BLOCKED_LIMIT = "user_account_blocked_limit";
    public static final String USER_ACCOUNT_PASS_EXPIRED = "user_account_pass_expired";
    public static final String UNKNOWN_ERROR = "unknown_error";
    public static final String TOKEN_EXPIRED = "token_expired";
    public static final String LOGIN_REQUIRED = "login_required";
    public static final String UNKNOWN_TOKEN = "unknown_token";
    public static final String ROLE_FORBIDDEN = "role_forbidden_for_selected_user";
    public static final String PROFILE_FORBIDDEN = "profile_forbidden_for_selected_user";
    public static final String ROLE_REMOVE_ERROR = "role_remove_error";
    public static final String CAPITAL_LETTERS_MISSING = "capital_letters_missing";
    public static final String SMALL_LETTERS_MISSING = "small_letters_missing";
    public static final String NUMBERS_MISSING = "numbers_missing";
    public static final String SPECIAL_CHARACTERS_MISSING = "special_characters_missing";
    public static final String SPACES_MISSING = "spaces_missing";
    public static final String REGEX_MISMATCH = "regex_mismatch";
    public static final String MIN_LENGTH_ERROR = "min_length_error";
    public static final String MAX_LENGTH_ERROR = "max_length_error";
    public static final String LENGTH_ERROR = "length_error";
    public static final String USER_NOT_ACTIVE = "user_not_active";
    public static final String OLD_PASSWORD_MATCH = "old_password_match";
    public static final String NEW_PASSWORD_MATCH = "new_password_match";
    public static final String NEW_PASSWORD_MISSING = "new_password_missing";
    public static final String PASSWORD_USER_ERROR = "password_used_error";
    public static final String DELETE_ROLE_ERROR = "delete_role_error";
    public static final String UNKNOWN_CLIENT = "unknown_client";
    public static final String INACTIVE_CLIENT = "inactive_client";
    public static final String APP_CODE_NO_SPL_CHAR = "app_code_no_spl_char";
    public static final String CLIENT_REGISTRATION_ERROR = "client_registration_error";
    public static final String USER_ALREADY_EXISTS = "user_already_exists";
    public static final String PASS_RESET_REQ_ERROR = "pass_reset_req_error";
    public static final String REQUEST_EXISTING = "request_existing";
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
    public static final String FIELDS_MISSING = "fields_missing";
    public static final String MANDATORY_APP_CODE = "mandatory_app_code";
    public static final String CLIENT_CODE_OR_ID_ONLY_ONE = "client_code_or_id_only_one";
    public static final String FORBIDDEN_APP_REG_OBJECTS = "forbidden_app_reg_objects";
    public static final String SUBDOMAIN_ALREADY_EXISTS = "subdomain_already_exists";
    public static final String APP_DEPENDENCY_SAME_APP_CODE = "app_dependency_same_app_code";
    public static final String ACTIVE_INACTIVE_ERROR = "active_inactive_error";
    public static final String HIERARCHY_ERROR = "hierarchy_error";
    public static final String SMS_OTP_ERROR = "sms_otp_error";
    public static final String SESSION_EXPIRED = "session_expired";
    public static final String SOCIAL_LOGIN_FAILED = "social_login_failed";
    public static final String UNSUPPORTED_PLATFORM = "unsupported_platform";
    public static final String CRT_KEY_ISSUE = "crt_key_issue";
    public static final String SUBDOMAIN_SUFFIX_FORBIDDEN = "subdomain_suffix_forbidden";
    public static final String PROFILE_NEEDS_APP = "profile_needs_app";
    public static final String FORBIDDEN_ROLE_ACCESS = "forbidden_role_access";
    public static final String USER_DESIGNATION_MISMATCH = "user_designation_mismatch";
    public static final String USER_REPORTING_ERROR = "user_reporting_error";
    public static final String USER_APP_REQUEST_ALREADY_EXISTS = "user_app_request_already_exists";
    public static final String USER_ALREADY_HAVING_APP_ACCESS = "user_already_having_app_access";
    public static final String USER_APP_REQUEST_ACCEPT_INCORRECT_DATA = "user_app_request_accept_incorrect_data";
    public static final String USER_APP_REQUEST_INCORRECT_STATUS = "user_app_request_incorrect_status";
    public static final String USER_APP_REQUEST_MANDATORY_REQUEST_ID = "user_app_request_mandatory_request_id";
    public static final String FORBIDDEN_WRITE_APPLICATION_ACCESS = "forbidden_write_application_access";
    public static final String PLAN_CONFLICT_PLAN_ALREADY_EXISTS = "plan_conflict_plan_already_exists";
    public static final String PLAN_DEFAULT_PLAN_MUST_HAVE_ONE_APP = "plan_default_plan_must_have_one_app";
    public static final String PLAN_CYCLE_NOT_FOUND = "plan_cycle_not_found";

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
