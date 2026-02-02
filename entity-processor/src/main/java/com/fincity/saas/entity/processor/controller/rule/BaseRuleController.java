package com.fincity.saas.entity.processor.controller.rule;

import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.util.ConditionUtil;
import com.fincity.saas.entity.processor.controller.base.BaseUpdatableController;
import com.fincity.saas.entity.processor.dao.rule.BaseRuleDAO;
import com.fincity.saas.entity.processor.dto.rule.BaseRuleDto;
import com.fincity.saas.entity.processor.dto.rule.BaseUserDistributionDto;
import com.fincity.saas.entity.processor.eager.EagerUtil;
import com.fincity.saas.entity.processor.service.rule.BaseRuleService;
import java.util.List;
import java.util.Map;
import org.jooq.UpdatableRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

public abstract class BaseRuleController<
                R extends UpdatableRecord<R>,
                D extends BaseRuleDto<U, D>,
                O extends BaseRuleDAO<R, U, D>,
                U extends BaseUserDistributionDto<U>,
                S extends BaseRuleService<R, D, O, U>>
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

    @Override
    @GetMapping(EAGER_BASE)
    public Mono<ResponseEntity<Page<Map<String, Object>>>> readPageFilterEager(
            Pageable pageable, ServerHttpRequest request) {
        pageable = (pageable == null ? PageRequest.of(0, 10, Sort.Direction.DESC, PATH_VARIABLE_ID) : pageable);

        Tuple2<AbstractCondition, List<String>> fieldParams = EagerUtil.getFieldConditions(request.getQueryParams());
        return this.service
                .readPageFilterEager(pageable, fieldParams.getT1(), fieldParams.getT2(), request.getQueryParams())
                .flatMap(page -> this.service
                        .fillDetailsEager(page.getContent(), request.getQueryParams())
                        .thenReturn(page))
                .map(ResponseEntity::ok);
    }

    @Override
    @PostMapping(EAGER_PATH_QUERY)
    public Mono<ResponseEntity<Page<Map<String, Object>>>> readPageFilterEager(Query query, ServerHttpRequest request) {

        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(), query.getSort());

        MultiValueMap<String, String> queryParams = EagerUtil.addEagerParamsFromQuery(request.getQueryParams(), query);

        return this.service
                .readPageFilterEager(pageable, query.getCondition(), query.getFields(), queryParams)
                .flatMap(page -> this.service
                        .fillDetailsEager(page.getContent(), queryParams)
                        .thenReturn(page))
                .map(ResponseEntity::ok);
    }
}
