package com.modlix.saas.adzump.controller;

import java.beans.PropertyEditorSupport;
import java.time.LocalDate;

import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.DataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.modlix.saas.adzump.dto.PlanCompleteness;
import com.modlix.saas.adzump.dto.ValidationResult;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.snapshot.PerformanceSnapshot;
import com.modlix.saas.adzump.model.snapshot.SnapshotWindow;
import com.modlix.saas.adzump.service.CampaignPlanService;
import com.modlix.saas.adzump.service.PlanValidationService;
import com.modlix.saas.adzump.service.creative.AttributeAttribution;
import com.modlix.saas.adzump.service.creative.CreativeScoringService;
import com.modlix.saas.adzump.service.feedback.FeedbackService;
import com.modlix.saas.adzump.service.optimize.ActionSet;
import com.modlix.saas.adzump.service.optimize.OptimizationEngine;
import com.modlix.saas.adzump.service.optimize.ProposedAction;

/**
 * Plan-resource endpoints (J18). Thin pass-throughs — <b>no {@code @PreAuthorize} here</b>; all authority
 * and tenant checks live on the services. Alongside the CRUD/validate verbs this exposes the A5
 * read/propose surface (A5 §6) over the existing green J10/J12/J20 services:
 * <ul>
 * <li>{@code GET  /{id}/performance}    — the latest J10 {@link PerformanceSnapshot} (read, tenant-scoped);</li>
 * <li>{@code GET  /{id}/recommendations}— the on-demand J12 {@link ActionSet} (read, tenant-scoped);</li>
 * <li>{@code POST /{id}/actions/propose}— gate a caller-proposed action (EDIT; applies nothing in P3);</li>
 * <li>{@code GET  /{id}/attribute-map}  — the tenant-private J20 attribute map for the plan's vertical.</li>
 * </ul>
 */
@RestController
@RequestMapping("api/adzump/plans")
public class PlanController {

    private final CampaignPlanService planService;
    private final PlanValidationService validationService;
    private final FeedbackService feedbackService;
    private final OptimizationEngine optimizationEngine;
    private final CreativeScoringService creativeScoringService;

    public PlanController(CampaignPlanService planService, PlanValidationService validationService,
            FeedbackService feedbackService, OptimizationEngine optimizationEngine,
            CreativeScoringService creativeScoringService) {

        this.planService = planService;
        this.validationService = validationService;
        this.feedbackService = feedbackService;
        this.optimizationEngine = optimizationEngine;
        this.creativeScoringService = creativeScoringService;
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
        // window query param: "from,to[,timezone]" (ISO dates); blank/unparseable -> null (latest snapshot).
        binder.registerCustomEditor(SnapshotWindow.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(parseWindow(text));
            }
        });
    }

    @PostMapping
    public ResponseEntity<CampaignPlan> create(@RequestBody CampaignPlan plan,
            @RequestParam(required = false) String clientCode) {
        return ResponseEntity.ok(this.planService.create(plan, clientCode));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CampaignPlan> read(@PathVariable ULong id) {
        return ResponseEntity.ok(this.planService.read(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CampaignPlan> patch(@PathVariable ULong id, @RequestBody JsonNode mergePatch) {
        return ResponseEntity.ok(this.planService.patch(id, mergePatch));
    }

    @GetMapping("/{id}/completeness")
    public ResponseEntity<PlanCompleteness> completeness(@PathVariable ULong id) {
        return ResponseEntity.ok(this.planService.completeness(id));
    }

    @PostMapping("/{id}/validate")
    public ResponseEntity<ValidationResult> validate(@PathVariable ULong id,
            @RequestParam(required = false) String clientCode) {
        return ResponseEntity.ok(this.validationService.validate(id, clientCode));
    }

    // ------------------------------------------------------------------------------------------
    // A5 read/propose surface (thin pass-throughs; authz on the service methods)
    // ------------------------------------------------------------------------------------------

    /** get_performance (J10): the latest stored PerformanceSnapshot for the plan+window. Read; tenant-scoped. */
    @GetMapping("/{id}/performance")
    public ResponseEntity<PerformanceSnapshot> performance(@PathVariable ULong id,
            @RequestParam(required = false) SnapshotWindow window,
            @RequestParam(required = false) String clientCode) {
        return ResponseEntity.ok(this.feedbackService.readLatest(id, window, clientCode));
    }

    /** get_recommendations (J12): the on-demand ActionSet (recommend-mode, all requiresApproval). Read. */
    @GetMapping("/{id}/recommendations")
    public ResponseEntity<ActionSet> recommendations(@PathVariable ULong id,
            @RequestParam(required = false) SnapshotWindow window,
            @RequestParam(required = false) String clientCode) {
        return ResponseEntity.ok(this.optimizationEngine.getRecommendations(id, window, clientCode));
    }

    /** propose_action (J12): gate a caller-proposed action. EDIT; applies nothing in P3. */
    @PostMapping("/{id}/actions/propose")
    public ResponseEntity<ActionSet> proposeAction(@PathVariable ULong id, @RequestBody ProposedAction proposed,
            @RequestParam(required = false) String clientCode) {
        return ResponseEntity.ok(this.optimizationEngine.proposeAction(id, proposed, clientCode));
    }

    /** get_attribute_map (J20): the tenant-private attribute map for the plan's vertical. Read; tenant-scoped. */
    @GetMapping("/{id}/attribute-map")
    public ResponseEntity<AttributeAttribution> attributeMap(@PathVariable ULong id,
            @RequestParam(required = false) String clientCode) {
        return ResponseEntity.ok(this.creativeScoringService.getAttributeMapForPlan(id, clientCode));
    }

    /**
     * Parses the {@code window} query param ("from,to[,timezone]", ISO dates) into a {@link SnapshotWindow},
     * or {@code null} when blank/unparseable so the read falls back to the overall-latest snapshot.
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
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }
}
