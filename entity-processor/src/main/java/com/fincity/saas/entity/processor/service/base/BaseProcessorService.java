package com.fincity.saas.entity.processor.service.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.entity.processor.dao.base.BaseProcessorDAO;
import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import org.jooq.UpdatableRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public abstract class BaseProcessorService<
                R extends UpdatableRecord<R>, D extends BaseProcessorDto<D>, O extends BaseProcessorDAO<R, D>>
        extends BaseService<R, D, O> {

    @Override
    protected Mono<D> updatableEntity(D entity) {
        return FlatMapUtil.flatMapMono(() -> super.updatableEntity(entity), e -> {
            if (e.getVersion() != entity.getVersion())
                return this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                        AbstractMongoMessageResourceService.VERSION_MISMATCH);

            e.setCurrentUserId(entity.getCurrentUserId());
            e.setStage(entity.getStage());
            e.setStatus(entity.getStatus());

            e.setVersion(e.getVersion() + 1);

            return Mono.just(e);
        });
    }

    public Mono<D> create(D entity, String appCode, String clientCode) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> {
                    if (!ca.isAuthenticated())
                        return msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                ProcessorMessageResourceService.INVALID_USER_FOR_CLIENT);

                    return this.hasAccess(appCode, clientCode, ca.getUser().getId());
                },
                (ca, hasAccess) -> {
                    entity.setAppCode(hasAccess.getT1().getT1());
                    entity.setClientCode(hasAccess.getT1().getT2());
                    entity.setAddedByUserId(hasAccess.getT1().getT3());

                    return super.create(entity);
                });
    }

    public Mono<D> update(D entity, String appCode, String clientCode) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> {
                    if (!ca.isAuthenticated())
                        return msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                ProcessorMessageResourceService.INVALID_USER_FOR_CLIENT);

                    return this.hasAccess(appCode, clientCode, ca.getUser().getId());
                },
                (ca, hasAccess) -> super.create(entity));
    }
}
