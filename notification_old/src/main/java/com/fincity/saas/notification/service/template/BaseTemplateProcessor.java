package com.fincity.saas.notification.service.template;

import com.fincity.saas.commons.exeception.GenericException;
import freemarker.cache.MruCacheStorage;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Objects;
import java.util.TimeZone;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Getter
@Component
public abstract class BaseTemplateProcessor {

    protected static final Configuration CONFIGURATION = new Configuration(Configuration.VERSION_2_3_33);
    private static final StringTemplateLoader TEMPLATE_LOADER = new StringTemplateLoader();
    private static final Object TEMPLATE_LOCK = new Object();

    private static final int INITIAL_BUFFER_SIZE = 4096;
    private static final Logger logger = LoggerFactory.getLogger(BaseTemplateProcessor.class);

    static {
        CONFIGURATION.setDefaultEncoding(StandardCharsets.UTF_8.name());
        CONFIGURATION.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        CONFIGURATION.setLogTemplateExceptions(false);
        CONFIGURATION.setWrapUncheckedExceptions(true);
        CONFIGURATION.setFallbackOnNullLoopVariable(false);
        CONFIGURATION.setSQLDateAndTimeTimeZone(TimeZone.getTimeZone(ZoneOffset.UTC));
        CONFIGURATION.setTemplateLoader(TEMPLATE_LOADER);
        CONFIGURATION.setCacheStorage(new MruCacheStorage(200, Integer.MAX_VALUE));
    }

    public Configuration getConfiguration() {
        return CONFIGURATION;
    }

    public int getInitialBufferSize() {
        return INITIAL_BUFFER_SIZE;
    }

    public Mono<Template> toTemplate(String templateName, String sourceCode) {
        return this.toTemplateSync(templateName, sourceCode);
    }

    public Mono<Boolean> evictTemplate(Object... templateNames) {
        this.removeFreeMarkerCache(templateNames);
        return Mono.just(Boolean.TRUE);
    }

    private void removeFreeMarkerCache(Object... templateNames) {
        Arrays.stream(templateNames).filter(Objects::nonNull).forEach(templateName -> {
            try {
                CONFIGURATION.removeTemplateFromCache(templateName.toString());
            } catch (IOException e) {
                logger.error("No cache found in Freemarker cache for template: {}", templateName);
            }
        });
    }

    private Mono<Template> toTemplateSync(String templateName, String sourceCode) {
        return Mono.fromCallable(() -> {
                    synchronized (TEMPLATE_LOCK) {
                        Object eTemplate = TEMPLATE_LOADER.findTemplateSource(templateName);
                        if (eTemplate == null) TEMPLATE_LOADER.putTemplate(templateName, sourceCode);
                        return CONFIGURATION.getTemplate(templateName);
                    }
                })
                .onErrorMap(
                        IOException.class,
                        e -> new GenericException(
                                HttpStatus.BAD_REQUEST, "Failed to create template: " + templateName, e))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<String> processString(String templateName, String sourceCode, Object dataModel) {
        return this.toTemplate(templateName, sourceCode)
                .flatMap(template -> this.processString(template, dataModel))
                .onErrorMap(e ->
                        new GenericException(HttpStatus.BAD_REQUEST, "Failed to process template: " + templateName, e));
    }

    public Mono<String> processString(String templateName, String sourceCode, Object dataModel, String defaultString) {
        return this.toTemplate(templateName, sourceCode)
                .flatMap(template -> this.processString(template, dataModel))
                .onErrorResume(e -> {
                    logger.error("Failed to process template: {}", templateName);
                    return Mono.justOrEmpty(defaultString);
                });
    }

    private Mono<String> processString(Template template, Object dataModel) {
        return Mono.fromCallable(() -> this.processStringSync(template, dataModel))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String processStringSync(Template template, Object dataModel) {
        try (StringWriter result = new StringWriter(this.getInitialBufferSize())) {
            template.process(dataModel, result);
            return result.toString();
        } catch (IOException | TemplateException e) {
            throw new GenericException(HttpStatus.BAD_REQUEST, "Failed to convert to String: " + template.getName(), e);
        }
    }
}
