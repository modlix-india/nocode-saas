package com.fincity.security.service.plansnbilling.paymentgateway;

import java.math.BigDecimal;
import java.util.Map;

import com.fincity.security.dto.invoicesnpayments.Invoice;
import com.fincity.security.dto.invoicesnpayments.Payment;
import com.fincity.security.dto.invoicesnpayments.PaymentGateway;

import reactor.core.publisher.Mono;

/**
 * Interface for payment gateway integrations.
 * Implementations should handle the specific logic for each payment gateway provider.
 */
public interface IPaymentGatewayIntegration {

    /**
     * Initialize a payment with the gateway and return payment details.
     *
     * @param invoice The invoice to create payment for
     * @param paymentGateway The payment gateway configuration
     * @param amount The payment amount
     * @param metadata Additional metadata for the payment
     * @return Payment object with gateway-specific details
     */
    Mono<Payment> initializePayment(Invoice invoice, PaymentGateway paymentGateway, BigDecimal amount,
            Map<String, Object> metadata);

    /**
     * Verify and process a payment callback/webhook from the gateway.
     *
     * @param paymentGateway The payment gateway configuration
     * @param callbackData The callback data from the gateway
     * @return Payment object with updated status
     */
    Mono<Payment> processCallback(PaymentGateway paymentGateway, Map<String, Object> callbackData);

    /**
     * Check the status of a payment.
     *
     * @param paymentGateway The payment gateway configuration
     * @param paymentReference The payment reference/transaction ID
     * @return Payment object with current status
     */
    Mono<Payment> checkPaymentStatus(PaymentGateway paymentGateway, String paymentReference);

    /**
     * Refund a payment.
     *
     * @param paymentGateway The payment gateway configuration
     * @param paymentReference The original payment reference
     * @param amount The amount to refund (null for full refund)
     * @return Payment object with refund details
     */
    Mono<Payment> refundPayment(PaymentGateway paymentGateway, String paymentReference, BigDecimal amount);

    /**
     * Get the payment gateway type this implementation supports.
     *
     * @return The payment gateway enum value
     */
    com.fincity.security.jooq.enums.SecurityPaymentGatewayPaymentGateway getSupportedGateway();
}
