package com.fincity.security.dao.billing;

import static com.fincity.security.jooq.Tables.SECURITY_INVOICE;
import static com.fincity.security.jooq.Tables.SECURITY_INVOICE_COUNTER;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.security.dto.billing.Invoice;
import com.fincity.security.jooq.tables.records.SecurityInvoiceRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class InvoiceDAO extends AbstractUpdatableDAO<SecurityInvoiceRecord, ULong, Invoice> {

    protected InvoiceDAO() {
        super(Invoice.class, SECURITY_INVOICE, SECURITY_INVOICE.ID);
    }

    /**
     * Allocate the next sequential number for (seller, financial year) and return
     * a formatted invoice number. The UNIQUE (seller, invoice_number) constraint
     * is the final guard against a racing duplicate (caller retries on conflict).
     */
    public Mono<String> nextInvoiceNumber(ULong sellerClientId, String finYear) {
        return Mono.from(this.dslContext.insertInto(SECURITY_INVOICE_COUNTER)
                .set(SECURITY_INVOICE_COUNTER.SELLER_CLIENT_ID, sellerClientId)
                .set(SECURITY_INVOICE_COUNTER.FIN_YEAR, finYear)
                .set(SECURITY_INVOICE_COUNTER.LAST_NUMBER, ULong.valueOf(1))
                .onDuplicateKeyUpdate()
                .set(SECURITY_INVOICE_COUNTER.LAST_NUMBER, SECURITY_INVOICE_COUNTER.LAST_NUMBER.add(1)))
                .then(Mono.from(this.dslContext.select(SECURITY_INVOICE_COUNTER.LAST_NUMBER)
                        .from(SECURITY_INVOICE_COUNTER)
                        .where(SECURITY_INVOICE_COUNTER.SELLER_CLIENT_ID.eq(sellerClientId))
                        .and(SECURITY_INVOICE_COUNTER.FIN_YEAR.eq(finYear))))
                .map(r -> "INV/" + finYear + "/" + sellerClientId.toBigInteger() + "/" + r.value1());
    }

    public Mono<Invoice> findByPaymentReference(String paymentReference) {
        return Mono.from(this.dslContext.selectFrom(SECURITY_INVOICE)
                .where(SECURITY_INVOICE.PAYMENT_REFERENCE.eq(paymentReference)))
                .map(r -> r.into(Invoice.class));
    }

    public Flux<Invoice> findByClient(ULong clientId) {
        return Flux.from(this.dslContext.selectFrom(SECURITY_INVOICE)
                .where(SECURITY_INVOICE.CLIENT_ID.eq(clientId))
                .orderBy(SECURITY_INVOICE.ID.desc()))
                .map(r -> r.into(Invoice.class));
    }
}
