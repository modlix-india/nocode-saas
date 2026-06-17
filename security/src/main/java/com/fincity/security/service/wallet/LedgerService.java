package com.fincity.security.service.wallet;

import org.springframework.stereotype.Service;

import com.fincity.security.dao.wallet.WalletTransactionDAO;
import com.fincity.security.dto.wallet.WalletTransaction;

import reactor.core.publisher.Mono;

/**
 * Records append-only wallet ledger entries. On a duplicate idempotency key the
 * prior row is returned, so a replayed charge is a no-op.
 *
 * <p>Note: exactly-once under truly concurrent identical-key charges needs the
 * debit + ledger insert to share one reactive transaction; that hardening is
 * tracked as an open item. The idempotency pre-check in WalletService plus this
 * fallback cover sequential retries (the common case).
 */
@Service
public class LedgerService {

    private final WalletTransactionDAO dao;

    public LedgerService(WalletTransactionDAO dao) {
        this.dao = dao;
    }

    public Mono<WalletTransaction> record(WalletTransaction txn) {
        return dao.create(txn)
                .onErrorResume(
                        e -> txn.getIdempotencyKey() != null && isDuplicate(e),
                        e -> dao.findByIdempotencyKey(txn.getWalletId(), txn.getIdempotencyKey()));
    }

    private boolean isDuplicate(Throwable e) {
        String msg = e.getMessage();
        return msg != null && msg.toLowerCase().contains("duplicate");
    }
}
