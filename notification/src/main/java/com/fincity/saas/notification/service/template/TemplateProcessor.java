package com.fincity.saas.notification.service.template;

import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.saas.notification.model.NotificationTemplate;
import com.fincity.saas.notification.model.message.NotificationMessage;
import com.fincity.saas.notification.service.NotificationMessageResourceService;

import lombok.Getter;
import reactor.core.publisher.Mono;

@Service
public class TemplateProcessor extends BaseTemplateProcessor {

	public static final String PRO_LANGUAGE = "language";
	public static final String PRO_SUBJECT = "subject";
	public static final String PRO_BODY = "body";

	private static final String DEFAULT_LANGUAGE = Locale.ENGLISH.getLanguage();

	protected Logger logger;

	@Getter
	private NotificationMessageResourceService msgService;

	protected TemplateProcessor() {
		logger = LoggerFactory.getLogger(this.getClass());
	}

	@Autowired
	public void setMsgService(NotificationMessageResourceService msgService) {
		this.msgService = msgService;
	}

	public Mono<NotificationMessage> process(NotificationTemplate template, Map<String, Object> templateData) {
		return this.process(null, template, templateData);
	}

	public Mono<NotificationMessage> process(String language, NotificationTemplate template,
			Map<String, Object> templateData) {

		if (template.getTemplateParts().isEmpty())
			return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
					NotificationMessageResourceService.TEMPLATE_DATA_NOT_FOUND, "No template parts found");

		return FlatMapUtil.flatMapMono(

				() -> this.getEffectiveLanguage(language, template, templateData),

				lang -> {
					NotificationMessage notificationMessage = NotificationMessage
							.of(template.getTemplateParts().getOrDefault(this.getDefaultLanguage(language), null));

					return !notificationMessage.isNull() ? Mono.just(notificationMessage)
							: Mono.just(NotificationMessage.of(template.getTemplateParts().values().iterator().next()));
				},
				(lang, notificationMessage) -> this.process(template.getCode(), notificationMessage, templateData));
	}

	public Mono<NotificationMessage> process(String templateCode, NotificationMessage notificationMessage,
			Map<String, Object> templateData) {
		return Mono.zip(
				super.processString(this.getSubjectName(templateCode), notificationMessage.getSubject(), templateData),
				super.processString(this.getBodyName(templateCode), notificationMessage.getBody(), templateData))
				.map(NotificationMessage::of);
	}

	protected Mono<String> getEffectiveLanguage(String requestedLanguage, NotificationTemplate template,
			Map<String, Object> templateData) {
		return !StringUtil.safeIsBlank(requestedLanguage) ? Mono.just(requestedLanguage)
				: this.getLanguage(template, templateData);
	}

	protected Mono<String> getLanguage(NotificationTemplate template, Map<String, Object> templateData) {

		if (StringUtil.safeIsBlank(template.getLanguageExpression()))
			return Mono.just(this.getDefaultLanguage(template.getDefaultLanguage()));

		return super.processString(this.getLanguageName(template.getCode()), template.getLanguageExpression(),
				templateData)
				.map(lang -> StringUtil.safeIsBlank(lang) ? this.getDefaultLanguage(template.getDefaultLanguage())
						: lang);
	}

	private String getDefaultLanguage(String defaultTemplateLanguage) {
		return !StringUtil.safeIsBlank(defaultTemplateLanguage) ? defaultTemplateLanguage : DEFAULT_LANGUAGE;
	}

	private String getLanguageName(String templateCode) {
		return this.createTemplateName(templateCode, PRO_LANGUAGE);
	}

	private String getSubjectName(String templateCode) {
		return this.createTemplateName(templateCode, PRO_SUBJECT);
	}

	private String getBodyName(String templateCode) {
		return this.createTemplateName(templateCode, PRO_BODY);
	}

	private String createTemplateName(String code, String fieldName) {
		return StringUtil.safeIsBlank(code) ? UniqueUtil.shortUUID() + ":" + fieldName : code + ":" + fieldName;
	}
}
