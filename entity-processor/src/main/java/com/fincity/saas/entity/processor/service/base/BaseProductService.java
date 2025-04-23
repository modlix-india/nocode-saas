package com.fincity.saas.entity.processor.service.base;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.entity.processor.dao.base.BaseProductDAO;
import com.fincity.saas.entity.processor.dto.base.BaseProductDto;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public abstract class BaseProductService<
                R extends UpdatableRecord<R>, D extends BaseProductDto<D>, O extends BaseProductDAO<R, D>>
        extends BaseService<R, D, O> {

    @Override
    protected Mono<D> updatableEntity(D entity) {
        return FlatMapUtil.flatMapMono(() -> super.updatableEntity(entity), e -> {
            if (e.isValidChild(entity.getParentLevel0(), entity.getParentLevel1()))
                return this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                        ProcessorMessageResourceService.INVALID_CHILD_FOR_PARENT);

            e.setParentLevel0(entity.getParentLevel0());
            e.setParentLevel1(entity.getParentLevel1());

            return Mono.just(e);
        });
    }

    @Override
    protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

        if (fields == null || key == null) return Mono.just(new HashMap<>());

        return super.updatableFields(key, fields).flatMap(f -> {
            f.remove(BaseProductDto.Fields.productId);

            return Mono.just(f);
        });
    }

    protected Flux<String> getAllByProduct(ULong appId, ULong clientId, ULong productId, Boolean onlyParent) {
        return Flux.empty();
    }

    protected Mono<Map<String, Set<String>>> getAllByProduct(ULong appId, ULong clientId, ULong productId) {
        return Mono.empty();
    }
}
