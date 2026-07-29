package com.fincity.security.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.security.model.billing.PurchaseResult;
import com.fincity.security.service.billing.BundlePurchaseService;
import com.fincity.security.service.billing.RazorpayPaymentService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * End-to-end purchase flow against real MySQL: pricing (FIXED / CUSTOM), config
 * GST on top, and a PENDING invoice with a gapless per-seller number persisted.
 * Razorpay (external HTTP) is mocked; the webhook, not this flow, credits wallets.
 */
class BundlePurchaseServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BundlePurchaseService purchaseService;

    @MockitoBean
    private RazorpayPaymentService razorpayService;

    private ULong seller; // config owner C
    private ULong buyer;  // billed client M
    private ULong appId;
    private ULong configId;
    private ULong fixedBundleId;
    private ULong customBundleId;

    @BeforeEach
    void setUp() {
        setupMockBeans();
        lenient().when(razorpayService.initialize(any(), any())).thenReturn(Mono.just("https://rzp.io/pay"));

        seller = insertTestClient("CSEL", "Seller Co", "BUS").block();
        buyer = insertTestClient("MBUY", "Buyer Co", "BUS").block();
        appId = insertTestApp(seller, "buyapp", "Buy App").block();
        configId = seedConfig(seller, appId, new BigDecimal("18.00"));
        fixedBundleId = seedFixedBundle(configId, new BigDecimal("1000"), new BigDecimal("100"));
        customBundleId = seedCustomBundle(configId, new BigDecimal("0.500000"),
                new BigDecimal("100"), new BigDecimal("10000"));
    }

    @AfterEach
    void tearDown() {
        databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
                .then(databaseClient.sql("DELETE FROM security_payment WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_invoice WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_invoice_counter WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_app_billing_bundle WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_app_billing_config WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_app WHERE APP_CODE IN ('buyapp')").then())
                .then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
                .then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
                .block();
    }

    private ContextAuthentication buyerAuth() {
        return TestDataFactory.createBusinessAuth(buyer, "MBUY",
                List.of("Authorities.Payment_CREATE", "Authorities.Logged_IN"));
    }

    private <T> Mono<T> asBuyer(Mono<T> mono) {
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(buyerAuth()));
    }

    private Map<String, Object> invoiceRow(ULong invoiceId) {
        return databaseClient.sql("SELECT STATUS, SELLER_CLIENT_ID, CLIENT_ID, TOKENS_PURCHASED, BASE_AMOUNT, "
                + "GST_PERCENTAGE, GST_AMOUNT, TOTAL_AMOUNT, INVOICE_NUMBER FROM security_invoice WHERE ID = :id")
                .bind("id", invoiceId.longValue())
                .map((row, meta) -> Map.<String, Object>of(
                        "status", row.get("STATUS", String.class),
                        "seller", row.get("SELLER_CLIENT_ID", Long.class),
                        "buyer", row.get("CLIENT_ID", Long.class),
                        "tokens", row.get("TOKENS_PURCHASED", BigDecimal.class),
                        "base", row.get("BASE_AMOUNT", BigDecimal.class),
                        "gstPct", row.get("GST_PERCENTAGE", BigDecimal.class),
                        "gstAmt", row.get("GST_AMOUNT", BigDecimal.class),
                        "total", row.get("TOTAL_AMOUNT", BigDecimal.class),
                        "number", row.get("INVOICE_NUMBER", String.class)))
                .one().block();
    }

    @Test
    @DisplayName("FIXED bundle: PENDING invoice priced at bundle price + config GST, seller/buyer snapshotted")
    void purchaseFixedCreatesPendingInvoiceWithGst() {
        PurchaseResult result = asBuyer(purchaseService.purchase(fixedBundleId, null, null)).block();

        org.junit.jupiter.api.Assertions.assertNotNull(result);
        assertEquals("https://rzp.io/pay", result.paymentUrl());

        Map<String, Object> inv = invoiceRow(result.invoiceId());
        assertEquals("PENDING", inv.get("status"));
        assertEquals(seller.longValue(), inv.get("seller"));
        assertEquals(buyer.longValue(), inv.get("buyer"));
        assertEquals(0, ((BigDecimal) inv.get("tokens")).compareTo(BigDecimal.valueOf(1000)));
        assertEquals(0, ((BigDecimal) inv.get("base")).compareTo(BigDecimal.valueOf(100)));
        assertEquals(0, ((BigDecimal) inv.get("gstAmt")).compareTo(new BigDecimal("18.00")));
        assertEquals(0, ((BigDecimal) inv.get("total")).compareTo(new BigDecimal("118.00")));
        String number = (String) inv.get("number");
        assertTrue(number.startsWith("INV/"), "invoice number is formatted");
        assertTrue(number.endsWith("/" + seller.toBigInteger() + "/1"), "gapless first number for the seller");
    }

    @Test
    @DisplayName("CUSTOM bundle: base = tokens x price-per-token, plus GST")
    void purchaseCustomPricesPerToken() {
        PurchaseResult result = asBuyer(purchaseService.purchase(customBundleId, new BigDecimal("200"), null)).block();

        Map<String, Object> inv = invoiceRow(result.invoiceId());
        assertEquals(0, ((BigDecimal) inv.get("tokens")).compareTo(BigDecimal.valueOf(200)));
        assertEquals(0, ((BigDecimal) inv.get("base")).compareTo(BigDecimal.valueOf(100)), "200 x 0.5 = 100");
        assertEquals(0, ((BigDecimal) inv.get("total")).compareTo(new BigDecimal("118.00")));
    }

    @Test
    @DisplayName("CUSTOM bundle below the minimum is rejected")
    void purchaseCustomBelowMinimumFails() {
        StepVerifier.create(asBuyer(purchaseService.purchase(customBundleId, new BigDecimal("50"), null)))
                .verifyError(GenericException.class);
    }

    private ULong seedConfig(ULong client, ULong app, BigDecimal gstPct) {
        return databaseClient.sql(
                "INSERT INTO security_app_billing_config (CLIENT_ID, APP_ID, GST_PERCENTAGE, SELLER_LEGAL_NAME, STATUS) "
                        + "VALUES (:c, :app, :gst, 'Seller Co Pvt Ltd', 'ACTIVE')")
                .bind("c", client.longValue()).bind("app", app.longValue()).bind("gst", gstPct)
                .filter(s -> s.returnGeneratedValues("ID"))
                .map(row -> ULong.valueOf(row.get("ID", Long.class)))
                .one().block();
    }

    private ULong seedFixedBundle(ULong config, BigDecimal tokens, BigDecimal price) {
        return databaseClient.sql(
                "INSERT INTO security_app_billing_bundle (BILLING_CONFIG_ID, LABEL, BUNDLE_TYPE, TOKENS, PRICE, STATUS) "
                        + "VALUES (:cfg, 'Fixed 1000', 'FIXED', :tokens, :price, 'ACTIVE')")
                .bind("cfg", config.longValue()).bind("tokens", tokens).bind("price", price)
                .filter(s -> s.returnGeneratedValues("ID"))
                .map(row -> ULong.valueOf(row.get("ID", Long.class)))
                .one().block();
    }

    private ULong seedCustomBundle(ULong config, BigDecimal perToken, BigDecimal min, BigDecimal max) {
        return databaseClient.sql(
                "INSERT INTO security_app_billing_bundle (BILLING_CONFIG_ID, LABEL, BUNDLE_TYPE, PRICE_PER_TOKEN, MIN_TOKENS, MAX_TOKENS, STATUS) "
                        + "VALUES (:cfg, 'Custom', 'CUSTOM', :pt, :min, :max, 'ACTIVE')")
                .bind("cfg", config.longValue()).bind("pt", perToken).bind("min", min).bind("max", max)
                .filter(s -> s.returnGeneratedValues("ID"))
                .map(row -> ULong.valueOf(row.get("ID", Long.class)))
                .one().block();
    }
}
