package com.fincity.security.service.billing;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.dao.billing.InvoiceDAO;
import com.fincity.security.dao.billing.PaymentDAO;
import com.fincity.security.dto.billing.AppBillingConfig;
import com.fincity.security.dto.billing.Invoice;
import com.fincity.security.dto.billing.Payment;
import com.fincity.security.jooq.enums.SecurityInvoiceGateway;
import com.fincity.security.jooq.enums.SecurityInvoiceStatus;
import com.fincity.security.jooq.enums.SecurityPaymentGateway;
import com.fincity.security.jooq.enums.SecurityPaymentStatus;
import com.google.gson.Gson;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Razorpay token-purchase integration via hosted payment links. Credentials are
 * read per-seller from {@code config.PAYMENT_GATEWAY_CONFIG}; the webhook is the
 * authoritative source of payment state.
 */
@Service
public class RazorpayPaymentService {

    private static final Logger logger = LoggerFactory.getLogger(RazorpayPaymentService.class);

    private static final String RZP_BASE_URL = "https://api.razorpay.com/v1";
    private static final BigDecimal PAISE = BigDecimal.valueOf(100);

    private final PaymentDAO paymentDAO;
    private final InvoiceDAO invoiceDAO;
    private final InvoiceService invoiceService;
    private final WalletService walletService;
    private final AppBillingConfigService configService;
    private final Gson gson;

    public RazorpayPaymentService(PaymentDAO paymentDAO, InvoiceDAO invoiceDAO, InvoiceService invoiceService,
            WalletService walletService, AppBillingConfigService configService, Gson gson) {
        this.paymentDAO = paymentDAO;
        this.invoiceDAO = invoiceDAO;
        this.invoiceService = invoiceService;
        this.walletService = walletService;
        this.configService = configService;
        this.gson = gson;
    }

    /**
     * Create a Razorpay payment link for a PENDING invoice against the seller's
     * credentials, persist a PENDING payment, and return the hosted short URL for
     * the browser to open.
     */
    @SuppressWarnings("unchecked")
    public Mono<String> initialize(Invoice invoice, AppBillingConfig config) {
        Map<String, Object> gw = config.getPaymentGatewayConfig();
        String keyId = str(gw, "keyId");
        String keySecret = str(gw, "keySecret");
        if (StringUtil.safeIsBlank(keyId) || StringUtil.safeIsBlank(keySecret))
            return Mono.error(new IllegalStateException("Razorpay keyId/keySecret missing in billing config"));

        Map<String, Object> body = new HashMap<>();
        body.put("amount", invoice.getTotalAmount().multiply(PAISE).longValue());
        body.put("currency", invoice.getCurrency() == null ? "INR" : invoice.getCurrency());
        body.put("description", "Token purchase " + invoice.getInvoiceNumber());
        body.put("reference_id", "INV_" + invoice.getId());
        String callbackUrl = str(gw, "callbackUrl");
        if (!StringUtil.safeIsBlank(callbackUrl)) {
            body.put("callback_url", callbackUrl);
            body.put("callback_method", "get");
        }

        return webClient(keyId, keySecret).post()
                .uri("/payment_links")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(resp -> {
                    Map<String, Object> r = (Map<String, Object>) resp;
                    Map<String, Object> response = new HashMap<>();
                    response.put("init", r);
                    Payment payment = new Payment()
                            .setInvoiceId(invoice.getId())
                            .setClientId(invoice.getClientId())
                            .setGateway(SecurityPaymentGateway.RAZORPAY)
                            .setGatewayOrderId((String) r.get("id"))
                            .setAmount(invoice.getTotalAmount())
                            .setStatus(SecurityPaymentStatus.PENDING)
                            .setResponse(response);
                    return this.paymentDAO.create(payment).thenReturn((String) r.get("short_url"));
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "RazorpayPaymentService.initialize"));
    }

