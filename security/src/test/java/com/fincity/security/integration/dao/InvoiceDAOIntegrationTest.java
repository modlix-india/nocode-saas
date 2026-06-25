package com.fincity.security.integration.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.security.dao.billing.InvoiceDAO;
import com.fincity.security.integration.AbstractIntegrationTest;

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
                .then(databaseClient.sql("DELETE FROM security_invoice_counter WHERE ID > 0").then())
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
}
