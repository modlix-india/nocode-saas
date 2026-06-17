package com.fincity.saas.commons.security.model.wallet;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Cross-service wire contract for a code-based wallet charge. The metering layer
 * sends security-context codes; the security service resolves them:
 * {@code clientCode} -> consumer (wallet owner), {@code urlClientCode} ->
 * exposing client (config/rates), {@code appCode} -> app.
 */
@Data
@Accessors(chain = true)
public class WalletChargeRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String appCode;
    private String clientCode;
    private String urlClientCode;
    private String actionKey;
    private BigDecimal quantity;
    private String idempotencyKey;
    private String referenceType;
    private String referenceId;
    private boolean shadow;
}
