package com.fincity.security.service.wallet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fincity.security.dao.wallet.UsageEventDAO;
import com.fincity.security.dto.wallet.UsageEvent;
import com.fincity.security.model.billing.ChargeResult;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for {@link UsageConsolidationService}, the 15-minute pass that turns
 * the durable consumption log (email/sms sends, etc.) into one idempotent wallet
 * debit per (consumer, exposing client, app, action) group, then purges the
 * consumed rows. This is the metering half of scenarios 1, 2 and 4.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UsageConsolidationServiceTest {

    @Mock private UsageEventDAO usageEventDAO;
    @Mock private WalletService walletService;

    private UsageConsolidationService service;

    private static final ULong CLIENT = ULong.valueOf(100);
    private static final ULong URL_CLIENT = ULong.valueOf(1);
    private static final ULong APP = ULong.valueOf(50);
    private static final String ACTION = "core.email.send";

    @BeforeEach
    void setUp() {
        service = new UsageConsolidationService(usageEventDAO, walletService);
        lenient().when(walletService.consolidatedDebit(any(), any(), any(), anyString(), any(), anyString()))
                .thenReturn(Mono.just(ChargeResult.charged(BigDecimal.ONE, BigDecimal.ONE, null)));
        lenient().when(usageEventDAO.purge(any())).thenReturn(Mono.just(1));
    }

    private UsageEvent event(long id, ULong app, String action, BigDecimal qty) {
        UsageEvent e = new UsageEvent().setClientId(CLIENT).setUrlClientId(URL_CLIENT)
                .setAppId(app).setActionKey(action).setQuantity(qty);
        e.setId(ULong.valueOf(id));
        return e;
    }

    @Test
    void consolidate_noRows_returnsZeroWithoutDebit() {
        when(usageEventDAO.findUnconsolidatedBefore(any(LocalDateTime.class), anyInt()))
                .thenReturn(Mono.just(List.of()));

        StepVerifier.create(service.consolidate()).expectNext(0).verifyComplete();
        verify(walletService, never()).consolidatedDebit(any(), any(), any(), anyString(), any(), anyString());
    }

    @Test
    void consolidate_sameGroup_sumsQuantityIntoOneDebitThenPurges() {
        when(usageEventDAO.findUnconsolidatedBefore(any(LocalDateTime.class), anyInt()))
                .thenReturn(Mono.just(List.of(
                        event(1, APP, ACTION, new BigDecimal("2")),
                        event(2, APP, ACTION, new BigDecimal("3")))));
        when(usageEventDAO.purge(argThat(ids -> ids.size() == 2))).thenReturn(Mono.just(2));

        StepVerifier.create(service.consolidate()).expectNext(2).verifyComplete();

        verify(walletService).consolidatedDebit(eq(CLIENT), eq(URL_CLIENT), eq(APP), eq(ACTION),
                argThat(q -> q.compareTo(new BigDecimal("5")) == 0), anyString());
    }

    @Test
    void consolidate_distinctGroups_debitsEach() {
        when(usageEventDAO.findUnconsolidatedBefore(any(LocalDateTime.class), anyInt()))
                .thenReturn(Mono.just(List.of(
                        event(1, APP, ACTION, BigDecimal.ONE),
                        event(2, APP, "core.sms.send", BigDecimal.ONE))));

        StepVerifier.create(service.consolidate()).expectNext(2).verifyComplete();
        verify(walletService, times(2))
                .consolidatedDebit(any(), any(), any(), anyString(), any(), anyString());
    }

    @Test
    void consolidate_nullQuantity_countsAsOne() {
        when(usageEventDAO.findUnconsolidatedBefore(any(LocalDateTime.class), anyInt()))
                .thenReturn(Mono.just(List.of(event(1, APP, ACTION, null))));

        StepVerifier.create(service.consolidate()).expectNext(1).verifyComplete();
        verify(walletService).consolidatedDebit(eq(CLIENT), eq(URL_CLIENT), eq(APP), eq(ACTION),
                argThat(q -> q.compareTo(BigDecimal.ONE) == 0), anyString());
    }

    @Test
    void consolidate_nullAppId_groupsUnderClientLevelWallet() {
        when(usageEventDAO.findUnconsolidatedBefore(any(LocalDateTime.class), anyInt()))
                .thenReturn(Mono.just(List.of(event(1, null, ACTION, BigDecimal.ONE))));

        StepVerifier.create(service.consolidate()).expectNext(1).verifyComplete();
        verify(walletService).consolidatedDebit(eq(CLIENT), eq(URL_CLIENT), isNull(), eq(ACTION),
                any(), anyString());
    }

    @Test
    void consolidate_debitError_skipsGroupAndCompletes() {
        when(usageEventDAO.findUnconsolidatedBefore(any(LocalDateTime.class), anyInt()))
                .thenReturn(Mono.just(List.of(event(1, APP, ACTION, BigDecimal.ONE))));
        when(walletService.consolidatedDebit(any(), any(), any(), anyString(), any(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("debit failed")));

        StepVerifier.create(service.consolidate()).expectNext(0).verifyComplete();
        // The debit was attempted; the error is swallowed so the next window retries.
        verify(walletService).consolidatedDebit(eq(CLIENT), eq(URL_CLIENT), eq(APP), eq(ACTION),
                any(), anyString());
    }
}
