package com.fincity.security.dao.plansnbilling;

import static com.fincity.security.jooq.tables.SecurityInvoice.SECURITY_INVOICE;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.security.dto.invoicesnpayments.Invoice;
import com.fincity.security.dto.plansnbilling.ClientPlan;
import com.fincity.security.jooq.tables.records.SecurityInvoiceRecord;

import reactor.core.publisher.Flux;

@Component
public class InvoiceDAO extends AbstractUpdatableDAO<SecurityInvoiceRecord, ULong, Invoice> {

    protected InvoiceDAO() {
        super(Invoice.class, SECURITY_INVOICE, SECURITY_INVOICE.ID);
    }

    public Flux<ClientPlan> querySubscriptionsNeedingInvoices() {

        // return Flux.from(this.dslContext.selectFrom(SECURITY_CLIENT_PLAN)
        // .where(SECURITY_CLIENT_PLAN.START_DATE.lt(LocalDateTime.now())));

        return Flux.empty();
    }
}
