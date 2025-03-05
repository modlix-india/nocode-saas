package com.fincity.saas.notification.service;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;

import reactor.core.publisher.Mono;

@Service
public class NotificationMessageResourceService extends AbstractMessageService {

	public static final String NOTIFICATION_PREFIX = "NOTIFICATION_";

	public static final String UKNOWN_ERROR = "unknown_error";
	public static final String FORBIDDEN_CREATE = "forbidden_create";
	public static final String FORBIDDEN_UPDATE = "forbidden_update";
	public static final String TEMPLATE_DATA_NOT_FOUND = "template_data_not_found";
	public static final String MAIL_SEND_ERROR = "mail_send_error";

	protected NotificationMessageResourceService() {
		super(Map.of(Locale.ENGLISH, ResourceBundle.getBundle("messages", Locale.ENGLISH)));
	}

	@Override
	public Mono<String> getMessage(String messageId) {

		return SecurityContextUtil.getUsersLocale()
				.flatMap(locale -> Mono.justOrEmpty(this.findResourceBundle(locale)))
				.defaultIfEmpty(
						this.bundleMap.get(Locale.ENGLISH))
				.map(bundle -> bundle.containsKey(messageId) ? bundle.getString(messageId)
						: bundle.getString(UKNOWN_ERROR));
	}

	private ResourceBundle findResourceBundle(Locale locale) {

		ResourceBundle bundle = this.bundleMap.get(locale);

		return bundle == null ? this.bundleMap.get(Locale.forLanguageTag(locale.getLanguage())) : bundle;
	}
}
