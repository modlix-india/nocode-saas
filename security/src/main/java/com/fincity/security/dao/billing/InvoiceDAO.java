package com.fincity.security.dao.billing;

import static com.fincity.security.jooq.Tables.SECURITY_INVOICE;
import static com.fincity.security.jooq.Tables.SECURITY_INVOICE_COUNTER;

import org.jooq.Condition;
import org.jooq.Record;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.security.dto.billing.Invoice;
import com.fincity.security.jooq.tables.records.SecurityInvoiceRecord;

import reactor.core.publisher.Mono;

@Component
public class InvoiceDAO extends AbstractUpdatableDAO<SecurityInvoiceRecord, ULong, Invoice> {

    protected InvoiceDAO() {
        super(Invoice.class, SECURITY_INVOICE, SECURITY_INVOICE.ID);
    }

    /**
     * Restrict every filtered read (page + all) to invoices the caller is a party
     * to on its own client: SELLER_CLIENT_ID = clientId OR CLIENT_ID = clientId.
     * The SYSTEM client sees everything. {@code readById} bypasses this and is
     * guarded in the service.
     */
    @Override
    public Mono<Condition> filter(AbstractCondition condition, SelectJoinStep<Record> selectJoinStep) {
        return super.filter(condition, selectJoinStep)
                .flatMap(cond -> SecurityContextUtil.getUsersContextAuthentication().map(ca -> {
                    if (ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode()))
                        return cond;
                    ULong clientId = ULong.valueOf(ca.getUser().getClientId());
                    Condition access = SECURITY_INVOICE.SELLER_CLIENT_ID.eq(clientId)
                            .or(SECURITY_INVOICE.CLIENT_ID.eq(clientId));
                    return DSL.and(cond, access);
                }));
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
}
