package com.fincity.security.dto.invoicesnpayments;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.security.jooq.enums.SecurityInvoiceInvoiceStatus;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Lean invoice for token purchases (top-up / auto-recharge / credit refund).
 * Subscription concepts (plan, cycle, proration, line items) are gone.
 *
 * <p>Token-specific columns (WALLET_ID, INVOICE_TYPE, CREDITS_PURCHASED,
 * CURRENCY, TAX_*) are added in Phase 2 once V78 is applied and JOOQ is
 * regenerated; the matching fields are introduced alongside the wallet domain.
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Invoice extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 1L;

    private ULong clientId;
    private String invoiceNumber;
    private LocalDateTime invoiceDate;
    private LocalDateTime invoiceDueDate;
    private BigDecimal invoiceAmount;
    private SecurityInvoiceInvoiceStatus invoiceStatus;
}
