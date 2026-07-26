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
import com.fincity.security.dao.billing.PaymentDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.billing.Invoice;
import com.fincity.security.dto.billing.Payment;
import com.fincity.security.jooq.enums.SecurityPaymentGateway;
import com.fincity.security.jooq.enums.SecurityPaymentStatus;
import com.fincity.security.service.AppService;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.ClientUrlService;
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
    private final PaymentDAO paymentDAO;
    private final AppService appService;
    private final ClientService clientService;
    private final EventCreationService ecService;
    private final SecurityMessageResourceService messageResourceService;
    private final ClientUrlService clientUrlService;

    public InvoiceService(InvoiceDAO dao, PaymentDAO paymentDAO, AppService appService, ClientService clientService,
            EventCreationService ecService, SecurityMessageResourceService messageResourceService,
            ClientUrlService clientUrlService) {
        this.dao = dao;
        this.paymentDAO = paymentDAO;
        this.appService = appService;
        this.clientService = clientService;
        this.ecService = ecService;
        this.messageResourceService = messageResourceService;
        this.clientUrlService = clientUrlService;
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
                .flatMap(this::withPaymentMethod)
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
                (app, seller) -> this.dao.readPageFilter(pageable, scope(app.getId(), seller.getId(), condition))
                        .flatMap(this::withPaymentMethods))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "InvoiceService.readPageFilter"));
    }

    /** Attach the gateway payment method to a single invoice from its linked payment. */
    private Mono<Invoice> withPaymentMethod(Invoice invoice) {
        return this.paymentDAO.findByInvoiceIds(java.util.List.of(invoice.getId()))
                .collectList()
                .map(payments -> {
                    invoice.setPaymentMethod(methodByInvoice(payments).get(invoice.getId()));
                    return invoice;
                });
    }

    /** Attach the gateway payment method to every invoice on the page (one batched lookup). */
    private Mono<Page<Invoice>> withPaymentMethods(Page<Invoice> page) {
        if (page.getContent().isEmpty())
            return Mono.just(page);
        java.util.List<ULong> ids = page.getContent().stream().map(Invoice::getId).toList();
        return this.paymentDAO.findByInvoiceIds(ids)
                .collectList()
                .map(payments -> {
                    Map<ULong, String> methods = methodByInvoice(payments);
                    page.getContent().forEach(inv -> inv.setPaymentMethod(methods.get(inv.getId())));
                    return page;
                });
    }

    /** invoiceId -> payment method from the captured block, preferring the PAID payment. */
    private static Map<ULong, String> methodByInvoice(java.util.List<Payment> payments) {
        Map<ULong, String> out = new HashMap<>();
        for (Payment p : payments) {
            String method = capturedMethod(p);
            if (method == null)
                continue;
            if (!out.containsKey(p.getInvoiceId()) || p.getStatus() == SecurityPaymentStatus.PAID)
                out.put(p.getInvoiceId(), method);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static String capturedMethod(Payment payment) {
        Map<String, Object> response = payment.getResponse();
        if (response == null)
            return null;
        Object captured = response.get("captured");
        if (!(captured instanceof Map))
            return null;
        Object method = ((Map<String, Object>) captured).get("method");
        return method == null ? null : method.toString();
    }

    /** Prefer the PAID payment; otherwise the last attempt (drives the receipt / failed-attempt detail). */
    private static Payment pickPayment(java.util.List<Payment> payments) {
        if (payments == null || payments.isEmpty())
            return null;
        Payment fallback = null;
        for (Payment p : payments) {
            if (p.getStatus() == SecurityPaymentStatus.PAID)
                return p;
            fallback = p;
        }
        return fallback;
    }

    /** Human method label from the captured block (e.g. "upi", "card"), else the gateway name. */
    private static String paymentLabel(Payment payment) {
        if (payment == null)
            return "";
        String method = capturedMethod(payment);
        if (method != null)
            return method;
        return payment.getGateway() == null ? "" : gatewayLabel(payment.getGateway());
    }

    /** RAZORPAY -> "Razorpay". */
    private static String gatewayLabel(SecurityPaymentGateway gateway) {
        String n = gateway.name();
        return n.charAt(0) + n.substring(1).toLowerCase();
    }

    /** AND the seller + app scope from the request context with the caller's filters. */
    private static AbstractCondition scope(ULong appId, ULong sellerClientId, AbstractCondition condition) {
        AbstractCondition seller = FilterCondition.of("sellerClientId", sellerClientId, FilterConditionOperator.EQUALS);
        AbstractCondition app = FilterCondition.of("appId", appId, FilterConditionOperator.EQUALS);
        if (condition == null)
            return ComplexCondition.and(seller, app);
        return ComplexCondition.and(seller, app, condition);
    }

    /**
     * The AUTHENTICATED caller's own purchase history: invoices where the caller's
     * client is the BUYER ({@code clientId}), for the app in context (appCode
     * header). The buyer is always the security-context caller, never a header, so a
     * caller can only ever read their own purchases (no @PreAuthorize needed - own
     * data only). Extra query filters (e.g. status) are ANDed in.
     */
    public Mono<Page<Invoice>> readMyPurchases(Pageable pageable, AbstractCondition condition, String appCode) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> this.appService.getAppByCode(appCode),
                (ca, app) -> this.dao
                        .readPageFilter(pageable, buyerScope(app.getId(),
                                ULong.valueOf(ca.getUser().getClientId()), condition))
                        .flatMap(this::withPaymentMethods))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "InvoiceService.readMyPurchases"));
    }

    /** AND the buyer + app scope with the caller's filters (buyer = the invoice's clientId). */
    private static AbstractCondition buyerScope(ULong appId, ULong buyerClientId, AbstractCondition condition) {
        AbstractCondition buyer = FilterCondition.of("clientId", buyerClientId, FilterConditionOperator.EQUALS);
        AbstractCondition app = FilterCondition.of("appId", appId, FilterConditionOperator.EQUALS);
        if (condition == null)
            return ComplexCondition.and(buyer, app);
        return ComplexCondition.and(buyer, app, condition);
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

    /** Persist the (already PAID-stamped) invoice and raise INVOICE_GENERATED (success/receipt). */
    public Mono<Invoice> markPaidAndEmit(Invoice invoice) {
        return this.dao.update(invoice)
                .flatMap(saved -> this.raiseInvoiceEvent(saved, EventNames.INVOICE_GENERATED).thenReturn(saved))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "InvoiceService.markPaidAndEmit"));
    }

    /**
     * Raise INVOICE_PAYMENT_FAILED for an invoice the caller has already marked FAILED, so the
     * seeded EventAction can send the payment-failed email. Persists nothing (the webhook path
     * already updated the invoice + payment).
     */
    public Mono<Void> emitPaymentFailed(Invoice invoice) {
        return this.raiseInvoiceEvent(invoice, EventNames.INVOICE_PAYMENT_FAILED)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "InvoiceService.emitPaymentFailed"));
    }

    private Mono<Void> raiseInvoiceEvent(Invoice invoice, String eventName) {
        return FlatMapUtil.flatMapMono(
                () -> this.appService.getAppByIdInternal(invoice.getAppId()),
                app -> this.clientService.getClientInfoById(invoice.getClientId()),
                // The app URL lives under the buyer's managing client (SYSTEM for a direct
                // customer, the reseller for a white-labelled one), never the buyer itself.
                // URL resolution is best-effort: a client with no hierarchy row throws, so we
                // swallow it to an empty Client rather than abort the whole invoice event.
                (app, client) -> this.clientService.getManagedClientOfClientById(invoice.getClientId())
                        .onErrorResume(e -> Mono.just(new Client())),
                (app, client, mgmtClient) -> this.clientUrlService.getAppUrlInternal(app.getAppCode(),
                        invoice.getAppId(),
                        mgmtClient.getId() != null ? mgmtClient.getId() : invoice.getClientId()),
                (app, client, mgmtClient, urlPrefix) -> this.paymentDAO
                        .findByInvoiceIds(java.util.List.of(invoice.getId())).collectList(),
                (app, client, mgmtClient, urlPrefix, payments) -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("urlPrefix", urlPrefix);
                    data.put("invoiceId", invoice.getId());
                    data.put("invoiceNumber", invoice.getInvoiceNumber());
                    data.put("invoiceDate", invoice.getInvoiceDate() == null ? ""
                            : invoice.getInvoiceDate().toLocalDate().toString());
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
                    data.put("status", invoice.getStatus() == null ? null : invoice.getStatus().name());
                    Payment payment = pickPayment(payments);
                    data.put("paymentReference",
                            payment == null || payment.getGatewayPaymentId() == null ? ""
                                    : payment.getGatewayPaymentId());
                    data.put("paymentMethod", paymentLabel(payment));
                    data.put("paymentGateway",
                            payment == null || payment.getGateway() == null ? "" : gatewayLabel(payment.getGateway()));
                    data.put("paidOn", payment == null || payment.getPaidAt() == null ? ""
                            : payment.getPaidAt().toLocalDate().toString());
                    EventQueObject evt = new EventQueObject()
                            .setAppCode(app.getAppCode())
                            .setClientCode(client.getCode())
                            .setEventName(eventName)
                            .setData(data);
                    return this.ecService.createEvent(evt);
                }).then();
    }
}
