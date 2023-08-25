package com.fincity.saas.core.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.core.document.EventDefinition;
import com.fincity.saas.core.repository.EventDefinitionRepository;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class EventDefinitionService extends AbstractOverridableDataService<EventDefinition, EventDefinitionRepository> {

	protected EventDefinitionService() {
		super(EventDefinition.class);
	}

	@Override
	protected Mono<EventDefinition> updatableEntity(EventDefinition entity) {

		return FlatMapUtil.flatMapMono(

		        () -> this.read(entity.getId()),

		        existing ->
				{
			        if (existing.getVersion() != entity.getVersion())
				        return this.messageResourceService.throwMessage(
				                msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
				                AbstractMongoMessageResourceService.VERSION_MISMATCH);

			        existing.setSchema(entity.getSchema());

			        existing.setVersion(existing.getVersion() + 1);

			        return Mono.just(existing);
		        })
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "EventDefinitionService.updatableEntity"));
	}

}
