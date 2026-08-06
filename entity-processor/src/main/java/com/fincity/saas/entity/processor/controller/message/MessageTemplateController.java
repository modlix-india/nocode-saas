package com.fincity.saas.entity.processor.controller.message;

import com.fincity.saas.entity.processor.controller.base.BaseUpdatableController;
import com.fincity.saas.entity.processor.dao.message.MessageTemplateDAO;
import com.fincity.saas.entity.processor.dto.message.MessageTemplate;
import com.fincity.saas.entity.processor.enums.message.MessageTemplateChannel;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorMessageTemplatesRecord;
import com.fincity.saas.entity.processor.service.message.MessageTemplateService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * The reusable message library.
 *
 * <p>Standard CRUD from the base controller, plus the two reads the editor needs. Note that there is
 * no submit, no approval status and no sync endpoint: a message written here is usable immediately,
 * which is the difference the linked-device protocol bought and the thing most likely to be assumed
 * away by anyone who worked on the Cloud API version.
 */
@RestController
@RequestMapping("api/entity/processor/messageTemplates")
public class MessageTemplateController
        extends BaseUpdatableController<
                EntityProcessorMessageTemplatesRecord, MessageTemplate, MessageTemplateDAO, MessageTemplateService> {

    /** The library for one channel, which is what a rule's message picker lists. */
    @GetMapping("/library")
    public Mono<ResponseEntity<List<MessageTemplate>>> readLibrary(
            @RequestParam(value = "channel", required = false) MessageTemplateChannel channel) {
        return this.service.readLibrary(channel).map(ResponseEntity::ok);
    }

    /**
     * The variables a body may reference.
     *
     * <p>Served rather than hard-coded in the page so the editor's insertion list cannot drift from
     * what substitution actually resolves. A body referencing a variable the sender does not know
     * interpolates to nothing and sends a sentence with a hole in it.
     */
    @GetMapping("/variables")
    public Mono<ResponseEntity<List<String>>> readSupportedVariables() {
        return Mono.just(ResponseEntity.ok(MessageTemplateService.SUPPORTED_VARIABLES));
    }
}
