package com.fincity.security.dao.plansnbilling;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.security.dto.invoicesnpayments.Invoice;
import static com.fincity.security.jooq.tables.SecurityInvoice.SECURITY_INVOICE;
import com.fincity.security.jooq.tables.records.SecurityInvoiceRecord;

/**
 * Lean invoice DAO for token-purchase invoices. Read/update/create are provided
 * by {@link AbstractUpdatableDAO}; token-specific queries (e.g. by wallet) are
 * added in Phase 2 with the wallet domain.
 */
@Component
public class InvoiceDAO extends AbstractUpdatableDAO<SecurityInvoiceRecord, ULong, Invoice> {

    protected InvoiceDAO() {
        super(Invoice.class, SECURITY_INVOICE, SECURITY_INVOICE.ID);
    }
}
