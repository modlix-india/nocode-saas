package com.fincity.security.dto.billing;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.security.jooq.enums.SecurityWalletTransactionType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Append-only wallet ledger row. Every wallet mutation writes one, with a
 * required {@code reason} and the acting user in {@code createdBy} (null for
 * worker/machine charges). Idempotent per (walletId, idempotencyKey).
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class WalletTransaction extends AbstractDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 1L;

    private ULong walletId;
    private SecurityWalletTransactionType type;
    private BigDecimal tokens;
    private BigDecimal balanceAfter;
    private String actionKey;
    private ULong appId;
    private BigDecimal quantity;
    private LocalDate chargeDate;
    private Short windowIndex;
    private String idempotencyKey;
    private String referenceType;
    private ULong referenceId;
    private String reason;
    private String description;
}
