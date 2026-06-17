package com.fincity.security.model.billing;

import java.math.BigDecimal;

import org.jooq.types.ULong;

import com.fincity.security.jooq.enums.SecurityWalletTransactionReferenceType;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * A request to charge (debit) a wallet for a metered action. The credit cost is
 * resolved server-side from the per-app action cost / pricing map; the caller
 * supplies only the action key and quantity plus an idempotency key.
 */
@Data
@Accessors(chain = true)
public class ChargeRequest {

    /** Wallet owner: the logged-in user's client (the consumer who pays). */
    private ULong clientId;
    /** Exposing/URL client whose (app, client) config governs the rates and enforcement. */
    private ULong urlClientId;
    private ULong appId;
    private String actionKey;
    private BigDecimal quantity;
    private String idempotencyKey;
    private SecurityWalletTransactionReferenceType referenceType;
    private String referenceId;
    private boolean shadow;
}
