package com.fincity.saas.core.service.connection.appdata;

import static com.fincity.saas.commons.model.condition.FilterConditionOperator.BETWEEN;
import static com.fincity.saas.commons.model.condition.FilterConditionOperator.IN;
import static com.fincity.saas.commons.model.condition.FilterConditionOperator.IS_FALSE;
import static com.fincity.saas.commons.model.condition.FilterConditionOperator.IS_NULL;
import static com.fincity.saas.commons.model.condition.FilterConditionOperator.IS_TRUE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.bson.BsonDateTime;
import org.bson.BsonInt64;
import org.bson.BsonObjectId;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.validator.reactive.ReactiveSchemaValidator;
import com.fincity.nocode.kirun.engine.reactive.ReactiveHybridRepository;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.kirun.engine.repository.reactive.KIRunReactiveSchemaRepository;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.util.BJsonUtil;
import com.fincity.saas.commons.mongo.util.DifferenceApplicator;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.core.document.Connection;
import com.fincity.saas.core.document.Storage;
import com.fincity.saas.core.document.Storage.StorageIndex;
import com.fincity.saas.core.kirun.repository.CoreSchemaRepository;
import com.fincity.saas.core.model.DataObject;
import com.fincity.saas.core.model.StorageRelation;
import com.fincity.saas.core.service.CoreMessageResourceService;
import com.fincity.saas.core.service.CoreSchemaService;
import com.fincity.saas.core.service.StorageService;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;

