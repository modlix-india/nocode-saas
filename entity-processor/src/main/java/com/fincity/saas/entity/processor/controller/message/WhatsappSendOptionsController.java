package com.fincity.saas.entity.processor.controller.message;

import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.service.message.WhatsappSendOptionsService;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Public entry point for reading what a WhatsApp message can be sent as, and sent from. Thin by
 * design: the access check and the call out to the message service both live in the service, per
 * this codebase's convention that authorization belongs on the service layer.
 *
 * <p>Read-only. These stand in front of the message service's own routes so the UI has a single
 * host to talk to and {@code /api/message/**} can be closed at the edge; the administrative writes
 * behind those routes stay where they are, on {@code ROLE_Owner}, and are not mirrored here.
 *
 * <p>Bodies are passed through unchanged rather than remapped, so a page repointed from {@code
 * api/message/whatsapp/...} to here binds to exactly the fields it already did.
 */
@RestController
@RequestMapping("api/entity/processor/whatsapp")
public class WhatsappSendOptionsController {

    private final WhatsappSendOptionsService service;

    public WhatsappSendOptionsController(WhatsappSendOptionsService service) {
        this.service = service;
    }

    /**
     * The templates on offer in the composer.
     *
     * @param status repeatable, e.g. {@code ?status=APPROVED&status=PENDING}; omit for all statuses
     */
    @GetMapping("/templates")
    public Mono<ResponseEntity<Map<String, Object>>> readTemplates(
            @RequestParam(value = "status", required = false) List<String> status,
            @RequestParam(value = "templateName", required = false) String templateName,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return this.service
                .readTemplates(status, templateName, PageRequest.of(page, size))
                .map(ResponseEntity::ok);
    }

    /** One template, for the preview shown before an agent commits to sending it. */
    @GetMapping("/templates/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> readTemplate(@PathVariable("id") Identity id) {
        return this.service.readTemplate(id).map(ResponseEntity::ok);
    }

    /** The business numbers a message can go out from. */
    @GetMapping("/phone-numbers")
    public Mono<ResponseEntity<Map<String, Object>>> readPhoneNumbers(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return this.service.readPhoneNumbers(PageRequest.of(page, size)).map(ResponseEntity::ok);
    }

    /**
     * The number to preselect. An empty object, not a 404, when the tenant has named no default:
     * having none is a working configuration.
     */
    @GetMapping("/phone-numbers/default")
    public Mono<ResponseEntity<Map<String, Object>>> readDefaultPhoneNumber() {
        return this.service.readDefaultPhoneNumber().map(ResponseEntity::ok);
    }
}
