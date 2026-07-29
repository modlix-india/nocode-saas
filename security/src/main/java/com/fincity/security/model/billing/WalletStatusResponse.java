package com.fincity.security.model.billing;

import java.math.BigDecimal;

/**
 * The derived status of a wallet for the UI banner: ACTIVE / LOW / SUSPENDED,
 * the current balance, and the effective low-balance threshold it was judged
 * against (null when no threshold applies). Balance/threshold are null when the
 * (client, app) has no wallet yet (status ACTIVE).
 */
public record WalletStatusResponse(
        WalletDisplayStatus status,
        BigDecimal balance,
        BigDecimal threshold) {
}
