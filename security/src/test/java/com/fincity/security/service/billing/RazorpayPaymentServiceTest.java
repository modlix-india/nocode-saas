package com.fincity.security.service.billing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fincity.security.dao.billing.InvoiceDAO;
import com.fincity.security.dao.billing.PaymentDAO;
import com.fincity.security.dto.billing.AppBillingConfig;
import com.fincity.security.dto.billing.Invoice;
import com.fincity.security.dto.billing.Payment;
import com.fincity.security.jooq.enums.SecurityPaymentGateway;
import com.fincity.security.jooq.enums.SecurityPaymentStatus;
import com.fincity.security.model.billing.ChargeResult;
import com.google.gson.Gson;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for the Razorpay webhook: signature is verified with the seller's
 * secret before any state change; a verified payment_link.paid credits the wallet
 * and emits the invoice; an unverified payload is a no-op.
 */
@ExtendWith(MockitoExtension.class)
class RazorpayPaymentServiceTest {

    @Mock
    private PaymentDAO paymentDAO;
    @Mock
    private InvoiceDAO invoiceDAO;
    @Mock
    private InvoiceService invoiceService;
    @Mock
    private WalletService walletService;
    @Mock
    private AppBillingConfigService configService;

    private RazorpayPaymentService service;

    private static final String SECRET = "whsec_test_123";
    private static final String LINK_ID = "plink_1";
    private static final ULong APP_ID = ULong.valueOf(2);
    private static final ULong C_CLIENT = ULong.valueOf(10);
    private static final ULong M_CLIENT = ULong.valueOf(20);
    private static final ULong INVOICE_ID = ULong.valueOf(900);

    private static final String PAID_BODY = "{\"event\":\"payment_link.paid\",\"payload\":{\"payment_link\":"
            + "{\"entity\":{\"id\":\"" + LINK_ID + "\",\"payment_id\":\"pay_xyz\"}}}}";

    @BeforeEach
    void setUp() {
        service = new RazorpayPaymentService(paymentDAO, invoiceDAO, invoiceService, walletService,
                configService, new Gson());

        Payment payment = new Payment().setInvoiceId(INVOICE_ID).setGatewayOrderId(LINK_ID)
                .setGateway(SecurityPaymentGateway.RAZORPAY).setStatus(SecurityPaymentStatus.PENDING);
        Invoice invoice = new Invoice().setAppId(APP_ID).setSellerClientId(C_CLIENT).setClientId(M_CLIENT)
                .setTokensPurchased(BigDecimal.valueOf(1000)).setInvoiceNumber("INV/1");
        invoice.setId(INVOICE_ID);
        AppBillingConfig config = new AppBillingConfig().setAppId(APP_ID).setClientId(C_CLIENT)
                .setPaymentGatewayConfig(Map.of("webhookSecret", SECRET));

        lenient().when(paymentDAO.findByGatewayOrderId(LINK_ID)).thenReturn(Mono.just(payment));
        lenient().when(invoiceDAO.readById(INVOICE_ID)).thenReturn(Mono.just(invoice));
        lenient().when(configService.readByAppAndClientId(APP_ID, C_CLIENT)).thenReturn(Mono.just(config));
        lenient().when(paymentDAO.update(any(Payment.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
        lenient().when(walletService.creditFromPayment(any()))
                .thenReturn(Mono.just(new ChargeResult(true, false, false, BigDecimal.valueOf(1000))));
        lenient().when(invoiceService.markPaidAndEmit(any())).thenAnswer(i -> Mono.just(i.getArgument(0)));
    }

    @Test
    void verifiedPaidWebhookCreditsWalletAndEmitsInvoice() throws Exception {
        String signature = sign(PAID_BODY, SECRET);

        StepVerifier.create(service.handleWebhook(PAID_BODY, signature)).verifyComplete();

        verify(walletService).creditFromPayment(any());
        verify(invoiceService).markPaidAndEmit(any());
        verify(paymentDAO).update(any(Payment.class));
    }

    @Test
    void invalidSignatureIsANoOp() {
        StepVerifier.create(service.handleWebhook(PAID_BODY, "deadbeef")).verifyComplete();

        verify(walletService, never()).creditFromPayment(any());
        verify(invoiceService, never()).markPaidAndEmit(any());
    }

    @Test
    void unparseableBodyIsANoOp() {
        StepVerifier.create(service.handleWebhook("not-json", "sig")).verifyComplete();

        verify(paymentDAO, never()).findByGatewayOrderId(any());
        verify(walletService, never()).creditFromPayment(any());
    }

    private static String sign(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash)
            hex.append(String.format("%02x", b));
        return hex.toString();
    }
}
