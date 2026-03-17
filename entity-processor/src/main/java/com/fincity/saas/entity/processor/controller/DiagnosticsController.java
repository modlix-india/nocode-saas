package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.ConditionUtil;
import com.fincity.saas.entity.processor.constant.BusinessPartnerConstant;
import com.fincity.saas.entity.processor.dto.DiagnosticsLog;
import com.fincity.saas.entity.processor.service.DiagnosticsService;
import com.fincity.saas.entity.processor.service.MethodUsageTracker;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/diagnostics")
public class DiagnosticsController {

    private final DiagnosticsService service;
    private final MethodUsageTracker usageTracker;

    public DiagnosticsController(DiagnosticsService service, MethodUsageTracker usageTracker) {
        this.service = service;
        this.usageTracker = usageTracker;
    }

    @GetMapping
    public Mono<ResponseEntity<Page<DiagnosticsLog>>> readPageFilter(
            Pageable pageable, ServerHttpRequest request) {

        pageable = (pageable == null ? PageRequest.of(0, 10, Sort.Direction.DESC, "id") : pageable);

        AbstractCondition condition = ConditionUtil.parameterMapToMap(request.getQueryParams());

        Pageable finalPageable = pageable;
        return this.service
                .hasAccess()
                .flatMap(access -> {
                    if (!SecurityContextUtil.hasAuthority(
                            BusinessPartnerConstant.OWNER_ROLE,
                            access.getUser().getAuthorities()))
                        return Mono.error(new GenericException(
                                HttpStatus.FORBIDDEN, "Only Owner role can access diagnostics"));

                    return this.service.readPageFiltered(access, finalPageable, condition);
                })
                .map(ResponseEntity::ok);
    }

    @GetMapping("/methodUsage")
    public Mono<ResponseEntity<Map<String, Object>>> getMethodUsage() {
        return this.service
                .hasAccess()
                .flatMap(access -> {
                    if (!SecurityContextUtil.hasAuthority(
                            BusinessPartnerConstant.OWNER_ROLE,
                            access.getUser().getAuthorities()))
                        return Mono.error(new GenericException(
                                HttpStatus.FORBIDDEN, "Only Owner role can access diagnostics"));

                    return Mono.just(ResponseEntity.ok(usageTracker.getReport()));
                });
    }

    @DeleteMapping("/methodUsage")
    public Mono<ResponseEntity<Void>> resetMethodUsage() {
        return this.service
                .hasAccess()
                .flatMap(access -> {
                    if (!SecurityContextUtil.hasAuthority(
                            BusinessPartnerConstant.OWNER_ROLE,
                            access.getUser().getAuthorities()))
                        return Mono.error(new GenericException(
                                HttpStatus.FORBIDDEN, "Only Owner role can access diagnostics"));

                    usageTracker.reset();
                    return Mono.just(ResponseEntity.ok().<Void>build());
                });
    }
}
