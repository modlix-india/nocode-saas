package com.fincity.security.dao.wallet;

import static com.fincity.security.jooq.tables.SecurityWalletTransaction.SECURITY_WALLET_TRANSACTION;

import java.util.List;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.security.dto.wallet.WalletTransaction;
import com.fincity.security.jooq.tables.records.SecurityWalletTransactionRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Append-only ledger DAO. Inserts rely on the (WALLET_ID, IDEMPOTENCY_KEY)
 * unique index to dedupe; on a duplicate, the caller reads the prior row.
 */
@Component
public class WalletTransactionDAO extends AbstractDAO<SecurityWalletTransactionRecord, ULong, WalletTransaction> {

    public WalletTransactionDAO() {
        super(WalletTransaction.class, SECURITY_WALLET_TRANSACTION, SECURITY_WALLET_TRANSACTION.ID);
    }

    public Mono<WalletTransaction> findByIdempotencyKey(ULong walletId, String idempotencyKey) {
        return Mono.from(this.dslContext.selectFrom(SECURITY_WALLET_TRANSACTION)
                .where(SECURITY_WALLET_TRANSACTION.WALLET_ID.eq(walletId)
                        .and(SECURITY_WALLET_TRANSACTION.IDEMPOTENCY_KEY.eq(idempotencyKey))))
                .map(r -> r.into(WalletTransaction.class));
    }

    public Mono<List<WalletTransaction>> findRecentByWallet(ULong walletId, int limit) {
        return Flux.from(this.dslContext.selectFrom(SECURITY_WALLET_TRANSACTION)
                .where(SECURITY_WALLET_TRANSACTION.WALLET_ID.eq(walletId))
                .orderBy(SECURITY_WALLET_TRANSACTION.CREATED_AT.desc())
                .limit(limit))
                .map(r -> r.into(WalletTransaction.class))
                .collectList();
    }
}
