package com.fincity.saas.core.controller;

import com.fincity.saas.commons.core.document.Template;
import com.fincity.saas.commons.core.model.TemplatePreviewRequest;
import com.fincity.saas.commons.core.repository.TemplateRepository;
import com.fincity.saas.commons.core.service.TemplatePreviewService;
import com.fincity.saas.commons.core.service.TemplateService;
import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/core/templates")
public class TemplateController
        extends AbstractOverridableDataController<Template, TemplateRepository, TemplateService> {

    private final TemplatePreviewService previewService;

    public TemplateController(TemplatePreviewService previewService) {
        this.previewService = previewService;
    }

    @PostMapping("preview")
    public Mono<ResponseEntity<Map<String, String>>> preview(@RequestBody TemplatePreviewRequest request) {
        return this.previewService
                .renderParts(request.getTemplate(), request.getLanguage(), request.getData())
                .map(ResponseEntity::ok);
    }

    @PostMapping(value = "preview/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public Mono<ResponseEntity<byte[]>> previewPdf(@RequestBody TemplatePreviewRequest request) {
        return this.previewService
                .renderPdf(request.getTemplate(), request.getData())
                .map(bytes -> ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(bytes));
    }
}
