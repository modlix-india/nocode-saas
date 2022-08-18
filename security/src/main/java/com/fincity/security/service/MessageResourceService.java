package com.fincity.security.service;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.security.util.SecurityContextUtil;

import reactor.core.publisher.Mono;

@Service
public class MessageResourceService {

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
	public static final String VALUEOF_METHOD_NOT_FOUND = "valueof_method_not_found";
	public static final String UNABLE_TO_CONVERT = "unable_to_convert";
	public static final String TOKEN_EXPIRED = "token_expired";
	public static final String UNKNOWN_TOKEN = "unknown_token";
	public static final String ALREADY_EXISTS = "already_exists";

	private static final String UKNOWN_ERROR = "unknown_error";

	private Map<Locale, ResourceBundle> bundleMap = new ConcurrentHashMap<>();

	public MessageResourceService() {

		bundleMap.put(Locale.ENGLISH, ResourceBundle.getBundle("messages", Locale.ENGLISH));
	}

	public Mono<String> getMessage(final String messageId) {

		Mono<Locale> locale = SecurityContextUtil.getUsersLocale();

		return locale.map(this.bundleMap::get)
		        .defaultIfEmpty(this.bundleMap.get(Locale.ENGLISH))
		        .map(e -> e.getString(e.containsKey(messageId) ? messageId : UKNOWN_ERROR));
	}

	public Mono<String> getMessage(String messageId, Object... params) {

		return this.getMessage(messageId)
		        .map(e -> StringFormatter.format(e, params));
	}

	public String getDefaultLocaleMessage(String messageId) {
		return this.getLocaleLocaleMessage(Locale.ENGLISH, messageId);
	}

	public String getDefaultLocaleMessage(String messageId, Object... params) {
		return this.getLocaleLocaleMessage(Locale.ENGLISH, messageId, params);
	}

	public String getLocaleLocaleMessage(Locale locale, String messageId) {
		return this.bundleMap.get(locale)
		        .getString(messageId);
	}

	public String getLocaleLocaleMessage(Locale locale, String messageId, Object... params) {
		return StringFormatter.format(this.bundleMap.get(locale)
		        .getString(messageId), params);
	}
}