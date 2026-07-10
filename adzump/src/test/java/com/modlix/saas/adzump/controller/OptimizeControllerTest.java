package com.modlix.saas.adzump.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jooq.types.ULong;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.modlix.saas.adzump.model.snapshot.SnapshotWindow;
import com.modlix.saas.adzump.service.schedule.OptimizeRun;
import com.modlix.saas.adzump.service.schedule.ScheduleService;

/**
 * The internal {@code /api/adzump/internal/optimize/{campaignId}} endpoint is a thin, no-authority
 * pass-through: it delegates the one loop-execution to {@link ScheduleService#optimize(ULong, SnapshotWindow)}
 * (the campaign-scoped headless run) and returns the {@link OptimizeRun}.
 */
class OptimizeControllerTest {

    private static final ULong CID = ULong.valueOf(100);

    @Test
    void delegatesToScheduleServiceLoop() {
        ScheduleService scheduleService = mock(ScheduleService.class);
        OptimizeController controller = new OptimizeController(scheduleService);

        OptimizeRun expected = new OptimizeRun(CID, null, ULong.valueOf(7), 1, 2, 0, "run-1");
        when(scheduleService.optimize(eq(CID), isNull())).thenReturn(expected);

        ResponseEntity<OptimizeRun> response = controller.optimize(CID, null);

        verify(scheduleService).optimize(eq(CID), isNull());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
    }

    @Test
    void passesTheWindowThrough() {
        ScheduleService scheduleService = mock(ScheduleService.class);
        OptimizeController controller = new OptimizeController(scheduleService);

        SnapshotWindow window = new SnapshotWindow().setTimezone("Asia/Kolkata");
        OptimizeRun expected = new OptimizeRun(CID, window, null, 0, 0, 0, "run-2");
        when(scheduleService.optimize(eq(CID), eq(window))).thenReturn(expected);

        ResponseEntity<OptimizeRun> response = controller.optimize(CID, window);

        verify(scheduleService).optimize(eq(CID), eq(window));
        assertSame(expected, response.getBody());
    }
}
