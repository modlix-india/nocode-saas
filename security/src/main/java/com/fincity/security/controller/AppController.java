package com.fincity.security.controller;

import java.util.List;
import java.util.Map;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQDataController;
import com.mysql.cj.util.StringUtils;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.ConditionUtil;
import com.fincity.security.dao.AppDAO;
import com.fincity.security.dto.App;
import com.fincity.security.dto.AppProperty;
import com.fincity.security.dto.Client;
import com.fincity.security.jooq.tables.records.SecurityAppRecord;
import com.fincity.security.model.AppDependency;
import com.fincity.security.model.ApplicationAccessRequest;
import com.fincity.security.model.PropertiesResponse;
import com.fincity.security.service.AppService;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

@RestController
@RequestMapping("api/security/applications")
public class AppController
        extends AbstractJOOQUpdatableDataController<SecurityAppRecord, ULong, App, AppDAO, AppService> {

    private static final String SAME_ORIGIN = "SAMEORIGIN";

    @Value("${security.appCodeSuffix:}")
    private String appCodeSuffix;

    @Value("${security.resourceCacheAge:604800}")
    private int cacheAge;

    @GetMapping("/applyAppCodeSuffix")
    public Mono<ResponseEntity<String>> applyAppCodeSuffix(@RequestParam String appCode) {
        return Mono.just(ResponseEntity.ok().header("ETag", "W/" + appCode)
                .header("Cache-Control", "max-age: " + cacheAge)
                .header("x-frame-options", SAME_ORIGIN)
                .header("X-Frame-Options", SAME_ORIGIN).body(appCode + appCodeSuffix));
    }

    @GetMapping("/applyAppCodePrefix")
    public Mono<ResponseEntity<String>> applyAppCodePrefix(@RequestParam String appCode) {
        String prefix = appCodeSuffix;

        if (!StringUtils.isNullOrEmpty(appCodeSuffix)) {
            if (prefix.startsWith("."))
                prefix = prefix.substring(1) + ".";
        }

        return Mono.just(ResponseEntity.ok().header("ETag", "W/" + appCode)
                .header("Cache-Control", "max-age: " + cacheAge)
                .header("x-frame-options", SAME_ORIGIN)
                .header("X-Frame-Options", SAME_ORIGIN).body(prefix + appCode));
    }

    @GetMapping("/internal/hasReadAccess")
    public Mono<ResponseEntity<Boolean>> hasReadAccess(@RequestParam String appCode, @RequestParam String clientCode) {

        return this.service.hasReadAccess(appCode, clientCode)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/internal/appInheritance")
    public Mono<ResponseEntity<List<String>>> appInheritance(@RequestParam String appCode,
                                                             @RequestParam String urlClientCode, @RequestParam String clientCode) {

        return this.service.appInheritance(appCode, urlClientCode, clientCode)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/internal/hasWriteAccess")
    public Mono<ResponseEntity<Boolean>> hasWriteAccess(@RequestParam String appCode, @RequestParam String clientCode) {

        return this.service.hasWriteAccess(appCode, clientCode)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/everything/{id}")
    public Mono<ResponseEntity<Boolean>> deleteByAppId(@PathVariable(PATH_VARIABLE_ID) final ULong id,
                                                       @RequestParam(required = false) final Boolean forceDelete) {

        return this.service.deleteEverything(id, BooleanUtil.safeValueOf(forceDelete))
                .filter(BooleanUtil::safeValueOf)
                .defaultIfEmpty(Boolean.FALSE)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/hasDeleteAccess")
    public Mono<ResponseEntity<Boolean>> hasDeleteAccess(@RequestParam String deleteAppCode,
                                                         @RequestParam String deleteClientCode) {
        return this.service.hasDeleteAccess(deleteAppCode, deleteClientCode)
                .defaultIfEmpty(Boolean.FALSE).map(ResponseEntity::ok);
    }

    @GetMapping("/internal/appCode/{appCode}")
    public Mono<ResponseEntity<App>> getAppCode(@PathVariable("appCode") final String appCode) {

        return this.service.getAppByCode(appCode)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/appCode/{appCode}")
    public Mono<ResponseEntity<App>> getAppByCode(@PathVariable("appCode") final String appCode) {

        return this.service.getAppByCodeCheckAccess(appCode)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/internal/explicitInfo/{appCode}")
    public Mono<ResponseEntity<com.fincity.saas.commons.security.dto.App>> getAppExplicitInfoByCode(
            @PathVariable("appCode") final String appCode) {

        return this.service.getAppExplicitInfoByCode(appCode)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/{id}/access")
    public Mono<ResponseEntity<Boolean>> addClientAccess(@PathVariable(PATH_VARIABLE_ID) final ULong appId,
                                                         @RequestBody final ApplicationAccessRequest request) {
        return this.service.addClientAccess(appId, request.getClientId(), request.isWriteAccess())
                .map(ResponseEntity::ok);
    }

    @PatchMapping("/{id}/access")
    public Mono<ResponseEntity<Boolean>> updateClientAccess(
            @RequestBody final ApplicationAccessRequest request) {
        return this.service.updateClientAccess(request.getId(), request.isWriteAccess())
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{id}/access")
    public Mono<ResponseEntity<Boolean>> removeClientAccess(@PathVariable(PATH_VARIABLE_ID) final ULong appId,
                                                            @RequestParam final ULong accessId) {
        return this.service.removeClient(appId, accessId)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/clients/{appCode}")
    public Mono<ResponseEntity<Page<Client>>> getAppClients(@PathVariable final String appCode,
                                                            @RequestParam(required = false) Boolean onlyWriteAccess, @RequestParam(required = false) String name,
                                                            Pageable pageable) {
        return this.service.getAppClients(appCode, onlyWriteAccess, name, pageable)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/property")
    public Mono<ResponseEntity<PropertiesResponse>> getProperty(
            @RequestParam(required = false) ULong clientId,
            @RequestParam(required = false) ULong appId, @RequestParam(required = false) String appCode,
            @RequestParam(required = false) String propName) {

        return this.service.getPropertiesWithClients(clientId, appId, appCode, propName)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/property")
    public Mono<ResponseEntity<Boolean>> updateProperty(@RequestBody AppProperty property) {

        return this.service.updateProperty(property)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/property")
    public Mono<ResponseEntity<Boolean>> deleteProperty(@RequestParam ULong clientId,
                                                        @RequestParam ULong appId,
                                                        @RequestParam String name) {

        return this.service.deleteProperty(clientId, appId, name)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/property/{propertyId}")
    public Mono<ResponseEntity<Boolean>> deleteProperty(@PathVariable("propertyId") ULong propertyId) {

        return this.service.deletePropertyById(propertyId)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/findBaseClientCode/{applicationCode}")
    public Mono<ResponseEntity<Tuple2<String, Boolean>>> findBaseClientCodeForOverride(
            @PathVariable("applicationCode") String applicationCode) {
        return this.service.findBaseClientCodeForOverride(applicationCode)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/findAnyApps")
    public Mono<ResponseEntity<Page<App>>> findAnyApps(Pageable pageable, ServerHttpRequest request) {
        pageable = (pageable == null ? PageRequest.of(0, 10, Direction.ASC, PATH_VARIABLE_ID) : pageable);
        return this.service.findAnyAppsByPage(pageable, ConditionUtil.parameterMapToMap(request.getQueryParams()))
                .map(ResponseEntity::ok);
    }

    @GetMapping("/internal/dependencies")
    public Mono<List<String>> getInternalAppDependencies(@RequestParam String appCode) {
        return this.service.getAppDependencies(appCode)
                .map(dependencies -> dependencies.stream().map(AppDependency::getDependentAppCode)
                        .toList());
    }

    @GetMapping("/dependencies")
    public Mono<ResponseEntity<List<AppDependency>>> getAppDependencies(@RequestParam String appCode) {
        return this.service.getAppDependencies(appCode)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/dependency")
    public Mono<ResponseEntity<AppDependency>> addDependency(@RequestBody AppDependency dependency) {

        return this.service.addAppDependency(dependency.getAppCode(), dependency.getDependentAppCode())
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/dependency")
    public Mono<ResponseEntity<Boolean>> removeDependency(@RequestParam String appCode,
                                                          @RequestParam String dependencyCode) {
        return this.service.removeAppDependency(appCode, dependencyCode).map(ResponseEntity::ok);
    }

    @GetMapping("/clientHasReadAccess")
    public Mono<ResponseEntity<Map<String, Boolean>>> hasAccess(@RequestParam String[] appCodes) {
        return this.service.hasReadAccess(appCodes)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/internal/appStatus/{appCode}")
    public Mono<ResponseEntity<String>> getAppStatus(@PathVariable String appCode) {
        return this.service.getAppStatus(appCode)
                .map(ResponseEntity::ok);
    }
}
