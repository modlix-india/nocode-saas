package com.fincity.security.controller.appregistration;

import java.beans.PropertyEditorSupport;

import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.DataBinder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.security.dto.AppRegistrationAccess;
import com.fincity.security.dto.AppRegistrationFile;
import com.fincity.security.dto.AppRegistrationPackage;
import com.fincity.security.dto.AppRegistrationRole;
import com.fincity.security.enums.ClientLevelType;
import com.fincity.security.service.appregistration.AppRegistrationService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/applications/reg")
public class AppRegistrationController {

    private AppRegistrationService service;

    @InitBinder
    public void initBinder(DataBinder binder) {
        binder.registerCustomEditor(ULong.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                if (text == null)
                    setValue(null);
                setValue(ULong.valueOf(text));
            }
        });
    }

    public AppRegistrationController(AppRegistrationService service) {
        this.service = service;
    }

    @PostMapping("/{appCode}/access")
    public Mono<ResponseEntity<AppRegistrationAccess>> createAccess(@PathVariable("appCode") String appCode,
            @RequestBody AppRegistrationAccess access) {
        return service.createAccess(appCode, access).map(ResponseEntity::ok);
    }

    @GetMapping("/{appCode}/access")
    public Mono<ResponseEntity<Page<AppRegistrationAccess>>> getAccess(@PathVariable("appCode") String appCode,
            @RequestParam(required = false) String clientCode,
            @RequestParam(required = false) ULong clientId,
            @RequestParam(required = false) String clientType,
            @RequestParam(required = false) ClientLevelType level,
            @RequestParam(required = false) String businessType,
            Pageable pageable) {

        return service.getAccess(appCode, clientCode, clientId, clientType, level, businessType, pageable)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/access/{id}")
    public Mono<ResponseEntity<AppRegistrationAccess>> getAccessById(
            @PathVariable("id") ULong id) {
        return service.getAccessById(id).map(ResponseEntity::ok);
    }

    @DeleteMapping("/access/{id}")
    public Mono<ResponseEntity<Boolean>> deleteAccess(
            @PathVariable("id") ULong id) {
        return service.deleteAccess(id).map(ResponseEntity::ok);
    }

    @PostMapping("/{appCode}/file")
    public Mono<ResponseEntity<AppRegistrationFile>> createRole(@PathVariable("appCode") String appCode,
            @RequestBody AppRegistrationFile file) {
        return service.createFile(appCode, file).map(ResponseEntity::ok);
    }

    @GetMapping("/{appCode}/file")
    public Mono<ResponseEntity<Page<AppRegistrationFile>>> getFile(@PathVariable("appCode") String appCode,
            @RequestParam(required = false) String clientCode,
            @RequestParam(required = false) ULong clientId,
            @RequestParam(required = false) String clientType,
            @RequestParam(required = false) ClientLevelType level,
            @RequestParam(required = false) String businessType,
            Pageable pageable) {

        return service.getFile(appCode, clientCode, clientId, clientType, level, businessType, pageable)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/file/{id}")
    public Mono<ResponseEntity<AppRegistrationFile>> getFileById(@PathVariable("id") ULong id) {
        return service.getFileById(id).map(ResponseEntity::ok);
    }

    @DeleteMapping("/file/{id}")
    public Mono<ResponseEntity<Boolean>> deleteFile(@PathVariable("id") ULong id) {
        return service.deleteFile(id).map(ResponseEntity::ok);
    }

    @PostMapping("/{appCode}/package")
    public Mono<ResponseEntity<AppRegistrationPackage>> createPackage(@PathVariable("appCode") String appCode,
            @RequestBody AppRegistrationPackage regPackage) {
        return service.createPackage(appCode, regPackage).map(ResponseEntity::ok);
    }

    @GetMapping("/{appCode}/package")
    public Mono<ResponseEntity<Page<AppRegistrationPackage>>> getPackage(@PathVariable("appCode") String appCode,
            @RequestParam(required = false) String packageName,
            @RequestParam(required = false) String clientCode,
            @RequestParam(required = false) ULong clientId,
            @RequestParam(required = false) String clientType,
            @RequestParam(required = false) ClientLevelType level,
            @RequestParam(required = false) String businessType,
            Pageable pageable) {

        return service.getPackage(appCode, packageName, clientCode, clientId, clientType, level, businessType, pageable)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/package/{id}")
    public Mono<ResponseEntity<AppRegistrationPackage>> getPackageById(@PathVariable("id") ULong id) {
        return service.getPackageById(id).map(ResponseEntity::ok);
    }

    @DeleteMapping("/package/{id}")
    public Mono<ResponseEntity<Boolean>> deletePackage(@PathVariable("id") ULong id) {
        return service.deletePackage(id).map(ResponseEntity::ok);
    }

    @PostMapping("/{appCode}/role")
    public Mono<ResponseEntity<AppRegistrationRole>> createRole(@PathVariable("appCode") String appCode,
            @RequestBody AppRegistrationRole role) {
        return service.createRole(appCode, role).map(ResponseEntity::ok);
    }

    @GetMapping("/{appCode}/role")
    public Mono<ResponseEntity<Page<AppRegistrationRole>>> getRole(@PathVariable("appCode") String appCode,
            @RequestParam(required = false) String roleName,
            @RequestParam(required = false) String clientCode,
            @RequestParam(required = false) ULong clientId,
            @RequestParam(required = false) String clientType,
            @RequestParam(required = false) ClientLevelType level,
            @RequestParam(required = false) String businessType,
            Pageable pageable) {

        return service.getRole(appCode, roleName, clientCode, clientId, clientType, level, businessType, pageable)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/role/{id}")
    public Mono<ResponseEntity<AppRegistrationRole>> getRoleById(@PathVariable("id") ULong id) {
        return service.getRoleById(id).map(ResponseEntity::ok);
    }

    @DeleteMapping("/role/{id}")
    public Mono<ResponseEntity<Boolean>> deleteRole(@PathVariable("id") ULong id) {
        return service.deleteRole(id).map(ResponseEntity::ok);
    }
}
