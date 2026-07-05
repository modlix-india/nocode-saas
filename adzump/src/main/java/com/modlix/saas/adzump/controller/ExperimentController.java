package com.modlix.saas.adzump.controller;

import java.beans.PropertyEditorSupport;
import java.util.List;

import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.DataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.modlix.saas.adzump.dto.Experiment;
import com.modlix.saas.adzump.dto.ExperimentReadout;
import com.modlix.saas.adzump.service.experiment.ExperimentService;

/**
 * J21 creative-experiment endpoints (J18). Thin pass-throughs — <b>no {@code @PreAuthorize} here</b>; all
 * authority (EDIT on design/start/stop/readout/decide) and tenant checks live on {@link ExperimentService}.
 * <ul>
 * <li>{@code POST /experiments}              — design a controlled experiment; {@code ?start=true} also starts
 *     it (rotates the arms live through J13). EDIT.</li>
 * <li>{@code GET  /experiments?campaign=}    — the campaign's experiments + their readouts (read; tenant-scoped).</li>
 * <li>{@code GET  /experiments/{id}/readout} — the current maturity-aware readout (per-variant outcomes +
 *     winner + pValue + significant); recomputes + advances the experiment via the service. Tenant-scoped.</li>
 * <li>{@code POST /experiments/{id}/stop}    — force the decision point now (never lingers RUNNING). EDIT.</li>
 * <li>{@code POST /experiments/{id}/decide}  — act on a significant readout: promote the winner + retire the
 *     losers <b>through J13</b> and record the causal result to J20. EDIT.</li>
 * </ul>
 * The experiment body ({@link Experiment}) is the wire input for design; {@code clientCode}/{@code status}/
 * {@code readout} on it are ignored — scope is pinned server-side from the plan and status is machine-owned.
 */
@RestController
@RequestMapping("api/adzump/experiments")
public class ExperimentController {

    private final ExperimentService experimentService;

    public ExperimentController(ExperimentService experimentService) {
        this.experimentService = experimentService;
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
    }

    /** Designs an experiment; when {@code start=true}, immediately starts it (rotate live via J13). EDIT. */
    @PostMapping
    public ResponseEntity<Experiment> design(@RequestBody Experiment request,
            @RequestParam(required = false, defaultValue = "false") boolean start,
            @RequestParam(required = false) String clientCode) {

        Experiment designed = this.experimentService.design(request.getCampaignPlanId(), request.getHypothesis(),
                request.getVariants(), request.getMetric(), request.getMinVolumePerVariant(),
                request.getMaxDurationDays(), clientCode);

        if (start)
            designed = this.experimentService.start(designed.getId(), clientCode);

        return ResponseEntity.ok(designed);
    }

    /** The campaign's experiments (newest first) with their stored readouts. Read; tenant-scoped. */
    @GetMapping
    public ResponseEntity<List<Experiment>> list(@RequestParam ULong campaign,
            @RequestParam(required = false) String clientCode) {
        return ResponseEntity.ok(this.experimentService.listForCampaign(campaign, clientCode));
    }

    /** Forces the decision point now: terminates SIGNIFICANT or INCONCLUSIVE, never lingering RUNNING. EDIT. */
    @PostMapping("/{id}/stop")
    public ResponseEntity<Experiment> stop(@PathVariable ULong id,
            @RequestParam(required = false) String clientCode) {
        return ResponseEntity.ok(this.experimentService.stop(id, clientCode));
    }

    /**
     * The experiment's current maturity-aware readout (§5.3): per-variant outcomes + winner + pValue +
     * significant, with the maturity/judgeable verdict on each arm. Delegates to
     * {@link ExperimentService#readout} — which recomputes from the live creative-grain measurement and
     * advances the experiment (RUNNING → SIGNIFICANT/INCONCLUSIVE on a cap/mature-win) — and returns the
     * computed {@link ExperimentReadout}. Tenant-scoped on the service.
     */
    @GetMapping("/{id}/readout")
    public ResponseEntity<ExperimentReadout> readout(@PathVariable ULong id,
            @RequestParam(required = false) String clientCode) {
        return ResponseEntity.ok(this.experimentService.readout(id, clientCode).getReadout());
    }

    /**
     * Acts on a significant readout (§5.4): PROMOTE the winner + RETIRE the losers — <b>both guardrailed
     * applies routed through J13 ({@code ActionApplier})</b>, bound by the campaign's AutonomyConfig caps and
     * the {@code adzump.apply.live-enabled} kill-switch, never a platform side channel — then record the
     * causal attribute result to J20 and move the experiment to APPLIED. EDIT (authz + tenant gate on the
     * service).
     */
    @PostMapping("/{id}/decide")
    public ResponseEntity<Experiment> decide(@PathVariable ULong id,
            @RequestParam(required = false) String clientCode) {
        return ResponseEntity.ok(this.experimentService.decide(id, clientCode));
    }
}
