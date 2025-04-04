package com.fincity.saas.commons.core.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import com.fincity.saas.commons.core.document.Action;
import com.fincity.saas.commons.core.repository.ActionRepository;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.util.LogUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ActionService extends AbstractOverridableDataService<Action, ActionRepository> {

    protected ActionService() {
        super(Action.class);
    }

    @Override
    protected Mono<Action> updatableEntity(Action entity) {

        return flatMapMono(() -> this.read(entity.getId()), existing -> {
                    if (existing.getVersion() != entity.getVersion())
                        return this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                                AbstractMongoMessageResourceService.VERSION_MISMATCH);

                    existing.setFunctionName(entity.getFunctionName());
                    existing.setFunctionNamespace(entity.getFunctionNamespace());

                    existing.setVersion(existing.getVersion() + 1);

                    return Mono.just(existing);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ActionService.updatableEntity"));
    }
}
