package com.fincity.saas.core.mq.action.services;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.core.document.EventAction;
import com.fincity.saas.core.model.EventActionTask;

import reactor.core.publisher.Mono;

@Service
public class EventCallFunctionService implements IEventActionService {

	@Override
	public Mono<Boolean> execute(EventAction action, EventActionTask task, EventQueObject queObject) {

		return Mono.just(true);
	}

}
