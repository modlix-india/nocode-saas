package com.fincity.saas.commons.mongo.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.kirun.engine.Repository;
import com.fincity.saas.commons.mongo.document.Schema;
import com.fincity.saas.commons.mongo.repository.SchemaRepository;

import reactor.core.publisher.Mono;

@Service
public class SchemaService extends AbstractOverridableDataServcie<Schema, SchemaRepository> {

	private static final String CACHE_NAME_SCHEMA_REPO = "cacheSchemaRepo";

	private Map<String, Repository<com.fincity.nocode.kirun.engine.json.schema.Schema>> schemas = new HashMap<>();

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

	public Repository<com.fincity.nocode.kirun.engine.json.schema.Schema> getSchemaRepository(String appCode,
	        String clientCode) {

		return schemas.computeIfAbsent(appCode + " - " + clientCode, key -> (namespace, name) ->

		cacheService
		        .cacheValueOrGet(CACHE_NAME_SCHEMA_REPO, () -> read(namespace + "." + name, appCode, clientCode),
		                appCode, clientCode, namespace + "." + name)
		        .map(s -> objectMapper.convertValue(s.getDefinition(),
		                com.fincity.nocode.kirun.engine.json.schema.Schema.class))
		        .block());
	}
}
