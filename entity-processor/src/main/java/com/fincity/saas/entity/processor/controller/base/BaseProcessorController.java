package com.fincity.saas.entity.processor.controller.base;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQDataController;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.util.ConditionUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.dao.base.BaseProcessorDAO;
import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.eager.EagerUtil;
import com.fincity.saas.entity.processor.service.base.BaseProcessorService;
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

public abstract class BaseProcessorController<
                R extends UpdatableRecord<R>,
                D extends BaseProcessorDto<D>,
                O extends BaseProcessorDAO<R, D>,
                S extends BaseProcessorService<R, D, O>>
        extends BaseUpdatableController<R, D, O, S> {

    public static final String TIMEZONE_HEADER = "x-tmz";

    @Override
    @GetMapping()
    public Mono<ResponseEntity<Page<D>>> readPageFilter(Pageable pageable, ServerHttpRequest request) {
        String timezone = this.extractTimezone(request);
        pageable = (pageable == null
                ? PageRequest.of(0, 10, Sort.Direction.DESC, AbstractJOOQDataController.PATH_VARIABLE_ID)
                : pageable);

        AbstractCondition condition = ConditionUtil.parameterMapToMap(request.getQueryParams());
        return StringUtil.safeIsBlank(timezone)
                ? this.service.readPageFilter(pageable, condition).map(ResponseEntity::ok)
                : this.service.readPageFilter(pageable, condition, timezone).map(ResponseEntity::ok);
    }

    @PostMapping("/noMapping")
    @Override
    public Mono<ResponseEntity<Page<D>>> readPageFilter(Query query) {
        return Mono.just(ResponseEntity.badRequest().build());
    }

    @PostMapping(AbstractJOOQDataController.PATH_QUERY)
    public Mono<ResponseEntity<Page<D>>> readPageFilter(@RequestBody Query query, ServerHttpRequest request) {
        String timezone = this.extractTimezone(request);
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(), query.getSort());

        return StringUtil.safeIsBlank(timezone)
                ? this.service.readPageFilter(pageable, query.getCondition()).map(ResponseEntity::ok)
                : this.service
                        .readPageFilter(pageable, query.getCondition(), timezone)
                        .map(ResponseEntity::ok);
    }

    @Override
    @GetMapping(EAGER_BASE)
    public Mono<ResponseEntity<Page<Map<String, Object>>>> readPageFilterEager(
            Pageable pageable, ServerHttpRequest request) {
        String timezone = this.extractTimezone(request);
        pageable = (pageable == null
                ? PageRequest.of(0, 10, Sort.Direction.DESC, AbstractJOOQDataController.PATH_VARIABLE_ID)
                : pageable);

        Tuple2<AbstractCondition, List<String>> fieldParams = EagerUtil.getFieldConditions(request.getQueryParams());
        return StringUtil.safeIsBlank(timezone)
                ? this.service
                        .readPageFilterEager(
                                pageable, fieldParams.getT1(), fieldParams.getT2(), request.getQueryParams())
                        .map(ResponseEntity::ok)
                : this.service
                        .readPageFilterEager(
                                pageable, fieldParams.getT1(), fieldParams.getT2(), timezone, request.getQueryParams())
                        .map(ResponseEntity::ok);
    }

    @Override
    @PostMapping(EAGER_PATH_QUERY)
    public Mono<ResponseEntity<Page<Map<String, Object>>>> readPageFilterEager(
            @RequestBody Query query, ServerHttpRequest request) {
        String timezone = this.extractTimezone(request);
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(), query.getSort());
        MultiValueMap<String, String> queryParams = EagerUtil.addEagerParamsFromQuery(request.getQueryParams(), query);

        return StringUtil.safeIsBlank(timezone)
                ? this.service
                        .readPageFilterEager(
                                pageable,
                                query.getCondition(),
                                query.getFields(),
                                null,
                                queryParams,
                                query.getSubQueryCondition())
                        .map(ResponseEntity::ok)
                : this.service
                        .readPageFilterEager(
                                pageable,
                                query.getCondition(),
                                query.getFields(),
                                timezone,
                                queryParams,
                                query.getSubQueryCondition())
                        .map(ResponseEntity::ok);
    }

    private String extractTimezone(ServerHttpRequest request) {
        return request.getHeaders().getFirst(TIMEZONE_HEADER);
    }
}
