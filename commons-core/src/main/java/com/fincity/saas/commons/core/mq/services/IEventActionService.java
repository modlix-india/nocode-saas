package com.fincity.saas.commons.core.mq.services;

import com.fincity.saas.commons.core.document.EventAction;
import com.fincity.saas.commons.core.model.EventActionTask;
import com.fincity.saas.commons.mq.events.EventQueObject;
import reactor.core.publisher.Mono;

public interface IEventActionService {
    Mono<Boolean> execute(EventAction action, EventActionTask task, EventQueObject queObject);
}
