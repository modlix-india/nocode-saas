package com.fincity.security.dto.billing;

import java.io.Serial;
import java.math.BigDecimal;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.security.jooq.enums.SecurityWalletStatus;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * One flat token wallet per (billed client M, app). Created lazily seeded with
 * 1 token, ACTIVE. The charge that crosses zero is allowed, then the wallet
 * flips to SUSPENDED.
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Wallet extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 1L;

    private ULong clientId;
    private ULong appId;
    private BigDecimal balance;
    private SecurityWalletStatus status;
    private BigDecimal alertThreshold;
    private Byte lowBalanceNotified;
    private Integer version;
}
