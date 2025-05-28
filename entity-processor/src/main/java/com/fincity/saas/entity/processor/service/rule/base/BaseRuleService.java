package com.fincity.saas.entity.processor.service.rule.base;

import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.entity.processor.dao.rule.base.BaseRuleDAO;
import com.fincity.saas.entity.processor.dto.rule.base.BaseRule;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.base.BaseService;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;

@Service
public abstract class BaseRuleService<R extends UpdatableRecord<R>, D extends BaseRule<D>, O extends BaseRuleDAO<R, D>>
        extends BaseService<R, D, O> {

    @Override
    protected Mono<D> updatableEntity(D entity) {
        return super.updatableEntity(entity).flatMap(existing -> {
            if (existing.getVersion() != entity.getVersion())
                return this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                        ProcessorMessageResourceService.VERSION_MISMATCH);

            existing.setVersion(existing.getVersion() + 1);
            return Mono.just(existing);
        });
    }

    @Override
    public Mono<D> create(D entity) {
        return super.hasAccess().flatMap(hasAccess -> this.createInternal(entity, hasAccess));
    }

    public Mono<D> createPublic(D entity) {
        return super.hasPublicAccess().flatMap(hasAccess -> this.createInternal(entity, hasAccess));
    }

    protected Mono<D> createInternal(D entity, Tuple2<Tuple3<String, String, ULong>, Boolean> hasAccess) {
        entity.setAppCode(hasAccess.getT1().getT1());
        entity.setClientCode(hasAccess.getT1().getT2());

        entity.setCreatedBy(hasAccess.getT1().getT3());

        return super.create(entity);
    }
}
