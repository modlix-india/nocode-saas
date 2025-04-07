package com.fincity.saas.commons.core.service;

import com.fincity.saas.commons.core.document.Template;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.StringUtil;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

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

    protected CoreMessageResourceService msgService;

    protected Logger logger;

    protected AbstractTemplateService() {
        logger = LoggerFactory.getLogger(this.getClass());
    }

    @Autowired
    private void setMsgService(CoreMessageResourceService msgService) {
        this.msgService = msgService;
    }

    protected Mono<Map<String, String>> getProcessedTemplate(
            String language, Template template, Map<String, Object> templateData) {
        if (template.getTemplateParts() == null || template.getTemplateParts().isEmpty())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                    CoreMessageResourceService.MAIL_SEND_ERROR,
                    "No template parts found");

        Map<String, String> temp = template.getTemplateParts().get(language.isBlank() ? "en" : language);
        if (temp == null) temp = template.getTemplateParts().values().iterator().next();

        return Flux.fromIterable(temp.entrySet())
                .flatMap(e -> this.processFreeMarker("templatePart", e.getValue(), templateData)
                        .map(str -> Tuples.of(e.getKey(), str)))
                .collectMap(Tuple2::getT1, Tuple2::getT2);
    }

    protected Mono<String> getEffectiveLanguage(
            Template template, String requestedLanguage, Map<String, Object> templateData) {
        if (!StringUtil.safeIsBlank(requestedLanguage)) return Mono.just(requestedLanguage);

        return getLanguage(template, templateData);
    }

    protected Mono<String> processFreeMarker(String name, String template, Map<String, Object> templateData) {
        return Mono.fromCallable(() -> {
                    freemarker.template.Template temp = new freemarker.template.Template(name, template, CONFIGURATION);
                    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            Writer out = new OutputStreamWriter(baos)) {
                        temp.process(templateData, out);
                        return baos.toString(StandardCharsets.UTF_8);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    protected Mono<String> getLanguage(Template template, Map<String, Object> templateData) {
        boolean isBlankExpression = StringUtil.safeIsBlank(template.getLanguageExpression());

        if (isBlankExpression && StringUtil.safeIsBlank(template.getDefaultLanguage())) return Mono.just("");

        return processFreeMarker("language", template.getLanguageExpression(), templateData)
                .map(e -> e.isBlank() ? template.getDefaultLanguage() : e);
    }
}
	