package com.fincity.saas.message.controller.message.provider.whatsapp;

import com.fincity.saas.message.controller.base.BaseUpdatableController;
import com.fincity.saas.message.dao.message.provider.whatsapp.WhatsappPhoneNumberDAO;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappPhoneNumber;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappPhoneNumbersRecord;
import com.fincity.saas.message.model.common.Identity;
import com.fincity.saas.message.model.message.whatsapp.phone.RequestCode;
import com.fincity.saas.message.model.message.whatsapp.phone.VerifyCode;
import com.fincity.saas.message.model.message.whatsapp.response.Response;
import com.fincity.saas.message.service.message.provider.whatsapp.WhatsappPhoneNumberService;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/message/whatsapp/phone-numbers")
public class WhatsappPhoneNumberController
        extends BaseUpdatableController<
                MessageWhatsappPhoneNumbersRecord,
                WhatsappPhoneNumber,
                WhatsappPhoneNumberDAO,
                WhatsappPhoneNumberService> {

    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    @PostMapping("/sync")
    public Mono<ResponseEntity<List<WhatsappPhoneNumber>>> syncPhoneNumbers(@RequestParam final String connectionName) {
        return this.service.syncPhoneNumbers(connectionName).collectList().map(ResponseEntity::ok);
    }

    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    @PostMapping("/sync" + "/{" + PATH_VARIABLE_ID + "}")
    public Mono<ResponseEntity<WhatsappPhoneNumber>> syncPhoneNumber(
            @PathVariable(PATH_VARIABLE_ID) final Identity identity, @RequestParam final String connectionName) {
        return this.service.syncPhoneNumber(connectionName, identity).map(ResponseEntity::ok);
    }

    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    @PatchMapping("/default" + "/{" + PATH_VARIABLE_ID + "}")
    public Mono<ResponseEntity<WhatsappPhoneNumber>> setDefault(
            @PathVariable(PATH_VARIABLE_ID) final Identity identity) {
        return this.service.setDefault(identity).map(ResponseEntity::ok);
    }

    /**
     * Omitting {@code productId} unmaps the number, so the same endpoint moves a mapping and clears
     * one. A separate delete endpoint would be a second way to write the same column.
     */
    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    @PatchMapping("/product" + "/{" + PATH_VARIABLE_ID + "}")
    public Mono<ResponseEntity<WhatsappPhoneNumber>> setProductId(
            @PathVariable(PATH_VARIABLE_ID) final Identity identity,
            @RequestParam(value = "productId", required = false) final ULong productId) {
        return this.service.setProductId(identity, productId).map(ResponseEntity::ok);
    }

    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    @PutMapping("/status")
    public Mono<ResponseEntity<List<WhatsappPhoneNumber>>> updatePhoneNumbersStatus(
            @RequestParam final String connectionName) {
        return this.service
                .updatePhoneNumbersStatus(connectionName)
                .collectList()
                .map(ResponseEntity::ok);
    }

    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    @PutMapping("/status" + "/{" + PATH_VARIABLE_ID + "}")
    public Mono<ResponseEntity<WhatsappPhoneNumber>> updatePhoneNumberStatus(
            @PathVariable(PATH_VARIABLE_ID) final Identity identity, @RequestParam final String connectionName) {
        return this.service.updatePhoneNumberStatus(connectionName, identity).map(ResponseEntity::ok);
    }

    /**
     * Meta sends the code to the number itself, so the response says only that the request was
     * accepted. The code arrives out of band and comes back through {@link #verifyCode}.
     */
    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    @PostMapping("/{" + PATH_VARIABLE_ID + "}" + "/request-code")
    public Mono<ResponseEntity<Response>> requestCode(
            @PathVariable(PATH_VARIABLE_ID) final Identity identity,
            @RequestParam final String connectionName,
            @RequestBody final RequestCode requestCode) {
        return this.service.requestCode(connectionName, identity, requestCode).map(ResponseEntity::ok);
    }

    /**
     * Returns the phone number rather than the provider response, because verification changes what
     * Meta will let the number do and the table is what the caller is looking at.
     */
    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    @PostMapping("/{" + PATH_VARIABLE_ID + "}" + "/verify-code")
    public Mono<ResponseEntity<WhatsappPhoneNumber>> verifyCode(
            @PathVariable(PATH_VARIABLE_ID) final Identity identity,
            @RequestParam final String connectionName,
            @RequestBody final VerifyCode verifyCode) {
        return this.service.verifyCode(connectionName, identity, verifyCode).map(ResponseEntity::ok);
    }

    // -------------------------------------------------------------------------------------------
    // Internal read variants, for entity-processor only.
    //
    // The deal composer names the business number a message goes out from, so an agent has to be
    // able to read the list. It reaches these through entity-processor now, so the feature survives
    // /api/message/** being denied at the edge. They take the tenant as parameters rather than off a
    // security context, the same way the conversation reads already do, because there is no user on
    // a service-to-service hop.
    //
    // Read-only on purpose. Syncing numbers from Meta, choosing the default and pinning one to a
    // product all stay on the ROLE_Owner routes above and are not proxied.
    // -------------------------------------------------------------------------------------------

    @GetMapping("/internal")
    public Mono<ResponseEntity<Page<WhatsappPhoneNumber>>> readPageInternal(
            @RequestParam("appCode") final String appCode,
            @RequestParam("clientCode") final String clientCode,
            @RequestParam(value = "page", defaultValue = "0") final int page,
            @RequestParam(value = "size", defaultValue = "20") final int size) {
        return this.service
                .readPageInternal(appCode, clientCode, PageRequest.of(page, size))
                .map(ResponseEntity::ok);
    }

    /**
     * Empty body when the tenant has marked no default, rather than a 404. Having no default is a
     * working configuration, not a missing record, and the caller distinguishes the two.
     */
    @GetMapping("/internal/default")
    public Mono<ResponseEntity<WhatsappPhoneNumber>> readDefaultInternal(
            @RequestParam("appCode") final String appCode, @RequestParam("clientCode") final String clientCode) {
        return this.service.getDefaultInternal(appCode, clientCode).map(ResponseEntity::ok);
    }
}
