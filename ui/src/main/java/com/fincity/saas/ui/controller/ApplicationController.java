package com.fincity.saas.ui.controller;

import com.fincity.saas.ui.model.MobileAppStatusUpdateRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import com.fincity.saas.ui.document.Application;
import com.fincity.saas.ui.repository.ApplicationRepository;
import com.fincity.saas.ui.service.ApplicationService;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/ui/applications")
public class ApplicationController
        extends AbstractOverridableDataController<Application, ApplicationRepository, ApplicationService> {

    @GetMapping("/{appCode}/mobileApps")
    public Mono<ResponseEntity<List<Map<String, Object>>>> listMobileApps(@PathVariable String appCode, @RequestParam(required = false) String clientCode) {

        return this.service.listMobileApps(appCode, clientCode)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{appCode}/mobileApps/generate")
    public Mono<ResponseEntity<Boolean>> generateMobileApp(@PathVariable String appCode, @RequestParam(required = false) String clientCode, @RequestParam String mobileAppKey) {

        return this.service.generateMobileApp(appCode, clientCode, mobileAppKey)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/mobileApps/next")
    public Mono<ResponseEntity<Map<String, Object>>> nextMobileApp() {
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
