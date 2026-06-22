package com.fincity.security.service.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fincity.security.dao.wallet.UsageEventDAO;
import com.fincity.security.dto.wallet.UsageEvent;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for {@link UsageEventService}, the single dumb INSERT on the hot
 * path that records a raw consumption row (no pricing, no wallet resolution).
 * Pricing and debiting happen later in {@link UsageConsolidationService}.
 */
@ExtendWith(MockitoExtension.class)
class UsageEventServiceTest {

    @Mock private UsageEventDAO dao;

    private UsageEventService service;

    private static final ULong CLIENT = ULong.valueOf(100);
    private static final ULong URL_CLIENT = ULong.valueOf(1);
    private static final ULong APP = ULong.valueOf(50);
    private static final ULong USER = ULong.valueOf(7);

    @BeforeEach
    void setUp() {
        service = new UsageEventService(dao);
        when(dao.create(any(UsageEvent.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    @Test
    void record_writesRawDimensions_unconsolidated_defaultsQuantityToOne() {
        StepVerifier.create(service.record(CLIENT, URL_CLIENT, APP, USER, "core.sms.send", null))
                .assertNext(e -> {
                    assertEquals(CLIENT, e.getClientId());
                    assertEquals(URL_CLIENT, e.getUrlClientId());
                    assertEquals(APP, e.getAppId());
                    assertEquals(USER, e.getUserId());
                    assertEquals("core.sms.send", e.getActionKey());
                    assertEquals(0, BigDecimal.ONE.compareTo(e.getQuantity()));
                    assertFalse(e.isConsolidated());
                })
                .verifyComplete();
    }

    @Test
    void record_passesExplicitQuantityThrough() {
        ArgumentCaptor<UsageEvent> captor = ArgumentCaptor.forClass(UsageEvent.class);

        StepVerifier.create(service.record(CLIENT, URL_CLIENT, APP, USER, "core.email.send",
                new BigDecimal("9")))
                .expectNextCount(1)
                .verifyComplete();

        verify(dao).create(captor.capture());
        assertEquals(0, new BigDecimal("9").compareTo(captor.getValue().getQuantity()));
    }
}
