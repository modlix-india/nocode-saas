package com.fincity.security.controller;

import com.fincity.security.service.ProfileService;
import jakarta.ws.rs.PathParam;
import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.security.dao.RoleV2DAO;
import com.fincity.security.dto.RoleV2;
import com.fincity.security.jooq.tables.records.SecurityV2RoleRecord;
import com.fincity.security.service.RoleV2Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Stream;

@RestController
@RequestMapping("api/security/rolev2")
public class RoleV2Controller
        extends AbstractJOOQUpdatableDataController<SecurityV2RoleRecord, ULong, RoleV2, RoleV2DAO, RoleV2Service> {

    private final ProfileService profileService;

    public RoleV2Controller(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/assignable/{appCode}")
    public Mono<ResponseEntity<List<RoleV2>>> assignableRoles(@PathVariable(required = false) String appCode) {

        return Mono.zip(this.service.getRolesForAssignmentInApp(appCode),
                        this.profileService.getRolesForAssignmentInApp(appCode))
                .map(tup -> {
                    if (tup.getT1().isEmpty()) return tup.getT2();
                    if (tup.getT2().isEmpty()) return tup.getT1();
                    return Stream.concat(tup.getT1().stream(), tup.getT2().stream()).toList();
                })
                .flatMap(this.service::fetchSubRolesAlso)
                .map(ResponseEntity::ok);
    }
}
