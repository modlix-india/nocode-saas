package com.modlix.saas.adzump.controller;

import java.beans.PropertyEditorSupport;

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
import com.modlix.saas.adzump.service.CampaignPlanService;
import com.modlix.saas.adzump.service.PlanValidationService;

@RestController
@RequestMapping("api/adzump/plans")
public class PlanController {

    private final CampaignPlanService planService;
    private final PlanValidationService validationService;

    public PlanController(CampaignPlanService planService, PlanValidationService validationService) {

        this.planService = planService;
        this.validationService = validationService;
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
    public ResponseEntity<ValidationResult> validate(@PathVariable ULong id) {
        return ResponseEntity.ok(this.validationService.validate(id));
    }
}
