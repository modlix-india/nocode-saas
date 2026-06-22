package com.fincity.security.service.billing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fincity.saas.commons.security.model.wallet.RentTarget;
import com.fincity.security.dao.AppDAO;
import com.fincity.security.dao.UserDAO;
import com.fincity.security.dao.billing.AppActionCostDAO;
import com.fincity.security.dto.App;
import com.fincity.security.service.AppService;
import com.fincity.security.service.ClientManagerService;
import com.fincity.security.service.ClientService;
import com.fincity.security.model.billing.ChargeResult;
import com.fincity.security.service.wallet.WalletService;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for {@link RentDripService}, the hourly seat/app/site rent pass that
 * runs entirely inside security. These cover the "one hop" rule the billing
 * scenarios depend on (scenarios 3 = appbuilder app rent, 5 = sitezump site rent,
 * platform seats throughout): a billing config charges only its owner's DIRECT
 * managed clients, the right metric is counted per client (seats = active users;
 * app/site = apps owned), and a zero count is skipped.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RentDripServiceTest {

    @Mock private AppActionCostDAO appActionCostDAO;
    @Mock private ClientService clientService;
    @Mock private AppService appService;
    @Mock private ClientManagerService clientManagerService;
    @Mock private UserDAO userDAO;
    @Mock private AppDAO appDAO;
    @Mock private WalletService walletService;

    private RentDripService service;

    private static final String SEAT = "platform.seat";
    private static final String APP_RENT = "security.app.rent";
    private static final String SITE_RENT = "security.site.rent";

    private static final ULong OWNER_ID = ULong.valueOf(10);
    private static final ULong APP_ID = ULong.valueOf(50);
    private static final ULong M1 = ULong.valueOf(201);
    private static final ULong M2 = ULong.valueOf(202);

    @BeforeEach
    void setUp() {
        service = new RentDripService(appActionCostDAO, clientService, appService, clientManagerService,
                userDAO, appDAO, walletService);

        // Default: no configs for any rent action; debit accepted when reached.
        lenient().when(appActionCostDAO.findConfigsWithActionCost(anyString()))
                .thenReturn(Mono.just(List.of()));
        lenient().when(walletService.consolidatedDebit(any(), any(), any(), anyString(), any(), anyString()))
                .thenReturn(Mono.just(ChargeResult.charged(BigDecimal.ONE, BigDecimal.ONE, null)));

        App app = org.mockito.Mockito.mock(App.class);
        lenient().when(app.getId()).thenReturn(APP_ID);
        lenient().when(appService.getAppByCode("appbuilder")).thenReturn(Mono.just(app));
        lenient().when(clientService.getClientId("ACME")).thenReturn(Mono.just(OWNER_ID));
    }

    private RentTarget target() {
        return new RentTarget().setAppCode("appbuilder").setOwnerClientCode("ACME");
    }

    @Test
    void dripInternalRent_seat_chargesEachDirectManagedClient() {
        when(appActionCostDAO.findConfigsWithActionCost(SEAT)).thenReturn(Mono.just(List.of(target())));
        when(clientManagerService.getClientIdsOfManagerInternal(OWNER_ID))
                .thenReturn(Mono.just(List.of(M1, M2)));
        when(userDAO.countActiveByClient(M1)).thenReturn(Mono.just(3L));
        when(userDAO.countActiveByClient(M2)).thenReturn(Mono.just(2L));

        StepVerifier.create(service.dripInternalRent())
                .expectNext(5L)
                .verifyComplete();

        // Consumer's wallet (managed client), owner's config drives the rate (one hop).
        verify(walletService).consolidatedDebit(eq(M1), eq(OWNER_ID), eq(APP_ID), eq(SEAT), any(), anyString());
        verify(walletService).consolidatedDebit(eq(M2), eq(OWNER_ID), eq(APP_ID), eq(SEAT), any(), anyString());
        // Seats are counted from active users, never the app table.
        verify(appDAO, never()).countByClientId(any());
    }

    @Test
    void dripInternalRent_appRent_countsAppsOwnedByEachManagedClient() {
        when(appActionCostDAO.findConfigsWithActionCost(APP_RENT)).thenReturn(Mono.just(List.of(target())));
        when(clientManagerService.getClientIdsOfManagerInternal(OWNER_ID)).thenReturn(Mono.just(List.of(M1)));
        when(appDAO.countByClientId(M1)).thenReturn(Mono.just(4L));

        StepVerifier.create(service.dripInternalRent())
                .expectNext(4L)
                .verifyComplete();

        verify(walletService).consolidatedDebit(eq(M1), eq(OWNER_ID), eq(APP_ID), eq(APP_RENT), any(), anyString());
        verify(userDAO, never()).countActiveByClient(any());
    }

    @Test
    void dripInternalRent_siteRent_usesAppCount() {
        when(appActionCostDAO.findConfigsWithActionCost(SITE_RENT)).thenReturn(Mono.just(List.of(target())));
        when(clientManagerService.getClientIdsOfManagerInternal(OWNER_ID)).thenReturn(Mono.just(List.of(M1)));
        when(appDAO.countByClientId(M1)).thenReturn(Mono.just(2L));

        StepVerifier.create(service.dripInternalRent())
                .expectNext(2L)
                .verifyComplete();

        verify(walletService).consolidatedDebit(eq(M1), eq(OWNER_ID), eq(APP_ID), eq(SITE_RENT), any(), anyString());
    }

    @Test
    void dripInternalRent_zeroCount_skipsDebit() {
        when(appActionCostDAO.findConfigsWithActionCost(SEAT)).thenReturn(Mono.just(List.of(target())));
        when(clientManagerService.getClientIdsOfManagerInternal(OWNER_ID)).thenReturn(Mono.just(List.of(M1)));
        when(userDAO.countActiveByClient(M1)).thenReturn(Mono.just(0L));

        StepVerifier.create(service.dripInternalRent())
                .expectNext(0L)
                .verifyComplete();

        verify(walletService, never()).consolidatedDebit(any(), any(), any(), anyString(), any(), anyString());
    }

    @Test
    void dripInternalRent_noConfigs_chargesNothing() {
        StepVerifier.create(service.dripInternalRent())
                .expectNext(0L)
                .verifyComplete();

        verify(walletService, never()).consolidatedDebit(any(), any(), any(), anyString(), any(), anyString());
        verify(clientManagerService, never()).getClientIdsOfManagerInternal(any());
    }
}
