package com.modlix.saas.adzump.controller;

import java.beans.PropertyEditorSupport;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.DataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditTriggeredBy;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.campaign.AttributeRequest;
import com.modlix.saas.adzump.model.campaign.CampaignProductLink;
import com.modlix.saas.adzump.model.snapshot.SnapshotWindow;
import com.modlix.saas.adzump.platform.RunState;
import com.modlix.saas.adzump.service.apply.ActionApplier;
import com.modlix.saas.adzump.service.apply.ApplyDecision;
import com.modlix.saas.adzump.service.apply.ApplyResult;
import com.modlix.saas.adzump.service.campaign.CampaignService;
import com.modlix.saas.adzump.service.schedule.OptimizeRun;
import com.modlix.saas.adzump.service.schedule.ScheduleService;

/**
 * J8 lifecycle endpoints (J18). Thin pass-throughs — NO {@code @PreAuthorize} here; all authority
 * and tenant checks live on {@link CampaignService}. The launch verb sits under the plan resource
 * ({@code /plans/{id}/launch}); post-launch verbs act on the campaign resource
 * ({@code /campaigns/...}), so the paths are declared per-method rather than via a single class-level
 * base (and never collide with {@code PlanController}'s {@code /plans/{id}} routes).
 */
@RestController
public class CampaignController {

    private final CampaignService campaignService;
    private final ActionApplier actionApplier;
    private final ScheduleService scheduleService;

    public CampaignController(CampaignService campaignService, ActionApplier actionApplier,
            ScheduleService scheduleService) {
        this.campaignService = campaignService;
        this.actionApplier = actionApplier;
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

    /** The single launch path: validate -> compile -> launch PAUSED -> write links + status. */
    @PostMapping("/api/adzump/plans/{id}/launch")
    public ResponseEntity<CampaignPlan> launch(@PathVariable ULong id,
            @RequestParam(required = false) String clientCode) {
        return ResponseEntity.ok(this.campaignService.launch(id, clientCode));
    }

    @PostMapping("/api/adzump/campaigns/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable ULong id,
            @RequestParam(required = false) String clientCode) {
        this.campaignService.setStatus(id, RunState.ACTIVE, clientCode);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/adzump/campaigns/{id}/pause")
    public ResponseEntity<Void> pause(@PathVariable ULong id,
            @RequestParam(required = false) String clientCode) {
        this.campaignService.setStatus(id, RunState.PAUSE, clientCode);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/adzump/campaigns/{id}/archive")
    public ResponseEntity<Void> archive(@PathVariable ULong id,
            @RequestParam(required = false) String clientCode) {
        this.campaignService.setStatus(id, RunState.ARCHIVED, clientCode);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/api/adzump/campaigns/{id}")
    public ResponseEntity<CampaignPlan> editLive(@PathVariable ULong id, @RequestBody JsonNode patch,
            @RequestParam(required = false) String clientCode) {
        return ResponseEntity.ok(this.campaignService.editLive(id, patch, clientCode));
    }

    @PostMapping("/api/adzump/campaigns/attribute")
    public ResponseEntity<CampaignProductLink> attribute(@RequestBody AttributeRequest request,
            @RequestParam(required = false) String clientCode) {
        return ResponseEntity.ok(this.campaignService.attributeExisting(
                request.getPlatform(), request.getExternalCampaignId(), request.getProductId(), clientCode));
    }

    // ---- J13 apply / approval-queue verbs (authz + tenancy on ActionApplier) -----------------

    /**
     * Applies the campaign's latest ActionSet headlessly: route by autonomy, guardrail-check, apply the
     * eligible actions through the one spine, audit every decision (§6). AGENT-triggered.
     */
    @PostMapping("/api/adzump/campaigns/{id}/apply")
    public ResponseEntity<ApplyResult> apply(@PathVariable ULong id,
            @RequestParam(required = false) String clientCode) {
        return ResponseEntity.ok(this.actionApplier.applyLatest(id, AdzumpActionAuditTriggeredBy.AGENT, clientCode));
    }

    /**
     * "Run now" (J14 §5.4): runs one closed-loop execution on-demand — the <b>same</b> loop-execution
     * path as the scheduler, via {@link ScheduleService#optimizeOnDemand} (EDIT + tenant gate on the
     * service). Human-triggered manual optimize.
     */
    @PostMapping("/api/adzump/campaigns/{id}/optimize")
    public ResponseEntity<OptimizeRun> optimize(@PathVariable ULong id,
            @RequestParam(required = false) SnapshotWindow window,
            @RequestParam(required = false) String clientCode) {
        return ResponseEntity.ok(this.scheduleService.optimizeOnDemand(id, window, clientCode));
    }

    /** Approves a queued recommendation ({@code id} = audit row id): routes it to apply (§6). USER-triggered. */
    @PostMapping("/api/adzump/recommendations/{id}/approve")
    public ResponseEntity<ApplyDecision> approve(@PathVariable ULong id,
            @RequestParam(required = false) String clientCode) {
        return ResponseEntity.ok(this.actionApplier.approve(id, clientCode));
    }

    /** Rejects a queued recommendation ({@code id} = audit row id): records it, applies nothing (§6). */
    @PostMapping("/api/adzump/recommendations/{id}/reject")
    public ResponseEntity<ApplyDecision> reject(@PathVariable ULong id,
            @RequestParam(required = false) String clientCode) {
        return ResponseEntity.ok(this.actionApplier.reject(id, clientCode));
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
