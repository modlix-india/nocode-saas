package com.fincity.security.dto.billing;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.security.jooq.enums.SecurityInvoiceGateway;
import com.fincity.security.jooq.enums.SecurityInvoiceStatus;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Lean token-purchase invoice. Seller (C) and buyer (M) legal details are
 * snapshotted at generation so the tax invoice is immutable. One bundle per
 * invoice, no line items.
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Invoice extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 1L;

    private String invoiceNumber;
    private LocalDateTime invoiceDate;
    private SecurityInvoiceStatus status;

    private ULong sellerClientId;
    private String sellerLegalName;
    private String sellerGstin;
    private String sellerAddress;

    private ULong clientId;
    private String buyerLegalName;
    private String buyerGstin;
    private String buyerAddress;

    private ULong appId;
    private ULong bundleId;
    private String bundleLabel;
    private BigDecimal tokensPurchased;

    private BigDecimal baseAmount;
    private BigDecimal gstPercentage;
    private BigDecimal gstAmount;
    private BigDecimal totalAmount;
    private String currency;

    private String paymentReference;
    private SecurityInvoiceGateway gateway;
    private LocalDateTime paidAt;
    private String pdfFileKey;

    /**
     * The gateway payment method (card / upi / netbanking / wallet ...), read from
     * the linked payment's captured response at read time. Not a column - never
     * persisted on the invoice.
     */
    private transient String paymentMethod;
}
