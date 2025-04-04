package com.fincity.saas.commons.core.service.file;

import com.fincity.saas.commons.core.document.Template;
import java.util.Map;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;

public interface ITemplateConversionService {

    Mono<byte[]> convertTemplate(Template template, String outputFormat, Map<String, Object> templateData);

    MediaType getMediaType(String outputFormat);

    String getFileExtension(String outputFormat);
}
