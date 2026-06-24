package com.fincity.security.dto.billing;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.security.jooq.enums.SecurityPaymentGateway;
import com.fincity.security.jooq.enums.SecurityPaymentStatus;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Lean Razorpay-only token-purchase payment, linked to an invoice.
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Payment extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 1L;

    private ULong invoiceId;
    private ULong clientId;
    private SecurityPaymentGateway gateway;
    private String gatewayOrderId;
    private String gatewayPaymentId;
    private BigDecimal amount;
    private SecurityPaymentStatus status;
    private Map<String, Object> response;
    private LocalDateTime paidAt;
}
