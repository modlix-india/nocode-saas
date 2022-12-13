package com.fincity.saas.core.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataServcie;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.core.document.Connection;
import com.fincity.saas.core.repository.ConnectionRepository;

import reactor.core.publisher.Mono;

@Service
public class ConnectionService extends AbstractOverridableDataServcie<Connection, ConnectionRepository> {

	protected ConnectionService() {
		super(Connection.class);
	}

	@Override
	public Mono<Connection> create(Connection entity) {
		return super.create(entity).flatMap(this::makeOtherNotDefault);
	}

	private Mono<Connection> makeOtherNotDefault(Connection created) {

		if (!BooleanUtil.safeValueOf(created.getDefaultConnection()))
			return Mono.just(created);

		Criteria criteria = new Criteria().andOperator(Criteria.where("id")
		        .ne(created.getId()),
		        Criteria.where("appCode")
		                .is(created.getAppCode()),
		        Criteria.where("clientCode")
		                .is(created.getClientCode()));

		return this.mongoTemplate
		        .updateMulti(Query.query(criteria), Update.update("defaultConnection", Boolean.FALSE), pojoClass)
		        .map(e -> created);
	}

	@Override
	public Mono<Connection> update(Connection entity) {
		return super.update(entity).flatMap(this::makeOtherNotDefault);
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
