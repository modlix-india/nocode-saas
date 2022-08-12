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