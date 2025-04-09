package com.fincity.saas.commons.core.mq.services;

import com.fincity.saas.commons.core.document.EventAction;
import com.fincity.saas.commons.core.model.EventActionTask;
import com.fincity.saas.commons.mq.events.EventQueObject;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class EventCallFunctionService implements IEventActionService {

    @Override
    public Mono<Boolean> execute(EventAction action, EventActionTask task, EventQueObject queObject) {
        return Mono.just(true);
    }
}
