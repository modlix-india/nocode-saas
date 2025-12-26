package com.fincity.security.service.plansnbilling.paymentgateway;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fincity.security.dto.invoicesnpayments.Invoice;
import com.fincity.security.dto.invoicesnpayments.Payment;
import com.fincity.security.jooq.enums.SecurityPaymentPaymentMethod;
import com.fincity.security.jooq.enums.SecurityPaymentPaymentStatus;

import reactor.core.publisher.Mono;

/**
 * Abstract base class for payment gateway integrations.
 * Provides common functionality and default implementations.
 */
public abstract class AbstractPaymentGatewayIntegration implements IPaymentGatewayIntegration {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractPaymentGatewayIntegration.class);

    @Override
    public Mono<Payment> initializePayment(Invoice invoice,
            com.fincity.security.dto.invoicesnpayments.PaymentGateway paymentGateway,
            BigDecimal amount, Map<String, Object> metadata) {

        logger.info("Initializing payment for invoice {} with gateway {}", invoice.getInvoiceNumber(),
                paymentGateway.getPaymentGateway());

        return createPaymentRecord(invoice, paymentGateway, amount, metadata)
                .flatMap(payment -> initializePaymentWithGateway(invoice, paymentGateway, payment, metadata))
                .doOnError(error -> logger.error("Error initializing payment for invoice {}",
                        invoice.getInvoiceNumber(), error));
    }

    /**
     * Create a payment record with initial status.
     */
    protected Mono<Payment> createPaymentRecord(Invoice invoice,
            com.fincity.security.dto.invoicesnpayments.PaymentGateway paymentGateway, BigDecimal amount,
            Map<String, Object> metadata) {

        Payment payment = new Payment();
        payment.setInvoiceId(invoice.getId());
        payment.setPaymentDate(LocalDateTime.now());
        payment.setPaymentAmount(amount);
        payment.setPaymentStatus(SecurityPaymentPaymentStatus.PENDING);
        payment.setPaymentMethod(mapGatewayToPaymentMethod(paymentGateway.getPaymentGateway()));

        if (metadata != null) {
            payment.setPaymentResponse(metadata);
        }

        return Mono.just(payment);
    }

    /**
     * Initialize payment with the specific gateway. Subclasses should implement
     * this.
     */
    protected abstract Mono<Payment> initializePaymentWithGateway(Invoice invoice,
            com.fincity.security.dto.invoicesnpayments.PaymentGateway paymentGateway, Payment payment,
            Map<String, Object> metadata);

    /**
     * Map payment gateway enum to payment method enum.
     */
    protected SecurityPaymentPaymentMethod mapGatewayToPaymentMethod(
            com.fincity.security.jooq.enums.SecurityPaymentGatewayPaymentGateway gateway) {
        return switch (gateway) {
            case CASHFREE -> SecurityPaymentPaymentMethod.CASHFREE;
            case RAZORPAY -> SecurityPaymentPaymentMethod.RAZORPAY;
            case STRIPE -> SecurityPaymentPaymentMethod.STRIPE;
            default -> SecurityPaymentPaymentMethod.OTHER;
        };
    }

    @Override
    public Mono<Payment> processCallback(com.fincity.security.dto.invoicesnpayments.PaymentGateway paymentGateway,
            Map<String, Object> callbackData) {
        logger.info("Processing callback for gateway {}", paymentGateway.getPaymentGateway());
        return processGatewayCallback(paymentGateway, callbackData)
                .doOnError(error -> logger.error("Error processing callback for gateway {}",
                        paymentGateway.getPaymentGateway(), error));
    }

    /**
     * Process gateway-specific callback. Subclasses should implement this.
     */
    protected abstract Mono<Payment> processGatewayCallback(
            com.fincity.security.dto.invoicesnpayments.PaymentGateway paymentGateway, Map<String, Object> callbackData);

    @Override
    public Mono<Payment> checkPaymentStatus(com.fincity.security.dto.invoicesnpayments.PaymentGateway paymentGateway,
            String paymentReference) {
        logger.info("Checking payment status for reference {} with gateway {}", paymentReference,
                paymentGateway.getPaymentGateway());
        return checkGatewayPaymentStatus(paymentGateway, paymentReference)
                .doOnError(error -> logger.error("Error checking payment status for reference {}",
                        paymentReference, error));
    }

    /**
     * Check gateway-specific payment status. Subclasses should implement this.
     */
    protected abstract Mono<Payment> checkGatewayPaymentStatus(
            com.fincity.security.dto.invoicesnpayments.PaymentGateway paymentGateway, String paymentReference);

    @Override
    public Mono<Payment> refundPayment(com.fincity.security.dto.invoicesnpayments.PaymentGateway paymentGateway,
            String paymentReference, BigDecimal amount) {
        logger.info("Processing refund for reference {} with gateway {}", paymentReference,
                paymentGateway.getPaymentGateway());
        return processGatewayRefund(paymentGateway, paymentReference, amount)
                .doOnError(error -> logger.error("Error processing refund for reference {}",
                        paymentReference, error));
    }

    /**
     * Process gateway-specific refund. Subclasses should implement this.
     */
    protected abstract Mono<Payment> processGatewayRefund(
            com.fincity.security.dto.invoicesnpayments.PaymentGateway paymentGateway, String paymentReference,
            BigDecimal amount);
}
