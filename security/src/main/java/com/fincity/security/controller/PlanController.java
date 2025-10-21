package com.fincity.security.controller;

import java.util.List;

import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.security.dao.plansnbilling.PlanDAO;
import com.fincity.security.dto.plansnbilling.Plan;
import com.fincity.security.dto.plansnbilling.PlanLimit;
import com.fincity.security.jooq.tables.records.SecurityPlanRecord;
import com.fincity.security.service.plansnbilling.PlanService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/plans")
public class PlanController
        extends AbstractJOOQUpdatableDataController<SecurityPlanRecord, ULong, Plan, PlanDAO, PlanService> {

    public PlanController(PlanService service) {
        this.service = service;
    }

    @GetMapping("/registration")
    public Mono<ResponseEntity<List<Plan>>> getRegistrationPlans(@RequestParam(required = false, defaultValue = "false") boolean includeMultiAppPlans) {
        return this.service.readRegistrationPlans(includeMultiAppPlans).map(ResponseEntity::ok);
    }

    @GetMapping("/internal/limits")
    public Mono<ResponseEntity<List<PlanLimit>>> getLimits(@RequestParam String appCode, @RequestParam String clientCode) {
        return this.service.readLimits(appCode, clientCode).map(ResponseEntity::ok);
    }

}
