package com.modlix.saas.adzump.controller;

import java.beans.PropertyEditorSupport;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.DataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.modlix.saas.adzump.model.snapshot.SnapshotWindow;
import com.modlix.saas.adzump.service.schedule.OptimizeRun;
import com.modlix.saas.adzump.service.schedule.ScheduleService;

/**
 * J14 §5.1 — the <b>INTERNAL</b> optimize endpoint. Runs one closed-loop execution for a campaign
 * (J10 snapshot &rarr; J12 &rarr; J13 apply per autonomy), headless, triggered by the platform scheduled
 * task or a delegated on-demand caller.
 *
 * <p><b>Not user-reachable.</b> The path lives under {@code /api/adzump/internal/**}, which the gateway
 * keeps off the public surface and the service security chain permits without a user
 * ({@code AdzumpConfiguration} whitelists {@code (.*internal.*)} + {@code /api/adzump/internal/**}). There
 * is deliberately <b>no {@code @PreAuthorize}</b> here: a scheduled run has no user — the guard is
 * "internal + scoped to the campaign row" (the campaign is the principal; {@link ScheduleService} mints
 * and enforces the campaign-scoped context). Thin pass-through, like the other adzump controllers.
 */
@RestController
public class OptimizeController {

    private final ScheduleService scheduleService;

    public OptimizeController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @InitBinder
    public void initBinder(DataBinder binder) {
        binder.registerCustomEditor(ULong.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                if (text == null)
                    setValue(null);
                else
                    setValue(ULong.valueOf(text));
            }
        });
        // window query param: "from,to[,timezone]" (ISO dates); blank/unparseable -> null (account-tz yesterday).
        binder.registerCustomEditor(SnapshotWindow.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(parseWindow(text));
            }
        });
    }

    /** INTERNAL: run the loop for a campaign. Scheduler-fired or delegated on-demand; no user, no authority gate. */
    @PostMapping("/api/adzump/internal/optimize/{campaignId}")
    public ResponseEntity<OptimizeRun> optimize(@PathVariable ULong campaignId,
            @RequestParam(required = false) SnapshotWindow window) {
        return ResponseEntity.ok(this.scheduleService.optimize(campaignId, window));
    }

    /**
     * Parses the {@code window} query param ("from,to[,timezone]", ISO dates) into a {@link SnapshotWindow},
     * or {@code null} when blank/unparseable so the run falls back to the account-tz "yesterday" window.
     */
    private static SnapshotWindow parseWindow(String text) {
        if (text == null || text.isBlank())
            return null;
        String[] parts = text.split(",", 3);
        if (parts.length < 2)
            return null;
        try {
            SnapshotWindow window = new SnapshotWindow()
                    .setFrom(LocalDate.parse(parts[0].trim()))
                    .setTo(LocalDate.parse(parts[1].trim()));
            if (parts.length == 3 && !parts[2].isBlank())
                window.setTimezone(parts[2].trim());
            return window;
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
