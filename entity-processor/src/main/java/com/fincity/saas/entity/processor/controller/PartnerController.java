package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.commons.model.Query;
import com.fincity.saas.entity.processor.dto.Partner;
import com.fincity.saas.entity.processor.enums.PartnerVerificationStatus;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.PartnerRequest;
import com.fincity.saas.entity.processor.service.PartnerDenormalizationService;
import com.fincity.saas.entity.processor.service.PartnerService;
import java.beans.PropertyEditorSupport;
import java.util.Map;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
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
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/partners")
public class PartnerController {

    private final PartnerService service;
    private final PartnerDenormalizationService denormService;

    public PartnerController(PartnerService service, PartnerDenormalizationService denormService) {
        this.service = service;
        this.denormService = denormService;
    }

    @InitBinder
    public void initBinder(DataBinder binder) {
        binder.registerCustomEditor(ULong.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(text == null ? null : ULong.valueOf(text));
            }
        });
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Partner>> readById(@PathVariable("id") ULong id) {
        return this.service.read(id).map(ResponseEntity::ok)
                .switchIfEmpty(Mono.defer(() -> Mono.just(ResponseEntity.notFound().build())));
    }

    @PostMapping("/internal/denorm")
    public Mono<ResponseEntity<Map<String, Object>>> triggerDenormalization(
            @RequestParam(defaultValue = "false") boolean delta) {
        return (delta ? denormService.syncDelta() : denormService.syncFull())
                .map(count -> ResponseEntity.ok(Map.of("partnersUpdated", count, "delta", delta)));
    }

    @PostMapping("query")
    public Mono<ResponseEntity<Page<Partner>>> readPageFilter(@RequestBody Query query) {
        return this.service
                .readPageFilter(PageRequest.of(query.getPage(), query.getSize(), query.getSort()), query.getCondition())
                .map(ResponseEntity::ok);
    }

    @PostMapping("/req")
    public Mono<ResponseEntity<Partner>> createFromRequest(@RequestBody PartnerRequest partnerRequest) {
        return this.service.createRequest(partnerRequest).map(ResponseEntity::ok);
    }

    @GetMapping("/me")
    public Mono<ResponseEntity<Partner>> getLoggedInPartner() {
        return this.service.getLoggedInPartner().map(ResponseEntity::ok);
    }

    @PatchMapping("/me/verification-status")
    public Mono<ResponseEntity<Partner>> updateLoggedInPartnerVerificationStatus(
            @RequestParam("status") PartnerVerificationStatus status) {
        return this.service.updateLoggedInPartnerVerificationStatus(status).map(ResponseEntity::ok);
    }

    @PatchMapping("/me/dnc")
    public Mono<ResponseEntity<Partner>> toggleLoggedInPartnerDnc() {
        return this.service.toggleLoggedInPartnerDnc().map(ResponseEntity::ok);
    }

    @PostMapping("/me/teammates")
    public Mono<ResponseEntity<Page<Map<String, Object>>>> readLoggedInPartnerTeammates(
            @RequestBody Query query, ServerHttpRequest request) {
        return this.service
                .readLoggedInPartnerTeammates(query, request.getQueryParams())
                .map(ResponseEntity::ok);
    }

    @PatchMapping("/req/{id}/verification-status")
    public Mono<ResponseEntity<Partner>> updateVerificationStatus(
            @PathVariable("id") Identity identity,
            @RequestParam("status") PartnerVerificationStatus status) {
        return this.service.updatePartnerVerificationStatus(identity, status).map(ResponseEntity::ok);
    }

    @PatchMapping("/req/{id}/dnc")
    public Mono<ResponseEntity<Partner>> toggleDnc(@PathVariable("id") Identity identity) {
        return this.service.togglePartnerDnc(identity).map(ResponseEntity::ok);
    }

    @PostMapping("/clients/teammates")
    public Mono<ResponseEntity<Page<Map<String, Object>>>> readPartnerTeammates(
            @RequestParam("partnerId") Identity partnerId, @RequestBody Query query, ServerHttpRequest request) {
        return this.service
                .readPartnerTeammates(partnerId, query, request.getQueryParams())
                .map(ResponseEntity::ok);
    }
}