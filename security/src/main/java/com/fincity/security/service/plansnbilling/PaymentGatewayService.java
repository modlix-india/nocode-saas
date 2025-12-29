package com.fincity.security.service.plansnbilling;

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
import com.fincity.security.dao.plansnbilling.PaymentGatewayDAO;
import com.fincity.security.dto.invoicesnpayments.PaymentGateway;
import com.fincity.security.jooq.enums.SecurityPaymentGatewayPaymentGateway;
import com.fincity.security.jooq.tables.records.SecurityPaymentGatewayRecord;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.SecurityMessageResourceService;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class PaymentGatewayService
        extends
        AbstractJOOQUpdatableDataService<SecurityPaymentGatewayRecord, ULong, PaymentGateway, PaymentGatewayDAO> {

    private final ClientService clientService;
    private final SecurityMessageResourceService messageResourceService;

    public PaymentGatewayService(PaymentGatewayDAO dao, ClientService clientService,
            SecurityMessageResourceService messageResourceService) {
        this.dao = dao;
        this.clientService = clientService;
        this.messageResourceService = messageResourceService;
    }

    @PreAuthorize("hasAuthority('Authorities.Payment_CREATE')")
    @Override
    public Mono<PaymentGateway> create(PaymentGateway entity) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,

                ca -> {
                    if (entity.getClientId() == null) {
                        entity.setClientId(ULong.valueOf(ca.getUser().getClientId()));
                        return Mono.just(true);
                    }

                    return this.clientService
                            .isBeingManagedBy(ULong.valueOf(ca.getUser().getClientId()), entity.getClientId())
                            .filter(BooleanUtil::safeValueOf);
                },

                (ca, hasAccess) -> this.validatePaymentGatewayDetails(entity),

                (ca, hasAccess, validated) -> super.create(entity))
                .switchIfEmpty(Mono.defer(() -> this.messageResourceService
                        .throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                SecurityMessageResourceService.FORBIDDEN_CREATE, "Payment Gateway")))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PaymentGatewayService.create"));
    }

    @PreAuthorize("hasAuthority('Authorities.Payment_UPDATE')")
    @Override
    public Mono<PaymentGateway> update(PaymentGateway entity) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.clientService
                        .isBeingManagedBy(ULong.valueOf(ca.getUser().getClientId()), entity.getClientId())
                        .filter(BooleanUtil::safeValueOf),

                (ca, hasAccess) -> this.validatePaymentGatewayDetails(entity),

                (ca, hasAccess, validated) -> super.update(entity))
                .switchIfEmpty(Mono.defer(() -> this.messageResourceService
                        .throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                SecurityMessageResourceService.FORBIDDEN_UPDATE, "Payment Gateway")))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PaymentGatewayService.update"));
    }

    @PreAuthorize("hasAuthority('Authorities.Payment_READ')")
    @Override
    public Mono<PaymentGateway> read(ULong id) {
        return super.read(id);
    }

    @PreAuthorize("hasAuthority('Authorities.Payment_READ')")
    public Mono<PaymentGateway> findByClientIdAndGateway(ULong clientId,
            SecurityPaymentGatewayPaymentGateway gateway) {
        return this.dao.findByClientIdAndGateway(clientId, gateway);
    }

    private Mono<Boolean> validatePaymentGatewayDetails(PaymentGateway entity) {
        Map<String, Object> details = entity.getPaymentGatewayDetails();

        if (details == null || details.isEmpty()) {
            return this.messageResourceService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    SecurityMessageResourceService.FIELDS_MISSING, "Payment gateway details are required");
        }

        // Validate based on gateway type
        return switch (entity.getPaymentGateway()) {
            case CASHFREE -> validateCashfreeDetails(details);
            case RAZORPAY -> validateRazorpayDetails(details);
            case STRIPE -> validateStripeDetails(details);
            default -> Mono.just(true);
        };
    }

    private Mono<Boolean> validateCashfreeDetails(Map<String, Object> details) {
        if (!details.containsKey("apiKey") || !details.containsKey("apiSecret")) {
            return this.messageResourceService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    SecurityMessageResourceService.FIELDS_MISSING,
                    "Cashfree requires apiKey and apiSecret");
        }
        return Mono.just(true);
    }

    private Mono<Boolean> validateRazorpayDetails(Map<String, Object> details) {
        if (!details.containsKey("keyId") || !details.containsKey("keySecret")) {
            return this.messageResourceService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    SecurityMessageResourceService.FIELDS_MISSING,
                    "Razorpay requires keyId and keySecret");
        }
        return Mono.just(true);
    }

    private Mono<Boolean> validateStripeDetails(Map<String, Object> details) {
        if (!details.containsKey("apiKey")) {
            return this.messageResourceService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    SecurityMessageResourceService.FIELDS_MISSING,
                    "Stripe requires apiKey");
        }
        return Mono.just(true);
    }

    @Override
    protected Mono<PaymentGateway> updatableEntity(PaymentGateway entity) {
        return FlatMapUtil.flatMapMono(
                () -> this.read(entity.getId()),
                existing -> {
                    existing.setPaymentGateway(entity.getPaymentGateway());
                    existing.setPaymentGatewayDetails(entity.getPaymentGatewayDetails());
                    return Mono.just(existing);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PaymentGatewayService.updatableEntity"));
    }
}
