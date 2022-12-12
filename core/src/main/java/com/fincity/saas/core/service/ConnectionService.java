package com.fincity.saas.core.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataServcie;
import com.fincity.saas.core.document.Connection;
import com.fincity.saas.core.repository.ConnectionRepository;

import reactor.core.publisher.Mono;

@Service
public class ConnectionService extends AbstractOverridableDataServcie<Connection, ConnectionRepository> {

	protected ConnectionService() {
		super(Connection.class);
	}

	@Override
	protected Mono<Connection> updatableEntity(Connection entity) {

		return flatMapMono(

		        () -> this.read(entity.getId()),

		        existing ->
				{
			        if (existing.getVersion() != entity.getVersion())
				        return this.messageResourceService.throwMessage(HttpStatus.PRECONDITION_FAILED,
				                AbstractMongoMessageResourceService.VERSION_MISMATCH);

			        existing.setConnectionDetails(entity.getConnectionDetails());
			        existing.setVersion(existing.getVersion() + 1);

			        return Mono.just(existing);
		        });
	}
}
