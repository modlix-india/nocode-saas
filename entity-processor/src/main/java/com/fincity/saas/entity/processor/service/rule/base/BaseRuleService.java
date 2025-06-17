package com.fincity.saas.entity.processor.service.rule.base;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.entity.processor.dao.rule.base.BaseRuleDAO;
import com.fincity.saas.entity.processor.dto.rule.base.BaseRule;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import org.jooq.UpdatableRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public abstract class BaseRuleService<R extends UpdatableRecord<R>, D extends BaseRule<D>, O extends BaseRuleDAO<R, D>>
        extends BaseUpdatableService<R, D, O> {

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
        return super.hasAccess().flatMap(access -> this.createInternal(access, entity));
    }

    public Mono<D> createPublic(D entity) {
        return super.hasPublicAccess().flatMap(access -> this.createInternal(access, entity));
    }

    protected Mono<D> createInternal(ProcessorAccess access, D entity) {
        entity.setAppCode(access.getAppCode());
        entity.setClientCode(access.getClientCode());

        entity.setCreatedBy(access.getUserId());

        return super.create(entity);
    }
}
