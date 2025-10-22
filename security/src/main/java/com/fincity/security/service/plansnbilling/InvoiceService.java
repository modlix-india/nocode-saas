package com.fincity.security.service.plansnbilling;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.security.dao.plansnbilling.InvoiceDAO;
import com.fincity.security.dto.invoicesnpayments.Invoice;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.jooq.tables.records.SecurityInvoiceRecord;
import com.fincity.security.service.AbstractSecurityUpdatableDataService;

import reactor.core.publisher.Mono;

@Service
public class InvoiceService
        extends AbstractSecurityUpdatableDataService<SecurityInvoiceRecord, ULong, Invoice, InvoiceDAO> {

    private final InvoiceDAO invoiceDAO;

    public InvoiceService(InvoiceDAO invoiceDAO) {
        this.invoiceDAO = invoiceDAO;
    }

    @Override
    public SecuritySoxLogObjectName getSoxObjectName() {
        return SecuritySoxLogObjectName.INVOICE;
    }

    // Pseudo code for generating invoices
    // function generate_invoices(run_ts = now()):
    // subs = query_subscriptions_needing_invoices(run_ts)

    // for each sub in subs:
    // current_idx = period_index(run_ts, sub.START_DATE, sub.INTERVAL)
    // last_idx = last_invoiced_index(sub.CLIENT_ID, sub.PLAN_ID, sub.CYCLE_ID,
    // sub.START_DATE, sub.INTERVAL)

    // for i in range(last_idx+1, current_idx+1):
    // period_start = sub.START_DATE + i * sub.INTERVAL days
    // period_end = period_start + sub.INTERVAL days

    // begin transaction
    // inv_id = insert_invoice_header(
    // client_id=sub.CLIENT_ID, plan_id=sub.PLAN_ID, cycle_id=sub.CYCLE_ID,
    // number=next_invoice_number(), date=run_ts, due_date=run_ts + terms
    // )

    // taxes = compute_taxes(sub.COST, sub.TAX1..TAX5) // percents or fixed
    // insert_invoice_line(inv_id,
    // name=f"{plan_name(cycle)} — {period_start} to {period_end}",
    // amount=sub.COST, tax_fields=taxes
    // )
    // update_invoice_total(inv_id, sub.COST + taxes.sum)
    // commit

    public Mono<Void> generateInvoices() {
        return Mono.empty();
    }
}
