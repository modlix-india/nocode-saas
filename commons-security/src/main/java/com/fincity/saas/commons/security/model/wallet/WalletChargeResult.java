package com.fincity.saas.commons.security.model.wallet;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Cross-service wire contract for a wallet charge result. {@code allowed} tells
 * the caller whether the action may proceed; {@code outcome} carries the detail
 * (CHARGED / GRACE_ALLOWED / BLOCKED_INSUFFICIENT / NOT_ENFORCED / ...).
 */
@Data
@Accessors(chain = true)
public class WalletChargeResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private boolean allowed;
    private String outcome;
    private BigDecimal creditsCharged;
    private BigDecimal balanceAfter;
}
