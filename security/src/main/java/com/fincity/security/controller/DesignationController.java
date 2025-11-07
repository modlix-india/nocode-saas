package com.fincity.security.controller;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.saas.commons.util.ConditionUtil;
import com.fincity.security.dao.DesignationDAO;
import com.fincity.security.dto.Designation;
import com.fincity.security.jooq.tables.records.SecurityDesignationRecord;
import com.fincity.security.service.DesignationService;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/designations")
public class DesignationController
        extends AbstractJOOQUpdatableDataController<SecurityDesignationRecord, ULong, Designation, DesignationDAO, DesignationService> {

    @GetMapping()
    @Override
    public Mono<ResponseEntity<Page<Designation>>> readPageFilter(Pageable pageable, ServerHttpRequest request) {
        pageable = (pageable == null ? PageRequest.of(0, 10, Sort.Direction.ASC, PATH_VARIABLE_ID) : pageable);
        return this.service
                .readPageFilter(pageable, ConditionUtil.parameterMapToMap(request.getQueryParams()))
                .flatMap(page -> this.service
                        .fillDetails(page.getContent(), request.getQueryParams())
                        .thenReturn(page))
                .map(ResponseEntity::ok);
    }
}
