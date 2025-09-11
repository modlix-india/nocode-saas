package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.commons.model.Query;
import com.fincity.saas.entity.processor.controller.base.BaseUpdatableController;
import com.fincity.saas.entity.processor.dao.PartnerDAO;
import com.fincity.saas.entity.processor.dto.Partner;
import com.fincity.saas.entity.processor.enums.PartnerVerificationStatus;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorPartnersRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.PartnerRequest;
import com.fincity.saas.entity.processor.service.PartnerService;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
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
public class PartnerController
        extends BaseUpdatableController<EntityProcessorPartnersRecord, Partner, PartnerDAO, PartnerService> {

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<Partner>> createFromRequest(@RequestBody PartnerRequest partnerRequest) {
        return this.service.createPartner(partnerRequest).map(ResponseEntity::ok);
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

    @PatchMapping(REQ_PATH_ID + "/verification-status")
    public Mono<ResponseEntity<Partner>> updateVerificationStatus(
            @PathVariable(PATH_VARIABLE_ID) Identity identity,
            @RequestParam("status") PartnerVerificationStatus status) {
        return this.service.updatePartnerVerificationStatus(identity, status).map(ResponseEntity::ok);
    }

    @PatchMapping(REQ_PATH_ID + "/dnc")
    public Mono<ResponseEntity<Partner>> toggleDnc(@PathVariable(PATH_VARIABLE_ID) Identity identity) {
        return this.service.togglePartnerDnc(identity).map(ResponseEntity::ok);
    }

    @PostMapping("/clients")
    public Mono<ResponseEntity<Page<Map<String, Object>>>> createClient(
            @RequestBody Query query, ServerHttpRequest request) {
        return this.service.readPartnerClient(query, request.getQueryParams()).map(ResponseEntity::ok);
    }
}
