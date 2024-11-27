package com.fincity.saas.core.service.file;

import java.util.Map;

import org.springframework.http.MediaType;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.core.document.Template;
import com.fincity.saas.core.service.AbstractTemplateService;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public abstract class AbstractTemplateConversionService extends AbstractTemplateService
		implements ITemplateConversionService {

	@Override
	public Mono<byte[]> convertTemplate(Template template, String outputFormat, Map<String, Object> templateData) {

		return FlatMapUtil.flatMapMono(
				() -> this.getLanguage(template, templateData),

				lang -> getProcessedTemplate(lang, template, templateData),

				(lang, processed) -> convertToFormat(processed, outputFormat, template)

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractConversionService.convertTemplate"));
	}

	@Override
	public MediaType getMediaType(String outputFormat) {
		return null;
	}

	@Override
	public String getFileExtension(String outputFormat) {
		return null;
	}

	protected abstract Mono<byte[]> convertToFormat(Map<String, String> processedParts, String outputFormat,
			Template template);
}
