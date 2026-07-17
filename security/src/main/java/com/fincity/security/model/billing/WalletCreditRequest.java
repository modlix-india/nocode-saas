package com.fincity.security.model.billing;

import java.math.BigDecimal;

/**
 * Admin credit-a-wallet request body. Adds {@code tokens} to the ({@code clientCode},
 * {@code appCode}) wallet, creating the wallet if it does not exist yet.
 * {@code urlClientCode} is the tenant/URL client context the operation runs under;
 * {@code clientCode} is the client whose wallet is being credited. The caller must
 * be able to see both (own / managed / SYSTEM) and have write access to the app.
 */
public record WalletCreditRequest(String appCode, String urlClientCode, String clientCode,
        BigDecimal tokens, String reason) {
}
