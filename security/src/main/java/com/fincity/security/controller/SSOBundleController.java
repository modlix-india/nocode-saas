package com.fincity.security.controller;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQDataController;
import com.fincity.security.dto.SSOBundle;
import com.fincity.security.service.SSOBundleService;
import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.ArrayList;

@RestController
@RequestMapping("api/security/ssoBundle")
public class SSOBundleController {

    private final SSOBundleService service;

    public SSOBundleController(SSOBundleService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<ResponseEntity<ArrayList<SSOBundle>>> get(@RequestParam String appCode, @RequestParam String clientCode) {
        return this.service.readBundles(clientCode, appCode).map(ResponseEntity::ok);
    }

    @PostMapping
    public Mono<ResponseEntity<SSOBundle>> createOrUpdate(@RequestBody SSOBundle ssoBundle) {
        if (ssoBundle.getId() == null) return this.service.create(ssoBundle).map(ResponseEntity::ok);
        else return this.service.update(ssoBundle).map(ResponseEntity::ok);
    }

    @DeleteMapping(AbstractJOOQDataController.PATH_ID)
    public Mono<ResponseEntity<Integer>> delete(@PathVariable Long id) {
        return this.service.delete(ULong.valueOf(id)).map(ResponseEntity::ok);
    }
}
