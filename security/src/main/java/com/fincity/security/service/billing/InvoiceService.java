package com.fincity.security.service.billing;

import java.util.HashMap;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventNames;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.billing.InvoiceDAO;
import com.fincity.security.dto.billing.Invoice;
import com.fincity.security.service.AppService;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.SecurityMessageResourceService;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Token-purchase invoices. Creation snapshots seller (C) and buyer (M) so the tax
 * invoice is immutable. On payment, {@link #markPaidAndEmit} persists the PAID
 * invoice and raises {@code INVOICE_GENERATED}, which the seeded EventActions turn
 * into the PDF + email (no PDF/email code lives here).
 */
@Service
public class InvoiceService {

    private final InvoiceDAO dao;
    private final AppService appService;
    private final ClientService clientService;
    private final EventCreationService ecService;
    private final SecurityMessageResourceService messageResourceService;

    public InvoiceService(InvoiceDAO dao, AppService appService, ClientService clientService,
            EventCreationService ecService, SecurityMessageResourceService messageResourceService) {
        this.dao = dao;
        this.appService = appService;
        this.clientService = clientService;
        this.ecService = ecService;
        this.messageResourceService = messageResourceService;
    }

    /** Ungated: called only from the purchase flow, which carries its own gate. */
    public Mono<Invoice> create(Invoice invoice) {
        return this.dao.create(invoice);
    }

    /** Owner / Invoice_READ holder; readable only when the caller is the invoice's seller or buyer. */
    @PreAuthorize("hasAnyAuthority('Authorities.ROLE_Owner', 'Authorities.Invoice_READ')")
    public Mono<Invoice> readById(ULong id) {
        return this.dao.readById(id)
                .flatMap(invoice -> this.assertCanSee(invoice).thenReturn(invoice))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "InvoiceService.readById"));
    }

    /**
     * Owner / Invoice_READ holder. The page is scoped to the seller (clientCode
     * header) and app (appCode header) in context; the DAO further restricts to
     * invoices the caller is a party to. Extra query filters are ANDed in.
     */
    @PreAuthorize("hasAnyAuthority('Authorities.ROLE_Owner', 'Authorities.Invoice_READ')")
    public Mono<Page<Invoice>> readPageFilter(Pageable pageable, AbstractCondition condition, String appCode,
            String clientCode) {
        return FlatMapUtil.flatMapMono(
                () -> this.appService.getAppByCode(appCode),
                app -> this.clientService.getClientBy(clientCode),
                (app, seller) -> this.dao.readPageFilter(pageable, scope(app.getId(), seller.getId(), condition)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "InvoiceService.readPageFilter"));
    }

    /** AND the seller + app scope from the request context with the caller's filters. */
    private static AbstractCondition scope(ULong appId, ULong sellerClientId, AbstractCondition condition) {
        AbstractCondition seller = FilterCondition.of("sellerClientId", sellerClientId, FilterConditionOperator.EQUALS);
        AbstractCondition app = FilterCondition.of("appId", appId, FilterConditionOperator.EQUALS);
        if (condition == null)
            return ComplexCondition.and(seller, app);
        return ComplexCondition.and(seller, app, condition);
    }

    /** Allow only when the caller's client is the invoice's seller or buyer (SYSTEM sees all). */
    private Mono<Boolean> assertCanSee(Invoice invoice) {
        return SecurityContextUtil.getUsersContextAuthentication().flatMap(ca -> {
            if (ca.isSystemClient())
                return Mono.just(Boolean.TRUE);
            ULong callerClientId = ULong.valueOf(ca.getUser().getClientId());
            if (callerClientId.equals(invoice.getSellerClientId()) || callerClientId.equals(invoice.getClientId()))
                return Mono.just(Boolean.TRUE);
            return this.messageResourceService.throwMessage(
                    msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                    SecurityMessageResourceService.FORBIDDEN_PERMISSION, "the invoice");
        });
    }

    /** Persist the (already PAID-stamped) invoice and raise INVOICE_GENERATED. */
    public Mono<Invoice> markPaidAndEmit(Invoice invoice) {
        return this.dao.update(invoice)
                .flatMap(saved -> this.raiseInvoiceEvent(saved).thenReturn(saved))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "InvoiceService.markPaidAndEmit"));
    }

    private Mono<Void> raiseInvoiceEvent(Invoice invoice) {
        return FlatMapUtil.flatMapMono(
                () -> this.appService.getAppByIdInternal(invoice.getAppId()),
                app -> this.clientService.getClientInfoById(invoice.getClientId()),
                (app, client) -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("invoiceId", invoice.getId());
                    data.put("invoiceNumber", invoice.getInvoiceNumber());
                    data.put("appCode", app.getAppCode());
                    data.put("clientCode", client.getCode());
                    data.put("tokensPurchased", invoice.getTokensPurchased());
                    data.put("baseAmount", invoice.getBaseAmount());
                    data.put("gstPercentage", invoice.getGstPercentage());
                    data.put("gstAmount", invoice.getGstAmount());
                    data.put("totalAmount", invoice.getTotalAmount());
                    data.put("currency", invoice.getCurrency());
                    data.put("sellerLegalName", invoice.getSellerLegalName());
                    data.put("sellerGstin", invoice.getSellerGstin());
                    data.put("sellerAddress", invoice.getSellerAddress());
                    data.put("buyerLegalName", invoice.getBuyerLegalName());
                    data.put("buyerGstin", invoice.getBuyerGstin());
                    data.put("buyerAddress", invoice.getBuyerAddress());
                    data.put("pdfFileKey", invoice.getPdfFileKey());
                    EventQueObject evt = new EventQueObject()
                            .setAppCode(app.getAppCode())
                            .setClientCode(client.getCode())
                            .setEventName(EventNames.INVOICE_GENERATED)
                            .setData(data);
                    return this.ecService.createEvent(evt);
                }).then();
    }
}
