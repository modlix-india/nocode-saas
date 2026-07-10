package com.fincity.security.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.security.dao.billing.WalletDAO;
import com.fincity.security.service.billing.RazorpayPaymentService;

import reactor.test.StepVerifier;

/**
 * End-to-end Razorpay webhook against real MySQL: a signature-verified
 * payment_link.paid flips the payment + invoice to PAID (capturing the real
 * payment entity) and credits the buyer's wallet; a bad signature changes nothing.
 */
class RazorpayWebhookIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RazorpayPaymentService razorpayService;
    @Autowired
    private WalletDAO walletDAO;

    private static final String SECRET = "whsec_int_test";
    private static final String LINK_ID = "plink_int_1";

    private ULong seller;
    private ULong buyer;
    private ULong appId;
    private ULong invoiceId;

    @BeforeEach
    void setUp() {
        setupMockBeans();
        seller = insertTestClient("CRZP", "Rzp Seller", "BUS").block();
        buyer = insertTestClient("MRZP", "Rzp Buyer", "BUS").block();
        appId = insertTestApp(seller, "rzpapp", "Rzp App").block();
        seedConfig(seller, appId);
        invoiceId = insertInvoice("INV/RZP/1", seller, buyer, appId);
        insertPayment(invoiceId, buyer, LINK_ID);
        walletDAO.createSeeded(buyer, appId, BigDecimal.ZERO).block(); // start at 0 so credit == tokensPurchased
    }

    @AfterEach
    void tearDown() {
        databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
                .then(databaseClient.sql("DELETE FROM security_wallet_transaction WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_wallet WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_payment WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_invoice WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_app_billing_config WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_app WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
                .then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
                .block();
    }

    private String paidBody() {
        // amount in paise 11800 -> 118.00 captured, matching the invoice total.
        return "{\"event\":\"payment_link.paid\",\"payload\":{"
                + "\"payment_link\":{\"entity\":{\"id\":\"" + LINK_ID + "\",\"payment_id\":\"pay_link\"}},"
                + "\"payment\":{\"entity\":{\"id\":\"pay_real\",\"amount\":11800,\"method\":\"upi\","
                + "\"currency\":\"INR\",\"status\":\"captured\"}}}}";
    }

    @Test
    @DisplayName("a signature-verified payment_link.paid marks PAID, captures the payment, and credits the wallet")
    void verifiedWebhookAppliesPayment() throws Exception {
        String body = paidBody();
        StepVerifier.create(razorpayService.handleWebhook(body, sign(body, SECRET))).verifyComplete();

        assertEquals("PAID", paymentStatus());
        assertEquals("pay_real", paymentField("GATEWAY_PAYMENT_ID"));
        assertEquals("PAID", invoiceStatus());
        assertEquals("pay_real", invoiceField("PAYMENT_REFERENCE"));
        assertEquals(0, walletBalance().compareTo(BigDecimal.valueOf(1000)), "wallet credited by tokens purchased");
    }

    @Test
    @DisplayName("a bad signature is a no-op: nothing is marked paid and no wallet is credited")
    void badSignatureIsNoOp() {
        StepVerifier.create(razorpayService.handleWebhook(paidBody(), "deadbeef")).verifyComplete();

        assertEquals("PENDING", paymentStatus());
        assertEquals("PENDING", invoiceStatus());
        assertEquals(0, walletBalance().compareTo(BigDecimal.ZERO), "wallet untouched");
    }

    // --- helpers ---

    private String paymentStatus() {
        return paymentField("STATUS");
    }

    private String paymentField(String col) {
        return databaseClient.sql("SELECT " + col + " v FROM security_payment WHERE GATEWAY_ORDER_ID = :o")
                .bind("o", LINK_ID).map(row -> row.get("v", String.class)).one().block();
    }

    private String invoiceStatus() {
        return invoiceField("STATUS");
    }

    private String invoiceField(String col) {
        return databaseClient.sql("SELECT " + col + " v FROM security_invoice WHERE ID = :id")
                .bind("id", invoiceId.longValue()).map(row -> row.get("v", String.class)).one().block();
    }

    private BigDecimal walletBalance() {
        return walletDAO.findByClientAndApp(buyer, appId).map(w -> w.getBalance()).block();
    }

    private void seedConfig(ULong client, ULong app) {
        databaseClient.sql(
                "INSERT INTO security_app_billing_config (CLIENT_ID, APP_ID, PAYMENT_GATEWAY_CONFIG, STATUS) "
                        + "VALUES (:c, :app, :cfg, 'ACTIVE')")
                .bind("c", client.longValue()).bind("app", app.longValue())
                .bind("cfg", "{\"webhookSecret\":\"" + SECRET + "\"}")
                .then().block();
    }

    private ULong insertInvoice(String number, ULong sellerId, ULong buyerId, ULong app) {
        return databaseClient.sql(
                "INSERT INTO security_invoice (INVOICE_NUMBER, STATUS, SELLER_CLIENT_ID, CLIENT_ID, APP_ID, TOKENS_PURCHASED, BASE_AMOUNT, TOTAL_AMOUNT) "
                        + "VALUES (:num, 'PENDING', :seller, :buyer, :app, 1000, 100, 118)")
                .bind("num", number).bind("seller", sellerId.longValue()).bind("buyer", buyerId.longValue())
                .bind("app", app.longValue())
                .filter(s -> s.returnGeneratedValues("ID"))
                .map(row -> ULong.valueOf(row.get("ID", Long.class)))
                .one().block();
    }

    private void insertPayment(ULong invoice, ULong buyerId, String orderId) {
        databaseClient.sql(
                "INSERT INTO security_payment (INVOICE_ID, CLIENT_ID, GATEWAY, GATEWAY_ORDER_ID, AMOUNT, STATUS, RESPONSE) "
                        + "VALUES (:inv, :buyer, 'RAZORPAY', :order, 118, 'PENDING', :resp)")
                .bind("inv", invoice.longValue()).bind("buyer", buyerId.longValue()).bind("order", orderId)
                .bind("resp", "{\"init\":{\"short_url\":\"https://rzp.io/x\"}}")
                .then().block();
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
