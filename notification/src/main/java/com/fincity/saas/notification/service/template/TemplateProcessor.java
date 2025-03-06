package com.fincity.saas.notification.service.template;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.enums.notification.NotificationRecipientType;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.saas.notification.enums.NotificationChannelType;
import com.fincity.saas.notification.model.NotificationTemplate;
import com.fincity.saas.notification.model.message.NotificationMessage;
import com.fincity.saas.notification.model.message.RecipientInfo;
import com.fincity.saas.notification.service.NotificationMessageResourceService;

import lombok.Getter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Getter
@Service
public class TemplateProcessor extends BaseTemplateProcessor {

	public static final String PRO_LANGUAGE = "language";
	public static final String PRO_SUBJECT = "subject";
	public static final String PRO_BODY = "body";

	private static final String DEFAULT_LANGUAGE = Locale.ENGLISH.getLanguage();

	private NotificationMessageResourceService msgService;

	protected TemplateProcessor() {
	}

	@Autowired
	public void setMsgService(NotificationMessageResourceService msgService) {
		this.msgService = msgService;
	}

	public <T extends NotificationMessage<T>> Mono<T> process(NotificationTemplate template,
			Map<String, Object> templateData) {
		return this.process(null, template, templateData);
	}

	public <T extends NotificationMessage<T>> Mono<T> process(String language, NotificationTemplate template,
			Map<String, Object> templateData) {

		if (template.getTemplateParts().isEmpty())
			return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
					NotificationMessageResourceService.TEMPLATE_DATA_NOT_FOUND, "No template parts found");

		return FlatMapUtil.flatMapMono(

				() -> this.getEffectiveLanguage(language, template, templateData),

				lang -> {

					Map<String, String> templatePart = template.getTemplateParts().getOrDefault(
							this.getDefaultLanguage(language), template.getTemplateParts().values().iterator().next());

					return Mono.just(new NotificationMessage<T>(templatePart));
				},
				(lang, notificationMessage) -> this.processUserInfo(notificationMessage.getChannelType(),
						template.getCode(), templateData, template.getRecipientExpressions()),
				(lang, notificationMessage, userInfo) -> this.process(userInfo, template.getCode(), notificationMessage,
						templateData));
	}

	private <T extends NotificationMessage<T>> Mono<T> process(RecipientInfo userInfo, String templateCode,
			NotificationMessage<T> notificationMessage, Map<String, Object> templateData) {
		return Mono.zip(
				super.processString(this.getSubjectName(templateCode), notificationMessage.getSubject(), templateData),
				super.processString(this.getBodyName(templateCode), notificationMessage.getBody(), templateData))
				.map(message -> new NotificationMessage<T>(message.getT1(), message.getT2())
						.addRecipientInfo(userInfo));
	}

	public Mono<RecipientInfo> processUserInfo(NotificationChannelType channelType, String templateCode,
			Map<String, Object> templateData, Map<String, String> userExpressions) {

		Set<NotificationRecipientType> recipientTypes = channelType.getAllowedRecipientTypes();

		if (recipientTypes == null || recipientTypes.isEmpty())
			return Mono.just(new RecipientInfo());

		return Flux.fromIterable(userExpressions.entrySet())
				.map(entry -> Map.entry(NotificationRecipientType.lookupLiteral(entry.getKey()), entry.getValue()))
				.filter(entry -> recipientTypes.contains(entry.getKey()))
				.flatMap(entry -> this.getRecipient(templateCode, entry.getKey().name(), entry.getValue(), templateData)
						.map(recipient -> Map.entry(entry.getKey(), recipient)))
				.collect(RecipientInfo::new,
						(recipientInfo, entry) -> recipientInfo.addRecipientInto(entry.getKey(), entry.getValue()));
	}

	protected Mono<String> getRecipient(String templateCode, String recipient, String recipientExpression,
			Map<String, Object> templateData) {
		return super.processString(this.getRecipientName(templateCode, recipient), recipientExpression, templateData);
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

	private String getRecipientName(String templateCode, String recipientType) {
		return this.createTemplateName(templateCode, recipientType);
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
