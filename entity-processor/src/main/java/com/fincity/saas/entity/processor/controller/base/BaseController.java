package com.fincity.saas.entity.processor.controller.base;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.saas.entity.processor.dao.base.BaseDAO;
import com.fincity.saas.entity.processor.dto.base.BaseDto;
import com.fincity.saas.entity.processor.model.base.BaseResponse;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.service.base.BaseService;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import reactor.core.publisher.Mono;

public abstract class BaseController<
                R extends UpdatableRecord<R>,
                D extends BaseDto<D>,
                O extends BaseDAO<R, D>,
                S extends BaseService<R, D, O>>
        extends AbstractJOOQUpdatableDataController<R, ULong, D, O, S> {

    public static final String PATH_BASE = "/base";
    public static final String PATH_VARIABLE_CODE = "code";

    public static final String PATH_CODE = "/code/{" + PATH_VARIABLE_CODE + "}";
    public static final String PATH_BASE_CODE = PATH_BASE + PATH_CODE;
    public static final String PATH_BASE_ID = PATH_BASE + PATH_ID;

    public static final String REQ_PATH = "/req";
    public static final String REQ_PATH_ID = REQ_PATH + "/{" + PATH_VARIABLE_ID + "}";

    @GetMapping(PATH_CODE)
    public Mono<ResponseEntity<D>> getByCode(@PathVariable(PATH_VARIABLE_CODE) final String code) {
        return this.service
                .readByCode(code)
                .map(ResponseEntity::ok)
                .switchIfEmpty(
                        Mono.defer(() -> Mono.just(ResponseEntity.notFound().build())));
    }

    @GetMapping(PATH_BASE_ID)
    public Mono<ResponseEntity<BaseResponse>> getBaseResponseById(@PathVariable(PATH_VARIABLE_ID) final ULong id) {
        return this.service
                .getBaseResponse(id)
                .map(ResponseEntity::ok)
                .switchIfEmpty(
                        Mono.defer(() -> Mono.just(ResponseEntity.notFound().build())));
    }

    @GetMapping(PATH_BASE_CODE)
    public Mono<ResponseEntity<BaseResponse>> getBaseResponseByCode(
            @PathVariable(PATH_VARIABLE_CODE) final String code) {
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
    public Mono<Integer> delete(@PathVariable(PATH_VARIABLE_CODE) final String code) {
        return this.service.deleteByCode(code);
    }

    @GetMapping(REQ_PATH_ID)
    public Mono<ResponseEntity<D>> getFromRequest(@PathVariable(PATH_VARIABLE_ID) Identity identity) {
        return this.service.readIdentity(identity).map(ResponseEntity::ok);
    }

    @DeleteMapping(REQ_PATH_ID)
    @ResponseStatus(value = HttpStatus.NO_CONTENT)
    public Mono<Integer> deleteFromRequest(@PathVariable(PATH_VARIABLE_ID) Identity identity) {
        return this.service.deleteIdentity(identity);
    }
}
