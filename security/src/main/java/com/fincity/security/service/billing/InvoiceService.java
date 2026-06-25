package com.fincity.security.service.billing;

import java.util.HashMap;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventNames;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.billing.InvoiceDAO;
import com.fincity.security.dto.billing.Invoice;
import com.fincity.security.service.AppService;
import com.fincity.security.service.ClientService;

import reactor.core.publisher.Flux;
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

    public InvoiceService(InvoiceDAO dao, AppService appService, ClientService clientService,
            EventCreationService ecService) {
        this.dao = dao;
        this.appService = appService;
        this.clientService = clientService;
        this.ecService = ecService;
    }

    public Mono<Invoice> create(Invoice invoice) {
        return this.dao.create(invoice);
    }

    public Mono<Invoice> readById(ULong id) {
        return this.dao.readById(id);
    }

    public Flux<Invoice> findByClient(ULong clientId) {
        return this.dao.findByClient(clientId);
    }

    /** Persist the (already PAID-stamped) invoice and raise INVOICE_GENERATED. */
    public Mono<Invoice> markPaidAndEmit(Invoice invoice) {
        return this.dao.update(invoice)
                .flatMap(saved -> this.raiseInvoiceEvent(saved).thenReturn(saved))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "InvoiceService.markPaidAndEmit"));
    }

    private Mono<Void> raiseInvoiceEvent(Invoice invoice) {
        return FlatMapUtil.flatMapMono(
                () -> this.appService.getAppById(invoice.getAppId()),
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
