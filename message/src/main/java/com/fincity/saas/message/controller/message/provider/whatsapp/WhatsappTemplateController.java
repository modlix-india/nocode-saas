package com.fincity.saas.message.controller.message.provider.whatsapp;

import com.fincity.saas.message.controller.base.BaseUpdatableController;
import com.fincity.saas.message.dao.message.provider.whatsapp.WhatsappTemplateDAO;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappTemplate;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappTemplatesRecord;
import com.fincity.saas.message.model.request.message.provider.whatsapp.business.WhatsappTemplateRequest;
import com.fincity.saas.message.service.message.provider.whatsapp.WhatsappTemplateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/message/whatsapp/templates")
public class WhatsappTemplateController
        extends BaseUpdatableController<
                MessageWhatsappTemplatesRecord, WhatsappTemplate, WhatsappTemplateDAO, WhatsappTemplateService> {

    @PostMapping("/fb")
    public Mono<ResponseEntity<WhatsappTemplate>> createTemplate(
            @RequestBody WhatsappTemplateRequest whatsappTemplateRequest) {
        return this.service.createTemplate(whatsappTemplateRequest).map(ResponseEntity::ok);
    }

    @PutMapping("/fb")
    public Mono<ResponseEntity<WhatsappTemplate>> updateTemplate(
            @RequestBody WhatsappTemplateRequest whatsappTemplateRequest) {
        return this.service.updateTemplate(whatsappTemplateRequest).map(ResponseEntity::ok);
    }

    @PutMapping("/fb/status")
    public Mono<ResponseEntity<WhatsappTemplate>> updateTemplateStatus(
            @RequestBody WhatsappTemplateRequest whatsappTemplateRequest) {
        return this.service.updateTemplateStatus(whatsappTemplateRequest).map(ResponseEntity::ok);
    }
}
