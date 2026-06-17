package com.fincity.security.dao.wallet;

import static com.fincity.security.jooq.tables.SecurityWallet.SECURITY_WALLET;

import java.math.BigDecimal;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.security.dto.wallet.Wallet;
import com.fincity.security.jooq.tables.records.SecurityWalletRecord;

import reactor.core.publisher.Mono;

/**
 * Wallet DAO. The atomic* methods are the concurrency primitives: each is a
 * single conditional UPDATE whose WHERE guard makes check-and-mutate atomic.
 * A returned row count of 0 means the guard failed (e.g. insufficient balance)
 * and nothing changed.
 */
@Component
public class WalletDAO extends AbstractUpdatableDAO<SecurityWalletRecord, ULong, Wallet> {

    public WalletDAO() {
        super(Wallet.class, SECURITY_WALLET, SECURITY_WALLET.ID);
    }

    public Mono<Wallet> findByClientId(ULong clientId) {
        return Mono.from(this.dslContext.selectFrom(SECURITY_WALLET)
                .where(SECURITY_WALLET.CLIENT_ID.eq(clientId)))
                .map(r -> r.into(Wallet.class));
    }

    /** Debit only if the available balance covers it. Returns rows affected (0 or 1). */
    public Mono<Integer> atomicDebit(ULong walletId, BigDecimal credits) {
        return Mono.from(this.dslContext.update(SECURITY_WALLET)
                .set(SECURITY_WALLET.BALANCE, SECURITY_WALLET.BALANCE.minus(credits))
                .where(SECURITY_WALLET.ID.eq(walletId)
                        .and(SECURITY_WALLET.BALANCE.ge(credits))));
    }

    /** Debit allowing the balance to dip negative, but not below GRACE_FLOOR (engagement grace). */
    public Mono<Integer> atomicDebitAllowNegative(ULong walletId, BigDecimal credits) {
        return Mono.from(this.dslContext.update(SECURITY_WALLET)
                .set(SECURITY_WALLET.BALANCE, SECURITY_WALLET.BALANCE.minus(credits))
                .where(SECURITY_WALLET.ID.eq(walletId)
                        .and(SECURITY_WALLET.BALANCE.minus(credits).ge(SECURITY_WALLET.GRACE_FLOOR))));
    }

    public Mono<Integer> atomicCredit(ULong walletId, BigDecimal credits) {
        return Mono.from(this.dslContext.update(SECURITY_WALLET)
                .set(SECURITY_WALLET.BALANCE, SECURITY_WALLET.BALANCE.plus(credits))
                .where(SECURITY_WALLET.ID.eq(walletId)));
    }

    /** Move available -> reserved if balance covers it. */
    public Mono<Integer> atomicReserve(ULong walletId, BigDecimal credits) {
        return Mono.from(this.dslContext.update(SECURITY_WALLET)
                .set(SECURITY_WALLET.BALANCE, SECURITY_WALLET.BALANCE.minus(credits))
                .set(SECURITY_WALLET.RESERVED_BALANCE, SECURITY_WALLET.RESERVED_BALANCE.plus(credits))
                .where(SECURITY_WALLET.ID.eq(walletId)
                        .and(SECURITY_WALLET.BALANCE.ge(credits))));
    }

    /** Return a reservation fully to the available balance. */
    public Mono<Integer> atomicRelease(ULong walletId, BigDecimal credits) {
        return Mono.from(this.dslContext.update(SECURITY_WALLET)
                .set(SECURITY_WALLET.BALANCE, SECURITY_WALLET.BALANCE.plus(credits))
                .set(SECURITY_WALLET.RESERVED_BALANCE, SECURITY_WALLET.RESERVED_BALANCE.minus(credits))
                .where(SECURITY_WALLET.ID.eq(walletId)
                        .and(SECURITY_WALLET.RESERVED_BALANCE.ge(credits))));
    }

    /**
     * Settle a reservation: consume {@code actualCredits} of the {@code reservedCredits}
     * held, returning the unused remainder to the available balance.
     */
    public Mono<Integer> atomicSettle(ULong walletId, BigDecimal reservedCredits, BigDecimal actualCredits) {
        return Mono.from(this.dslContext.update(SECURITY_WALLET)
                .set(SECURITY_WALLET.RESERVED_BALANCE, SECURITY_WALLET.RESERVED_BALANCE.minus(reservedCredits))
                .set(SECURITY_WALLET.BALANCE, SECURITY_WALLET.BALANCE.plus(reservedCredits.subtract(actualCredits)))
                .where(SECURITY_WALLET.ID.eq(walletId)
                        .and(SECURITY_WALLET.RESERVED_BALANCE.ge(reservedCredits))));
    }
}
