package com.fincity.security.service.plansnbilling;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.plansnbilling.InvoiceDAO;
import com.fincity.security.dao.plansnbilling.PaymentDAO;
import com.fincity.security.dao.plansnbilling.PaymentGatewayDAO;
import com.fincity.security.dto.invoicesnpayments.Payment;
import com.fincity.security.jooq.enums.SecurityInvoiceInvoiceStatus;
import com.fincity.security.jooq.enums.SecurityPaymentGatewayPaymentGateway;
import com.fincity.security.jooq.enums.SecurityPaymentPaymentStatus;
import com.fincity.security.jooq.tables.records.SecurityPaymentRecord;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.SecurityMessageResourceService;
import com.fincity.security.service.plansnbilling.paymentgateway.IPaymentGatewayIntegration;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class PaymentService
        extends AbstractJOOQUpdatableDataService<SecurityPaymentRecord, ULong, Payment, PaymentDAO> {

    private final PaymentGatewayDAO paymentGatewayDAO;
    private final InvoiceDAO invoiceDAO;
    private final ClientService clientService;
    private final SecurityMessageResourceService messageResourceService;
    private final List<IPaymentGatewayIntegration> paymentGatewayIntegrations;

    public PaymentService(PaymentDAO dao, PaymentGatewayDAO paymentGatewayDAO, InvoiceDAO invoiceDAO,
            ClientService clientService, SecurityMessageResourceService messageResourceService,
            List<IPaymentGatewayIntegration> paymentGatewayIntegrations) {
        this.dao = dao;
        this.paymentGatewayDAO = paymentGatewayDAO;
        this.invoiceDAO = invoiceDAO;
        this.clientService = clientService;
        this.messageResourceService = messageResourceService;
        this.paymentGatewayIntegrations = paymentGatewayIntegrations;
    }

    /**
     * Initialize payment for an invoice. User must be an owner to make payments.
     */
    @PreAuthorize("hasAuthority('Authorities.Owner')")
    public Mono<Payment> initializePayment(ULong invoiceId, SecurityPaymentGatewayPaymentGateway gatewayType,
            Map<String, Object> metadata) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.invoiceDAO.readById(invoiceId)
                        .switchIfEmpty(this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                SecurityMessageResourceService.PARAMS_NOT_FOUND, "Invoice")),

                (ca, invoice) -> this.paymentGatewayDAO
                        .findByClientIdAndGateway(invoice.getClientId(), gatewayType)
                        .switchIfEmpty(this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                SecurityMessageResourceService.PARAMS_NOT_FOUND,
                                "Payment gateway configuration")),

                (ca, invoice, paymentGateway) -> this.getPaymentGatewayIntegration(gatewayType)
                        .switchIfEmpty(this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                SecurityMessageResourceService.UNKNOWN_ERROR,
                                "Payment gateway not supported")),

                (ca, invoice, paymentGateway, integration) -> integration
                        .initializePayment(invoice, paymentGateway, invoice.getInvoiceAmount(), metadata),

                (ca, invoice, paymentGateway, integration, payment) -> {
                    payment.setInvoiceId(invoiceId);
                    return this.dao.create(payment);
                })
                .switchIfEmpty(Mono.defer(() -> this.messageResourceService
                        .throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                SecurityMessageResourceService.FORBIDDEN_PERMISSION,
                                "You don't have permission to pay for this invoice")))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PaymentService.initializePayment"));
    }

    @PreAuthorize("hasAuthority('Authorities.Payment_UPDATE')")
    public Mono<Payment> processCallback(ULong clientId, SecurityPaymentGatewayPaymentGateway gatewayType,
            Map<String, Object> callbackData) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.clientService
                        .isBeingManagedBy(ULong.valueOf(ca.getUser().getClientId()), clientId)
                        .filter(BooleanUtil::safeValueOf),

                (ca, hasAccess) -> this.paymentGatewayDAO.findByClientIdAndGateway(clientId, gatewayType)
                        .switchIfEmpty(this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                SecurityMessageResourceService.PARAMS_NOT_FOUND,
                                "Payment gateway configuration")),

                (ca, hasAccess, paymentGateway) -> this.getPaymentGatewayIntegration(gatewayType)
                        .switchIfEmpty(this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                SecurityMessageResourceService.UNKNOWN_ERROR,
                                "Payment gateway not supported")),

                (ca, hasAccess, paymentGateway, integration) -> integration.processCallback(paymentGateway,
                        callbackData),

                (ca, hasAccess, paymentGateway, integration, payment) -> {
                    // Find existing payment by reference
                    String paymentReference = payment.getPaymentReference();
                    if (paymentReference == null) {
                        return this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                SecurityMessageResourceService.FIELDS_MISSING,
                                "Payment reference not found in callback");
                    }

                    // Try to find existing payment by reference
                    return this.dao.findByPaymentReference(paymentReference)
                            .flatMap(existing -> {
                                existing.setPaymentStatus(payment.getPaymentStatus());
                                existing.setPaymentResponse(payment.getPaymentResponse());
                                existing.setPaymentDate(LocalDateTime.now());
                                return this.dao.update(existing);
                            })
                            .switchIfEmpty(this.messageResourceService.throwMessage(
                                    msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                    SecurityMessageResourceService.PARAMS_NOT_FOUND, "Payment"));
                })
                .switchIfEmpty(Mono.defer(() -> this.messageResourceService
                        .throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                SecurityMessageResourceService.FORBIDDEN_UPDATE, "Payment")))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PaymentService.processCallback"));
    }

    /**
     * Get payments for an invoice. User must be an owner to view payments.
     */
    @PreAuthorize("hasAuthority('Authorities.Owner')")
    public Mono<List<Payment>> getPaymentsByInvoiceId(ULong invoiceId) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.invoiceDAO.readById(invoiceId)
                        .switchIfEmpty(this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                SecurityMessageResourceService.PARAMS_NOT_FOUND, "Invoice")),

                (ca, invoice) -> this.dao.findByInvoiceId(invoiceId))
                .switchIfEmpty(Mono.defer(() -> this.messageResourceService
                        .throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                SecurityMessageResourceService.FORBIDDEN_PERMISSION,
                                "You don't have permission to view payments for this invoice")))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PaymentService.getPaymentsByInvoiceId"));
    }

    /**
     * Update payment status. User must be an owner to update payments.
     */
    @PreAuthorize("hasAuthority('Authorities.Owner')")
    public Mono<Payment> updatePaymentStatus(ULong paymentId, SecurityPaymentPaymentStatus status) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.dao.readById(paymentId)
                        .switchIfEmpty(this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                SecurityMessageResourceService.PARAMS_NOT_FOUND, "Payment")),

                (ca, payment) -> this.invoiceDAO.readById(payment.getInvoiceId())
                        .switchIfEmpty(this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                SecurityMessageResourceService.PARAMS_NOT_FOUND, "Invoice")),

                (ca, payment, invoice) -> {
                    payment.setPaymentStatus(status);
                    payment.setPaymentDate(LocalDateTime.now());
                    return this.dao.update(payment);
                },

                (ca, payment, invoice, updated) -> {
                    // If payment is successful, update invoice status
                    if (status == SecurityPaymentPaymentStatus.PAID) {
                        return this.updateInvoiceStatus(payment.getInvoiceId(), SecurityInvoiceInvoiceStatus.PAID)
                                .thenReturn(updated);
                    }
                    return Mono.just(updated);
                })
                .switchIfEmpty(Mono.defer(() -> this.messageResourceService
                        .throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                SecurityMessageResourceService.FORBIDDEN_PERMISSION,
                                "You don't have permission to update this payment")))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PaymentService.updatePaymentStatus"));
    }

    private Mono<Boolean> updateInvoiceStatus(ULong invoiceId, SecurityInvoiceInvoiceStatus status) {
        return this.invoiceDAO.readById(invoiceId)
                .flatMap(invoice -> {
                    invoice.setInvoiceStatus(status);
                    return this.invoiceDAO.update(invoice);
                })
                .map(invoice -> true);
    }

    private Mono<IPaymentGatewayIntegration> getPaymentGatewayIntegration(
            SecurityPaymentGatewayPaymentGateway gatewayType) {
        return Mono.justOrEmpty(this.paymentGatewayIntegrations.stream()
                .filter(integration -> integration.getSupportedGateway() == gatewayType)
                .findFirst());
    }

    /**
     * Read a payment. User must be an owner to view payments.
     */
    @PreAuthorize("hasAuthority('Authorities.Owner')")
    @Override
    public Mono<Payment> read(ULong id) {
        return super.read(id);
    }

    @Override
    protected Mono<Payment> updatableEntity(Payment entity) {
        return FlatMapUtil.flatMapMono(
                () -> this.read(entity.getId()),
                existing -> {
                    existing.setPaymentStatus(entity.getPaymentStatus());
                    existing.setPaymentResponse(entity.getPaymentResponse());
                    existing.setPaymentDate(entity.getPaymentDate());
                    existing.setPaymentAmount(entity.getPaymentAmount());
                    existing.setPaymentReference(entity.getPaymentReference());
                    existing.setPaymentMethod(entity.getPaymentMethod());
                    return Mono.just(existing);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PaymentService.updatableEntity"));
    }
}
