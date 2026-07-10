package com.fincity.security.model.billing;

/**
 * The status a wallet presents to the owner. Unlike the persisted
 * {@code SecurityWalletStatus} (only ACTIVE/SUSPENDED), this adds a derived
 * {@code LOW} band: a positive balance that has dropped below the effective
 * low-balance threshold (wallet override, else config).
 */
public enum WalletDisplayStatus {
    ACTIVE,
    LOW,
    SUSPENDED
}
