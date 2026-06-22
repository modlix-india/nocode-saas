package com.fincity.security.dto.wallet;

import java.io.Serial;
import java.math.BigDecimal;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.security.jooq.enums.SecurityWalletAutoRechargeGateway;
import com.fincity.security.jooq.enums.SecurityWalletStatus;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Prepaid token wallet, scoped per (clientId, appId): APP_ID null is the
 * client's parent (client-level) wallet; APP_ID set is a ring-fenced app
 * sub-wallet. A charge on (clientId, appId) resolves to the sub-wallet if it
 * exists, else the parent.
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
    private BigDecimal reservedBalance;
    private String currency;
    private SecurityWalletStatus status;
    private BigDecimal lowBalanceThreshold;
    private BigDecimal graceFloor;
    private boolean autoRechargeEnabled;
    private BigDecimal autoRechargeThreshold;
    private BigDecimal autoRechargeAmount;
    private SecurityWalletAutoRechargeGateway autoRechargeGateway;
    private ULong version;
}
