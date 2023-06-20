package com.fincity.saas.core.service.connection.email;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.core.document.Template;
import com.fincity.saas.core.model.ProcessedEmailDetails;
import com.fincity.saas.core.service.CoreMessageResourceService;

import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public abstract class AbstractEmailService {

	protected static final Configuration CONFIGURATION = new Configuration(Configuration.VERSION_2_3_32);

	static {

		CONFIGURATION.setDefaultEncoding("UTF-8");
		CONFIGURATION.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
		CONFIGURATION.setLogTemplateExceptions(false);
		CONFIGURATION.setWrapUncheckedExceptions(true);
		CONFIGURATION.setFallbackOnNullLoopVariable(false);
		CONFIGURATION.setSQLDateAndTimeTimeZone(TimeZone.getDefault());
	}

	@Autowired
	protected CoreMessageResourceService msgService;

	protected Logger logger;

	public AbstractEmailService() {

		logger = LoggerFactory.getLogger(this.getClass());
	}

	protected Mono<ProcessedEmailDetails> getProcessedEmailDetails(List<String> toAddresses, Template template,
	        Map<String, Object> templateData) {

		return FlatMapUtil.flatMapMono(

		        () -> this.getToAddresses(toAddresses, template, templateData),

		        to -> this.getFromAddress(template, templateData),

		        (to, from) -> this.getLanguage(template, templateData),

		        (to, from, language) -> this.getProcessedTemplate(language, template, templateData),

		        (to, from, language, temp) -> Mono.just(new ProcessedEmailDetails().setTo(to)
		                .setFrom(from)
		                .setSubject(temp.getOrDefault("subject", ""))
		                .setBody(temp.getOrDefault("body", "")))

		)
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractEmailService.getProcessedEmailDetails"));
	}

	protected Mono<Map<String, String>> getProcessedTemplate(String language, Template template,
	        Map<String, Object> templateData) {

		if (template.getTemplateParts() == null || template.getTemplateParts()
		        .isEmpty()) {
			return this.msgService.throwMessage(HttpStatus.INTERNAL_SERVER_ERROR,
			        CoreMessageResourceService.MAIL_SEND_ERROR, "No template parts found");
		}

		Map<String, String> temp = template.getTemplateParts()
		        .get(language.isBlank() ? "en" : language);
		if (temp == null)
			temp = template.getTemplateParts()
			        .values()
			        .iterator()
			        .next();

		return Flux.fromIterable(temp.entrySet())
		        .flatMap(e -> this.processFreeMarker("templatePart", e.getValue(), templateData)
		                .map(str -> Tuples.of(e.getKey(), str)))
		        .collectMap(Tuple2::getT1, Tuple2::getT2);
	}

	protected Mono<String> getLanguage(Template template, Map<String, Object> templateData) {

		boolean isBlankExpression = StringUtil.safeIsBlank(template.getLanguageExpression());

		if (isBlankExpression && StringUtil.safeIsBlank(template.getDefaultLanguage()))
			return Mono.just("");

		return processFreeMarker("language", template.getLanguageExpression(), templateData)
		        .map(e -> e.isBlank() ? template.getDefaultLanguage() : e);
	}

	protected Mono<String> getFromAddress(Template template, Map<String, Object> templateData) {

		boolean isBlankExpression = StringUtil.safeIsBlank(template.getFromExpression());

		if (isBlankExpression)
			return Mono.just("");

		return processFreeMarker("fromExpression", template.getFromExpression(), templateData);
	}

	protected Mono<List<String>> getToAddresses(List<String> toAddresses, Template template,
	        Map<String, Object> templateData) {

		List<String> addresses = new ArrayList<>();

		boolean isBlankExpression = StringUtil.safeIsBlank(template.getToExpression());

		if (toAddresses != null && !toAddresses.isEmpty()) {

			if (isBlankExpression)
				return Mono.just(toAddresses);
			addresses.addAll(toAddresses);
		}

		return FlatMapUtil.flatMapMonoWithNull(

		        () -> Mono.just(addresses),

		        addrList ->
				{
			        if (isBlankExpression)
				        return Mono.just(addrList);

			        return processFreeMarker("toExpression", template.getToExpression(), templateData).map(e -> {

				        String[] addrs = e.split(";");

				        for (String addr : addrs) {
					        if (StringUtil.safeIsBlank(addr))
						        continue;
					        addrList.add(addr);
				        }
				        return addrList;
			        });
		        },

		        (addrList, finList) ->
				{
			        if (finList.isEmpty()) {
				        return this.msgService.throwMessage(HttpStatus.INTERNAL_SERVER_ERROR,
				                CoreMessageResourceService.MAIL_SEND_ERROR, "No Send Addresses Found.");
			        }

			        return Mono.just(finList);
		        })
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractEmailService.getToAddress"));

	}

	protected Mono<String> processFreeMarker(String name, String template, Map<String, Object> templateData) {

		return Mono.fromCallable(() -> {

			freemarker.template.Template temp = new freemarker.template.Template(name, template, CONFIGURATION);
			try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); Writer out = new OutputStreamWriter(baos);) {

				temp.process(templateData, out);
				return new String(baos.toByteArray(), StandardCharsets.UTF_8);
			}
		})
		        .subscribeOn(Schedulers.boundedElastic());
	}
}
