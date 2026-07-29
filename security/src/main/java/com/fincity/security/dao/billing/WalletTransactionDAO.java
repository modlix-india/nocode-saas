package com.fincity.security.dao.billing;

import static com.fincity.security.jooq.Tables.SECURITY_WALLET_TRANSACTION;

import java.time.LocalDate;
import java.util.List;

import org.jooq.Record1;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.security.dto.billing.WalletTransaction;
import com.fincity.security.jooq.tables.records.SecurityWalletTransactionRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class WalletTransactionDAO
        extends AbstractDAO<SecurityWalletTransactionRecord, ULong, WalletTransaction> {

    protected WalletTransactionDAO() {
        super(WalletTransaction.class, SECURITY_WALLET_TRANSACTION, SECURITY_WALLET_TRANSACTION.ID);
    }

    /**
     * Idempotent insert: a duplicate (walletId, idempotencyKey) is ignored.
     * Returns true if a new row was written.
     */
    public Mono<Boolean> recordTxn(WalletTransaction t) {
        return Mono.from(this.dslContext.insertInto(SECURITY_WALLET_TRANSACTION)
                .set(SECURITY_WALLET_TRANSACTION.WALLET_ID, t.getWalletId())
                .set(SECURITY_WALLET_TRANSACTION.TYPE, t.getType())
                .set(SECURITY_WALLET_TRANSACTION.TOKENS, t.getTokens())
                .set(SECURITY_WALLET_TRANSACTION.BALANCE_AFTER, t.getBalanceAfter())
                .set(SECURITY_WALLET_TRANSACTION.ACTION_KEY, t.getActionKey())
                .set(SECURITY_WALLET_TRANSACTION.APP_ID, t.getAppId())
                .set(SECURITY_WALLET_TRANSACTION.QUANTITY, t.getQuantity())
                .set(SECURITY_WALLET_TRANSACTION.CHARGE_DATE, t.getChargeDate())
                .set(SECURITY_WALLET_TRANSACTION.WINDOW_INDEX, t.getWindowIndex())
                .set(SECURITY_WALLET_TRANSACTION.IDEMPOTENCY_KEY, t.getIdempotencyKey())
                .set(SECURITY_WALLET_TRANSACTION.REFERENCE_TYPE, t.getReferenceType())
                .set(SECURITY_WALLET_TRANSACTION.REFERENCE_ID, t.getReferenceId())
                .set(SECURITY_WALLET_TRANSACTION.REASON, t.getReason())
                .set(SECURITY_WALLET_TRANSACTION.DESCRIPTION, t.getDescription())
                .set(SECURITY_WALLET_TRANSACTION.CREATED_BY, t.getCreatedBy())
                .onDuplicateKeyIgnore())
                .map(rows -> rows > 0);
    }

    /** Window indices (0..95) already charged for a wallet+action on a day. */
    public Mono<List<Short>> chargedWindows(ULong walletId, String actionKey, LocalDate chargeDate) {
        return Flux.from(this.dslContext.select(SECURITY_WALLET_TRANSACTION.WINDOW_INDEX)
                .from(SECURITY_WALLET_TRANSACTION)
                .where(SECURITY_WALLET_TRANSACTION.WALLET_ID.eq(walletId))
                .and(SECURITY_WALLET_TRANSACTION.ACTION_KEY.eq(actionKey))
                .and(SECURITY_WALLET_TRANSACTION.CHARGE_DATE.eq(chargeDate))
                .and(SECURITY_WALLET_TRANSACTION.WINDOW_INDEX.isNotNull()))
                .map(Record1::value1)
                .collectList();
    }
}
