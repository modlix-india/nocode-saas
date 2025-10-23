package com.fincity.security.dto.invoicesnpayments;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.security.jooq.enums.SecurityInvoiceInvoiceStatus;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Invoice extends AbstractUpdatableDTO<ULong, ULong> {
    @Serial
    private static final long serialVersionUID = 1L;

    private ULong clientId;
    private ULong planId;
    private ULong cycleId;
    private String invoiceNumber;
    private LocalDateTime invoiceDate;
    private LocalDateTime invoiceDueDate;
    private BigDecimal invoiceAmount;
    private SecurityInvoiceInvoiceStatus invoiceStatus;
    private String invoiceReason;

    private List<InvoiceItem> items;
}
