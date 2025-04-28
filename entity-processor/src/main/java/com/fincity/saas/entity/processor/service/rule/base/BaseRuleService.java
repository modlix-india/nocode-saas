package com.fincity.saas.entity.processor.service.rule.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.entity.processor.dao.rule.base.BaseRuleDAO;
import com.fincity.saas.entity.processor.dto.rule.base.BaseRule;
import com.fincity.saas.entity.processor.service.base.BaseService;
import java.util.HashMap;
import java.util.Map;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public abstract class BaseRuleService<R extends UpdatableRecord<R>, D extends BaseRule<D>, O extends BaseRuleDAO<R, D>>
        extends BaseService<R, D, O> {

    @Override
    protected Mono<D> updatableEntity(D entity) {
        return FlatMapUtil.flatMapMono(() -> super.updatableEntity(entity), e -> {
            if (e.getVersion() != entity.getVersion())
                return this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                        AbstractMongoMessageResourceService.VERSION_MISMATCH);

            e.setVersion(e.getVersion() + 1);
            return Mono.just(e);
        });
    }

    @Override
    protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

        if (fields == null || key == null) return Mono.just(new HashMap<>());

        return super.updatableFields(key, fields).flatMap(f -> {
            f.remove(BaseRule.Fields.ruleId);
            return Mono.just(f);
        });
    }
}
