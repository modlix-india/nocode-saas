package com.fincity.security.dao.plansnbilling;

import java.util.List;

import org.jooq.Record1;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.security.dto.invoicesnpayments.Invoice;
import com.fincity.security.dto.invoicesnpayments.InvoiceItem;
import com.fincity.security.jooq.enums.SecurityInvoiceInvoiceStatus;
import static com.fincity.security.jooq.tables.SecurityInvoice.SECURITY_INVOICE;
import static com.fincity.security.jooq.tables.SecurityInvoiceItem.SECURITY_INVOICE_ITEM;
import static com.fincity.security.jooq.tables.SecurityPlan.SECURITY_PLAN;
import com.fincity.security.jooq.tables.records.SecurityInvoiceItemRecord;
import com.fincity.security.jooq.tables.records.SecurityInvoiceRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class InvoiceDAO extends AbstractUpdatableDAO<SecurityInvoiceRecord, ULong, Invoice> {

    protected InvoiceDAO() {
        super(Invoice.class, SECURITY_INVOICE, SECURITY_INVOICE.ID);
    }

    public Mono<Integer> getInvoiceCount(ULong clientId, ULong appId) {

        return Mono.from(this.dslContext.selectCount().from(SECURITY_INVOICE)
                .join(SECURITY_PLAN).on(SECURITY_INVOICE.PLAN_ID.eq(SECURITY_PLAN.ID))
                .where(SECURITY_INVOICE.CLIENT_ID.eq(clientId).and(
                        SECURITY_PLAN.APP_ID.eq(appId))))
                .map(Record1::value1);
    }

    public Mono<List<InvoiceItem>> createInvoiceItems(ULong invoiceId, List<InvoiceItem> invoiceItems) {

        return Flux.fromIterable(invoiceItems)
                .flatMap(item -> {

                    SecurityInvoiceItemRecord rec = this.dslContext.newRecord(SECURITY_INVOICE_ITEM);
                    rec.from(item);
                    return Mono
                            .from(this.dslContext.insertInto(SECURITY_INVOICE_ITEM).set(rec)
                                    .returning(SECURITY_INVOICE_ITEM.ID))
                            .map(r -> r.get(0, ULong.class)).map(item::setId).map(InvoiceItem.class::cast);
                }).collectList();
    }

    public Mono<Invoice> getLastPaidInvoice(ULong planId, ULong cycleId, ULong clientId) {
        return Mono.from(this.dslContext.selectFrom(SECURITY_INVOICE)
                .where(SECURITY_INVOICE.CLIENT_ID.eq(clientId)
                        .and(SECURITY_INVOICE.PLAN_ID.eq(planId))
                        .and(SECURITY_INVOICE.CYCLE_ID.eq(cycleId)))
                .orderBy(SECURITY_INVOICE.INVOICE_DATE.desc())
                .limit(1))
                .map(e -> e.into(Invoice.class))
                .filter(i -> i.getInvoiceStatus().equals(SecurityInvoiceInvoiceStatus.PAID));
    }
}
