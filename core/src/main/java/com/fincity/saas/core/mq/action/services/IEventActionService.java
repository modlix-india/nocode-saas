package com.fincity.saas.core.mq.action.services;

import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.core.document.EventAction;
import com.fincity.saas.core.model.EventActionTask;

import reactor.core.publisher.Mono;

public interface IEventActionService {

	public Mono<Boolean> execute(EventAction action, EventActionTask task, EventQueObject queObject);
}
