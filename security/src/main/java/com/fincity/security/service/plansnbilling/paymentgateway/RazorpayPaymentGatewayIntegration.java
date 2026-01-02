package com.fincity.security.service.plansnbilling.paymentgateway;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fincity.security.dto.invoicesnpayments.Invoice;
import com.fincity.security.dto.invoicesnpayments.Payment;
import com.fincity.security.dto.invoicesnpayments.PaymentGateway;
import com.fincity.security.jooq.enums.SecurityPaymentGatewayPaymentGateway;
import com.fincity.security.jooq.enums.SecurityPaymentPaymentStatus;

import reactor.core.publisher.Mono;

/**
 * Razorpay payment gateway integration.
 * Implements dynamic payment links, webhook verification, payment status
 * checks, and refunds.
 *
 * Reference:
 * https://www.notion.so/Razorpay-Dynamic-Payment-Link-Full-Step-by-Step-Guide-29394c7eb9a580308276eea8d81ca260
 */
@Component
public class RazorpayPaymentGatewayIntegration extends AbstractPaymentGatewayIntegration {

    private static final String RAZORPAY_API_BASE_URL = "https://api.razorpay.com/v1";
    private static final String RAZORPAY_PAYMENT_LINKS_ENDPOINT = "/payment_links";
    private static final String RAZORPAY_PAYMENTS_ENDPOINT = "/payments";
    private static final String RAZORPAY_REFUNDS_ENDPOINT = "/refunds";

    @Override
    protected Mono<Payment> initializePaymentWithGateway(Invoice invoice, PaymentGateway paymentGateway,
            Payment payment, Map<String, Object> metadata) {

        // Extract Razorpay credentials from payment gateway details
        Map<String, Object> gatewayDetails = paymentGateway.getPaymentGatewayDetails();
        String keyId = (String) gatewayDetails.get("keyId");
        String keySecret = (String) gatewayDetails.get("keySecret");

        if (keyId == null || keySecret == null) {
            return Mono.error(new IllegalArgumentException("Razorpay keyId and keySecret are required"));
        }

        // Create WebClient with basic authentication
        WebClient webClient = createRazorpayWebClient(keyId, keySecret);

        // Prepare payment link request
        Map<String, Object> paymentLinkRequest = new HashMap<>();
        paymentLinkRequest.put("amount", payment.getPaymentAmount().multiply(new BigDecimal(100)).longValue()); // Razorpay
                                                                                                                // uses
                                                                                                                // paise
        paymentLinkRequest.put("currency", gatewayDetails.getOrDefault("currency", "INR"));
        paymentLinkRequest.put("description", "Payment for Invoice: " + invoice.getInvoiceNumber());

        // Customer information
        Map<String, Object> customer = new HashMap<>();
        if (metadata != null && metadata.containsKey("customerName")) {
            customer.put("name", metadata.get("customerName"));
        }
        if (metadata != null && metadata.containsKey("customerEmail")) {
            customer.put("email", metadata.get("customerEmail"));
        }
        if (metadata != null && metadata.containsKey("customerContact")) {
            customer.put("contact", metadata.get("customerContact"));
        }
        if (!customer.isEmpty()) {
            paymentLinkRequest.put("customer", customer);
        }

        // Notify configuration
        Map<String, Object> notify = new HashMap<>();
        notify.put("sms", gatewayDetails.getOrDefault("notifySms", true));
        notify.put("email", gatewayDetails.getOrDefault("notifyEmail", true));
        paymentLinkRequest.put("notify", notify);

        // Callback URL for webhook
        String callbackUrl = (String) gatewayDetails.get("callbackUrl");
        if (callbackUrl != null) {
            paymentLinkRequest.put("callback_url", callbackUrl);
            paymentLinkRequest.put("callback_method", "post");
        }

        // Reference ID (using invoice ID)
        paymentLinkRequest.put("reference_id", "INV_" + invoice.getId());

        // Expire by (optional - default 30 days)
        if (gatewayDetails.containsKey("expireBy")) {
            paymentLinkRequest.put("expire_by", gatewayDetails.get("expireBy"));
        }

        // Create payment link via Razorpay API
        return webClient.post()
                .uri(RAZORPAY_PAYMENT_LINKS_ENDPOINT)
                .bodyValue(paymentLinkRequest)
                .retrieve()
                .bodyToMono(Map.class)
                .cast(Map.class)
                .map(response -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> responseMap = (Map<String, Object>) response;
                    // Extract payment link details from response
                    String paymentLinkId = (String) responseMap.get("id");
                    String shortUrl = (String) responseMap.get("short_url");
                    String status = (String) responseMap.get("status");

                    payment.setPaymentReference(paymentLinkId);

                    Map<String, Object> paymentResponse = new HashMap<>();
                    paymentResponse.put("paymentLinkId", paymentLinkId);
                    paymentResponse.put("shortUrl", shortUrl);
                    paymentResponse.put("status", status);
                    paymentResponse.put("gateway", "RAZORPAY");
                    paymentResponse.put("amount", responseMap.get("amount"));
                    paymentResponse.put("currency", responseMap.get("currency"));
                    paymentResponse.putAll(responseMap);

                    payment.setPaymentResponse(paymentResponse);

                    return payment;
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    logger.error("Error creating Razorpay payment link: {}", ex.getResponseBodyAsString(), ex);
                    return Mono
                            .error(new RuntimeException("Failed to create Razorpay payment link: " + ex.getMessage()));
                })
                .onErrorResume(Exception.class, ex -> {
                    logger.error("Unexpected error creating Razorpay payment link", ex);
                    return Mono.error(new RuntimeException("Failed to create Razorpay payment link", ex));
                });
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Mono<Payment> processGatewayCallback(PaymentGateway paymentGateway, Map<String, Object> callbackData) {
        // Extract Razorpay webhook data
        // Razorpay sends webhook events with event type and payload
        String event = (String) callbackData.get("event");
        Map<String, Object> payload = (Map<String, Object>) callbackData.get("payload");

        if (payload == null) {
            // Fallback: direct payment data (for backward compatibility)
            return processDirectCallback(callbackData);
        }

        // Extract payment entity from payload
        Map<String, Object> paymentEntity = (Map<String, Object>) payload.get("payment");
        if (paymentEntity == null) {
            paymentEntity = (Map<String, Object>) payload.get("payment_link");
        }

        if (paymentEntity == null) {
            logger.warn("No payment entity found in Razorpay webhook payload");
            return Mono.error(new IllegalArgumentException("Invalid Razorpay webhook payload"));
        }

        String paymentId = (String) paymentEntity.get("id");
        String paymentLinkId = (String) paymentEntity.get("payment_link_id");
        String status = (String) paymentEntity.get("status");

        Payment payment = new Payment();
        payment.setPaymentReference(paymentId != null ? paymentId : paymentLinkId);
        payment.setPaymentResponse(callbackData);

        // Map Razorpay status to our payment status
        // Events: payment.captured, payment.failed, payment.authorized,
        // payment_link.paid, etc.
        payment.setPaymentStatus(switch (event != null ? event : status) {
            case "payment.captured", "payment_link.paid", "captured", "paid" -> SecurityPaymentPaymentStatus.PAID;
            case "payment.failed", "payment_link.expired", "failed", "expired" -> SecurityPaymentPaymentStatus.FAILED;
            case "payment.authorized", "authorized" -> SecurityPaymentPaymentStatus.PAID; // Authorized is considered
                                                                                          // paid
            default -> SecurityPaymentPaymentStatus.PENDING;
        });

        return Mono.just(payment);
    }

