package com.modlix.saas.notification.service.email;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

import com.modlix.saas.notification.model.CoreNotification;
import com.modlix.saas.notification.service.AbstractTemplateService;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public abstract class AbstractEmailService extends AbstractTemplateService {

    private static record ProcessedEmailDetails(List<String> to, String from, String subject, String body) {}

    protected ProcessedEmailDetails getProcessedEmailDetails(
            List<String> toAddresses, CoreNotification.NotificationTemplate template, Map<String, Object> templateData) {

                String to = this.getToAddresses(toAddresses, template, templateData);
                String from = this.getFromAddress(template, templateData);
                String language = this.getLanguage(template, templateData);
                Map<String, String> temp = this.getProcessedTemplate(language, template, templateData);

        return FlatMapUtil.flatMapMono(
                        () -> this.getToAddresses(toAddresses, template, templateData),
                        to -> this.getFromAddress(template, templateData),
                        (to, from) -> this.getLanguage(template, templateData),
                        (to, from, language) -> this.getProcessedTemplate(language, template, templateData),
                        (to, from, language, temp) -> Mono.just(new ProcessedEmailDetails()
                                .setTo(to)
                                .setFrom(from)
                                .setSubject(temp.getOrDefault("subject", ""))
                                .setBody(temp.getOrDefault("body", ""))))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractEmailService.getProcessedEmailDetails"));
    }

    protected Mono<String> getFromAddress(Template template, Map<String, Object> templateData) {
        boolean isBlankExpression = StringUtil.safeIsBlank(template.getFromExpression());

        if (isBlankExpression) return Mono.just("");

        return processFreeMarker("fromExpression", template.getFromExpression(), templateData);
    }

}
