package com.fincity.saas.notification.service.template;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Objects;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.service.CacheService;

import freemarker.cache.MruCacheStorage;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import lombok.Getter;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Getter
@Component
public abstract class BaseTemplateProcessor {

	protected static final Configuration DEFAULT = new Configuration(Configuration.VERSION_2_3_33);

	private static final int INITIAL_BUFFER_SIZE = 4096;

	private static final String CACHE_NAME_TEMPLATE = "notificationTemplateProcessor";

	private static final Logger logger = LoggerFactory.getLogger(BaseTemplateProcessor.class);

	static {
		DEFAULT.setDefaultEncoding(StandardCharsets.UTF_8.name());
		DEFAULT.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
		DEFAULT.setLogTemplateExceptions(false);
		DEFAULT.setWrapUncheckedExceptions(true);
		DEFAULT.setFallbackOnNullLoopVariable(false);
		DEFAULT.setSQLDateAndTimeTimeZone(TimeZone.getTimeZone(ZoneOffset.UTC));
		DEFAULT.setTemplateLoader(new StringTemplateLoader());
		DEFAULT.setCacheStorage(new MruCacheStorage(200, Integer.MAX_VALUE));
	}

	private CacheService cacheService;

	@Autowired
	public void setCacheService(CacheService cacheService) {
		this.cacheService = cacheService;
	}

	public Configuration getConfiguration() {
		return DEFAULT;
	}

	public int getInitialBufferSize() {
		return INITIAL_BUFFER_SIZE;
	}

	public Mono<Template> toTemplate(String templateName, String sourceCode) {
		return cacheService.cacheValueOrGet(CACHE_NAME_TEMPLATE, () -> this.toTemplateSync(templateName, sourceCode),
				templateName);
	}

	public Mono<Boolean> evictTemplate(Object... templateNames) {

		this.removeFreeMarkerCache(templateNames);
		return cacheService.evict(CACHE_NAME_TEMPLATE, templateNames);
	}

	private void removeFreeMarkerCache(Object... templateNames) {
		Arrays.stream(templateNames).filter(Objects::nonNull).forEach(templateName -> {
			try {
				DEFAULT.removeTemplateFromCache(templateName.toString());
			} catch (IOException e) {
				logger.error("No cache found in Freemarker cache for template: {}", templateName);
			}
		});
	}

	private Mono<Template> toTemplateSync(String templateName, String sourceCode) {
		return Mono.fromCallable(() -> new Template(templateName, sourceCode, this.getConfiguration()))
				.onErrorMap(IOException.class, e -> new GenericException(HttpStatus.BAD_REQUEST,
						"Failed to create template: " + templateName, e));
	}

	public Mono<String> processString(String templateName, String sourceCode, Object dataModel) {
		return this.toTemplate(templateName, sourceCode)
				.flatMap(template -> this.processString(template, dataModel))
				.onErrorMap(e -> new GenericException(HttpStatus.BAD_REQUEST,
						"Failed to process template: " + templateName, e));
	}

	public Mono<String> processString(Template template, Object dataModel) {
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
