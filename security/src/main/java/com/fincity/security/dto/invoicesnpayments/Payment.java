package com.fincity.security.dto.invoicesnpayments;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.security.jooq.enums.SecurityPaymentPaymentMethod;
import com.fincity.security.jooq.enums.SecurityPaymentPaymentStatus;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Payment extends AbstractUpdatableDTO<ULong, ULong> {
    @Serial
    private static final long serialVersionUID = 1L;

    private ULong invoiceId;
    private LocalDateTime paymentDate;
    private BigDecimal paymentAmount;
    private SecurityPaymentPaymentStatus paymentStatus;
    private SecurityPaymentPaymentMethod paymentMethod;
    private String paymentReference;
    private Map<String, Object> paymentResponse;
    
}
