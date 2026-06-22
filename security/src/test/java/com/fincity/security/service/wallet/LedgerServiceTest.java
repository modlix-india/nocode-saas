package com.fincity.security.service.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fincity.security.dao.wallet.WalletTransactionDAO;
import com.fincity.security.dto.wallet.WalletTransaction;
import com.fincity.security.jooq.enums.SecurityWalletTransactionTransactionType;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for {@link LedgerService}. A normal append succeeds; a duplicate
 * idempotency-key collision falls back to the existing row (so a replayed charge
 * is a no-op); other failures (and missing keys) propagate. This is the
 * idempotency backstop the whole wallet model leans on.
 */
@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock private WalletTransactionDAO dao;

    private LedgerService service;

    private static final ULong WALLET_ID = ULong.valueOf(900);
    private static final ULong TXN_ID = ULong.valueOf(5000);

    @BeforeEach
    void setUp() {
        service = new LedgerService(dao);
    }

    private WalletTransaction txn(String idempotencyKey) {
        WalletTransaction t = new WalletTransaction().setWalletId(WALLET_ID)
                .setTransactionType(SecurityWalletTransactionTransactionType.DEBIT)
                .setCredits(new BigDecimal("3")).setIdempotencyKey(idempotencyKey);
        return t;
    }

    @Test
    void record_success_returnsCreatedRow() {
        WalletTransaction t = txn("k1");
        when(dao.create(t)).thenReturn(Mono.just(t));

        StepVerifier.create(service.record(t)).expectNext(t).verifyComplete();
        verify(dao, never()).findByIdempotencyKey(any(), any());
    }

    @Test
    void record_duplicateKeyCollision_returnsExistingRow() {
        WalletTransaction t = txn("dupe");
        WalletTransaction existing = txn("dupe");
        existing.setId(TXN_ID);
        when(dao.create(t)).thenReturn(Mono.error(new RuntimeException("Duplicate entry 'dupe' for key")));
        when(dao.findByIdempotencyKey(WALLET_ID, "dupe")).thenReturn(Mono.just(existing));

        StepVerifier.create(service.record(t))
                .assertNext(r -> assertEquals(TXN_ID, r.getId()))
                .verifyComplete();
    }

    @Test
    void record_duplicateButNullKey_propagatesError() {
        WalletTransaction t = txn(null);
        when(dao.create(t)).thenReturn(Mono.error(new RuntimeException("Duplicate entry")));

        StepVerifier.create(service.record(t))
                .expectErrorMessage("Duplicate entry")
                .verify();
        verify(dao, never()).findByIdempotencyKey(any(), any());
    }

    @Test
    void record_errorWithNullMessage_propagates() {
        WalletTransaction t = txn("k3");
        when(dao.create(t)).thenReturn(Mono.error(new RuntimeException((String) null)));

        StepVerifier.create(service.record(t)).expectError(RuntimeException.class).verify();
        verify(dao, never()).findByIdempotencyKey(any(), any());
    }

    @Test
    void record_nonDuplicateError_propagates() {
        WalletTransaction t = txn("k2");
        when(dao.create(t)).thenReturn(Mono.error(new RuntimeException("connection reset")));

        StepVerifier.create(service.record(t))
                .expectErrorMessage("connection reset")
                .verify();
        verify(dao, never()).findByIdempotencyKey(any(), any());
    }
}
