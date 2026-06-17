package com.fincity.security.service.billing;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.jooq.types.ULong;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.plansnbilling.InvoiceDAO;
import com.fincity.security.dto.invoicesnpayments.Invoice;
import com.fincity.security.jooq.enums.SecurityInvoiceInvoiceStatus;
import com.fincity.security.jooq.enums.SecurityInvoiceInvoiceType;
import com.fincity.security.jooq.tables.records.SecurityInvoiceRecord;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Lean invoice service for token-purchase invoices. No subscription cycles,
 * proration or line items; an invoice simply records a token purchase that, on
 * payment, credits the wallet (see PaymentService).
 */
@Service
public class InvoiceService
        extends AbstractJOOQUpdatableDataService<SecurityInvoiceRecord, ULong, Invoice, InvoiceDAO> {

    public InvoiceService(InvoiceDAO dao) {
        this.dao = dao;
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Invoice_READ')")
    public Mono<Invoice> read(ULong id) {
        return super.read(id);
    }

    /** Create a PENDING top-up invoice for a token purchase. */
    @PreAuthorize("hasAuthority('Authorities.Owner')")
    public Mono<Invoice> generateTopUpInvoice(ULong clientId, ULong walletId, SecurityInvoiceInvoiceType type,
            BigDecimal amount, BigDecimal credits, String currency, BigDecimal taxAmount) {

        LocalDateTime now = LocalDateTime.now();
        Invoice invoice = new Invoice()
                .setClientId(clientId)
                .setWalletId(walletId)
                .setInvoiceType(type == null ? SecurityInvoiceInvoiceType.TOPUP : type)
                .setInvoiceNumber("INV-" + System.currentTimeMillis() + "-" + clientId)
                .setInvoiceDate(now)
                .setInvoiceDueDate(now)
                .setInvoiceAmount(amount)
                .setCurrency(currency == null ? "INR" : currency)
                .setCreditsPurchased(credits)
                .setTaxAmount(taxAmount)
                .setInvoiceStatus(SecurityInvoiceInvoiceStatus.PENDING);

        return this.dao.create(invoice)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "InvoiceService.generateTopUpInvoice"));
    }

    @Override
    protected Mono<Invoice> updatableEntity(Invoice entity) {
        return this.read(entity.getId()).map(existing -> existing
                .setInvoiceStatus(entity.getInvoiceStatus())
                .setInvoiceAmount(entity.getInvoiceAmount())
                .setCreditsPurchased(entity.getCreditsPurchased())
                .setTaxAmount(entity.getTaxAmount()));
    }
}
