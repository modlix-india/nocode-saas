package com.fincity.saas.commons.core.service;

import com.fincity.saas.commons.core.document.Template;
import com.fincity.saas.commons.core.service.file.PdfConversionService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * Renders an in-memory {@link Template} for editor previews. Accepts an unsaved template straight
 * from the request body, so work-in-progress edits preview without persisting; data falls back to
 * the template's {@code sampleData}.
 *
 * <p>Unlike the real send/convert flows (which use a strict RETHROW FreeMarker handler so a mail is
 * never sent with missing data), previews render <b>leniently</b>: an undefined {@code ${var}}
 * renders as empty rather than failing the whole preview. This lets a template be previewed before
 * every merge field has sample data.
 */
@Service
public class TemplatePreviewService extends AbstractTemplateService {

    private static final String BODY_KEY = "body";

    private static final Configuration PREVIEW_CONFIG = new Configuration(Configuration.VERSION_2_3_32);

    static {
        PREVIEW_CONFIG.setDefaultEncoding("UTF-8");
        // Suppress (render empty) instead of rethrowing on undefined references — preview leniency.
        PREVIEW_CONFIG.setTemplateExceptionHandler(TemplateExceptionHandler.IGNORE_HANDLER);
        PREVIEW_CONFIG.setLogTemplateExceptions(false);
        PREVIEW_CONFIG.setWrapUncheckedExceptions(true);
        PREVIEW_CONFIG.setFallbackOnNullLoopVariable(false);
    }

    private PdfConversionService pdfConversionService;

    @Autowired
    private void setPdfConversionService(PdfConversionService pdfConversionService) {
        this.pdfConversionService = pdfConversionService;
    }

    public Mono<Map<String, String>> renderParts(
            Template template, String language, Map<String, Object> templateData) {

        if (template == null
                || template.getTemplateParts() == null
                || template.getTemplateParts().isEmpty()) return Mono.just(Map.of());

        Map<String, Object> data = resolveData(template, templateData);
        Map<String, String> parts = template.getTemplateParts().get(pickLanguage(template, language));
        if (parts == null) parts = template.getTemplateParts().values().iterator().next();

        return Flux.fromIterable(parts.entrySet())
                .flatMap(e -> renderLenient(e.getValue(), data).map(str -> Tuples.of(e.getKey(), str)))
                .collectMap(Tuple2::getT1, Tuple2::getT2)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TemplatePreviewService.renderParts"));
    }

    public Mono<byte[]> renderPdf(Template template, Map<String, Object> templateData) {
        return renderParts(template, "", templateData)
                .flatMap(parts -> this.pdfConversionService.renderHtmlToPdf(parts.getOrDefault(BODY_KEY, "")))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TemplatePreviewService.renderPdf"));
    }

    private String pickLanguage(Template template, String language) {
        if (!StringUtil.safeIsBlank(language)) return language;
        if (!StringUtil.safeIsBlank(template.getDefaultLanguage())) return template.getDefaultLanguage();
        return "en";
    }

    private Mono<String> renderLenient(String templateString, Map<String, Object> data) {
        return Mono.fromCallable(() -> {
                    freemarker.template.Template temp = new freemarker.template.Template(
                            "templatePart", templateString == null ? "" : templateString, PREVIEW_CONFIG);
                    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            Writer out = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
                        temp.process(data, out);
                        return baos.toString(StandardCharsets.UTF_8);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Map<String, Object> resolveData(Template template, Map<String, Object> templateData) {
        if (templateData != null && !templateData.isEmpty()) return templateData;
        return template.getSampleData() == null ? new HashMap<>() : template.getSampleData();
    }
}
