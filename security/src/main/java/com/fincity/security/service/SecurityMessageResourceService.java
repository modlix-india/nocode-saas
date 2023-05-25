package com.fincity.security.service;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.springframework.stereotype.Service;

import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.configuration.service.AbstractMessageService;

import reactor.core.publisher.Mono;

@Service
public class SecurityMessageResourceService extends AbstractMessageService {

	public static final String OBJECT_NOT_FOUND = "object_not_found";
	public static final String OBJECT_NOT_FOUND_TO_UPDATE = "object_not_found_to_update";
	public static final String FORBIDDEN_CREATE = "forbidden_create";
	public static final String FORBIDDEN_PERMISSION = "forbidden_permission";
	public static final String UNABLE_TO_DELETE = "unable_to_delete";
	public static final String OBJECT_NOT_UPDATABLE = "object_not_updatable";
	public static final String USER_CREDENTIALS_MISMATCHED = "user_credentials_mismatched";
	public static final String UNKNOWN_ERROR = "unknown_error";
	public static final String UNKNOWN_ERROR_WITH_ID = "unknown_error_with_id";
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
		})
		        .defaultIfEmpty(this.bundleMap.get(Locale.ENGLISH))
		        .map(e -> e.getString(e.containsKey(messageId) ? messageId : UKNOWN_ERROR));

	}
}