package com.fincity.saas.message.controller.message.provider.whatsapp;

import com.fincity.saas.message.controller.base.BaseUpdatableController;
import com.fincity.saas.message.dao.message.provider.whatsapp.WhatsappPhoneNumberDAO;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappPhoneNumber;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappPhoneNumbersRecord;
import com.fincity.saas.message.model.common.Identity;
import com.fincity.saas.message.service.message.provider.whatsapp.WhatsappPhoneNumberService;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
 */
@PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
@RestController
@RequestMapping("/api/message/whatsapp/phone-numbers")
public class WhatsappPhoneNumberController
        extends BaseUpdatableController<
                MessageWhatsappPhoneNumbersRecord,
                WhatsappPhoneNumber,
                WhatsappPhoneNumberDAO,
                WhatsappPhoneNumberService> {

    @PostMapping("/sync")
    public Mono<ResponseEntity<List<WhatsappPhoneNumber>>> syncPhoneNumbers(@RequestParam final String connectionName) {
        return this.service.syncPhoneNumbers(connectionName).collectList().map(ResponseEntity::ok);
    }

    @PostMapping("/sync" + "/{" + PATH_VARIABLE_ID + "}")
    public Mono<ResponseEntity<WhatsappPhoneNumber>> syncPhoneNumber(
            @PathVariable(PATH_VARIABLE_ID) final Identity identity, @RequestParam final String connectionName) {
        return this.service.syncPhoneNumber(connectionName, identity).map(ResponseEntity::ok);
    }

    @PatchMapping("/default" + "/{" + PATH_VARIABLE_ID + "}")
    public Mono<ResponseEntity<WhatsappPhoneNumber>> setDefault(
            @PathVariable(PATH_VARIABLE_ID) final Identity identity) {
        return this.service.setDefault(identity).map(ResponseEntity::ok);
    }

    @PatchMapping("/product" + "/{" + PATH_VARIABLE_ID + "}")
    public Mono<ResponseEntity<WhatsappPhoneNumber>> setProductId(
            @PathVariable(PATH_VARIABLE_ID) final Identity identity, @RequestParam("productId") final ULong productId) {
        return this.service.setProductId(identity, productId).map(ResponseEntity::ok);
    }

    @PutMapping("/status")
    public Mono<ResponseEntity<List<WhatsappPhoneNumber>>> updatePhoneNumbersStatus(
            @RequestParam final String connectionName) {
        return this.service
                .updatePhoneNumbersStatus(connectionName)
                .collectList()
                .map(ResponseEntity::ok);
    }

    @PutMapping("/status" + "/{" + PATH_VARIABLE_ID + "}")
    public Mono<ResponseEntity<WhatsappPhoneNumber>> updatePhoneNumberStatus(
            @PathVariable(PATH_VARIABLE_ID) final Identity identity, @RequestParam final String connectionName) {
        return this.service.updatePhoneNumberStatus(connectionName, identity).map(ResponseEntity::ok);
    }
}
