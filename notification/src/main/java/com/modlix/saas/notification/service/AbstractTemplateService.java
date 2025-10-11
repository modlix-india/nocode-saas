package com.modlix.saas.notification.service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.util.StringUtil;
import com.modlix.saas.commons2.util.Tuples;
import com.modlix.saas.commons2.util.Tuples.Tuple2;
import com.modlix.saas.notification.model.CoreNotification;

import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;


public abstract class AbstractTemplateService {

    protected static final Configuration CONFIGURATION = new Configuration(Configuration.VERSION_2_3_32);

    static {
        CONFIGURATION.setDefaultEncoding("UTF-8");
        CONFIGURATION.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        CONFIGURATION.setLogTemplateExceptions(false);
        CONFIGURATION.setWrapUncheckedExceptions(true);
        CONFIGURATION.setFallbackOnNullLoopVariable(false);
        CONFIGURATION.setSQLDateAndTimeTimeZone(TimeZone.getDefault());
    }

    protected NotificationMessageResourceService msgService;

    protected Logger logger;

    protected AbstractTemplateService() {
        logger = LoggerFactory.getLogger(this.getClass());
    }

    @Autowired
    private void setMsgService(NotificationMessageResourceService msgService) {
        this.msgService = msgService;
    }

    protected Map<String, String> getProcessedTemplate(
            String language, CoreNotification.NotificationTemplate template, Map<String, Object> templateData) {
        if (template.getTemplateParts() == null || template.getTemplateParts().isEmpty())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                    NotificationMessageResourceService.MAIL_SEND_ERROR,
                    "No template parts found");

        Map<String, String> temp = template.getTemplateParts().get(language.isBlank() ? "en" : language);
        if (temp == null) temp = template.getTemplateParts().values().iterator().next();

        return temp.entrySet()
                .stream()
                .map(e -> {
                    String str = this.processFreeMarker("templatePart", e.getValue(), templateData);
                    return Tuples.of(e.getKey(), str);
                })
                .collect(Collectors.toMap(Tuple2::getT1, Tuple2::getT2));
    }

    protected String getEffectiveLanguage(
            CoreNotification template, String requestedLanguage, Map<String, Object> templateData) {
        if (!StringUtil.safeIsBlank(requestedLanguage)) return requestedLanguage;

        return getLanguage(template, templateData);
    }

    protected String processFreeMarker(String name, String template, Map<String, Object> templateData) {
        
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Writer out = new OutputStreamWriter(baos)) {
            freemarker.template.Template temp = new freemarker.template.Template(name, template, CONFIGURATION);
            temp.process(templateData, out);
            return baos.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
        
    }

    protected String getLanguage(CoreNotification template, Map<String, Object> templateData) {
        boolean isBlankExpression = StringUtil.safeIsBlank(template.getLanguageExpression());

        if (isBlankExpression && StringUtil.safeIsBlank(template.getDefaultLanguage())) return "";

        String lang = processFreeMarker("language", template.getLanguageExpression(), templateData);
        return lang == null || lang.isBlank() ? template.getDefaultLanguage() : lang;
    }
}
