package com.fincity.security.controller.billing;

import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.security.dto.billing.Invoice;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.SecurityMessageResourceService;
import com.fincity.security.service.billing.InvoiceService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Token-purchase invoices, readable by an Owner or a holder of Invoice_READ,
 * restricted to the caller's own client and the clients it manages.
 */
@RestController
@RequestMapping("api/security/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final ClientService clientService;
    private final SecurityMessageResourceService messageResourceService;

    public InvoiceController(InvoiceService invoiceService, ClientService clientService,
            SecurityMessageResourceService messageResourceService) {
        this.invoiceService = invoiceService;
        this.clientService = clientService;
        this.messageResourceService = messageResourceService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('Authorities.ROLE_Owner', 'Authorities.Invoice_READ')")
    public Mono<ResponseEntity<Invoice>> read(@PathVariable ULong id) {
        return this.invoiceService.readById(id)
                .flatMap(invoice -> this.assertCanSee(invoice.getClientId()).thenReturn(invoice))
                .map(ResponseEntity::ok);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('Authorities.ROLE_Owner', 'Authorities.Invoice_READ')")
    public Flux<Invoice> list(@RequestParam ULong clientId) {
        return this.assertCanSee(clientId).thenMany(this.invoiceService.findByClient(clientId));
    }

    /** Allow only the caller's own client or a client it manages. */
    private Mono<Boolean> assertCanSee(ULong clientId) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> ULong.valueOf(ca.getUser().getClientId()).equals(clientId)
                        ? Mono.just(true)
                        : this.clientService.isUserClientManageClient(ca, clientId))
                .filter(BooleanUtil::safeValueOf)
                .switchIfEmpty(this.messageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.FORBIDDEN_PERMISSION, "the invoices"));
    }
}
