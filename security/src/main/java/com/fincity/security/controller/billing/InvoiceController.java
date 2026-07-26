package com.fincity.security.controller.billing;

import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.util.ConditionUtil;
import com.fincity.security.dto.billing.Invoice;
import com.fincity.security.service.billing.InvoiceService;

import reactor.core.publisher.Mono;

/**
 * Token-purchase invoices. Two reads: by id, and a filtered page (query params or
 * a {@link Query} body) scoped to the seller + app in the request context. The
 * {@code appCode}/{@code clientCode} headers carry that context; all authorization
 * (Owner / Invoice_READ, and the party-to-the-invoice restriction) is enforced in
 * {@link InvoiceService} and {@code InvoiceDAO}.
 */
@RestController
@RequestMapping("api/security/invoices")
public class InvoiceController {

    private static final String HEADER_APP_CODE = "appCode";
    private static final String HEADER_CLIENT_CODE = "clientCode";

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Invoice>> read(@PathVariable ULong id) {
        return this.invoiceService.readById(id).map(ResponseEntity::ok);
    }

    @GetMapping
    public Mono<ResponseEntity<Page<Invoice>>> readPageFilter(Pageable pageable, ServerHttpRequest request) {
        Pageable page = pageable == null ? PageRequest.of(0, 10, Direction.DESC, "id") : pageable;
        return this.invoiceService.readPageFilter(page,
                ConditionUtil.parameterMapToMap(request.getQueryParams(), "page", "size", "sort", HEADER_APP_CODE,
                        HEADER_CLIENT_CODE),
                appCode(request), clientCode(request))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/query")
    public Mono<ResponseEntity<Page<Invoice>>> readPageFilter(@RequestBody Query query, ServerHttpRequest request) {
        return this.invoiceService.readPageFilter(query.getPageable(), query.getCondition(),
                appCode(request), clientCode(request))
                .map(ResponseEntity::ok);
    }

    /**
     * The authenticated caller's OWN purchase history (invoices where they are the
     * buyer), for the app in the appCode header. Scoped to the caller's client from
     * the security context, so any logged-in buyer sees only their own purchases.
     */
    @GetMapping("/my")
    public Mono<ResponseEntity<Page<Invoice>>> readMyPurchases(Pageable pageable, ServerHttpRequest request) {
        Pageable page = pageable == null ? PageRequest.of(0, 10, Direction.DESC, "id") : pageable;
        return this.invoiceService.readMyPurchases(page,
                ConditionUtil.parameterMapToMap(request.getQueryParams(), "page", "size", "sort", HEADER_APP_CODE,
                        HEADER_CLIENT_CODE),
                appCode(request))
                .map(ResponseEntity::ok);
    }

    private static String appCode(ServerHttpRequest request) {
        return request.getHeaders().getFirst(HEADER_APP_CODE);
    }

    private static String clientCode(ServerHttpRequest request) {
        return request.getHeaders().getFirst(HEADER_CLIENT_CODE);
    }
}
