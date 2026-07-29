package com.modlix.saas.files.service.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.modlix.saas.files.dao.FileSystemDao;
import com.modlix.saas.files.feign.IFeignSecurityBillingService;
import com.modlix.saas.files.model.billing.ChargeRequest;
import com.modlix.saas.files.model.billing.MeteringInstruction;

/**
 * Files metering (blocking): per (C, app, M) instruction with a GB rate, sum M's
 * stored bytes (per client) and post the GB amount to security. A window posts one
 * charge; reconciliation posts all 96; zero bytes posts nothing.
 */
@ExtendWith(MockitoExtension.class)
class FilesBillingMeteringServiceTest {

    @Mock
    private IFeignSecurityBillingService securityBilling;
    @Mock
    private FileSystemDao fileSystemDao;

    private FilesBillingMeteringService service;

    private static final ULong APP_ID = ULong.valueOf(2);
    private static final ULong C_CLIENT = ULong.valueOf(10);
    private static final ULong M_CLIENT = ULong.valueOf(20);
    private static final String ACTION = "files.gb";
    private static final long FIVE_GB = 5L * 1024 * 1024 * 1024;

    private static final MeteringInstruction INSTR =
            new MeteringInstruction("CCCC", C_CLIENT, "appbuilder", APP_ID, "MMMM", M_CLIENT);

    @BeforeEach
    void setUp() {
        service = new FilesBillingMeteringService(securityBilling, fileSystemDao);
        lenient().when(securityBilling.charge(any())).thenReturn(Boolean.TRUE);
    }

    @SuppressWarnings("unchecked")
    private List<ChargeRequest> captureCharges() {
        ArgumentCaptor<List<ChargeRequest>> cap = ArgumentCaptor.forClass(List.class);
        verify(securityBilling).charge(cap.capture());
        return cap.getValue();
    }

    @Test
    void windowPostsOneChargeWithBytesConvertedToGb() {
        when(securityBilling.getInstructions(ACTION)).thenReturn(List.of(INSTR));
        when(fileSystemDao.sumBytesByClient("MMMM")).thenReturn(FIVE_GB);

        assertTrue(service.meterCurrentWindow());

        List<ChargeRequest> charges = captureCharges();
        assertEquals(1, charges.size());
        ChargeRequest req = charges.get(0);
        assertEquals(ACTION, req.actionKey());
        assertEquals(M_CLIENT, req.billedClientId());
        assertEquals(0, req.quantity().compareTo(new BigDecimal("5")));
    }

    @Test
    void reconcilePostsAllNinetySixWindows() {
        when(securityBilling.getInstructions(ACTION)).thenReturn(List.of(INSTR));
        when(fileSystemDao.sumBytesByClient("MMMM")).thenReturn(FIVE_GB);

        assertTrue(service.reconcile(java.time.LocalDate.of(2026, 6, 15)));

        assertEquals(96, captureCharges().size());
    }

    @Test
    void zeroBytesPostsNothing() {
        when(securityBilling.getInstructions(ACTION)).thenReturn(List.of(INSTR));
        when(fileSystemDao.sumBytesByClient("MMMM")).thenReturn(0L);

        assertTrue(service.meterCurrentWindow());

        verify(securityBilling, never()).charge(any());
    }
}
