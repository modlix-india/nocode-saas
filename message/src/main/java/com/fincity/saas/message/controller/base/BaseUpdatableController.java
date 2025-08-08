package com.fincity.saas.message.controller.base;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.saas.message.dao.base.BaseUpdatableDAO;
import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.model.base.BaseResponse;
import com.fincity.saas.message.model.common.Identity;
import com.fincity.saas.message.service.base.BaseUpdatableService;
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

public abstract class BaseUpdatableController<
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

    @GetMapping(PATH_CODE)
    public Mono<ResponseEntity<D>> read(@PathVariable(PATH_VARIABLE_CODE) final String code) {
        return this.service
                .read(code)
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
    public Mono<ResponseEntity<D>> readIdentity(@PathVariable(PATH_VARIABLE_ID) final Identity identity) {
        return this.service.readIdentityWithAccess(identity).map(ResponseEntity::ok);
    }

    @DeleteMapping(REQ_PATH_ID)
    @ResponseStatus(value = HttpStatus.NO_CONTENT)
    public Mono<Integer> deleteIdentity(@PathVariable(PATH_VARIABLE_ID) final Identity identity) {
        return this.service.deleteIdentity(identity);
    }
}
