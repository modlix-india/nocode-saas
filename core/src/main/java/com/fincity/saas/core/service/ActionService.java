package com.fincity.saas.core.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.core.document.Action;
import com.fincity.saas.core.repository.ActionRepository;

import reactor.core.publisher.Mono;

@Service
public class ActionService extends AbstractOverridableDataService<Action, ActionRepository> {

	protected ActionService() {
		super(Action.class);
	}

	@Override
	protected Mono<Action> updatableEntity(Action entity) {

		return flatMapMono(

		        () -> this.read(entity.getId()),

		        existing ->
				{
			        if (existing.getVersion() != entity.getVersion())
				        return this.messageResourceService.throwMessage(HttpStatus.PRECONDITION_FAILED,
				                AbstractMongoMessageResourceService.VERSION_MISMATCH);

			        existing.setFunctionName(entity.getFunctionName());
			        existing.setFunctionNamespace(entity.getFunctionNamespace());

			        existing.setVersion(existing.getVersion() + 1);

			        return Mono.just(existing);
		        });
	}
}
