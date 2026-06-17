package com.fincity.security.dto.invoicesnpayments;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.security.jooq.enums.SecurityInvoiceInvoiceStatus;
import com.fincity.security.jooq.enums.SecurityInvoiceInvoiceType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Lean invoice for token purchases (top-up / auto-recharge / credit refund).
 * Subscription concepts (plan, cycle, proration, line items) are gone.
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Invoice extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 1L;

    private ULong clientId;
    private ULong walletId;
    private String invoiceNumber;
    private SecurityInvoiceInvoiceType invoiceType;
    private LocalDateTime invoiceDate;
    private LocalDateTime invoiceDueDate;
    private BigDecimal invoiceAmount;
    private String currency;
    private BigDecimal creditsPurchased;
    private BigDecimal taxAmount;
    private SecurityInvoiceInvoiceStatus invoiceStatus;
}