import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class MongoAppDataService extends RedisPubSubAdapter<String, String> implements IAppDataService {

	private static final String OBJECT = "object";

	private static final String ID = "_id";

	private static final String CREATED_AT = "createdAt";

	private static final String CREATED_BY = "createdBy";

	private static final String OBJECT_ID = "objectId";

	private static final String MESSAGE = "message";

	private static final String OPERATION = "operation";

	private static final String TEXT_INDEX_NAME = "_textIndex";

	private final Map<String, MongoClient> mongoClients = new HashMap<>();

	@Autowired(required = false) // NOSONAR
	@Qualifier("subRedisAsyncCommand")
	private RedisPubSubAsyncCommands<String, String> subAsyncCommand;

	@Autowired(required = false) // NOSONAR
	private StatefulRedisPubSubConnection<String, String> subConnect;

	@Value("${redis.connection.eviction.channel:connectionChannel}")
	private String channel;

	private final StorageService storageService;
	private final CoreSchemaService schemaService;
	private final CacheService cacheService;
	private final MongoClient defaultClient;
	private final CoreMessageResourceService msgService;
	private final Gson gson;

	private static final Map<FilterConditionOperator, String> FILTER_MATCH_OPERATOR = Map.of(
			FilterConditionOperator.EQUALS, "$eq",
			FilterConditionOperator.GREATER_THAN, "$gt",
			FilterConditionOperator.GREATER_THAN_EQUAL, "$gte",
			FilterConditionOperator.LESS_THAN, "$lt",
			FilterConditionOperator.LESS_THAN_EQUAL, "$lte",
			FilterConditionOperator.LIKE, "$regex",
			FilterConditionOperator.STRING_LOOSE_EQUAL, "$regex");

	public MongoAppDataService(StorageService storageService, CoreSchemaService schemaService,
			CacheService cacheService, CoreMessageResourceService msgService, Gson gson, MongoClient defaultClient) {
		this.storageService = storageService;
		this.schemaService = schemaService;
		this.cacheService = cacheService;
		this.msgService = msgService;
		this.gson = gson;
		this.defaultClient = defaultClient;
	}

	@PostConstruct
	public void init() {

		if (subAsyncCommand == null || subConnect == null)
			return;

		subAsyncCommand.subscribe(channel);
		subConnect.addListener(this);
	}

	@PreDestroy
	public void closeClients() {
		if (this.defaultClient != null)
			this.defaultClient.close();
		this.mongoClients.values().stream().filter(Objects::nonNull).forEach(MongoClient::close);
		this.mongoClients.clear();
	}

	@Override
	public void message(String channel, String message) {

		if (channel == null || !channel.equals(this.channel))
			return;

		MongoClient client = this.mongoClients.remove(message);

		if (client != null)
			client.close();
	}

	private <T> Mono<T> mongoObjectNotFound(String messageId, Object... params) {
		return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg), messageId, params);
	}

	@Override
	public Mono<Map<String, Object>> create(Connection conn, Storage storage, DataObject dataObject) {

		return FlatMapUtil.flatMapMonoWithNull(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> storageService.getSchema(storage),

				(ca, schema) -> schemaService.getSchemaRepository(storage.getAppCode(), storage.getClientCode()),

				(ca, schema, appSchemaRepo) -> this.handleRelationsAndValidate(dataObject, storage, schema,
						appSchemaRepo),

				(ca, schema, appSchemaRepo, je) -> this
						.getCollection(conn, storage.getAppCode(), ca.getUrlClientCodeOrElse(storage.getIsAppLevel()),
								storage.getUniqueName(), storage.getIndexes(), storage.getTextIndexFields())
						.flatMap(collection -> Mono.from(collection.insertOne(BJsonUtil.from(
								storage.getRelations() != null ? storage.getRelations().keySet() : Set.of(), je)))),

				(ca, schema, appSchemaRepo, je, result) -> this
						.getCollection(conn, storage.getAppCode(), ca.getUrlClientCodeOrElse(storage.getIsAppLevel()),
								storage.getUniqueName(), storage.getIndexes(), storage.getTextIndexFields())
						.flatMap(collection -> Mono
								.from(collection.find(Filters.eq(ID, result.getInsertedId())).first())),

				(ca, schema, appSchemaRepo, je, result, doc) -> {

					if (!BooleanUtil.safeValueOf(storage.getIsAudited())
							&& !BooleanUtil.safeValueOf(storage.getIsVersioned()))
						return Mono.empty();

					Document versionDocument = new Document();
					versionDocument.append(OBJECT_ID, result.getInsertedId());
					versionDocument.append(MESSAGE, dataObject.getMessage());
					versionDocument.append(CREATED_AT, new BsonDateTime(System.currentTimeMillis()));
					versionDocument.append(OPERATION, "CREATE");
					if (ca.getUser() != null)
						versionDocument.append(CREATED_BY, new BsonInt64(ca.getUser().getId().longValue()));
					if (BooleanUtil.safeValueOf(storage.getIsVersioned()))
						versionDocument.append(OBJECT, new Document(dataObject.getData()));

					return this
							.getVersionCollection(conn, storage.getAppCode(),
									ca.getUrlClientCodeOrElse(storage.getIsAppLevel()), storage.getUniqueName())
							.flatMap(collection -> Mono.from(collection.insertOne(versionDocument)));
				},

				(ca, schema, appSchemaRepo, je, result, doc, versionResult) -> {
					doc.remove(ID);
					BsonValue insertedId = result.getInsertedId();
					if (insertedId != null)
						doc.append(ID, insertedId.asObjectId().getValue().toHexString());
					return Mono.just((Map<String, Object>) doc);
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "MongoAppDataService.create"));
	}

	@Override
	public Mono<Map<String, Object>> update(Connection conn, Storage storage, DataObject dataObject, Boolean override) {

		// TODO: Added boolean override to differentiate between the incoming request in
		// patch/put

		String key = StringUtil.safeValueOf(dataObject.getData().get(ID));

		if (StringUtil.safeIsBlank(key))
			return this.mongoObjectNotFound(AbstractMongoMessageResourceService.OBJECT_NOT_FOUND_TO_UPDATE,
					storage.getName(), key);

		if (!ObjectId.isValid(key))
			return Mono.just(Map.of());

		BsonObjectId objectId = new BsonObjectId(new ObjectId(key));

		return FlatMapUtil.flatMapMonoWithNull(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> storageService.getSchema(storage),

				(ca, schema) -> schemaService.getSchemaRepository(storage.getAppCode(), storage.getClientCode()),

				(ca, schema, appSchemaRepo) -> {

					Map<String, Object> overridableObject = dataObject.getData();
					overridableObject.remove(ID);

					if (BooleanUtil.safeValueOf(override))
						return Mono.justOrEmpty(overridableObject);

					return FlatMapUtil.flatMapMono(

							() -> this.getCollection(conn, storage.getAppCode(),
									ca.getUrlClientCodeOrElse(storage.getIsAppLevel()),
									storage.getUniqueName(), storage.getIndexes(), storage.getTextIndexFields())
									.flatMap(collection -> Mono.from(
											collection.find(Filters.eq(ID, objectId)).first()))
									.map(orgDoc -> this.removeBisonKey(orgDoc, ID)),

							originalDocument -> DifferenceApplicator.apply(originalDocument, overridableObject))
							.contextWrite(Context.of(LogUtil.METHOD_NAME, "MongoAppDataService.update"));
				},

				(ca, schema, appSchemaRepo, overridableObject) -> this.handleRelationsAndValidate(dataObject, storage,
						schema, appSchemaRepo),

				(ca, schema, appSchemaRepo, overridableObject, je) -> this
						.getCollection(conn, storage.getAppCode(), ca.getUrlClientCodeOrElse(storage.getIsAppLevel()),
								storage.getUniqueName(), storage.getIndexes(), storage.getTextIndexFields())
						.flatMap(collection -> Mono.from(collection
								.replaceOne(Filters.eq(ID, objectId),
										BJsonUtil.from(storage.getRelations() != null ? storage.getRelations().keySet()
												: Set.of(), je)))),

				(ca, schema, appSchemaRepo, overridableObject, je, result) -> this
						.getCollection(conn, storage.getAppCode(), ca.getUrlClientCodeOrElse(storage.getIsAppLevel()),
								storage.getUniqueName(), storage.getIndexes(), storage.getTextIndexFields())
						.flatMap(collection -> Mono.from(collection.find(Filters.eq(ID, objectId)).first())),

				(ca, scheme, appSchemaRepo, overridableObject, je, result, doc) -> {

					if (!BooleanUtil.safeValueOf(storage.getIsAudited())
							&& !BooleanUtil.safeValueOf(storage.getIsVersioned()))
						return Mono.empty();

					return this.addVersion(conn, storage, dataObject, objectId, ca, doc);
				},

				(ca, scheme, appSchemaRepo, overridableObject, je, result, doc, versionResult) -> {
					doc.append(ID, key);
					return Mono.just(this.updateDocWithIds(storage, doc));
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "MongoAppDataService.update"));
	}

	private Mono<JsonObject> handleRelationsAndValidate(DataObject dataObject, Storage storage, Schema schema,
			ReactiveRepository<Schema> appSchemaRepo) {

		JsonObject job = this.gson.toJsonTree(dataObject.getData())
				.getAsJsonObject();

		Map<String, JsonElement> relations = new HashMap<>();
		if (storage.getRelations() != null && !storage.getRelations().isEmpty())
			storage.getRelations().forEach((key, relation) -> {
				if (job.has(key))
					relations.put(key, job.remove(key));
			});

		return ReactiveSchemaValidator
				.validate(null, schema,
						new ReactiveHybridRepository<>(new KIRunReactiveSchemaRepository(), new CoreSchemaRepository(),
								appSchemaRepo),
						job)
				.map(JsonElement::getAsJsonObject)
				.map(validatedJsonObject -> {
					relations.forEach(validatedJsonObject::add);
					return validatedJsonObject;
				});
	}

	private Mono<InsertOneResult> addVersion(Connection conn, Storage storage, DataObject dataObject,
			BsonObjectId objectId, ContextAuthentication ca, Document doc) {

		Document versionDocument = new Document();
		versionDocument.append(OBJECT_ID, objectId);
		versionDocument.append(MESSAGE, dataObject.getMessage());
		versionDocument.append(CREATED_AT, new BsonDateTime(System.currentTimeMillis()));
		versionDocument.append(OPERATION, "UPDATE");
		if (ca.getUser() != null)
			versionDocument.append(CREATED_BY, new BsonInt64(ca.getUser()
					.getId()
					.longValue()));
		doc.remove(ID); // removing id from the document
		if (BooleanUtil.safeValueOf(storage.getIsVersioned()))
			versionDocument.append(OBJECT, new Document(doc));

		return this
				.getVersionCollection(conn, storage.getAppCode(), ca.getUrlClientCodeOrElse(storage.getIsAppLevel()),
						storage.getUniqueName())
				.flatMap(collection -> Mono.from(collection.insertOne(versionDocument)));
	}

	@Override
	public Mono<Map<String, Object>> read(Connection conn, Storage storage, String id) {

		if (ObjectId.isValid(id))
			return this.mongoObjectNotFound(AbstractMongoMessageResourceService.OBJECT_NOT_FOUND,
					storage.getName(), id);

		BsonObjectId objectId = new BsonObjectId(new ObjectId(id));

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this
						.getCollection(conn, storage.getAppCode(), ca.getUrlClientCodeOrElse(storage.getIsAppLevel()),
								storage.getUniqueName(), storage.getIndexes(), storage.getTextIndexFields()),

				(ca, collection) -> Mono.from(collection.find(Filters.eq(ID, objectId)).first()))
				.map(doc -> this.convertBisonIds(storage, doc, Boolean.FALSE))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "MongoAppDataService.read"))
				.switchIfEmpty(this.mongoObjectNotFound(AbstractMongoMessageResourceService.OBJECT_NOT_FOUND,
						storage.getName(), id));
	}

	@Override
	public Mono<Page<Map<String, Object>>> readPage(Connection conn, Storage storage, Query query) {

		Pageable page = query.getPageable();
		AbstractCondition condition = query.getCondition();
		Boolean count = query.getCount();

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.filter(storage, condition),

				(ca, bsonCondition) -> this.getCollection(conn, storage.getAppCode(),
						ca.getUrlClientCodeOrElse(storage.getIsAppLevel()), storage.getUniqueName(),
						storage.getIndexes(), storage.getTextIndexFields()),

				(ca, bsonCondition, collection) -> this.applyQueryOnElements(collection, query, bsonCondition, page)
						.map(doc -> this.convertBisonIds(storage, doc, Boolean.FALSE)).collectList(),

				(ca, bsonCondition, collection, list) -> BooleanUtil.safeValueOf(count)
						? Mono.from(collection.countDocuments(bsonCondition))
						: Mono.just(page.getOffset() + list.size()),

				(ca, bsonCondition, collection, list, cnt) -> Mono
						.just(PageableExecutionUtils.getPage(list, page, cnt::longValue)))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "MongoAppDataService.readPage"));
	}

	@Override
	public Flux<Map<String, Object>> readPageAsFlux(Connection conn, Storage storage, Query query) {

		Pageable page = query.getPageable();
		AbstractCondition condition = query.getCondition();

		return SecurityContextUtil.getUsersContextAuthentication()
				.flatMapMany(ca -> this.filter(storage, condition)
						.flatMapMany(bsonCondition -> this.getCollection(conn, storage.getAppCode(),
								ca.getUrlClientCodeOrElse(storage.getIsAppLevel()), storage.getUniqueName(),
								storage.getIndexes(), storage.getTextIndexFields())
								.flatMapMany(
										collection -> this.applyQueryOnElements(collection, query, bsonCondition, page)
												.map(doc -> this.convertBisonIds(storage, doc, Boolean.FALSE)))));
	}

	@Override
	public Mono<Boolean> delete(Connection conn, Storage storage, String id) {

		if (ObjectId.isValid(id))
			return this.mongoObjectNotFound(AbstractMongoMessageResourceService.OBJECT_NOT_FOUND,
					storage.getName(), id);

		BsonObjectId objectId = new BsonObjectId(new ObjectId(id));

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.getCollection(conn, storage.getAppCode(), ca.getUrlClientCodeOrElse(storage.getIsAppLevel()),
						storage.getUniqueName(), storage.getIndexes(), storage.getTextIndexFields()),

				(ca, collection) -> Mono.from(collection
						.findOneAndDelete(Filters.eq(ID, objectId))).map(e -> true))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "MongoAppDataService.delete"))
				.switchIfEmpty(this.mongoObjectNotFound(AbstractMongoMessageResourceService.OBJECT_NOT_FOUND,
						storage.getName(), id));
	}

	@Override
	public Mono<Long> deleteByFilter(Connection conn, Storage storage, Query query, Boolean devMode) {

		AbstractCondition condition = query.getCondition();

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.filter(storage, condition),

				(ca, bsonCondition) -> {

					if (BooleanUtil.safeValueOf(devMode)) {
						return this
								.getCollection(conn, storage.getAppCode(),
										ca.getUrlClientCodeOrElse(storage.getIsAppLevel()), storage.getUniqueName(),
										storage.getIndexes(), storage.getTextIndexFields())
								.flatMap(collection -> Mono.from(collection.countDocuments(bsonCondition)));
					} else {
						return this
								.getCollection(conn, storage.getAppCode(),
										ca.getUrlClientCodeOrElse(storage.getIsAppLevel()), storage.getUniqueName(),
										storage.getIndexes(), storage.getTextIndexFields())
								.flatMap(collection -> Mono.from(collection.deleteMany(bsonCondition))
										.map(DeleteResult::getDeletedCount));
					}
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "MongoAppDataService.deleteByFilter"));
	}

	@Override
	public Mono<Map<String, Object>> readVersion(Connection conn, Storage storage, String versionId) {

		if (ObjectId.isValid(versionId))
			return this.mongoObjectNotFound(AbstractMongoMessageResourceService.OBJECT_NOT_FOUND, storage.getName(),
					versionId);

		BsonObjectId objectId = new BsonObjectId(new ObjectId(versionId));

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.getVersionCollection(conn, storage.getAppCode(),
						ca.getUrlClientCodeOrElse(storage.getIsAppLevel()), storage.getUniqueName()),

				(ca, collection) -> Mono.from(collection.find(Filters.eq(ID, objectId)).first())
						.map(doc -> convertBisonIds(storage, doc, Boolean.TRUE)))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "MongoAppDataService.read"))
				.switchIfEmpty(this.msgService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
						AbstractMongoMessageResourceService.OBJECT_NOT_FOUND, storage.getName(), versionId));
	}

	@Override
	public Mono<Page<Map<String, Object>>> readPageVersion(Connection conn, Storage storage, String objectId,
			Query query) {

		Pageable page = query.getPageable();

		FilterCondition objectIdFilterCondition = new FilterCondition().setField(OBJECT_ID)
				.setValue(new ObjectId(objectId)).setOperator(FilterConditionOperator.EQUALS);

		AbstractCondition condition = query.getCondition() == null ? objectIdFilterCondition
				: new ComplexCondition().setConditions(List.of(objectIdFilterCondition, query.getCondition()))
						.setOperator(ComplexConditionOperator.AND);
		Boolean count = query.getCount();

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.filter(storage, condition),

				(ca, bisonCondition) -> this.getVersionCollection(conn, storage.getAppCode(),
						ca.getUrlClientCodeOrElse(storage.getIsAppLevel()), storage.getUniqueName()),

				(ca, bsonCondition, collection) -> this
						.applyQueryOnElements(collection, query, bsonCondition, page)
						.map(doc -> this.convertBisonIds(storage, doc, Boolean.FALSE)).collectList(),

				(ca, bsonCondition, collection, list) -> BooleanUtil.safeValueOf(count)
						? Mono.from(collection.countDocuments(bsonCondition))
						: Mono.just(page.getOffset() + list.size()),

				(ca, bsonCondition, collection, list, cnt) -> Mono
						.just(PageableExecutionUtils.getPage(list, page, cnt::longValue)))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "MongoAppDataService.readPageVersion"));

	}

	@Override
	public Mono<Boolean> checkifExists(Connection conn, Storage storage, String id) {

		if (ObjectId.isValid(id))
			return Mono.just(Boolean.FALSE);

		BsonObjectId objectId = new BsonObjectId(new ObjectId(id));

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.getCollection(conn, storage.getAppCode(), ca.getUrlClientCodeOrElse(storage.getIsAppLevel()),
						storage.getUniqueName(), storage.getIndexes(), storage.getTextIndexFields()),

				(ca, collection) -> Mono.from(collection.countDocuments(Filters.eq(ID, objectId))).map(e -> e > 0)

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "MongoAppDataService.checkifExists"));
	}

	@Override
	public Mono<Boolean> deleteStorage(Connection conn, Storage storage) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.getCollection(conn, storage.getAppCode(), ca.getUrlClientCodeOrElse(storage.getIsAppLevel()),
						storage.getUniqueName(), storage.getIndexes(), storage.getTextIndexFields())
						.flatMap(collection -> Mono.from(collection.drop()).then(Mono.just(Boolean.TRUE))),

				(ca, deleted) -> {
					if (BooleanUtil.safeValueOf(storage.getIsVersioned())
							|| BooleanUtil.safeValueOf(storage.getIsAudited()))
						return this.getVersionCollection(conn, storage.getAppCode(),
								ca.getUrlClientCodeOrElse(storage.getIsAppLevel()), storage.getUniqueName())
								.flatMap(vCollection -> Mono.from(vCollection.drop())).then(Mono.just(deleted));

					return Mono.just(deleted);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "MongoAppDataService.deleteStorage"));
	}

	private Flux<Document> applyQueryOnElements(MongoCollection<Document> collection, Query query, Bson bsonCondition,
			Pageable page) {

		if (query.getFields() == null || query.getFields().isEmpty()) {
			FindPublisher<Document> publisher = collection.find(bsonCondition);

			if (!Query.DEFAULT_SORT.equals(page.getSort()))
				publisher.sort(this.sort(page.getSort()));

			return Flux.from(publisher.skip((int) page.getOffset()).limit(page.getPageSize()));

		} else {

			List<Bson> pipeLines = new ArrayList<>(List.of(Aggregates.match(bsonCondition)));

			Bson sort = null;
			if (!Query.DEFAULT_SORT.equals(page.getSort()))
				sort = this.sort(page.getSort());

			if (sort != null)
				pipeLines.add(Aggregates.sort(sort));

			pipeLines.add(Aggregates.project(Projections
					.fields(BooleanUtil.safeValueOf(query.getExcludeFields()) ? Projections.exclude(query.getFields())
							: Projections.include(query.getFields()))));
			pipeLines.add(Aggregates.skip((int) page.getOffset()));
			pipeLines.add(Aggregates.limit(page.getPageSize()));

			return Flux.from(collection.aggregate(pipeLines));
		}
	}

	private Bson sort(Sort sort) {

		if (sort == null)
			return null;

		if (sort.equals(Query.DEFAULT_SORT))
			return null;

		return Sorts.orderBy(sort.stream()
				.map(e -> e.getDirection() == Direction.ASC ? Sorts.ascending(e.getProperty())
						: Sorts.descending(e.getProperty()))
				.toList());
	}

	private Mono<Bson> filter(Storage storage, AbstractCondition condition) {

		if (condition == null)
			return Mono.just(Filters.empty());

		return (condition instanceof ComplexCondition cc ? this.complexConditionFilter(storage, cc)
				: this.filterConditionFilter(storage, (FilterCondition) condition))
				.defaultIfEmpty(Filters.empty());
	}

	private Mono<Bson> complexConditionFilter(Storage storage, ComplexCondition cc) {

		if (cc.getConditions() == null || cc.getConditions().isEmpty())
			return Mono.empty();

		return Flux.concat(cc.getConditions()
				.stream()
				.map(e -> this.filter(storage, e))
				.toList())
				.map(e -> cc.isNegate() ? Filters.not(e) : e)
				.collectList()
				.map(conds -> {

					if (cc.getOperator() == ComplexConditionOperator.AND)
						return cc.isNegate() ? Filters.or(conds) : Filters.and(conds);

					return cc.isNegate() ? Filters.and(conds) : Filters.or(conds);
				});
	}

	private Mono<Bson> filterConditionFilter(Storage storage, FilterCondition fc) { // NOSONAR
		// in order to cover all operators this kind of check is essential

		if (fc == null)
			return Mono.empty();

		if (fc.getOperator() == FilterConditionOperator.TEXT_SEARCH) {

			if (fc.getValue() == null)
				return Mono.empty();

			Document doc = new Document(Map.of("$text", Map.of("$search", fc.getValue().toString())));
			return Mono.just(doc);
		}

		if (fc.getField() == null)
			return Mono.empty();

		if (fc.getOperator() == IS_FALSE || fc.getOperator() == IS_TRUE)
			return Mono.just(Filters.eq(fc.getField(),
					fc.isNegate() ? fc.getOperator() == IS_FALSE : fc.getOperator() == IS_TRUE));

		if (fc.getOperator() == IS_NULL)
			return fc.isNegate() ? Mono.just(Filters.ne(fc.getField(), null))
					: Mono.just(Filters.or(Filters.eq(fc.getField(), null), Filters.exists(fc.getField(), false)));

		Object value = fc.getValue();
		boolean isObjectIdField = (fc.getField().equals("_id")
				|| (storage.getRelations() != null && storage.getRelations().containsKey(fc.getField())));
		if (isObjectIdField && value != null)
			value = new ObjectId(value.toString());

		if (fc.getOperator() == IN) {

			if (value == null && (fc.getMultiValue() == null || fc.getMultiValue()
					.isEmpty()))
				return Mono.empty();

			BiFunction<String, Iterable<?>, Bson> function = fc.isNegate() ? Filters::nin : Filters::in;
			return Mono.just(function.apply(fc.getField(),
					this.multiFieldValue(isObjectIdField, fc.getValue(), fc.getMultiValue())));
		}

		if (fc.getOperator() == FilterConditionOperator.MATCH) {

			if (value == null)
				return Mono.empty();

			Document doc = new Document(Map.of(fc.getField(),
					Map.of("$elemMatch",
							Map.of(CommonsUtil.nonNullValue(FILTER_MATCH_OPERATOR.get(fc.getMatchOperator()), "$eq"),
									value))));
			return Mono.just(doc);
		}

		if (fc.getOperator() == FilterConditionOperator.MATCH_ALL) {

			if (value == null && fc.getMultiValue() == null && fc.getMultiValue().isEmpty())
				return Mono.empty();

			Document doc = new Document(Map.of(fc.getField(), Map.of("$all", value == null ? fc.getMultiValue()
					: List.of(value))));

			return Mono.just(doc);
		}

		if (value == null)
			return Mono.empty();

		if (fc.getOperator() == BETWEEN) {

			var first = fc.isNegate() ? Filters.lt(fc.getField(), value)
					: Filters.gte(fc.getField(), value);
			var second = fc.isNegate() ? Filters.gt(fc.getField(), fc.getToValue())
					: Filters.lte(fc.getField(), fc.getToValue());

			if (fc.isNegate())
				return Mono.just(Filters.and(first, second));
			else
				return Mono.just(Filters.or(first, second));
		}

		BiFunction<String, Object, Bson> function = switch (fc.getOperator()) {
			case EQUALS -> fc.isNegate() ? Filters::ne : Filters::eq;
			case GREATER_THAN -> fc.isNegate() ? Filters::lte : Filters::gt;
			case GREATER_THAN_EQUAL -> fc.isNegate() ? Filters::lt : Filters::gte;
			case LESS_THAN -> fc.isNegate() ? Filters::gte : Filters::lt;
			case LESS_THAN_EQUAL -> fc.isNegate() ? Filters::gt : Filters::lte;
			default -> null;
		};

		if (function != null)
			return Mono.just(function.apply(fc.getField(), value));

		Bson filter;

		switch (fc.getOperator()) {

			case LIKE -> {
				filter = Filters.regex(fc.getField(), StringUtil.safeValueOf(value, ""));
				return Mono.just(fc.isNegate() ? Filters.not(filter) : filter);
			}
			case STRING_LOOSE_EQUAL -> {
				filter = Filters.regex(fc.getField(), value.toString(), "i");
				return Mono.just(fc.isNegate() ? Filters.not(filter) : filter);
			}
			default -> {
				return Mono.empty();
			}
		}
	}

	private List<?> multiFieldValue(boolean convertId, Object objValue, List<?> values) {

		if (values != null && !values.isEmpty())
			return !convertId ? values
					: values.stream()
							.map(e -> new ObjectId(e.toString()))
							.toList();

		if (objValue == null)
			return List.of();

		int from = 0;
		String iValue = objValue.toString().trim();

		List<Object> obj = new ArrayList<>();
		for (int i = 0; i < iValue.length(); i++) { // NOSONAR
			// Having multiple continue statements is not confusing

			if (iValue.charAt(i) != ',')
				continue;

			if (i != 0 && iValue.charAt(i - 1) == '\\')
				continue;

			String str = iValue.substring(from, i)
					.trim();
			if (str.isEmpty())
				continue;

			obj.add(!convertId ? str : new ObjectId(str));
			from = i + 1;
		}

		return obj;

	}

	private Mono<MongoCollection<Document>> getCollection(Connection conn, String appCode, String clientCode,
			String uniqueName, Map<String, StorageIndex> indexes, List<String> textIndexFields) {

		MongoClient client = this.getMongoClient(conn);

		if (client == null)
			throw msgService.nonReactiveMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
					CoreMessageResourceService.CONNECTION_DETAILS_MISSING, "url");

		final MongoCollection<Document> collection = client.getDatabase(clientCode + "_" + appCode)
				.getCollection(uniqueName);

		return cacheService
				.cacheValueOrGet(uniqueName + IAppDataService.CACHE_SUFFIX_FOR_INDEX_CREATION,
						() -> this.manageIndexes(collection, indexes, textIndexFields), appCode, clientCode)
				.map(e -> collection);
	}

	private Mono<MongoCollection<Document>> getVersionCollection(Connection conn, String appCode, String clientCode,
			String uniqueName) {

		MongoClient client = this.getMongoClient(conn);

		if (client == null)
			throw msgService.nonReactiveMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
					CoreMessageResourceService.CONNECTION_DETAILS_MISSING, "url");

		return Mono.just(client.getDatabase(clientCode + "_" + appCode)
				.getCollection(uniqueName + "_version"));
	}

	private Map<String, Object> convertBisonIds(Storage storage, Document document, boolean isVersion) {

		this.convertBisonId(document, ID);

		if (isVersion)
			this.convertBisonId(document, OBJECT_ID);

		return storage.getRelations() != null ? this.updateDocWithIds(storage, document) : document;
	}

	private void convertBisonId(Document document, String key) {
		String id = document.getObjectId(key).toHexString();
		this.removeBisonKey(document, key);
		document.append(key, id);
	}

	private Document removeBisonKey(Document document, String key) {
		document.remove(key);
		return document;
	}

	private Map<String, Object> updateDocWithIds(Storage storage, Document doc) {

		for (Entry<String, StorageRelation> relationEntry : storage.getRelations().entrySet()) {
			if (!doc.containsKey(relationEntry.getKey()))
				continue;

			Object v = doc.get(relationEntry.getKey());
			if (v instanceof ObjectId oId)
				doc.put(relationEntry.getKey(), oId.toHexString());
			else if (v instanceof List<?> list) {
				List<String> newList = new ArrayList<>();
				for (Object o : list) {
					if (o instanceof ObjectId oId)
						newList.add(oId.toHexString());
					else if (o != null)
						newList.add(o.toString());
				}
				doc.put(relationEntry.getKey(), newList);
			}
		}

		return doc;
	}

	private Mono<Boolean> manageIndexes(MongoCollection<Document> collection, Map<String, StorageIndex> indexes,
			List<String> textIndexFields) {

		return FlatMapUtil.flatMapMonoWithNull(
				() -> this.dropRemovedIndexes(collection, indexes, textIndexFields),

				x -> {

					if (indexes == null || indexes.isEmpty())
						return Mono.empty();

					return Flux.fromIterable(indexes.entrySet())
							.flatMap(e -> {
								StorageIndex ins = e.getValue();

								if (ins.getFields().isEmpty())
									return Mono.just(true);

								List<Bson> bDocs = ins.getFields().stream()
										.map(si -> si.getDirection() == Direction.ASC
												? Indexes.ascending(si.getFieldName())
												: Indexes.descending(si.getFieldName()))
										.toList();

								Bson ind = bDocs.size() == 1 ? bDocs.getFirst() : Indexes.compoundIndex(bDocs);

								IndexOptions options = new IndexOptions().name(e.getKey()).unique(ins.isUnique());
								return Mono.from(collection.createIndex(ind, options)).map(y -> true);
							}).collectList().map(e -> true);
				},

				(x, ins) -> {
					if (textIndexFields == null || textIndexFields.isEmpty())
						return Mono.empty();

					return Mono.from(collection.createIndex(
							new Document(textIndexFields.stream()
									.collect(Collectors.toMap(field -> field, value -> "text"))),
							new IndexOptions().name(TEXT_INDEX_NAME))).map(e -> true);
				})
				.defaultIfEmpty(true)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "MongoAppDataService.getCollection (Cache Supplier)"));

	}

	private Mono<Boolean> dropRemovedIndexes(MongoCollection<Document> collection, Map<String, StorageIndex> indexes,
			List<String> textIndexFields) {

		return Flux.from(collection.listIndexes())
				.filter(e -> {
					String indexName = e.getString("name");
					if (indexName.equals("_id_"))
						return false;

					if (indexName.equals(TEXT_INDEX_NAME))
						return textIndexFields == null || textIndexFields.isEmpty();

					if (indexes == null || indexes.isEmpty())
						return true;

					return !indexes.containsKey(indexName);
				})
				.flatMap(e -> Mono.from(collection.dropIndex(e.getString("name"))))
				.then(Mono.just(true));
	}

	private MongoClient getMongoClient(Connection connection) {
		return connection == null ? defaultClient
				: mongoClients.computeIfAbsent(getConnectionString(connection),
						key -> this.createMongoClient(connection));
	}

	private synchronized MongoClient createMongoClient(Connection conn) {

		if (conn.getConnectionDetails() == null || StringUtil.safeIsBlank(conn.getConnectionDetails().get("url")))
			throw msgService.nonReactiveMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
					CoreMessageResourceService.CONNECTION_DETAILS_MISSING, "url");

		return MongoClients.create(conn.getConnectionDetails().get("url").toString());
	}

	private String getConnectionString(Connection conn) {
		return "Connection : " + conn.getId();
	}

}
