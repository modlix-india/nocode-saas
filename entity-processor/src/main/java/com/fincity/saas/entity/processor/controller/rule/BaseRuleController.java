package com.fincity.saas.entity.processor.controller.rule;

import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.util.ConditionUtil;
import com.fincity.saas.entity.processor.controller.base.BaseUpdatableController;
import com.fincity.saas.entity.processor.dao.rule.BaseRuleDAO;
import com.fincity.saas.entity.processor.dao.rule.BaseUserDistributionDAO;
import com.fincity.saas.entity.processor.dto.rule.BaseRuleDto;
import com.fincity.saas.entity.processor.dto.rule.BaseUserDistributionDto;
import com.fincity.saas.entity.processor.service.rule.BaseRuleService;
import org.jooq.UpdatableRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import reactor.core.publisher.Mono;

public abstract class BaseRuleController<
                R extends UpdatableRecord<R>,
                D extends BaseRuleDto<U, D>,
                O extends BaseRuleDAO<R, U, D>,
                T extends UpdatableRecord<T>,
                U extends BaseUserDistributionDto<U>,
                P extends BaseUserDistributionDAO<T, U>,
                S extends BaseRuleService<R, D, O, T, U, P>>
        extends BaseUpdatableController<R, D, O, S> {

    @Override
    @PostMapping("/noMapping")
    public Mono<ResponseEntity<Page<D>>> readPageFilter(Query query) {
        return Mono.just(ResponseEntity.badRequest().build());
    }

    @Override
    @GetMapping()
    public Mono<ResponseEntity<Page<D>>> readPageFilter(Pageable pageable, ServerHttpRequest request) {
        pageable = (pageable == null ? PageRequest.of(0, 10, Sort.Direction.ASC, PATH_VARIABLE_ID) : pageable);
        return this.service
                .readPageFilter(pageable, ConditionUtil.parameterMapToMap(request.getQueryParams()))
                .flatMap(page -> this.service
                        .fillDetails(page.getContent(), request.getQueryParams())
                        .thenReturn(page))
                .map(ResponseEntity::ok);
    }

    @PostMapping(PATH_QUERY)
    public Mono<ResponseEntity<Page<D>>> readPageFilter(@RequestBody Query query, ServerHttpRequest request) {

        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(), query.getSort());

        return this.service
                .readPageFilter(pageable, query.getCondition())
                .flatMap(page -> this.service
                        .fillDetails(page.getContent(), request.getQueryParams())
                        .thenReturn(page))
                .map(ResponseEntity::ok);
    }
}
