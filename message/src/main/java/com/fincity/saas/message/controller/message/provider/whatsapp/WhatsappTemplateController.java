package com.fincity.saas.message.controller.message.provider.whatsapp;

import com.fincity.saas.message.controller.base.BaseUpdatableController;
import com.fincity.saas.message.dao.message.provider.whatsapp.WhatsappTemplateDAO;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappTemplate;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappTemplatesRecord;
import com.fincity.saas.message.model.common.Identity;
import com.fincity.saas.message.model.request.message.provider.whatsapp.business.WhatsappTemplateRequest;
import com.fincity.saas.message.service.message.provider.whatsapp.WhatsappTemplateService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * WhatsApp configuration, restricted to tenant owners.
 *
 * <p>Annotated on the controller rather than the service, which is the usual place in this
 * codebase, because these services are shared with the inbound webhook path. That path runs
 * with no user at all, so a class-level rule on the service would gate HTTP access and break
 * message delivery at the same time. The controller is the boundary that only humans cross.
 *
 * <p>Annotated per method, never on the class. A class-level rule applies to every public method
 * including the inherited {@code @InitBinder}, and reactive method security only supports methods
 * returning a {@code Publisher}, so a {@code void} binder callback makes every request to this
 * controller fail with a 500 before it is even routed. It fails for owners too, so it is not the
 * kind of mistake that shows up only in an access test.
 *
 * <p>What that leaves open is deliberate rather than incidental. The declared methods here are all
 * administrative writes and carry the gate; the generic read endpoints inherited from
 * {@code BaseUpdatableController} do not, because the deal profile calls them as an ordinary sales
 * agent to list approved templates and business numbers in order to send a message. Reading the
 * templates you are allowed to send is deal work, not settings administration. Those reads are
 * closed by moving them behind entity-processor and blocking {@code /api/message/**} at nginx, not
 * by an authority a salesperson will never hold.
 */
@RestController
@RequestMapping("/api/message/whatsapp/templates")
public class WhatsappTemplateController
        extends BaseUpdatableController<
                MessageWhatsappTemplatesRecord, WhatsappTemplate, WhatsappTemplateDAO, WhatsappTemplateService> {

    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    @PostMapping("/fb")
    public Mono<ResponseEntity<WhatsappTemplate>> createTemplate(
            @RequestBody WhatsappTemplateRequest whatsappTemplateRequest) {
        return this.service.createTemplate(whatsappTemplateRequest).map(ResponseEntity::ok);
    }

    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    @PutMapping("/fb")
    public Mono<ResponseEntity<WhatsappTemplate>> updateTemplate(
            @RequestBody WhatsappTemplateRequest whatsappTemplateRequest) {
        return this.service.updateTemplate(whatsappTemplateRequest).map(ResponseEntity::ok);
    }

    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    @PutMapping("/fb/status")
    public Mono<ResponseEntity<WhatsappTemplate>> updateTemplateStatus(
            @RequestBody WhatsappTemplateRequest whatsappTemplateRequest) {
        return this.service.updateTemplateStatus(whatsappTemplateRequest).map(ResponseEntity::ok);
    }

    // -------------------------------------------------------------------------------------------
    // Internal read variants, for entity-processor only.
    //
    // The deal profile lists the templates an agent may send, which is a read no salesperson can be
    // asked to hold ROLE_Owner for. It reaches them through entity-processor now, so that when
    // /api/message/** is denied at the edge the feature keeps working. These take the tenant as
    // parameters rather than off a security context, the same way the conversation reads already
    // do, because there is no user on a service-to-service hop.
    //
    // Read-only on purpose. Creating and editing templates stays on the ROLE_Owner routes above and
    // is not proxied: that is settings administration, and no part of it belongs in a deal.
    // -------------------------------------------------------------------------------------------

    @GetMapping("/internal")
    public Mono<ResponseEntity<Page<WhatsappTemplate>>> readPageInternal(
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode,
            @RequestParam(value = "statuses", required = false) String statuses,
            @RequestParam(value = "templateName", required = false) String templateName,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return this.service
                .readPageInternal(appCode, clientCode, statuses, templateName, PageRequest.of(page, size))
                .map(ResponseEntity::ok);
    }

    @GetMapping("/internal/{id}")
    public Mono<ResponseEntity<WhatsappTemplate>> readByIdentityInternal(
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode,
            @PathVariable("id") final Identity identity) {
        return this.service
                .readByIdentityInternal(appCode, clientCode, identity)
                .map(ResponseEntity::ok);
    }
}
