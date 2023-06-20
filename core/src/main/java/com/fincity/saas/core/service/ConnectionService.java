package com.fincity.saas.core.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.core.document.Connection;
import com.fincity.saas.core.enums.ConnectionType;
import com.fincity.saas.core.repository.ConnectionRepository;

import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ConnectionService extends AbstractOverridableDataService<Connection, ConnectionRepository> {

	private static final String CACHE_NAME_CONNECTION = "connCache";

	@Autowired(required = false)
	@Qualifier("pubRedisAsyncCommand")
	private RedisPubSubAsyncCommands<String, String> pubAsyncCommand;

	@Value("${redis.connection.eviction.channel:connectionChannel}")
	private String channel;

	protected ConnectionService() {
		super(Connection.class);
	}

	@Override
	public Mono<Connection> create(Connection entity) {
		return super.create(entity).flatMap(this::makeOtherNotDefault)
		        .flatMap(e -> cacheService
		                .evict(CACHE_NAME_CONNECTION, e.getAppCode(), ":", e.getClientCode(), ":",
		                        e.getConnectionType())
		                .map(b -> e));
	}

	private Mono<Connection> makeOtherNotDefault(Connection conn) {

		if (!BooleanUtil.safeValueOf(conn.getDefaultConnection()))
			return Mono.just(conn);

		Criteria criteria = new Criteria().andOperator(Criteria.where("id")
		        .ne(conn.getId()),
		        Criteria.where("appCode")
		                .is(conn.getAppCode()),
		        Criteria.where("clientCode")
		                .is(conn.getClientCode()),
		        Criteria.where("connectionType")
		                .is(conn.getConnectionType()));

		return this.mongoTemplate
		        .updateMulti(Query.query(criteria), Update.update("defaultConnection", Boolean.FALSE), pojoClass)
		        .map(e -> conn);
	}

	@Override
	public Mono<Connection> update(Connection entity) {
		return super.update(entity).flatMap(this::makeOtherNotDefault)
		        .flatMap(e -> cacheService
		                .evict(CACHE_NAME_CONNECTION, e.getAppCode(), ":", e.getClientCode(), ":",
		                        e.getConnectionType())
		                .map(b -> e))
		        .flatMap(e ->
				{

			        if (pubAsyncCommand == null)
				        return Mono.just(e);

			        return Mono
			                .fromCompletionStage(
			                        pubAsyncCommand.publish(this.channel, "Connection : " + entity.getId()))
			                .map(x -> e);
		        });
	}

	@Override
	public Mono<Boolean> delete(String id) {

		return super.delete(id).flatMap(e -> {

			if (pubAsyncCommand == null)
				return Mono.just(e);

			return Mono.fromCompletionStage(pubAsyncCommand.publish(this.channel, "Connection : " + id))
			        .map(x -> e);
		});
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

			        existing.setConnectionSubType(entity.getConnectionSubType());
			        existing.setConnectionDetails(entity.getConnectionDetails());
			        existing.setVersion(existing.getVersion() + 1);

			        return Mono.just(existing);
		        })
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "ConnectionService.updatableEntity"));
	}

	public Mono<Connection> find(String appCode, String clientCode, ConnectionType type) {
		return this.find(null, appCode, clientCode, type);
	}

	public Mono<Connection> find(String name, String appCode, String clientCode, ConnectionType type) {

		return cacheService.cacheEmptyValueOrGet(CACHE_NAME_CONNECTION,

		        () -> this.readAllFilter(new ComplexCondition().setOperator(ComplexConditionOperator.AND)
		                .setConditions(List.of(

		                        new FilterCondition().setField("appCode")
		                                .setValue(appCode)
		                                .setOperator(FilterConditionOperator.EQUALS),
		                        new FilterCondition().setField("clientCode")
		                                .setValue(clientCode)
		                                .setOperator(FilterConditionOperator.EQUALS),
		                        new FilterCondition().setField("connectionType")
		                                .setValue(type.toString())
		                                .setOperator(FilterConditionOperator.EQUALS)

						)))
		                .collectList()
		                .flatMap(cons ->
						{

			                if (cons.isEmpty())
				                return Mono.empty();

			                Connection finCon = null;
			                Connection defaultCon = null;
			                for (Connection conn : cons) {

				                if (StringUtil.safeEquals(name, conn.getName())) {
					                return Mono.just(conn);
				                }

				                if (defaultCon == null && BooleanUtil.safeValueOf(conn.getDefaultConnection()))
					                defaultCon = conn;

				                if (finCon == null || CommonsUtil
				                        .nonNullValue(finCon.getUpdatedAt(), finCon.getCreatedAt())
				                        .isAfter(CommonsUtil.nonNullValue(conn.getUpdatedAt(), conn.getCreatedAt())))
					                finCon = conn;
			                }

			                return Mono.justOrEmpty(CommonsUtil.nonNullValue(defaultCon, finCon));
		                }),
		        appCode, ":", clientCode, ":", type);
	}
}
