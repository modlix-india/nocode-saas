package com.fincity.security.dto.wallet;

import java.io.Serial;
import java.math.BigDecimal;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.security.jooq.enums.SecurityWalletTransactionReferenceType;
import com.fincity.security.jooq.enums.SecurityWalletTransactionTransactionType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Append-only wallet ledger entry. Created, never updated. Idempotency is
 * enforced by the (WALLET_ID, IDEMPOTENCY_KEY) unique index.
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class WalletTransaction extends AbstractDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 1L;

    private ULong walletId;
    private SecurityWalletTransactionTransactionType transactionType;
    private BigDecimal credits;
    private BigDecimal balanceAfter;
    private BigDecimal reservedAfter;
    private String actionKey;
    private ULong appId;
    private BigDecimal quantity;
    private boolean shadow;
    private SecurityWalletTransactionReferenceType referenceType;
    private String referenceId;
    private String idempotencyKey;
    private String description;
}
