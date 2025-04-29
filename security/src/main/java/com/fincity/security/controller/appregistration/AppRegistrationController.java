package com.fincity.security.controller.appregistration;

import java.beans.PropertyEditorSupport;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.DataBinder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.security.dto.appregistration.AbstractAppRegistration;
import com.fincity.security.enums.AppRegistrationObjectType;
import com.fincity.security.model.AppRegistrationQuery;
import com.fincity.security.service.appregistration.AppRegistrationServiceV2;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/applications/reg")
public class AppRegistrationController {

    private final AppRegistrationServiceV2 service;
    private final ObjectMapper mapper;

    @InitBinder
    public void initBinder(DataBinder binder) {
        binder.registerCustomEditor(ULong.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(text == null ? null : ULong.valueOf(text));
            }
        });
    }

    public AppRegistrationController(AppRegistrationServiceV2 service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping("/{appCode}/{urlPart}")
    public Mono<ResponseEntity<AbstractAppRegistration>> create(@PathVariable String appCode,
                                                                @PathVariable String urlPart,
                                                                @RequestBody Map<String, Object> entity) {

        AppRegistrationObjectType type = AppRegistrationObjectType.fromUrlPart(urlPart);
        if (type == null) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        AbstractAppRegistration appRegistration = this.mapper.convertValue(entity, type.pojoClass);

        return this.service.create(type, appCode, appRegistration).map(ResponseEntity::ok);
    }

    @PostMapping("/{appCode}/{urlPart}/query")
    public Mono<ResponseEntity<Page<? extends AbstractAppRegistration>>> getAccess(
            @PathVariable String appCode,
            @PathVariable String urlPart,
            @RequestBody AppRegistrationQuery query) {

        AppRegistrationObjectType type = AppRegistrationObjectType.fromUrlPart(urlPart);
        if (type == null) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        Pageable pageable = PageRequest.of(query.getPage(), query.getSize());

        return this.service
                .get(type, appCode, query.getClientCode(), query.getClientId(), query.getClientType(), query.getLevel(),
                        query.getBusinessType(), pageable)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{urlPart}/{id}")
    public Mono<ResponseEntity<Boolean>> delete(@PathVariable String urlPart, @PathVariable ULong id) {
        AppRegistrationObjectType type = AppRegistrationObjectType.fromUrlPart(urlPart);
        if (type == null) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        return service.delete(type, id).map(ResponseEntity::ok);
    }
}
