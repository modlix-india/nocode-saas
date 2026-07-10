package com.fincity.security.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.service.billing.InvoiceService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * End-to-end invoice reads against real MySQL: the page is scoped to the seller +
 * app from the request context AND the DAO party filter (caller is seller or
 * buyer) both apply; readById is guarded to the invoice's seller/buyer (SYSTEM
 * sees all).
 */
class InvoiceServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private InvoiceService invoiceService;
    @Autowired
    private CacheService cacheService;

    private ULong seller;  // C, owns the app
    private ULong buyer;   // M
    private ULong other;   // unrelated client
    private ULong appId;
    private ULong app2Id;
    private ULong invSellerAppPaid;    // seller=C, buyer=M, app, PAID
    private ULong invSellerAppPending; // seller=C, buyer=other, app, PENDING

    @BeforeEach
    void setUp() {
        setupMockBeans();
        // The Spring context (and its caches) is reused across tests; app/client are
        // resolved by code but re-seeded at fresh IDs each method, so clear the
        // by-code caches to avoid a stale code -> old-id mapping.
        cacheService.evictAll("byAppCode").block();
        cacheService.evictAll("clientCodeId").block();
        seller = insertTestClient("CINV", "Invoice Seller", "BUS").block();
        buyer = insertTestClient("MINV", "Invoice Buyer", "BUS").block();
        other = insertTestClient("OINV", "Other Client", "BUS").block();
        appId = insertTestApp(seller, "invapp", "Invoice App").block();
        app2Id = insertTestApp(seller, "invapp2", "Invoice App 2").block();

        invSellerAppPaid = insertInvoice("INV/S/1", seller, buyer, appId, "PAID");
        invSellerAppPending = insertInvoice("INV/S/2", seller, other, appId, "PENDING");
        insertInvoice("INV/S/3", other, buyer, appId, "PAID");   // different seller -> out of scope
        insertInvoice("INV/S/4", seller, buyer, app2Id, "PAID"); // different app -> out of scope
    }

    @AfterEach
    void tearDown() {
        databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
                .then(databaseClient.sql("DELETE FROM security_invoice WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_app WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
                .then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
                .block();
    }

    private ContextAuthentication auth(ULong clientId, String code) {
        return TestDataFactory.createBusinessAuth(clientId, code,
                List.of("Authorities.Invoice_READ", "Authorities.Logged_IN"));
    }

    private <T> Mono<T> as(Mono<T> mono, ContextAuthentication ca) {
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca));
    }

    @Test
    @DisplayName("readPageFilter is scoped to the seller + app in context (other seller/app excluded)")
    void pageScopedToSellerAndApp() {
        StepVerifier.create(as(invoiceService.readPageFilter(PageRequest.of(0, 10), null, "invapp", "CINV"),
                auth(seller, "CINV")))
                .assertNext(page -> assertEquals(2, page.getTotalElements(),
                        "only seller=C + app invoices (2), not the other-seller or other-app ones"))
                .verifyComplete();
    }

    @Test
    @DisplayName("readPageFilter ANDs an extra query filter (status=PAID) with the seller+app scope")
    void pageAppliesExtraFilter() {
        FilterCondition paid = FilterCondition.of("status", "PAID", FilterConditionOperator.EQUALS);
        StepVerifier.create(as(invoiceService.readPageFilter(PageRequest.of(0, 10), paid, "invapp", "CINV"),
                auth(seller, "CINV")))
                .assertNext(page -> {
                    assertEquals(1, page.getTotalElements(), "only the PAID one in scope");
                    assertEquals(invSellerAppPaid, page.getContent().get(0).getId());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("readById is visible to the seller and the buyer, forbidden to a third party, all to SYSTEM")
    void readByIdPartyGuard() {
        StepVerifier.create(as(invoiceService.readById(invSellerAppPaid), auth(seller, "CINV")))
                .assertNext(inv -> assertEquals(invSellerAppPaid, inv.getId()))
                .verifyComplete();

        StepVerifier.create(as(invoiceService.readById(invSellerAppPaid), auth(buyer, "MINV")))
                .assertNext(inv -> assertEquals(invSellerAppPaid, inv.getId()))
                .verifyComplete();

        StepVerifier.create(as(invoiceService.readById(invSellerAppPending), auth(buyer, "MINV")))
                .verifyError(GenericException.class); // buyer of this one is `other`, not M

        StepVerifier.create(as(invoiceService.readById(invSellerAppPaid), TestDataFactory.createSystemAuth()))
                .assertNext(inv -> assertEquals(invSellerAppPaid, inv.getId()))
                .verifyComplete();
    }

    private ULong insertInvoice(String number, ULong sellerId, ULong buyerId, ULong app, String status) {
        return databaseClient.sql(
                "INSERT INTO security_invoice (INVOICE_NUMBER, STATUS, SELLER_CLIENT_ID, CLIENT_ID, APP_ID, TOKENS_PURCHASED, BASE_AMOUNT, TOTAL_AMOUNT) "
                        + "VALUES (:num, :status, :seller, :buyer, :app, 1000, 100, 118)")
                .bind("num", number).bind("status", status).bind("seller", sellerId.longValue())
                .bind("buyer", buyerId.longValue()).bind("app", app.longValue())
                .filter(s -> s.returnGeneratedValues("ID"))
                .map(row -> ULong.valueOf(row.get("ID", Long.class)))
                .one().block();
    }
}
