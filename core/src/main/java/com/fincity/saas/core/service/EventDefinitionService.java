package com.fincity.saas.core.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.core.document.EventDefinition;
import com.fincity.saas.core.repository.EventDefinitionRepository;

import reactor.core.publisher.Mono;

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
				        return this.messageResourceService.throwMessage(HttpStatus.PRECONDITION_FAILED,
				                AbstractMongoMessageResourceService.VERSION_MISMATCH);

			        existing.setSchema(entity.getSchema());

			        existing.setVersion(existing.getVersion() + 1);

			        return Mono.just(existing);
		        });
	}

}
