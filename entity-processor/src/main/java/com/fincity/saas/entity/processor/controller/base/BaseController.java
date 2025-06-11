package com.fincity.saas.entity.processor.controller.base;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.model.base.BaseResponse;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import com.fincity.saas.entity.processor.util.EagerUtil;
import java.util.List;
import java.util.Map;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple4;

public abstract class BaseController<
                R extends UpdatableRecord<R>,
                D extends BaseUpdatableDto<D>,
                O extends BaseUpdatableDAO<R, D>,
                S extends BaseUpdatableService<R, D, O>>
        extends AbstractJOOQUpdatableDataController<R, ULong, D, O, S> {

    public static final String PATH_BASE = "/base";
    public static final String PATH_VARIABLE_CODE = "code";

    public static final String PATH_CODE = "/code/{" + PATH_VARIABLE_CODE + "}";
    public static final String PATH_BASE_CODE = PATH_BASE + PATH_CODE;
    public static final String PATH_BASE_ID = PATH_BASE + PATH_ID;

    public static final String REQ_PATH = "/req";
    public static final String REQ_PATH_ID = REQ_PATH + "/{" + PATH_VARIABLE_ID + "}";

    public static final String EAGER = "/eager";
    public static final String EAGER_PATH_QUERY = EAGER + "/query";
    public static final String PATH_CODE_EAGER = "/code/{" + PATH_VARIABLE_CODE + "}" + EAGER;

    public static final String PATH_ID_EAGER = PATH_ID + EAGER;

    @GetMapping(PATH_CODE)
    public Mono<ResponseEntity<D>> read(@PathVariable(PATH_VARIABLE_CODE) final String code) {
        return this.service
                .read(code)
                .map(ResponseEntity::ok)
                .switchIfEmpty(
                        Mono.defer(() -> Mono.just(ResponseEntity.notFound().build())));
    }

    @GetMapping(PATH_CODE_EAGER)
    public Mono<ResponseEntity<Map<String, Object>>> readEager(
            @PathVariable(PATH_VARIABLE_CODE) final String code, ServerHttpRequest request) {

        Boolean eager = EagerUtil.getIsEagerParams(request.getQueryParams());
        List<String> eagerParams = EagerUtil.getEagerParams(request.getQueryParams());
        List<String> fieldParams = EagerUtil.getFieldParams(request.getQueryParams());

        return this.service
                .readEager(code, fieldParams, eager, eagerParams)
                .map(ResponseEntity::ok)
                .switchIfEmpty(
                        Mono.defer(() -> Mono.just(ResponseEntity.notFound().build())));
    }

    @GetMapping(PATH_ID_EAGER)
    public Mono<ResponseEntity<Map<String, Object>>> readEager(
            @PathVariable(PATH_VARIABLE_ID) final ULong id, ServerHttpRequest request) {

        Boolean eager = EagerUtil.getIsEagerParams(request.getQueryParams());
        List<String> eagerParams = EagerUtil.getEagerParams(request.getQueryParams());
        List<String> fieldParams = EagerUtil.getFieldParams(request.getQueryParams());

        return this.service
                .readEager(id, fieldParams, eager, eagerParams)
                .map(ResponseEntity::ok)
                .switchIfEmpty(
                        Mono.defer(() -> Mono.just(ResponseEntity.notFound().build())));
    }

    @GetMapping(PATH_BASE_ID)
    public Mono<ResponseEntity<BaseResponse>> getBaseResponse(@PathVariable(PATH_VARIABLE_ID) final ULong id) {
        return this.service
                .getBaseResponse(id)
                .map(ResponseEntity::ok)
                .switchIfEmpty(
                        Mono.defer(() -> Mono.just(ResponseEntity.notFound().build())));
    }

    @GetMapping(PATH_BASE_CODE)
    public Mono<ResponseEntity<BaseResponse>> getBaseResponse(@PathVariable(PATH_VARIABLE_CODE) final String code) {
        return this.service
                .getBaseResponse(code)
                .map(ResponseEntity::ok)
                .switchIfEmpty(
                        Mono.defer(() -> Mono.just(ResponseEntity.notFound().build())));
    }

    @PutMapping(PATH_CODE)
    public Mono<ResponseEntity<D>> updateByCode(
            @PathVariable(PATH_VARIABLE_CODE) final String code, @RequestBody D entity) {
        return this.service.updateByCode(code, entity).map(ResponseEntity::ok);
    }

    @DeleteMapping(PATH_CODE)
    @ResponseStatus(value = HttpStatus.NO_CONTENT)
    public Mono<Integer> deleteByCode(@PathVariable(PATH_VARIABLE_CODE) final String code) {
        return this.service.deleteByCode(code);
    }

    @GetMapping(REQ_PATH_ID)
    public Mono<ResponseEntity<D>> readIdentity(@PathVariable(PATH_VARIABLE_ID) Identity identity) {
        return this.service.readIdentity(identity).map(ResponseEntity::ok);
    }

    @DeleteMapping(REQ_PATH_ID)
    @ResponseStatus(value = HttpStatus.NO_CONTENT)
    public Mono<Integer> deleteIdentity(@PathVariable(PATH_VARIABLE_ID) Identity identity) {
        return this.service.deleteIdentity(identity);
    }

    @GetMapping(EAGER)
    public Mono<ResponseEntity<Page<Map<String, Object>>>> readPageFilterEager(
            Pageable pageable, ServerHttpRequest request) {
        pageable = (pageable == null ? PageRequest.of(0, 10, Sort.Direction.DESC, PATH_VARIABLE_ID) : pageable);

        Tuple4<AbstractCondition, List<String>, Boolean, List<String>> eagerParams =
                EagerUtil.getEagerConditions(request.getQueryParams());
        return this.service
                .readPageFilterEager(
                        pageable, eagerParams.getT1(), eagerParams.getT2(), eagerParams.getT3(), eagerParams.getT4())
                .map(ResponseEntity::ok);
    }

    @PostMapping(EAGER_PATH_QUERY)
    public Mono<ResponseEntity<Page<Map<String, Object>>>> readPageFilterEager(@RequestBody Query query) {

        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(), query.getSort());

        return this.service
                .readPageFilterEager(
                        pageable, query.getCondition(), query.getFields(), query.getEager(), query.getEagerFields())
                .map(ResponseEntity::ok);
    }
}
