package com.fincity.saas.commons.mongo.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.document.Schema;
import com.fincity.saas.commons.mongo.repository.SchemaRepository;

import reactor.core.publisher.Mono;

@Service
public class SchemaService extends AbstractOverridableDataServcie<Schema, SchemaRepository> {

	
	public SchemaService() {
		super(Schema.class);
	}


	@Override
	protected Mono<Schema> updatableEntity(Schema entity) {
		
		return flatMapMono(

		        () -> this.read(entity.getId()),

		        existing ->
				{
			        if (existing.getVersion() != entity.getVersion())
				        return this.messageResourceService.throwMessage(HttpStatus.PRECONDITION_FAILED,
				                AbstractMongoMessageResourceService.VERSION_MISMATCH);

			        existing.setDefinition(entity.getDefinition());
			        
			        existing.setVersion(existing.getVersion() + 1);

			        return Mono.just(existing);
		        });
	}
}
