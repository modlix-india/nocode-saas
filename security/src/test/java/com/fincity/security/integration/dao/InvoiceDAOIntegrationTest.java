package com.fincity.security.integration.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.security.dao.billing.InvoiceDAO;
import com.fincity.security.dto.billing.Invoice;
import com.fincity.security.integration.AbstractIntegrationTest;
import com.fincity.security.testutil.TestDataFactory;

import reactor.test.StepVerifier;

/**
 * Deep DB test for the gapless per-(seller, financial-year) invoice number. The
 * upsert-and-increment counter is what makes the tax invoice number sequential
 * with no gaps, and isolated per seller and per financial year.
 */
class InvoiceDAOIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private InvoiceDAO invoiceDAO;

    private ULong sellerA;
    private ULong sellerB;

    @BeforeEach
    void setUp() {
        setupMockBeans();
        sellerA = insertTestClient("SELA", "Seller A", "BUS").block();
        sellerB = insertTestClient("SELB", "Seller B", "BUS").block();
    }

    @AfterEach
    void tearDown() {
        databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
                .then(databaseClient.sql("DELETE FROM security_invoice WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_invoice_counter WHERE ID > 0").then())
                .then(databaseClient.sql("DELETE FROM security_app WHERE APP_CODE LIKE 'tapp%'").then())
                .then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
                .then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
                .block();
    }

    @Test
    @DisplayName("numbers are allocated gaplessly per (seller, financial year)")
    void allocatesGaplessSequence() {
        StepVerifier.create(invoiceDAO.nextInvoiceNumber(sellerA, "2026-27"))
                .assertNext(n -> assertEquals("INV/2026-27/" + sellerA.toBigInteger() + "/1", n))
                .verifyComplete();
        StepVerifier.create(invoiceDAO.nextInvoiceNumber(sellerA, "2026-27"))
                .assertNext(n -> assertEquals("INV/2026-27/" + sellerA.toBigInteger() + "/2", n))
                .verifyComplete();
        StepVerifier.create(invoiceDAO.nextInvoiceNumber(sellerA, "2026-27"))
                .assertNext(n -> assertEquals("INV/2026-27/" + sellerA.toBigInteger() + "/3", n))
                .verifyComplete();
    }

    @Test
    @DisplayName("each financial year restarts the sequence at 1")
    void sequenceIsPerFinancialYear() {
        invoiceDAO.nextInvoiceNumber(sellerA, "2026-27").block();
        invoiceDAO.nextInvoiceNumber(sellerA, "2026-27").block();

        StepVerifier.create(invoiceDAO.nextInvoiceNumber(sellerA, "2027-28"))
                .assertNext(n -> assertEquals("INV/2027-28/" + sellerA.toBigInteger() + "/1", n))
                .verifyComplete();
    }

    @Test
    @DisplayName("each seller has its own independent sequence")
    void sequenceIsPerSeller() {
        invoiceDAO.nextInvoiceNumber(sellerA, "2026-27").block();
        invoiceDAO.nextInvoiceNumber(sellerA, "2026-27").block();

        StepVerifier.create(invoiceDAO.nextInvoiceNumber(sellerB, "2026-27"))
                .assertNext(n -> assertEquals("INV/2026-27/" + sellerB.toBigInteger() + "/1", n))
                .verifyComplete();
    }

    @Test
    @DisplayName("readPageFilter returns only invoices the caller is a party to (seller or buyer)")
    void readPageFilterRestrictsToParty() {
        ULong app = insertTestApp(sellerA, "tappParty", "Party App").block();
        ULong asSeller = insertInvoice("INV/PARTY/1", sellerA, sellerB, app); // caller (sellerA) is the seller
        ULong asBuyer = insertInvoice("INV/PARTY/2", sellerB, sellerA, app);  // caller is the buyer
        insertInvoice("INV/PARTY/3", sellerB, sellerB, app);                  // caller is neither

        var auth = TestDataFactory.createBusinessAuth(sellerA, "SELA", java.util.List.of("Authorities.Invoice_READ"));

        Page<Invoice> page = invoiceDAO.readPageFilter(PageRequest.of(0, 10), null)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
                .block();

        assertEquals(2, page.getTotalElements(), "only the two invoices the caller is a party to");
        Set<ULong> ids = page.getContent().stream().map(Invoice::getId).collect(Collectors.toSet());
        assertTrue(ids.contains(asSeller), "the invoice the caller sold");
        assertTrue(ids.contains(asBuyer), "the invoice the caller bought");
    }

    @Test
    @DisplayName("SYSTEM client bypasses the party restriction and sees all")
    void readPageFilterSystemSeesAll() {
        ULong app = insertTestApp(sellerA, "tappSystem", "System App").block();
        insertInvoice("INV/SYS/1", sellerA, sellerB, app);
        insertInvoice("INV/SYS/2", sellerB, sellerB, app);

        Page<Invoice> page = invoiceDAO.readPageFilter(PageRequest.of(0, 10), null)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(TestDataFactory.createSystemAuth()))
                .block();

        assertEquals(2, page.getTotalElements());
    }

    private ULong insertInvoice(String number, ULong sellerClientId, ULong buyerClientId, ULong appId) {
        return databaseClient.sql(
                "INSERT INTO security_invoice (INVOICE_NUMBER, SELLER_CLIENT_ID, CLIENT_ID, APP_ID, TOKENS_PURCHASED, BASE_AMOUNT, TOTAL_AMOUNT) "
                        + "VALUES (:num, :seller, :buyer, :app, 100, 100, 118)")
                .bind("num", number)
                .bind("seller", sellerClientId.longValue())
                .bind("buyer", buyerClientId.longValue())
                .bind("app", appId.longValue())
                .filter(s -> s.returnGeneratedValues("ID"))
                .map(row -> ULong.valueOf(row.get("ID", Long.class)))
                .one()
                .block();
    }
}
