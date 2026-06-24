package com.fincity.security.dao.billing;

import static com.fincity.security.jooq.Tables.SECURITY_WALLET;

import java.math.BigDecimal;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.security.dto.billing.Wallet;
import com.fincity.security.jooq.enums.SecurityWalletStatus;
import com.fincity.security.jooq.tables.records.SecurityWalletRecord;

import reactor.core.publisher.Mono;

@Component
public class WalletDAO extends AbstractUpdatableDAO<SecurityWalletRecord, ULong, Wallet> {

    protected WalletDAO() {
        super(Wallet.class, SECURITY_WALLET, SECURITY_WALLET.ID);
    }

    public Mono<Wallet> findByClientAndApp(ULong clientId, ULong appId) {
        return Mono.from(this.dslContext.selectFrom(SECURITY_WALLET)
                .where(SECURITY_WALLET.CLIENT_ID.eq(clientId))
                .and(SECURITY_WALLET.APP_ID.eq(appId)))
                .map(r -> r.into(Wallet.class));
    }

    /** Create a wallet seeded with {@code initial} tokens, ACTIVE. Concurrent-safe. */
    public Mono<Wallet> createSeeded(ULong clientId, ULong appId, BigDecimal initial) {
        return Mono.from(this.dslContext.insertInto(SECURITY_WALLET)
                .set(SECURITY_WALLET.CLIENT_ID, clientId)
                .set(SECURITY_WALLET.APP_ID, appId)
                .set(SECURITY_WALLET.BALANCE, initial)
                .set(SECURITY_WALLET.STATUS, SecurityWalletStatus.ACTIVE)
                .set(SECURITY_WALLET.LOW_BALANCE_NOTIFIED, (byte) 0)
                .set(SECURITY_WALLET.VERSION, 0)
                .onDuplicateKeyIgnore())
                .then(this.findByClientAndApp(clientId, appId));
    }

    /**
     * Atomic debit, allowed only while ACTIVE (so the charge that crosses zero
     * still applies once). Returns rows affected (1 = applied, 0 = suspended).
     */
    public Mono<Integer> debitActive(ULong walletId, BigDecimal tokens) {
        return Mono.from(this.dslContext.update(SECURITY_WALLET)
                .set(SECURITY_WALLET.BALANCE, SECURITY_WALLET.BALANCE.minus(tokens))
                .where(SECURITY_WALLET.ID.eq(walletId))
                .and(SECURITY_WALLET.STATUS.eq(SecurityWalletStatus.ACTIVE)));
    }

    public Mono<Integer> creditBalance(ULong walletId, BigDecimal tokens) {
        return Mono.from(this.dslContext.update(SECURITY_WALLET)
                .set(SECURITY_WALLET.BALANCE, SECURITY_WALLET.BALANCE.add(tokens))
                .where(SECURITY_WALLET.ID.eq(walletId)));
    }

    public Mono<Integer> setStatus(ULong walletId, SecurityWalletStatus status) {
        return Mono.from(this.dslContext.update(SECURITY_WALLET)
                .set(SECURITY_WALLET.STATUS, status)
                .where(SECURITY_WALLET.ID.eq(walletId)));
    }

    public Mono<Integer> setLowBalanceNotified(ULong walletId, boolean notified) {
        return Mono.from(this.dslContext.update(SECURITY_WALLET)
                .set(SECURITY_WALLET.LOW_BALANCE_NOTIFIED, (byte) (notified ? 1 : 0))
                .where(SECURITY_WALLET.ID.eq(walletId)));
    }
}
