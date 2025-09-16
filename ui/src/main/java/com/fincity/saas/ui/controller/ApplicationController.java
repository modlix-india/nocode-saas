package com.fincity.saas.ui.controller;

import com.fincity.saas.ui.document.MobileApp;
import com.fincity.saas.ui.model.MobileAppStatusUpdateRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import com.fincity.saas.ui.document.Application;
import com.fincity.saas.ui.repository.ApplicationRepository;
import com.fincity.saas.ui.service.ApplicationService;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("api/ui/applications")
public class ApplicationController
        extends AbstractOverridableDataController<Application, ApplicationRepository, ApplicationService> {

    @GetMapping("/{appCode}/mobileApps")
    public Mono<ResponseEntity<List<MobileApp>>> listMobileApps(@PathVariable String appCode, @RequestParam(required = false) String clientCode) {

        return this.service.listMobileApps(appCode, clientCode)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{appCode}/mobileApps/{id}")
    public Mono<ResponseEntity<Boolean>> deleteApp(@PathVariable String id) {

        return this.service.deleteMobileApp(id).map(ResponseEntity::ok);
    }

    @PostMapping("/{appCode}/mobileApps")
    public Mono<ResponseEntity<MobileApp>> updateMobileApp(@PathVariable String appCode, @RequestBody MobileApp mobileApp) {

        return this.service.updateMobileApp(mobileApp.setAppCode(appCode))
                .map(ResponseEntity::ok);
    }

    @GetMapping("/mobileApps/next")
    public Mono<ResponseEntity<MobileApp>> nextMobileApp() {
        return this.service.findNextAppToGenerate()
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/mobileApps/status/{statusID}")
    public Mono<ResponseEntity<Boolean>> updateStatus(@RequestBody MobileAppStatusUpdateRequest request, @PathVariable String statusID) {
        return this.service.updateStatus(statusID, request)
                .map(ResponseEntity::ok);
    }
}
