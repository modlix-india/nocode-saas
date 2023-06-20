package com.fincity.saas.core.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.core.document.EventAction;
import com.fincity.saas.core.repository.EventActionRepository;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class EventActionService extends AbstractOverridableDataService<EventAction, EventActionRepository> {

	protected EventActionService() {
		super(EventAction.class);
	}

	@Override
	protected Mono<EventAction> updatableEntity(EventAction entity) {
		
		return FlatMapUtil.flatMapMono(

		        () -> this.read(entity.getId()),

		        existing ->
				{
			        if (existing.getVersion() != entity.getVersion())
				        return this.messageResourceService.throwMessage(HttpStatus.PRECONDITION_FAILED,
				                AbstractMongoMessageResourceService.VERSION_MISMATCH);

			        existing.setTasks(entity.getTasks());

			        existing.setVersion(existing.getVersion() + 1);

			        return Mono.just(existing);
		        }).contextWrite(Context.of(LogUtil.METHOD_NAME, "EventActionService.updatableEntity"));
	}

}
