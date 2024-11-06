package com.fincity.saas.core.service.file;

import java.util.Map;

import org.springframework.http.MediaType;

import com.fincity.saas.core.document.Template;

import reactor.core.publisher.Mono;

public interface ITemplateConversionService {

    Mono<byte[]> convertTemplate(Template template, String outputFormat, Map<String, Object> templateData);

    MediaType getMediaType(String outputFormat);

    String getFileExtension(String outputFormat);
}
