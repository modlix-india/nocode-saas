package com.fincity.security.dto.wallet;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.security.jooq.enums.SecurityWalletBudgetPeriod;
import com.fincity.security.jooq.enums.SecurityWalletBudgetStatus;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Optional soft per-app spend cap tracked against the single shared wallet
 * balance (not separately funded).
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class WalletBudget extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 1L;

    private ULong walletId;
    private ULong appId;
    private BigDecimal capCredits;
    private SecurityWalletBudgetPeriod period;
    private BigDecimal consumedThisPeriod;
    private LocalDateTime periodStart;
    private SecurityWalletBudgetStatus status;
}
