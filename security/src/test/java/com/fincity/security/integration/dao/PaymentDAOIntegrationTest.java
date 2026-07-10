package com.fincity.security.integration.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.Map;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.security.dao.billing.PaymentDAO;
import com.fincity.security.dto.billing.Payment;
import com.fincity.security.integration.AbstractIntegrationTest;
import com.fincity.security.jooq.enums.SecurityPaymentGateway;
import com.fincity.security.jooq.enums.SecurityPaymentStatus;

import reactor.test.StepVerifier;

/**
 * Deep DB test for the payment row: lookup by gateway order id, and a create ->
 * update roundtrip that preserves the RESPONSE json and the PAID transition (the
 * shape the Razorpay webhook relies on).
 */
class PaymentDAOIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentDAO paymentDAO;

    private ULong buyer;
    private ULong appId;
    private ULong invoiceId;

    @BeforeEach
    void setUp() {
        setupMockBeans();
        ULong seller = insertTestClient("PSEL", "Payment Seller", "BUS").block();
        buyer = insertTestClient("PBUY", "Payment Buyer", "BUS").block();
        appId = insertTestApp(seller, "payapp", "Pay App").block();
        invoiceId = insertInvoice(seller, buyer, appId, "INV/PAY/1");
    }

    @AfterEach
    void tearDown() {
        databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
                .then(databaseClient.sql("DELETE FROM security_payment WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_invoice WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_app WHERE APP_CODE IN ('payapp')").then())
                .then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
                .then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
                .block();
    }

    @Test
    @DisplayName("findByGatewayOrderId returns the payment for a known order id, empty otherwise")
    void findsByGatewayOrderId() {
        paymentDAO.create(pendingPayment("order_known")).block();

        StepVerifier.create(paymentDAO.findByGatewayOrderId("order_known"))
                .assertNext(p -> {
                    assertEquals("order_known", p.getGatewayOrderId());
                    assertEquals(SecurityPaymentStatus.PENDING, p.getStatus());
                })
                .verifyComplete();

        StepVerifier.create(paymentDAO.findByGatewayOrderId("order_missing"))
                .verifyComplete();
    }

    @Test
    @DisplayName("create then update persists the PAID transition and the RESPONSE json")
    void createThenUpdateRoundtrips() {
        Payment created = paymentDAO.create(pendingPayment("order_rt")).block();

        created.setStatus(SecurityPaymentStatus.PAID)
                .setGatewayPaymentId("pay_rt")
                .setResponse(Map.of("captured", Map.of("amount", 1062, "method", "upi")));
        paymentDAO.update(created).block();

        StepVerifier.create(paymentDAO.findByGatewayOrderId("order_rt"))
                .assertNext(p -> {
                    assertEquals(SecurityPaymentStatus.PAID, p.getStatus());
                    assertEquals("pay_rt", p.getGatewayPaymentId());
                    assertEquals(0, p.getAmount().compareTo(BigDecimal.valueOf(1062)));
                    org.junit.jupiter.api.Assertions.assertNotNull(p.getResponse(), "RESPONSE json persisted");
                    org.junit.jupiter.api.Assertions.assertTrue(p.getResponse().containsKey("captured"));
                })
                .verifyComplete();
    }

    private Payment pendingPayment(String orderId) {
        return new Payment()
                .setInvoiceId(invoiceId)
                .setClientId(buyer)
                .setGateway(SecurityPaymentGateway.RAZORPAY)
                .setGatewayOrderId(orderId)
                .setAmount(BigDecimal.valueOf(1062))
                .setStatus(SecurityPaymentStatus.PENDING);
    }

    private ULong insertInvoice(ULong sellerId, ULong buyerId, ULong app, String number) {
        return databaseClient.sql(
                "INSERT INTO security_invoice (INVOICE_NUMBER, SELLER_CLIENT_ID, CLIENT_ID, APP_ID, TOKENS_PURCHASED, BASE_AMOUNT, TOTAL_AMOUNT) "
                        + "VALUES (:num, :seller, :buyer, :app, 1000, 900, 1062)")
                .bind("num", number).bind("seller", sellerId.longValue()).bind("buyer", buyerId.longValue())
                .bind("app", app.longValue())
                .filter(s -> s.returnGeneratedValues("ID"))
                .map(row -> ULong.valueOf(row.get("ID", Long.class)))
                .one().block();
    }
}
