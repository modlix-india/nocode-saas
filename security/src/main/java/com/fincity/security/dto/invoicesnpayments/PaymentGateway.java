package com.fincity.security.dto.invoicesnpayments;

import java.io.Serial;
import java.util.Map;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.security.jooq.enums.SecurityPaymentGatewayPaymentGateway;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PaymentGateway extends AbstractUpdatableDTO<ULong, ULong> {
    @Serial
    private static final long serialVersionUID = 1L;

    private ULong clientId;
    private SecurityPaymentGatewayPaymentGateway paymentGateway;
    private Map<String, Object> paymentGatewayDetails;   
}
