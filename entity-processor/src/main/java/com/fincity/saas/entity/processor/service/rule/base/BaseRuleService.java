package com.fincity.saas.entity.processor.service.rule.base;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.entity.processor.dao.rule.base.BaseRuleDAO;
import com.fincity.saas.entity.processor.dto.rule.base.BaseRule;
import com.fincity.saas.entity.processor.service.base.BaseService;
import org.jooq.UpdatableRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public abstract class BaseRuleService<R extends UpdatableRecord<R>, D extends BaseRule<D>, O extends BaseRuleDAO<R, D>>
        extends BaseService<R, D, O> {

    @Override
    protected Mono<D> updatableEntity(D entity) {
        return super.updatableEntity(entity).flatMap(existing -> {
            if (existing.getVersion() != entity.getVersion())
                return this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                        AbstractMongoMessageResourceService.VERSION_MISMATCH);

            existing.setVersion(existing.getVersion() + 1);
            return Mono.just(existing);
        });
    }
}
