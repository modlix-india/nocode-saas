package com.fincity.security.model.billing;

import java.math.BigDecimal;

/**
 * The serving status for a (client, app): whether the action gate should allow
 * the action (status ACTIVE/SUSPENDED) and the current balance.
 */
public record WalletStatusView(
        String status,
        BigDecimal balance,
        boolean suspended) {
}
