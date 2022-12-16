package com.fincity.saas.core.service.connection.appdata;

import java.util.Map;

import javax.annotation.PostConstruct;

import org.bson.BsonObjectId;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.kirun.engine.json.schema.validator.SchemaValidator;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.SchemaService;
import com.fincity.saas.commons.mongo.util.BJsonUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.core.document.Connection;
import com.fincity.saas.core.document.Storage;
import com.fincity.saas.core.model.DataObject;
import com.fincity.saas.core.service.CoreMessageResourceService;
import com.fincity.saas.core.service.StorageService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mongodb.client.model.Filters;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;

import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class MongoAppDataService extends RedisPubSubAdapter<String, String> implements IAppDataService {

	@Autowired
	private MongoClient defaultClient;

	@Autowired
	private CoreMessageResourceService msgService;

	private Map<String, MongoClient> mongoClients;

	@Autowired(required = false)
	@Qualifier("subRedisAsyncCommand")
	private RedisPubSubAsyncCommands<String, String> subAsyncCommand;

	@Autowired(required = false)
	private StatefulRedisPubSubConnection<String, String> subConnect;

	@Value("${redis.connection.eviction.channel:connectionChannel}")
	private String channel;

	@Autowired
	private StorageService storageService;

	@Autowired
	private SchemaService schemaService;

	@PostConstruct
	private void init() {

		if (subAsyncCommand == null || subConnect == null)
			return;

		subAsyncCommand.subscribe(channel);
		subConnect.addListener(this);
	}

	@Override
	public Mono<Map<String, Object>> create(Connection conn, Storage storage, DataObject dataObject) {

		if (storage == null)
			return msgService.throwMessage(HttpStatus.NOT_FOUND, CoreMessageResourceService.STORAGE_NOT_FOUND);

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> Mono.just(SecurityContextUtil.hasAuthority(storage.getCreateAuth(), ca.getUser()
		                .getAuthorities())),

		        (ca, hasAccess) -> this.createWithoutAuth(conn, storage, dataObject))

		        .switchIfEmpty(Mono.defer(() -> this.msgService.throwMessage(HttpStatus.FORBIDDEN,
		                CoreMessageResourceService.FORBIDDEN_CREATE_STORAGE, storage.getName())));
	}

	public Mono<Map<String, Object>> createWithoutAuth(Connection conn, Storage storage, DataObject dataObject) {

		return FlatMapUtil.flatMapMonoWithNull(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> storageService.getSchema(storage),

		        (ca, schema) -> Mono.fromCallable(() ->
				{

			        JsonObject job = (new Gson()).toJsonTree(dataObject.getData())
			                .getAsJsonObject();
			        return (JsonObject) SchemaValidator.validate(null, schema,
			                schemaService.getSchemaRepository(storage.getAppCode(), storage.getClientCode()), job);
		        })
		                .subscribeOn(Schedulers.boundedElastic()),

		        (ca, schema, je) -> Mono.from(this.getCollection(conn, storage)
		                .insertOne(BJsonUtil.from(je))),

		        (ca, schema, je, result) -> Mono.from(this.getCollection(conn, storage)
		                .find(Filters.eq("_id", result.getInsertedId()))
		                .first()),

		        (ca, scheme, je, result, doc) ->
				{
			        doc.remove("_id");
			        doc.append("_id", result.getInsertedId()
			                .asObjectId()
			                .getValue()
			                .toHexString());
			        return Mono.just(doc);
		        });
	}

	@Override
	public Mono<Map<String, Object>> update(Connection conn, Storage storage, DataObject dataObject) {

		if (storage == null)
			return msgService.throwMessage(HttpStatus.NOT_FOUND, CoreMessageResourceService.STORAGE_NOT_FOUND);

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> Mono.just(SecurityContextUtil.hasAuthority(storage.getUpdateAuth(), ca.getUser()
		                .getAuthorities())),

		        (ca, hasAccess) -> this.updateWithoutAuth(conn, storage, dataObject))

		        .switchIfEmpty(Mono.defer(() -> this.msgService.throwMessage(HttpStatus.FORBIDDEN,
		                CoreMessageResourceService.FORBIDDEN_UPDATE_STORAGE, storage.getName())));
	}

	public Mono<Map<String, Object>> updateWithoutAuth(Connection conn, Storage storage, DataObject dataObject) {

		String key = StringUtil.safeValueOf(dataObject.getData()
		        .get("_id"));

		if (StringUtil.safeIsBlank(key))
			return this.msgService.throwMessage(HttpStatus.NOT_FOUND,
			        AbstractMongoMessageResourceService.OBJECT_NOT_FOUND_TO_UPDATE, storage.getName(), key);

		BsonObjectId objectId = new BsonObjectId(new ObjectId(key));
		
		return FlatMapUtil.flatMapMonoWithNull(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> storageService.getSchema(storage),

		        (ca, schema) -> Mono.fromCallable(() ->
				{

			        JsonObject job = (new Gson()).toJsonTree(dataObject.getData())
			                .getAsJsonObject();
			        job.remove("_id");
			        return (JsonObject) SchemaValidator.validate(null, schema,
			                schemaService.getSchemaRepository(storage.getAppCode(), storage.getClientCode()), job);
		        })
		                .subscribeOn(Schedulers.boundedElastic()),

		        (ca, schema, je) -> Mono.from(this.getCollection(conn, storage)
		                .replaceOne(Filters.eq("_id", objectId), BJsonUtil.from(je))),

		        (ca, schema, je, result) -> Mono.from(this.getCollection(conn, storage)
		                .find(Filters.eq("_id", objectId))
		                .first()),

		        (ca, scheme, je, result, doc) ->
				{
			        doc.remove("_id");
			        doc.append("_id", key);
			        return Mono.just(doc);
		        });
	}

	@Override
	public Mono<Map<String, Object>> read(Connection conn, Storage storage, String id) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Mono<Page<Map<String, Object>>> readPage(Connection conn, Storage storage, Pageable page,
	        AbstractCondition condition) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Mono<Boolean> delete(Connection conn, Storage storage, String id) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void message(String channel, String message) {

		if (channel == null || !channel.equals(this.channel))
			return;

		this.mongoClients.remove(message);
	}

	private MongoCollection<Document> getCollection(Connection conn, Storage storage) {
		MongoClient client = conn == null ? defaultClient
		        : mongoClients.computeIfAbsent("Connection : " + conn.getId(), key -> this.getMongoClient(conn));

		if (client == null)
			throw msgService.nonReactiveMessage(HttpStatus.NOT_FOUND,
			        CoreMessageResourceService.CONNECTION_DETAILS_MISSING, "url");

		return client.getDatabase(storage.getClientCode() + "_" + storage.getAppCode())
		        .getCollection(storage.getUniqueName());
	}

	private MongoCollection<Document> getAuditCollection(Connection conn, Storage storage) {
		MongoClient client = conn == null ? defaultClient
		        : mongoClients.computeIfAbsent("Connection : " + conn.getId(), key -> this.getMongoClient(conn));

		if (client == null)
			throw msgService.nonReactiveMessage(HttpStatus.NOT_FOUND,
			        CoreMessageResourceService.CONNECTION_DETAILS_MISSING, "url");

		return client.getDatabase(storage.getClientCode() + "_" + storage.getAppCode())
		        .getCollection(storage.getUniqueName() + "_audit");
	}

	private MongoCollection<Document> getVersionCollection(Connection conn, Storage storage) {
		MongoClient client = conn == null ? defaultClient
		        : mongoClients.computeIfAbsent("Connection : " + conn.getId(), key -> this.getMongoClient(conn));

		if (client == null)
			throw msgService.nonReactiveMessage(HttpStatus.NOT_FOUND,
			        CoreMessageResourceService.CONNECTION_DETAILS_MISSING, "url");

		return client.getDatabase(storage.getClientCode() + "_" + storage.getAppCode())
		        .getCollection(storage.getUniqueName() + "_version");
	}

	private synchronized MongoClient getMongoClient(Connection conn) {

		if (conn.getConnectionDetails() == null || StringUtil.safeIsBlank(conn.getConnectionDetails()
		        .get("url")))
			throw msgService.nonReactiveMessage(HttpStatus.NOT_FOUND,
			        CoreMessageResourceService.CONNECTION_DETAILS_MISSING, "url");

		return MongoClients.create(conn.getConnectionDetails()
		        .get("url")
		        .toString());
	}
}
