package com.fincity.security.model.billing;

import java.math.BigDecimal;
import java.util.Map;

import org.jooq.types.ULong;

import com.fincity.security.jooq.enums.SecurityPaymentGatewayPaymentGateway;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Request to start a manual token top-up: create a TOPUP invoice and initialize
 * payment with the chosen gateway.
 */
@Data
@Accessors(chain = true)
public class TopUpRequest {

    /** Wallet owner: the consumer whose tokens are purchased. */
    private ULong clientId;
    /** Exposing client whose payment gateway processes the payment (e.g. the agency). */
    private ULong urlClientId;
    private ULong appId;
    private BigDecimal amount;
    private BigDecimal credits;
    private String currency;
    private BigDecimal taxAmount;
    private SecurityPaymentGatewayPaymentGateway gateway;
    private Map<String, Object> metadata;
}
