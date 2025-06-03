package com.fincity.saas.entity.processor.controller.base;

import com.fincity.saas.entity.processor.dao.base.BaseValueDAO;
import com.fincity.saas.entity.processor.dto.base.BaseValueDto;
import com.fincity.saas.entity.processor.enums.Platform;
import com.fincity.saas.entity.processor.enums.StageType;
import com.fincity.saas.entity.processor.model.response.BaseValueResponse;
import com.fincity.saas.entity.processor.service.base.BaseValueService;
import java.util.List;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

public abstract class BaseValueController<
                R extends UpdatableRecord<R>,
                D extends BaseValueDto<D>,
                O extends BaseValueDAO<R, D>,
                S extends BaseValueService<R, D, O>>
        extends BaseController<R, D, O, S> {

    public static final String PATH_VALUES = "/values";
    public static final String PATH_VALUES_ORDERED = "/values/ordered";

    @GetMapping(PATH_VALUES)
    public Mono<ResponseEntity<List<BaseValueResponse<D>>>> getAllValues(
            @RequestParam(required = false, defaultValue = "PRE_QUALIFICATION") Platform platform,
            @RequestParam(required = false) ULong productTemplateId) {

        return this.service
                .getAllValues(platform, productTemplateId)
                .map(ResponseEntity::ok)
                .switchIfEmpty(
                        Mono.defer(() -> Mono.just(ResponseEntity.notFound().build())));
    }

    @GetMapping(PATH_VALUES_ORDERED)
    public Mono<ResponseEntity<List<BaseValueResponse<D>>>> getValuesInOrder(
            @RequestParam(required = false, defaultValue = "PRE_QUALIFICATION") Platform platform,
            @RequestParam(required = false) ULong productTemplateId) {

        return this.service
                .getAllValuesInOrder(platform, productTemplateId)
                .map(ResponseEntity::ok)
                .switchIfEmpty(
                        Mono.defer(() -> Mono.just(ResponseEntity.notFound().build())));
    }
}