    private Mono<Payment> processDirectCallback(Map<String, Object> callbackData) {
        // Handle direct callback format (for backward compatibility)
        String paymentId = (String) callbackData.get("razorpay_payment_id");
        String orderId = (String) callbackData.get("razorpay_order_id");
        String paymentLinkId = (String) callbackData.get("razorpay_payment_link_id");
        String status = (String) callbackData.get("status");

        Payment payment = new Payment();
        payment.setPaymentReference(paymentId != null ? paymentId : (paymentLinkId != null ? paymentLinkId : orderId));
        payment.setPaymentResponse(callbackData);

        payment.setPaymentStatus(switch (status) {
            case "captured", "paid" -> SecurityPaymentPaymentStatus.PAID;
            case "failed", "expired" -> SecurityPaymentPaymentStatus.FAILED;
            default -> SecurityPaymentPaymentStatus.PENDING;
        });

        return Mono.just(payment);
    }

    @Override
    protected Mono<Payment> checkGatewayPaymentStatus(PaymentGateway paymentGateway, String paymentReference) {
        Map<String, Object> gatewayDetails = paymentGateway.getPaymentGatewayDetails();
        String keyId = (String) gatewayDetails.get("keyId");
        String keySecret = (String) gatewayDetails.get("keySecret");

        if (keyId == null || keySecret == null) {
            return Mono.error(new IllegalArgumentException("Razorpay keyId and keySecret are required"));
        }

        WebClient webClient = createRazorpayWebClient(keyId, keySecret);

        // Check if it's a payment ID or payment link ID
        // Try payment first, then payment link
        return webClient.get()
                .uri(RAZORPAY_PAYMENTS_ENDPOINT + "/" + paymentReference)
                .retrieve()
                .bodyToMono(Map.class)
                .cast(Map.class)
                .map(response -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> responseMap = (Map<String, Object>) response;
                    Payment payment = new Payment();
                    payment.setPaymentReference(paymentReference);
                    payment.setPaymentResponse(responseMap);

                    String status = (String) responseMap.get("status");
                    payment.setPaymentStatus(switch (status) {
                        case "captured", "authorized" -> SecurityPaymentPaymentStatus.PAID;
                        case "failed" -> SecurityPaymentPaymentStatus.FAILED;
                        default -> SecurityPaymentPaymentStatus.PENDING;
                    });

                    return payment;
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    if (ex.getStatusCode().value() == 404) {
                        // Not a payment ID, try as payment link
                        return checkPaymentLinkStatus(webClient, paymentReference);
                    }
                    logger.error("Error checking Razorpay payment status: {}", ex.getResponseBodyAsString(), ex);
                    return Mono
                            .error(new RuntimeException("Failed to check Razorpay payment status: " + ex.getMessage()));
                })
                .onErrorResume(Exception.class, ex -> {
                    logger.error("Unexpected error checking Razorpay payment status", ex);
                    return Mono.error(new RuntimeException("Failed to check Razorpay payment status", ex));
                });
    }

    private Mono<Payment> checkPaymentLinkStatus(WebClient webClient, String paymentLinkId) {
        return webClient.get()
                .uri(RAZORPAY_PAYMENT_LINKS_ENDPOINT + "/" + paymentLinkId)
                .retrieve()
                .bodyToMono(Map.class)
                .cast(Map.class)
                .map(response -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> responseMap = (Map<String, Object>) response;
                    Payment payment = new Payment();
                    payment.setPaymentReference(paymentLinkId);
                    payment.setPaymentResponse(responseMap);

                    String status = (String) responseMap.get("status");
                    payment.setPaymentStatus(switch (status) {
                        case "paid" -> SecurityPaymentPaymentStatus.PAID;
                        case "expired", "cancelled" -> SecurityPaymentPaymentStatus.FAILED;
                        default -> SecurityPaymentPaymentStatus.PENDING;
                    });

                    return payment;
                });
    }

    @Override
    protected Mono<Payment> processGatewayRefund(PaymentGateway paymentGateway, String paymentReference,
            BigDecimal amount) {
        Map<String, Object> gatewayDetails = paymentGateway.getPaymentGatewayDetails();
        String keyId = (String) gatewayDetails.get("keyId");
        String keySecret = (String) gatewayDetails.get("keySecret");

        if (keyId == null || keySecret == null) {
            return Mono.error(new IllegalArgumentException("Razorpay keyId and keySecret are required"));
        }

        WebClient webClient = createRazorpayWebClient(keyId, keySecret);

        Map<String, Object> refundRequest = new HashMap<>();
        refundRequest.put("payment_id", paymentReference);
        if (amount != null) {
            refundRequest.put("amount", amount.multiply(new BigDecimal(100)).longValue()); // Razorpay uses paise
        }
        // If amount is null, it's a full refund

        return webClient.post()
                .uri(RAZORPAY_REFUNDS_ENDPOINT)
                .bodyValue(refundRequest)
                .retrieve()
                .bodyToMono(Map.class)
                .cast(Map.class)
                .map(response -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> responseMap = (Map<String, Object>) response;
                    Payment payment = new Payment();
                    payment.setPaymentReference((String) responseMap.get("id"));
                    payment.setPaymentResponse(responseMap);

                    String status = (String) responseMap.get("status");
                    payment.setPaymentStatus("processed".equals(status)
                            ? SecurityPaymentPaymentStatus.PAID
                            : SecurityPaymentPaymentStatus.PENDING);

                    return payment;
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    logger.error("Error processing Razorpay refund: {}", ex.getResponseBodyAsString(), ex);
                    return Mono.error(new RuntimeException("Failed to process Razorpay refund: " + ex.getMessage()));
                })
                .onErrorResume(Exception.class, ex -> {
                    logger.error("Unexpected error processing Razorpay refund", ex);
                    return Mono.error(new RuntimeException("Failed to process Razorpay refund", ex));
                });
    }

    /**
     * Verify Razorpay webhook signature.
     *
     * @param payload   The webhook payload (as string)
     * @param signature The X-Razorpay-Signature header value
     * @param secret    The webhook secret from Razorpay
     * @return true if signature is valid
     */
    public boolean verifyWebhookSignature(String payload, String signature, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = bytesToHex(hash);
            return expectedSignature.equals(signature);
        } catch (java.security.NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            logger.error("Error verifying Razorpay webhook signature", e);
            return false;
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * Create Razorpay WebClient with basic authentication.
     */
    private WebClient createRazorpayWebClient(String keyId, String keySecret) {
        return WebClient.builder()
                .baseUrl(RAZORPAY_API_BASE_URL)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " +
                        java.util.Base64.getEncoder()
                                .encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8)))
                .build();
    }

    @Override
    public SecurityPaymentGatewayPaymentGateway getSupportedGateway() {
        return SecurityPaymentGatewayPaymentGateway.RAZORPAY;
    }
}
