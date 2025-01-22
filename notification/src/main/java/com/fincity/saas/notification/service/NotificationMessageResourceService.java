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

	protected NotificationMessageResourceService() {
		super(Map.of(Locale.ENGLISH, ResourceBundle.getBundle("messages", Locale.ENGLISH)));
	}

	@Override
	public Mono<String> getMessage(String messageId) {

		return SecurityContextUtil.getUsersLocale()
				.flatMap(locale -> Mono.justOrEmpty(this.findResourceBundle(locale)))
				.defaultIfEmpty(
						this.bundleMap.get(Locale.ENGLISH))
				.map(bundle -> bundle.containsKey(messageId) ? bundle.getString(messageId) : bundle.getString(UKNOWN_ERROR));
	}

	private ResourceBundle findResourceBundle(Locale locale) {

		ResourceBundle bundle = this.bundleMap.get(locale);

		return bundle == null ? this.bundleMap.get(Locale.forLanguageTag(locale.getLanguage())) : bundle;
	}
}
