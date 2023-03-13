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
import java.util.function.BiFunction;

import javax.annotation.PostConstruct;

import org.bson.BsonDateTime;
import org.bson.BsonInt64;
import org.bson.BsonObjectId;
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

import com.fincity.nocode.kirun.engine.HybridRepository;
import com.fincity.nocode.kirun.engine.json.schema.validator.SchemaValidator;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.util.BJsonUtil;
import com.fincity.saas.commons.mongo.util.DifferenceApplicator;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.core.document.Connection;
import com.fincity.saas.core.document.Storage;
import com.fincity.saas.core.kirun.repository.CoreSchemaRepository;
import com.fincity.saas.core.model.DataObject;
import com.fincity.saas.core.service.CoreMessageResourceService;
import com.fincity.saas.core.service.CoreSchemaService;
import com.fincity.saas.core.service.StorageService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;

import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class MongoAppDataService extends RedisPubSubAdapter<String, String> implements IAppDataService {

	private static final String ID = "_id";

	private static final String CREATED_AT = "createdAt";

	private static final String OBJECTID = "objectId";

	private static final String MESSAGE = "message";

	private static final String OPERATION = "operation";

	@Autowired
	private MongoClient defaultClient;

	@Autowired
	private CoreMessageResourceService msgService;

	private Map<String, MongoClient> mongoClients = new HashMap<>();

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
	private CoreSchemaService schemaService;

	@PostConstruct
	private void init() {

		if (subAsyncCommand == null || subConnect == null)
			return;

		subAsyncCommand.subscribe(channel);
		subConnect.addListener(this);
	}

	@Override
	public Mono<Map<String, Object>> create(Connection conn, Storage storage, DataObject dataObject) {

		return FlatMapUtil.flatMapMonoWithNull(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> storageService.getSchema(storage),

		        (ca, schema) -> Mono.fromCallable(() ->
				{

			        JsonObject job = (new Gson()).toJsonTree(dataObject.getData())
			                .getAsJsonObject();
			        return (JsonObject) SchemaValidator.validate(null, schema,
			                new HybridRepository<>(new CoreSchemaRepository(),
			                        schemaService.getSchemaRepository(storage.getAppCode(), storage.getClientCode())),
			                job);
		        })
		                .subscribeOn(Schedulers.boundedElastic()),

		        (ca, schema, je) -> Mono.from(this.getCollection(conn, storage)
		                .insertOne(BJsonUtil.from(je))),

		        (ca, schema, je, result) -> Mono.from(this.getCollection(conn, storage)
		                .find(Filters.eq(ID, result.getInsertedId()))
		                .first()),

		        (ca, schema, je, result, doc) ->
				{

			        if (!storage.getIsAudited()
			                .booleanValue()
			                && !storage.getIsVersioned()
			                        .booleanValue())
				        return Mono.empty();

			        Document versionDocument = new Document();
			        versionDocument.append(OBJECTID, result.getInsertedId());
			        versionDocument.append(MESSAGE, dataObject.getMessage());
			        versionDocument.append(CREATED_AT, new BsonDateTime(System.currentTimeMillis()));
			        versionDocument.append(OPERATION, "CREATE");
			        if (ca != null && ca.getUser() != null)
				        versionDocument.append("createdBy", new BsonInt64(ca.getUser()
				                .getId()
				                .longValue()));
			        if (storage.getIsVersioned()
			                .booleanValue())
				        versionDocument.append("object", new Document(dataObject.getData()));

			        return Mono.from(this.getVersionCollection(conn, storage)
			                .insertOne(versionDocument));
		        }, (ca, scheme, je, result, doc, versionResult) -> {
			        doc.remove(ID);
			        doc.append(ID, result.getInsertedId()
			                .asObjectId()
			                .getValue()
			                .toHexString());
			        return Mono.just(doc);
		        });

	}

	@Override
	public Mono<Map<String, Object>> update(Connection conn, Storage storage, DataObject dataObject, Boolean override) {

		// added boolean override to differentiate between the incoming request in
		// patch/put

		String key = StringUtil.safeValueOf(dataObject.getData()
		        .get(ID));

		if (StringUtil.safeIsBlank(key))
			return this.msgService.throwMessage(HttpStatus.NOT_FOUND,
			        AbstractMongoMessageResourceService.OBJECT_NOT_FOUND_TO_UPDATE, storage.getName(), key);

		BsonObjectId objectId = new BsonObjectId(new ObjectId(key));

		return FlatMapUtil.flatMapMonoWithNull(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> storageService.getSchema(storage),

		        (ca, schema) ->
				{

			        Map<String, Object> overridableObject = dataObject.getData();
			        overridableObject.remove(ID);

			        return override.booleanValue() ? Mono.justOrEmpty(overridableObject)
			                : FlatMapUtil.flatMapMono(

			                        () -> Mono.from(this.getCollection(conn, storage)
			                                .find(Filters.eq(ID, objectId))
			                                .first())
			                                .map(orgDoc ->
											{
				                                orgDoc.remove(ID);
				                                return orgDoc;
			                                }),

			                        originalDocument -> DifferenceApplicator.apply(overridableObject,
			                                originalDocument));
		        },

		        (ca, schema, overridableObject) -> Mono.fromCallable(() ->
				{

			        JsonObject job = (new Gson()).toJsonTree(overridableObject)
			                .getAsJsonObject();

			        return (JsonObject) SchemaValidator.validate(null, schema,
			                schemaService.getSchemaRepository(storage.getAppCode(), storage.getClientCode()), job);
		        })
		                .subscribeOn(Schedulers.boundedElastic()),

		        (ca, schema, overridableObject, je) -> Mono.from(this.getCollection(conn, storage)
		                .replaceOne(Filters.eq(ID, objectId), BJsonUtil.from(je))),

		        (ca, schema, overridableObject, je, result) -> Mono.from(this.getCollection(conn, storage)
		                .find(Filters.eq(ID, objectId))
		                .first()),

		        (ca, scheme, overridableObject, je, result, doc) ->
				{

			        if (!storage.getIsAudited()
			                .booleanValue()
			                && !storage.getIsVersioned()
			                        .booleanValue())
				        return Mono.empty();

			        Document versionDocument = new Document();
			        versionDocument.append(OBJECTID, objectId);
			        versionDocument.append(MESSAGE, dataObject.getMessage());
			        versionDocument.append(CREATED_AT, new BsonDateTime(System.currentTimeMillis()));
			        versionDocument.append(OPERATION, "UPDATE");
			        if (ca != null && ca.getUser() != null)
				        versionDocument.append("createdBy", new BsonInt64(ca.getUser()
				                .getId()
				                .longValue()));
			        doc.remove(ID); // removing id from the document
			        if (storage.getIsVersioned()
			                .booleanValue())
				        versionDocument.append("object", new Document(doc));

			        return Mono.from(this.getVersionCollection(conn, storage)
			                .insertOne(versionDocument));
		        }, (ca, scheme, overridableObject, je, result, doc, versionResult) -> {
			        doc.append(ID, key);
			        return Mono.just(doc);
		        });
	}

	@Override
	public Mono<Map<String, Object>> read(Connection conn, Storage storage, String id) {
		BsonObjectId objectId = new BsonObjectId(new ObjectId(id));

		return FlatMapUtil.flatMapMono(

		        () -> Mono.from(this.getCollection(conn, storage)
		                .find(Filters.eq(ID, objectId))
		                .first()),

		        doc ->
				{
			        doc.remove(ID);
			        doc.append(ID, id);
			        return Mono.just((Map<String, Object>) doc);
		        })
		        .switchIfEmpty(Mono.defer(() -> this.msgService.throwMessage(HttpStatus.NOT_FOUND,
		                AbstractMongoMessageResourceService.OBJECT_NOT_FOUND, storage.getName(), id)));
	}

	@Override
	public Mono<Boolean> delete(Connection conn, Storage storage, String id) {

		BsonObjectId objectId = new BsonObjectId(new ObjectId(id));

		return Mono.from(this.getCollection(conn, storage)
		        .findOneAndDelete(Filters.eq(ID, objectId)))
		        .map(e -> true)
		        .switchIfEmpty(Mono.defer(() -> this.msgService.throwMessage(HttpStatus.NOT_FOUND,
		                AbstractMongoMessageResourceService.OBJECT_NOT_FOUND, storage.getName(), id)));

	}

	@Override
	public Mono<Page<Map<String, Object>>> readPage(Connection conn, Storage storage, Pageable page, Boolean count,
	        AbstractCondition condition) {

		return FlatMapUtil.flatMapMono(

		        () -> this.filter(condition),

		        bsonCondition -> Flux.from(this.getCollection(conn, storage)
		                .find(bsonCondition)
		                .sort(this.sort(page.getSort()))
		                .skip((int) page.getOffset())
		                .limit(page.getPageSize()))
		                .map(doc ->
						{
			                String id = doc.getObjectId(ID)
			                        .toHexString();
			                doc.remove(ID);
			                doc.append(ID, id);
			                return (Map<String, Object>) doc;
		                })
		                .collectList(),

		        (bsonCondition, list) -> count.booleanValue() ? Mono.from(this.getCollection(conn, storage)
		                .countDocuments(bsonCondition)) : Mono.just(page.getOffset() + list.size()),

		        (bsonCondition, list, cnt) -> Mono.just(PageableExecutionUtils.getPage(list, page, cnt::longValue)));

	}

	@Override
	public Mono<Map<String, Object>> bulkCreate(Connection conn, Storage storage, JsonObject job) {

		return FlatMapUtil.flatMapMonoWithNull(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> storageService.getSchema(storage),

		        (ca, schema) -> Mono.fromCallable(() ->
				{

			        return (JsonObject) SchemaValidator.validate(null, schema,
			                new HybridRepository<>(new CoreSchemaRepository(),
			                        schemaService.getSchemaRepository(storage.getAppCode(), storage.getClientCode())),
			                job);
		        })
		                .subscribeOn(Schedulers.boundedElastic()),

		        (ca, schema, je) -> Mono.from(this.getCollection(conn, storage)
		                .insertOne(BJsonUtil.from(je))),

		        (ca, schema, je, result) -> Mono.from(this.getCollection(conn, storage)
		                .find(Filters.eq(ID, result.getInsertedId()))
		                .first()),

		        (ca, schema, je, result, doc) ->
				{

			        if (!storage.getIsAudited()
			                .booleanValue()
			                && !storage.getIsVersioned()
			                        .booleanValue())
				        return Mono.empty();

			        Document versionDocument = new Document();
			        versionDocument.append(OBJECTID, result.getInsertedId());
//				        versionDocument.append(MESSAGE, dataObject.getMessage());
			        versionDocument.append(CREATED_AT, new BsonDateTime(System.currentTimeMillis()));
			        versionDocument.append(OPERATION, "CREATE");
			        if (ca != null && ca.getUser() != null)
				        versionDocument.append("createdBy", new BsonInt64(ca.getUser()
				                .getId()
				                .longValue()));
//				        if (storage.getIsVersioned()
//				                .booleanValue())
//					        versionDocument.append("object", new Document(jsonList.get(0)));

			        return Mono.from(this.getVersionCollection(conn, storage)
			                .insertOne(versionDocument));
		        }, (ca, scheme, je, result, doc, versionResult) -> {
			        doc.remove(ID);
			        doc.append(ID, result.getInsertedId()
			                .asObjectId()
			                .getValue()
			                .toHexString());
			        return Mono.just(doc);
		        })

		;

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

	protected Mono<Bson> filter(AbstractCondition condition) {

		if (condition == null)
			return Mono.just(Filters.empty());

		Mono<Bson> cond = null;
		if (condition instanceof ComplexCondition cc)
			cond = complexConditionFilter(cc);
		else
			cond = filterConditionFilter((FilterCondition) condition);

		return cond.defaultIfEmpty(Filters.empty());
	}

	private Mono<Bson> complexConditionFilter(ComplexCondition cc) {

		if (cc.getConditions() == null || cc.getConditions()
		        .isEmpty())
			return Mono.empty();

		return Flux.concat(cc.getConditions()
		        .stream()
		        .map(this::filter)
		        .toList())
		        .map(e -> cc.isNegate() ? Filters.not(e) : e)
		        .collectList()
		        .map(conds ->
				{

			        if (cc.getOperator() == ComplexConditionOperator.AND)
				        return cc.isNegate() ? Filters.or(conds) : Filters.and(conds);

			        return cc.isNegate() ? Filters.and(conds) : Filters.or(conds);
		        });
	}

	private Mono<Bson> filterConditionFilter(FilterCondition fc) { // NOSONAR
		// in order to cover all operators this kind of check is essential

		if (fc == null || fc.getField() == null)
			return Mono.empty();

		if (fc.getOperator() == IS_FALSE || fc.getOperator() == IS_TRUE)
			return Mono.just(Filters.eq(fc.getField(),
			        fc.isNegate() ? fc.getOperator() == IS_FALSE : fc.getOperator() == IS_TRUE));

		if (fc.getOperator() == IS_NULL) {
			if (fc.isNegate())
				Mono.just(Filters.ne(fc.getField(), null));
			else
				return Mono.just(Filters.or(Filters.eq(fc.getField(), null), Filters.exists(fc.getField(), false)));
		}

		if (fc.getOperator() == IN) {

			if (fc.getValue() == null && (fc.getMultiValue() == null || fc.getMultiValue()
			        .isEmpty()))
				return Mono.empty();

			BiFunction<String, Iterable<Object>, Bson> function = fc.isNegate() ? Filters::nin : Filters::in;
			return Mono.just(function.apply(fc.getField(), this.multiFieldValue(fc.getValue(), fc.getMultiValue())));
		}

		if (fc.getValue() == null)
			return Mono.empty();

		if (fc.getOperator() == BETWEEN) {

			var first = fc.isNegate() ? Filters.lt(fc.getField(), fc.getValue())
			        : Filters.gte(fc.getField(), fc.getValue());
			var second = fc.isNegate() ? Filters.gt(fc.getField(), fc.getToValue())
			        : Filters.lte(fc.getField(), fc.getToValue());

			if (fc.isNegate())
				return Mono.just(Filters.and(first, second));
			else
				return Mono.just(Filters.or(first, second));
		}

		BiFunction<String, Object, Bson> function;

		switch (fc.getOperator()) {
		case EQUALS:
			function = fc.isNegate() ? Filters::ne : Filters::eq;
			break;

		case GREATER_THAN:
			function = fc.isNegate() ? Filters::lte : Filters::gt;
			break;

		case GREATER_THAN_EQUAL:
			function = fc.isNegate() ? Filters::lt : Filters::gte;
			break;

		case LESS_THAN:
			function = fc.isNegate() ? Filters::gte : Filters::lt;
			break;

		case LESS_THAN_EQUAL:
			function = fc.isNegate() ? Filters::gt : Filters::lte;
			break;

		default:
			function = null;
		}

		if (function != null)
			return Mono.just(function.apply(fc.getField(), fc.getValue()));

		Bson filter;

		switch (fc.getOperator()) {

		case LIKE:
			filter = Filters.regex(fc.getField(), StringUtil.safeValueOf(fc.getValue(), ""));
			return Mono.just(fc.isNegate() ? Filters.not(filter) : filter);

		case STRING_LOOSE_EQUAL:
			filter = Filters.regex(fc.getField(), fc.getValue()
			        .toString());
			return Mono.just(fc.isNegate() ? Filters.not(filter) : filter);

		default:
			return Mono.empty();
		}
	}

	private List<Object> multiFieldValue(Object objValue, List<Object> values) {

		if (values != null && !values.isEmpty())
			return values;

		if (objValue == null)
			return List.of();

		int from = 0;
		String iValue = objValue.toString()
		        .trim();

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

			obj.add(str);
			from = i + 1;
		}

		return obj;

	}

	@Override
	public void message(String channel, String message) {

		if (channel == null || !channel.equals(this.channel))
			return;

		this.mongoClients.remove(message);
	}

	private MongoCollection<Document> getCollection(Connection conn, Storage storage) {
		MongoClient client = conn == null ? defaultClient
		        : mongoClients.computeIfAbsent(getConnectionString(conn), key -> this.getMongoClient(conn));

		if (client == null)
			throw msgService.nonReactiveMessage(HttpStatus.NOT_FOUND,
			        CoreMessageResourceService.CONNECTION_DETAILS_MISSING, "url");

		return client.getDatabase(storage.getClientCode() + "_" + storage.getAppCode())
		        .getCollection(storage.getUniqueName());
	}

	private MongoCollection<Document> getVersionCollection(Connection conn, Storage storage) {
		MongoClient client = conn == null ? defaultClient
		        : mongoClients.computeIfAbsent(getConnectionString(conn), key -> this.getMongoClient(conn));

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

	private String getConnectionString(Connection conn) {
		return "Connection : " + conn.getId();
	}

}
