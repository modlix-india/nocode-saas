package com.fincity.security.dto.wallet;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Append-only durable consumption record. Written on the hot path with raw
 * dimensions only (no wallet resolution, no pricing). The 15-minute
 * consolidation job groups closed windows, resolves the wallet, prices via the
 * action catalog, writes one idempotent ledger debit per (wallet, action,
 * window), then purges the consumed rows.
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class UsageEvent extends AbstractDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 1L;

    private ULong clientId;
    private ULong urlClientId;
    private ULong appId;
    private ULong userId;
    private String actionKey;
    private BigDecimal quantity;
    private boolean consolidated;
    private LocalDateTime consolidatedAt;
}