    /**
     * Authoritative webhook: verify the signature with the seller's webhook secret,
     * then on a paid event mark payment + invoice PAID, credit the wallet (idempotent)
     * and emit INVOICE_GENERATED. Unknown / unverified / non-terminal events are no-ops.
     */
    @SuppressWarnings("unchecked")
    public Mono<Void> handleWebhook(String rawBody, String signature) {
        Map<String, Object> payload;
        try {
            payload = this.gson.fromJson(rawBody, Map.class);
        } catch (Exception e) {
            logger.warn("Razorpay webhook: unparseable body");
            return Mono.empty();
        }
        if (payload == null)
            return Mono.empty();

        String event = str(payload, "event");
        Map<String, Object> entity = paymentLinkEntity(payload);
        if (entity == null)
            return Mono.empty();

        String linkId = str(entity, "id");
        String rzpPaymentId = str(entity, "payment_id");
        if (StringUtil.safeIsBlank(linkId))
            return Mono.empty();

        return FlatMapUtil.flatMapMono(
                () -> this.paymentDAO.findByGatewayOrderId(linkId),
                payment -> this.invoiceDAO.readById(payment.getInvoiceId()),
                (payment, invoice) -> this.configService.readByAppAndClientId(invoice.getAppId(),
                        invoice.getSellerClientId()),
                (payment, invoice, config) -> {
                    String secret = str(config.getPaymentGatewayConfig(), "webhookSecret");
                    if (StringUtil.safeIsBlank(secret) || !verifySignature(rawBody, signature, secret)) {
                        logger.warn("Razorpay webhook: signature verification failed for link {}", linkId);
                        return Mono.empty();
                    }
                    if (!isPaidEvent(event))
                        return this.markFailedIfNeeded(event, payment, invoice);
                    return this.applyPaid(payment, invoice, rzpPaymentId, payload);
                })
                .then()
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "RazorpayPaymentService.handleWebhook"));
    }

    private Mono<Void> applyPaid(Payment payment, Invoice invoice, String linkPaymentId, Map<String, Object> payload) {
        LocalDateTime now = LocalDateTime.now();

        // The real payment entity (amount captured, method, fee, tax) is richer and
        // more authoritative than the link's payment_id; fall back to the link id.
        Map<String, Object> entity = paymentEntity(payload);
        String paymentId = entity != null && !StringUtil.safeIsBlank(str(entity, "id"))
                ? str(entity, "id")
                : linkPaymentId;
        BigDecimal capturedAmount = entity == null ? null : rupees(entity.get("amount"));

        if (capturedAmount != null && invoice.getTotalAmount() != null
                && capturedAmount.compareTo(invoice.getTotalAmount()) != 0)
            logger.warn("Razorpay captured amount {} != invoice {} total {} (payment {})",
                    capturedAmount, invoice.getInvoiceNumber(), invoice.getTotalAmount(), paymentId);

        // Preserve the init response stored at initialize; add the webhook payload and
        // a structured capture block. No new columns: all of it lives in RESPONSE.
        Map<String, Object> response = payment.getResponse() == null
                ? new HashMap<>()
                : new HashMap<>(payment.getResponse());
        response.put("webhook", payload);
        if (entity != null) {
            Map<String, Object> captured = new HashMap<>();
            captured.put("amount", capturedAmount);
            captured.put("method", entity.get("method"));
            captured.put("fee", rupees(entity.get("fee")));
            captured.put("tax", rupees(entity.get("tax")));
            captured.put("currency", entity.get("currency"));
            captured.put("status", entity.get("status"));
            response.put("captured", captured);
        }

        payment.setStatus(SecurityPaymentStatus.PAID)
                .setGatewayPaymentId(paymentId)
                .setPaidAt(now)
                .setResponse(response);
        invoice.setStatus(SecurityInvoiceStatus.PAID)
                .setGateway(SecurityInvoiceGateway.RAZORPAY)
                .setPaymentReference(paymentId == null ? payment.getGatewayOrderId() : paymentId)
                .setPaidAt(now);

        return this.paymentDAO.update(payment)
                .then(this.walletService.creditFromPayment(invoice))
                .then(this.invoiceService.markPaidAndEmit(invoice))
                .then();
    }

    private Mono<Void> markFailedIfNeeded(String event, Payment payment, Invoice invoice) {
        if (!isFailedEvent(event))
            return Mono.empty();
        payment.setStatus(SecurityPaymentStatus.FAILED);
        invoice.setStatus(SecurityInvoiceStatus.FAILED);
        return this.paymentDAO.update(payment).then(this.invoiceDAO.update(invoice)).then();
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static Map<String, Object> paymentLinkEntity(Map<String, Object> payload) {
        return nestedEntity(payload, "payment_link");
    }

    /** The real payment entity (payload.payment.entity): captured amount, method, fee, tax. */
    private static Map<String, Object> paymentEntity(Map<String, Object> payload) {
        return nestedEntity(payload, "payment");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedEntity(Map<String, Object> payload, String key) {
        Object payloadObj = payload.get("payload");
        if (!(payloadObj instanceof Map))
            return null;
        Object obj = ((Map<String, Object>) payloadObj).get(key);
        if (obj instanceof Map) {
            Object e = ((Map<String, Object>) obj).get("entity");
            if (e instanceof Map)
                return (Map<String, Object>) e;
        }
        return null;
    }

    /** Convert a Razorpay paise value (Number/String) to a rupee BigDecimal; null-safe. */
    private static BigDecimal rupees(Object paiseValue) {
        if (paiseValue == null)
            return null;
        try {
            return new BigDecimal(paiseValue.toString()).divide(PAISE);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isPaidEvent(String event) {
        return "payment_link.paid".equals(event);
    }

    private static boolean isFailedEvent(String event) {
        return "payment_link.expired".equals(event) || "payment_link.cancelled".equals(event);
    }

    private static String str(Map<String, Object> map, String key) {
        if (map == null)
            return null;
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    private WebClient webClient(String keyId, String keySecret) {
        String basic = Base64.getEncoder()
                .encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
        return WebClient.builder()
                .baseUrl(RZP_BASE_URL)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .build();
    }

    /** Razorpay webhook signature = HMAC-SHA256(rawBody, webhookSecret), hex-encoded. */
    private static boolean verifySignature(String payload, String signature, String secret) {
        if (StringUtil.safeIsBlank(signature))
            return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash)
                hex.append(String.format("%02x", b));
            return hex.toString().equals(signature);
        } catch (Exception e) {
            logger.error("Razorpay signature verification error: {}", e.getMessage());
            return false;
        }
    }
}
