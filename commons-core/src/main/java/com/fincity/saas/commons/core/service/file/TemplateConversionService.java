package com.fincity.saas.commons.core.service.file;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import com.fincity.saas.commons.core.service.TemplateService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class TemplateConversionService {

    private final Map<MediaType, ITemplateConversionService> services = new HashMap<>();

    @Autowired
    private PdfConversionService pdfConversionService;

    @Autowired
    private CoreMessageResourceService msgService;

    @Autowired
    private TemplateService templateService;

    @PostConstruct
    public void init() {
        this.services.put(MediaType.APPLICATION_PDF, pdfConversionService);
    }

    public Mono<byte[]> convert(
            String templateName,
            String appCode,
            String clientCode,
            String outputFormat,
            Map<String, Object> templateData) {

        return FlatMapUtil.flatMapMono(
                        () -> SecurityContextUtil.resolveAppAndClientCode(appCode, clientCode),
                        acTup -> Mono.justOrEmpty(
                                        this.services.getOrDefault(getMediaTypeFromFormat(outputFormat), null))
                                .switchIfEmpty(msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                        CoreMessageResourceService.FILE_FORMAT_INVALID,
                                        outputFormat)),
                        (acTup, convService) -> templateService
                                .read(templateName, acTup.getT1(), acTup.getT2())
                                .map(ObjectWithUniqueID::getObject)
                                .switchIfEmpty(msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                        CoreMessageResourceService.TEMPLATE_DETAILS_MISSING,
                                        templateName)),
                        (acTup, convService, template) ->
                                convService.convertTemplate(template, outputFormat, templateData))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TemplateConversionService.convert"));
    }

    public MediaType getMediaType(String outputFormat) {
        MediaType mediaType = getMediaTypeFromFormat(outputFormat);
        ITemplateConversionService service = this.services.get(mediaType);
        return service != null ? service.getMediaType(outputFormat) : MediaType.APPLICATION_OCTET_STREAM;
    }

    public String getFileExtension(String outputFormat) {
        MediaType mediaType = getMediaTypeFromFormat(outputFormat);
        ITemplateConversionService service = this.services.get(mediaType);
        return service != null ? service.getFileExtension(outputFormat) : ".bin";
    }

    private MediaType getMediaTypeFromFormat(String outputFormat) {
        return switch (outputFormat) {
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "docx" -> MediaType.APPLICATION_XML;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
