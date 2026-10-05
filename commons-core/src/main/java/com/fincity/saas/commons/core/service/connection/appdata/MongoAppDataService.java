package com.fincity.saas.commons.core.service.connection.appdata;

import static com.fincity.saas.commons.model.condition.FilterConditionOperator.BETWEEN;
import static com.fincity.saas.commons.model.condition.FilterConditionOperator.IN;
import static com.fincity.saas.commons.model.condition.FilterConditionOperator.IS_FALSE;
import static com.fincity.saas.commons.model.condition.FilterConditionOperator.IS_NULL;
import static com.fincity.saas.commons.model.condition.FilterConditionOperator.IS_TRUE;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.json.schema.validator.reactive.ReactiveSchemaValidator;
import com.fincity.nocode.kirun.engine.reactive.ReactiveHybridRepository;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.kirun.engine.repository.reactive.KIRunReactiveSchemaRepository;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.document.Connection;
import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.document.Storage.StorageIndex;
import com.fincity.saas.commons.core.exception.StorageObjectNotFoundException;
import com.fincity.saas.commons.core.kirun.repository.CoreSchemaRepository;
import com.fincity.saas.commons.core.model.DataObject;
import com.fincity.saas.commons.core.model.StorageRelation;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import com.fincity.saas.commons.core.service.CoreSchemaService;
import com.fincity.saas.commons.core.service.StorageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.AggregateQuery;
import com.fincity.saas.commons.model.Aggregation;
import com.fincity.saas.commons.model.DateEncoding;
import com.fincity.saas.commons.model.GroupByField;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.AggregateFunction;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.model.condition.HavingCondition;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.util.BJsonUtil;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.DifferenceApplicator;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.WriteModel;
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

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class MongoAppDataService extends RedisPubSubAdapter<String, String> implements IAppDataService {

    private static final String OBJECT = "object";

    private static final String ID = "_id";

    private static final String CREATED_AT = "createdAt";

    private static final long AGGREGATE_MAX_SECONDS = 30L;

    /** Version rows are purged in batches so a wide delete cannot build one huge $in. */
    private static final int VERSION_PURGE_BATCH = 1000;

    private static final String CREATED_BY = "createdBy";

    private static final String OBJECT_ID = "objectId";

    private static final String MESSAGE = "message";

    private static final String OPERATION = "operation";

    private static final String TEXT_INDEX_NAME = "_textIndex";

    // A duplicate key can come off any unique index, so the _id one is identified by name before
    // the failure is reported as a clash on the supplied _id.
    private static final String DUPLICATE_ID_INDEX = "index: _id_";

    // How many documents one bulk write carries while seeding the draft surface. Bounded
    // so a large live collection streams rather than being collected into one list.
    private static final int COPY_BATCH_SIZE = 500;

    private static final ReplaceOptions UPSERT = new ReplaceOptions().upsert(true);

    private static final Map<FilterConditionOperator, String> FILTER_MATCH_OPERATOR = Map.of(
            FilterConditionOperator.EQUALS,
            "$eq",
            FilterConditionOperator.GREATER_THAN,
            "$gt",
            FilterConditionOperator.GREATER_THAN_EQUAL,
            "$gte",
            FilterConditionOperator.LESS_THAN,
            "$lt",
            FilterConditionOperator.LESS_THAN_EQUAL,
            "$lte",
            FilterConditionOperator.LIKE,
            "$regex",
            FilterConditionOperator.STRING_LOOSE_EQUAL,
            "$regex");
    private final Map<String, MongoClient> mongoClients = new HashMap<>();
    private final StorageService storageService;
    private final CoreSchemaService schemaService;
    private final CacheService cacheService;
    private final MongoClient defaultClient;
    private final CoreMessageResourceService msgService;
    private final Gson gson;

    @Autowired(required = false) // NOSONAR
    @Qualifier("subRedisAsyncCommand")
    private RedisPubSubAsyncCommands<String, String> subAsyncCommand;

    @Autowired(required = false) // NOSONAR
    private StatefulRedisPubSubConnection<String, String> subConnect;

    @Value("${redis.connection.eviction.channel:connectionChannel}")
    private String channel;

    public MongoAppDataService(
            StorageService storageService,
            CoreSchemaService schemaService,
            CacheService cacheService,
            CoreMessageResourceService msgService,
            Gson gson,
            MongoClient defaultClient) {
        this.storageService = storageService;
        this.schemaService = schemaService;
        this.cacheService = cacheService;
        this.msgService = msgService;
        this.gson = gson;
        this.defaultClient = defaultClient;
    }

    @PostConstruct
    public void init() {
        if (subAsyncCommand == null || subConnect == null) return;

        subAsyncCommand.subscribe(channel);
        subConnect.addListener(this);
    }

    @PreDestroy
    public void closeClients() {
        if (this.defaultClient != null) this.defaultClient.close();
        this.mongoClients.values().stream().filter(Objects::nonNull).forEach(MongoClient::close);
        this.mongoClients.clear();
    }

    @Override
    public void message(String channel, String message) {
        if (channel == null || !channel.equals(this.channel)) return;

        MongoClient client = this.mongoClients.remove(message);

        if (client != null) client.close();
    }

    private <T> Mono<T> mongoObjectNotFound(String messageId, Object... params) {
        return this.msgService.throwMessage(
                msg -> new StorageObjectNotFoundException(HttpStatus.NOT_FOUND, msg), messageId, params);
    }

    @Override
    public Mono<Map<String, Object>> create(String clientCode, Connection conn, Storage storage, DataObject dataObject) {

        BsonObjectId givenId = this.takeGivenId(dataObject.getData());

        return FlatMapUtil.flatMapMonoWithNull(
                        SecurityContextUtil::getUsersContextAuthentication,
                        ca -> this.getCollection(
                                conn,
                                storage.getAppCode(),
                                storage.getIsAppLevel() ? ca.getUrlClientCode() : clientCode,
                                storage.getUniqueName(),
                                storage.getIndexes(),
                                storage.getTextIndexFields()),
                        (ca, collection) -> storageService.getSchema(storage),
                        (ca, collection, schema) ->
                                schemaService.getSchemaRepository(storage.getAppCode(), storage.getClientCode()),
                        (ca, collection, schema, appSchemaRepo) ->
                                this.handleRelationsAndValidate(dataObject.getData(), storage, schema, appSchemaRepo),
                        (ca, collection, schema, appSchemaRepo, je) -> {
                            Document document = BJsonUtil.from(
                                    storage.getRelations() != null
                                            ? storage.getRelations().keySet()
                                            : Set.of(),
                                    je);

                            if (givenId != null) document.append(ID, givenId);

                            return Mono.from(collection.insertOne(document))
                                    .onErrorResume(
                                            MongoWriteException.class,
                                            ex -> this.duplicateGivenId(ex, storage, givenId));
                        },
                        (ca, collection, schema, appSchemaRepo, je, result) -> Mono.from(collection
                                .find(Filters.eq(ID, this.insertedId(result, givenId)))
                                .first()),
                        (ca, collection, schema, appSchemaRepo, je, result, doc) -> this.addVersion(
                                clientCode,
                                conn,
                                storage,
                                dataObject.getMessage(),
                                this.insertedId(result, givenId),
                                ca,
                                doc,
                                "CREATE"),
                        (ca, collection, schema, appSchemaRepo, je, result, doc, versionResult) -> {
                            BsonObjectId insertedId = this.insertedId(result, givenId);
                            if (insertedId != null)
                                doc.append(ID, insertedId.getValue().toHexString());
                            return Mono.just((Map<String, Object>) doc);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MongoAppDataService.create"));
    }

    /**
     * Pulls a caller supplied _id out of the incoming data. Uploads and transports carry the _id of
     * every row so relation fields keep pointing at the right rows, but every read, update and
     * delete here matches _id as an ObjectId, so an _id left in the data as a plain string would be
     * stored as a string and the row would be unreachable afterwards. A hex id is therefore handed
     * back for the document to be inserted with, and anything else is dropped for Mongo to
     * generate. Removing it also keeps _id away from the schema validator, the same way relation
     * fields are held back.
     */
    private BsonObjectId takeGivenId(Map<String, Object> data) {
        if (data == null) return null;

        Object id = data.remove(ID);

        if (id instanceof ObjectId objectId) return new BsonObjectId(objectId);

        String hexId = StringUtil.safeValueOf(id);

        return !StringUtil.safeIsBlank(hexId) && ObjectId.isValid(hexId)
                ? new BsonObjectId(new ObjectId(hexId))
                : null;
    }

    private BsonObjectId insertedId(InsertOneResult result, BsonObjectId givenId) {
        BsonValue insertedId = result != null ? result.getInsertedId() : null;

        return insertedId != null && insertedId.isObjectId() ? insertedId.asObjectId() : givenId;
    }

    /**
     * Now that a supplied _id is kept, uploading the same file twice collides on it. Answering that
     * with the raw driver error says nothing about which row is already there.
     */
    private Mono<InsertOneResult> duplicateGivenId(
            MongoWriteException ex, Storage storage, BsonObjectId givenId) {
        if (givenId == null
                || ex.getError().getCategory() != ErrorCategory.DUPLICATE_KEY
                || !ex.getError().getMessage().contains(DUPLICATE_ID_INDEX)) return Mono.error(ex);

        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.CONFLICT, msg),
                CoreMessageResourceService.STORAGE_OBJECT_ID_EXISTS,
                givenId.getValue().toHexString(),
                storage.getName());
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<Map<String, Object>> update(String clientCode, Connection conn, Storage storage, DataObject dataObject, Boolean override) {
        // TODO: Added boolean override to differentiate between the incoming request in
        // patch/put

        String key = StringUtil.safeValueOf(dataObject.getData().get(ID));

        if (StringUtil.safeIsBlank(key) || !ObjectId.isValid(key))
            return this.mongoObjectNotFound(
                    AbstractMongoMessageResourceService.OBJECT_NOT_FOUND_TO_UPDATE, storage.getName(), key);

        BsonObjectId objectId = new BsonObjectId(new ObjectId(key));

        return FlatMapUtil.flatMapMonoWithNull(
                        SecurityContextUtil::getUsersContextAuthentication,
                        ca -> this.getCollection(
                                conn,
                                storage.getAppCode(),
                                storage.getIsAppLevel() ? ca.getUrlClientCode() : clientCode,
                                storage.getUniqueName(),
                                storage.getIndexes(),
                                storage.getTextIndexFields()),
                        (ca, collection) -> storageService.getSchema(storage),
                        (ca, collection, schema) ->
                                schemaService.getSchemaRepository(storage.getAppCode(), storage.getClientCode()),
                        (ca, collection, schema, appSchemaRepo) -> {
                            Map<String, Object> overridableObject = dataObject.getData();
                            overridableObject.remove(ID);

                            if (BooleanUtil.safeValueOf(override)) return Mono.justOrEmpty(overridableObject);

                            return Mono.from(collection
                                            .find(Filters.eq(ID, objectId))
                                            .first())
                                    .switchIfEmpty(this.mongoObjectNotFound(
                                            AbstractMongoMessageResourceService.OBJECT_NOT_FOUND_TO_UPDATE,
                                            storage.getName(),
                                            key))
                                    .map(doc -> this.removeKey(doc, ID))
                                    .map(doc -> this.convertBisonIds(storage, doc, Boolean.FALSE))
                                    .flatMap(oDocument -> DifferenceApplicator.apply(overridableObject, oDocument))
                                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "MongoAppDataService.update"));
                        },
                        (ca, collection, schema, appSchemaRepo, overridableObject) -> this.handleRelationsAndValidate(
                                (Map<String, Object>) overridableObject, storage, schema, appSchemaRepo),
                        (ca, collection, schema, appSchemaRepo, overridableObject, je) ->
                                Mono.from(collection.replaceOne(
                                        Filters.eq(ID, objectId),
                                        BJsonUtil.from(
                                                storage.getRelations() != null
                                                        ? storage.getRelations().keySet()
                                                        : Set.of(),
                                                je))),
                        (ca, collection, schema, appSchemaRepo, overridableObject, je, result) -> Mono.from(collection
                                        .find(Filters.eq(ID, objectId))
                                        .first())
                                .switchIfEmpty(this.mongoObjectNotFound(
                                        AbstractMongoMessageResourceService.OBJECT_NOT_FOUND_TO_UPDATE,
                                        storage.getName(),
                                        key)),
                        (ca, collection, scheme, appSchemaRepo, overridableObject, je, result, doc) ->
                                this.addVersion(clientCode, conn, storage, dataObject.getMessage(), objectId, ca, doc, "UPDATE"),
                        (ca, collection, scheme, appSchemaRepo, overridableObject, je, result, doc, versionResult) -> {
                            doc.append(ID, key);
                            return Mono.just(this.updateDocWithIds(storage, doc));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MongoAppDataService.update"));
    }

    private Mono<JsonObject> handleRelationsAndValidate(
            Map<String, Object> objectMap, Storage storage, Schema schema, ReactiveRepository<Schema> appSchemaRepo) {
        JsonObject job = this.gson.toJsonTree(objectMap).getAsJsonObject();

        Map<String, JsonElement> relations = new HashMap<>();
        if (storage.getRelations() != null && !storage.getRelations().isEmpty())
            storage.getRelations().forEach((key, relation) -> {
                if (job.has(key)) relations.put(key, job.remove(key));
            });

        return ReactiveSchemaValidator.validate(
                        null,
                        schema,
                        new ReactiveHybridRepository<>(
                                new KIRunReactiveSchemaRepository(), new CoreSchemaRepository(), appSchemaRepo),
                        job)
                .map(JsonElement::getAsJsonObject)
                .map(validatedJsonObject -> {
                    relations.forEach(validatedJsonObject::add);
                    return validatedJsonObject;
                });
    }

    private Mono<InsertOneResult> addVersion(
            String clientCode,
            Connection conn,
            Storage storage,
            String dataObjectMessage,
            BsonObjectId bsonObjectId,
            ContextAuthentication ca,
            Document document,
            String operation) {
        if (!BooleanUtil.safeValueOf(storage.getIsAudited()) && !BooleanUtil.safeValueOf(storage.getIsVersioned()))
            return Mono.empty();

        String objectId = bsonObjectId != null
                ? bsonObjectId.getValue().toHexString()
                : document.get(ID).toString();

        Document versionDocument = new Document();
        versionDocument.append(OBJECT_ID, objectId);
        versionDocument.append(MESSAGE, dataObjectMessage);
        versionDocument.append(CREATED_AT, new BsonDateTime(System.currentTimeMillis()));
        versionDocument.append(OPERATION, operation);
        if (ca.getUser() != null)
            versionDocument.append(
                    CREATED_BY, new BsonInt64(ca.getUser().getId().longValue()));
        document.remove(ID); // removing id from the document
        if (BooleanUtil.safeValueOf(storage.getIsVersioned())) versionDocument.append(OBJECT, document);

        return this.getVersionCollection(
                        conn,
                        storage.getAppCode(),
                        storage.getIsAppLevel() ? ca.getUrlClientCode() : clientCode,
                        storage.getUniqueName())
                .flatMap(collection -> Mono.from(collection.insertOne(versionDocument)));
    }

    @Override
    public Mono<Map<String, Object>> read(String clientCode, Connection conn, Storage storage, String id) {
        if (!ObjectId.isValid(id))
            return this.mongoObjectNotFound(
                    AbstractMongoMessageResourceService.OBJECT_NOT_FOUND, storage.getName(), id);

        BsonObjectId objectId = new BsonObjectId(new ObjectId(id));

        return FlatMapUtil.flatMapMono(() -> this.getCollection(clientCode, conn, storage), collection -> Mono.from(
                                collection.find(Filters.eq(ID, objectId)).first())
                        .map(doc -> this.convertBisonIds(storage, doc, Boolean.FALSE)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MongoAppDataService.read"))
                .switchIfEmpty(this.mongoObjectNotFound(
                        AbstractMongoMessageResourceService.OBJECT_NOT_FOUND, storage.getName(), id));
    }

    @Override
    public Mono<Page<Map<String, Object>>> readPage(String clientCode, Connection conn, Storage storage, Query query) {
        Pageable page = query.getPageable();
        AbstractCondition condition = query.getCondition();
        Boolean count = query.getCount();

        return FlatMapUtil.flatMapMono(
                        () -> this.getCollection(clientCode, conn, storage),
                        collection -> this.filter(storage, condition),
                        (collection, bsonCondition) -> this.applyQueryOnElements(collection, query, bsonCondition, page)
                                .map(doc -> this.convertBisonIds(storage, doc, Boolean.FALSE))
                                .collectList(),
                        (collection, bsonCondition, list) -> BooleanUtil.safeValueOf(count)
                                ? Mono.from(collection.countDocuments(bsonCondition))
                                : Mono.just(page.getOffset() + list.size()),
                        (ca, bsonCondition, list, cnt) ->
                                Mono.just(PageableExecutionUtils.getPage(list, page, cnt::longValue)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MongoAppDataService.readPage"));
    }

    @Override
    public Flux<Map<String, Object>> readPageAsFlux(String clientCode, Connection conn, Storage storage, Query query) {
        Pageable page = query.getPageable();
        AbstractCondition condition = query.getCondition();

        return this.getCollection(clientCode, conn, storage)
                .flatMapMany(collection -> this.filter(storage, condition)
                        .flatMapMany(bsonCondition -> this.applyQueryOnElements(collection, query, bsonCondition, page)
                                .map(document -> this.convertBisonIds(storage, document, Boolean.FALSE))))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MongoAppDataService.readPageAsFlux"));
    }

    private static final String GROUP_ID = "_id";

    private static final Pattern ALIAS_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /**
     * Epoch SECONDS for any plausible date sit near 1e9; milliseconds near 1e12.
     * A value past this declared as seconds is a units mistake, and left alone it
     * buckets silently into the year 56000 rather than failing.
     */
    private static final long EPOCH_SECONDS_SANITY_CEILING = 100_000_000_000L;

    @Override
    public Mono<Page<Map<String, Object>>> aggregate(
            String clientCode, Connection conn, Storage storage, AggregateQuery query) {

        Pageable page = query.getPageable();

        return FlatMapUtil.flatMapMono(
                        () -> this.getCollection(clientCode, conn, storage),
                        collection -> this.validateAggregate(storage, collection, query),
                        (collection, validated) -> this.filter(storage, query.getCondition()),
                        (collection, validated, matchBson) -> query.getHaving() == null
                                ? Mono.just(Filters.empty())
                                : this.filter(storage, query.getHaving()),
                        (collection, validated, matchBson, havingBson) ->
                                this.runAggregate(collection, query, matchBson, havingBson, page))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MongoAppDataService.aggregate"));
    }

    private Mono<Page<Map<String, Object>>> runAggregate(
            MongoCollection<Document> collection,
            AggregateQuery query,
            Bson matchBson,
            Bson havingBson,
            Pageable page) {

        List<Bson> shared = new ArrayList<>();
        shared.add(Aggregates.match(matchBson));
        shared.add(this.groupStage(query));
        shared.add(this.projectStage(query));
        if (query.getHaving() != null) shared.add(Aggregates.match(havingBson));

        List<Bson> paging = new ArrayList<>();
        Bson sort = this.sort(page.getSort());
        if (sort != null) paging.add(Aggregates.sort(sort));
        paging.add(Aggregates.skip((int) page.getOffset()));
        paging.add(Aggregates.limit(page.getPageSize()));

        if (!BooleanUtil.safeValueOf(query.getCount())) {
            List<Bson> pipeline = new ArrayList<>(shared);
            pipeline.addAll(paging);

            return this.aggregatePublisher(collection, pipeline)
                    .map(doc -> (Map<String, Object>) doc)
                    .collectList()
                    .map(list -> PageableExecutionUtils.getPage(
                            list, page, () -> page.getOffset() + (long) list.size()));
        }

        // One $facet rather than two pipelines: $group already defeats any index use
        // after the first stage, so running it twice buys nothing and costs a second
        // pass over the collection.
        List<Bson> pipeline = new ArrayList<>(shared);
        pipeline.add(new Document(
                "$facet",
                new Document("rows", paging).append("total", List.of(new Document("$count", "value")))));

        return this.aggregatePublisher(collection, pipeline)
                .next()
                .map(facet -> {
                    List<Map<String, Object>> rows = this.facetRows(facet);
                    long total = this.facetTotal(facet);
                    return PageableExecutionUtils.getPage(rows, page, () -> total);
                })
                .defaultIfEmpty(PageableExecutionUtils.getPage(List.of(), page, () -> 0L));
    }

    private Flux<Document> aggregatePublisher(MongoCollection<Document> collection, List<Bson> pipeline) {
        // allowDiskUse because $group over a real collection routinely exceeds the
        // 100MB in-memory limit, and maxTime so one bad grouping key cannot pin the node.
        return Flux.from(collection
                .aggregate(pipeline)
                .allowDiskUse(Boolean.TRUE)
                .maxTime(AGGREGATE_MAX_SECONDS, TimeUnit.SECONDS));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> facetRows(Document facet) {
        Object rows = facet.get("rows");
        if (!(rows instanceof List<?> list)) return List.of();
        return list.stream()
                .filter(Document.class::isInstance)
                .map(d -> (Map<String, Object>) d)
                .toList();
    }

    private long facetTotal(Document facet) {
        Object total = facet.get("total");
        if (!(total instanceof List<?> list) || list.isEmpty()) return 0L;
        if (!(list.getFirst() instanceof Document first)) return 0L;
        Object value = first.get("value");
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private Bson groupStage(AggregateQuery query) {
        Document group = new Document();

        if (query.getGroupBy() == null || query.getGroupBy().isEmpty()) {
            group.append(GROUP_ID, null);
        } else {
            Document keys = new Document();
            for (GroupByField g : query.getGroupBy()) keys.append(g.resolvedAlias(), this.groupKeyExpression(g));
            group.append(GROUP_ID, keys);
        }

        for (Aggregation a : query.getAggregations()) group.append(a.resolvedAlias(), this.accumulator(a));

        return new Document("$group", group);
    }

    /**
     * The group key for one field: its raw value, or a truncated date.
     *
     * App data dates are numbers rather than BSON dates, so $dateTrunc cannot see
     * them directly. The value is lifted to milliseconds, turned into a date, then
     * truncated in the caller's timezone.
     */
    private Object groupKeyExpression(GroupByField g) {
        String path = "$" + g.getField();

        if (g.getBucket() == null) return path;

        Object millis = g.getEncoding().getToMillis() == 1L
                ? path
                : new Document("$multiply", List.of(path, g.getEncoding().getToMillis()));

        return new Document(
                "$dateTrunc",
                new Document("date", new Document("$toDate", millis))
                        .append("unit", g.getBucket().getMongoUnit())
                        .append("timezone", g.resolvedTimezone()));
    }

    private Object accumulator(Aggregation a) {
        if (a.getFunction() == AggregateFunction.COUNT) {
            if (a.getField() == null || a.getField().isBlank()) return new Document("$sum", 1);

            // COUNT(col) in SQL skips nulls, and $sum: 1 would not.
            return new Document(
                    "$sum",
                    new Document(
                            "$cond",
                            List.of(
                                    new Document(
                                            "$in",
                                            List.of(
                                                    new Document("$type", "$" + a.getField()),
                                                    List.of("missing", "null"))),
                                    0,
                                    1)));
        }

        String path = "$" + a.getField();
        return switch (a.getFunction()) {
            case SUM -> new Document("$sum", path);
            case AVG -> new Document("$avg", path);
            case MIN -> new Document("$min", path);
            case MAX -> new Document("$max", path);
            case COUNT -> new Document("$sum", 1);
        };
    }

    /**
     * Flatten _id back to the top level.
     *
     * Nested group keys would force every consumer to reshape before binding, and
     * the chart components this exists for take a flat array of objects.
     */
    private Bson projectStage(AggregateQuery query) {
        Document project = new Document(GROUP_ID, 0);

        if (query.getGroupBy() != null) {
            for (GroupByField g : query.getGroupBy()) {
                String alias = g.resolvedAlias();
                project.append(alias, this.projectedKey(g, alias));
            }
        }

        for (Aggregation a : query.getAggregations()) project.append(a.resolvedAlias(), 1);

        return new Document("$project", project);
    }

    private Object projectedKey(GroupByField g, String alias) {
        String ref = "$" + GROUP_ID + "." + alias;

        if (g.getBucket() == null) return ref;

        // $dateTrunc hands back a BSON date. Every other timestamp this service
        // returns is a number in the encoding it was stored in, and the client's
        // date formatter reads nothing else, so convert back rather than leak a date.
        Object asLong = new Document("$toLong", ref);
        return g.getEncoding().getToMillis() == 1L
                ? asLong
                : new Document(
                        "$toLong", new Document("$divide", List.of(asLong, g.getEncoding().getToMillis())));
    }

    /**
     * Reject a malformed aggregate before it reaches Mongo.
     *
     * This is the security-critical step, not a convenience. Field names become
     * pipeline paths and aliases become $project keys, so an unvalidated request
     * is pipeline injection into app data.
     */
    private Mono<Boolean> validateAggregate(
            Storage storage, MongoCollection<Document> collection, AggregateQuery query) {

        if (query.getAggregations() == null || query.getAggregations().isEmpty())
            return this.invalidAggregation("at least one aggregation is required");

        if (query.getHaving() instanceof HavingCondition)
            return this.invalidAggregation(
                    "having must be a plain condition over the aliases; HavingCondition carries its own"
                            + " aggregate and is not supported here");

        Set<String> aliases = new LinkedHashSet<>();

        if (query.getGroupBy() != null) {
            for (GroupByField g : query.getGroupBy()) {
                String err = this.checkGroupBy(g, aliases);
                if (err != null) return this.invalidAggregation(err);
            }
        }

        for (Aggregation a : query.getAggregations()) {
            String err = this.checkAggregation(a, aliases);
            if (err != null) return this.invalidAggregation(err);
        }

        if (query.getSort() != null) {
            for (Sort.Order o : query.getSort()) {
                if (!aliases.contains(o.getProperty()))
                    return this.invalidAggregation("cannot sort on '" + o.getProperty()
                            + "'; sort is only possible on a group key or measure alias " + aliases);
            }
        }

        return this.validateFieldsAgainstSchema(storage, query).then(this.sanityCheckBuckets(collection, query));
    }

    private String checkGroupBy(GroupByField g, Set<String> aliases) {
        if (g.getField() == null || g.getField().isBlank()) return "a groupBy entry has no field";
        if (g.getField().indexOf('$') >= 0) return "field '" + g.getField() + "' may not contain '$'";

        String alias = g.resolvedAlias();
        if (!ALIAS_PATTERN.matcher(alias).matches())
            return "alias '" + alias + "' must match " + ALIAS_PATTERN.pattern();
        if (!aliases.add(alias)) return "duplicate alias '" + alias + "'";

        if (g.getBucket() == null) {
            if (g.getEncoding() != null)
                return "encoding is only meaningful with a bucket, on field '" + g.getField() + "'";
            return null;
        }

        if (g.getEncoding() == null)
            return "field '" + g.getField() + "' is bucketed by " + g.getBucket()
                    + " so it needs an encoding (EPOCH_SECONDS or EPOCH_MILLIS); dates are stored as"
                    + " numbers and seconds cannot be told from milliseconds";

        try {
            ZoneId.of(g.resolvedTimezone());
        } catch (DateTimeException e) {
            return "'" + g.getTimezone() + "' is not a known IANA timezone";
        }

        return null;
    }

    private String checkAggregation(Aggregation a, Set<String> aliases) {
        if (a.getFunction() == null) return "an aggregation has no function";

        if (a.getFunction() != AggregateFunction.COUNT && (a.getField() == null || a.getField().isBlank()))
            return a.getFunction() + " needs a field";

        if (a.getField() != null && a.getField().indexOf('$') >= 0)
            return "field '" + a.getField() + "' may not contain '$'";

        String alias = a.resolvedAlias();
        if (alias == null || !ALIAS_PATTERN.matcher(alias).matches())
            return "alias '" + alias + "' must match " + ALIAS_PATTERN.pattern();
        if (!aliases.add(alias)) return "duplicate alias '" + alias + "'";

        return null;
    }

    /**
     * Every referenced field has to be one the storage declares, and a bucketed one
     * has to be numeric. The schema is the only thing standing between a caller and
     * an arbitrary field path.
     */
    private Mono<Boolean> validateFieldsAgainstSchema(Storage storage, AggregateQuery query) {

        return this.storageService.getSchema(storage).flatMap(schema -> {
            Map<String, Schema> props = schema.getProperties();
            if (props == null || props.isEmpty()) return Mono.just(Boolean.TRUE);

            if (query.getGroupBy() != null) {
                for (GroupByField g : query.getGroupBy()) {
                    Schema fieldSchema = this.declaredField(props, g.getField());
                    if (fieldSchema == null)
                        return this.invalidAggregation(
                                "storage " + storage.getName() + " declares no field '" + g.getField() + "'");

                    if (g.getBucket() != null && !this.isNumeric(fieldSchema))
                        return this.invalidAggregation("field '" + g.getField()
                                + "' is bucketed as a date but is not a number in the storage schema;"
                                + " only epoch-number dates can be bucketed");
                }
            }

            for (Aggregation a : query.getAggregations()) {
                if (a.getField() == null || a.getField().isBlank()) continue;
                if (this.declaredField(props, a.getField()) == null)
                    return this.invalidAggregation(
                            "storage " + storage.getName() + " declares no field '" + a.getField() + "'");
            }

            return Mono.just(Boolean.TRUE);
        })
                .defaultIfEmpty(Boolean.TRUE);
    }

    private Schema declaredField(Map<String, Schema> props, String field) {
        if (field == null) return null;
        if (GROUP_ID.equals(field)) return props.get(GROUP_ID);

        int dot = field.indexOf('.');
        return props.get(dot < 0 ? field : field.substring(0, dot));
    }

    private boolean isNumeric(Schema fieldSchema) {
        if (fieldSchema.getType() == null) return false;
        Set<SchemaType> types = fieldSchema.getType().getAllowedSchemaTypes();
        if (types == null) return false;
        return types.contains(SchemaType.INTEGER)
                || types.contains(SchemaType.LONG)
                || types.contains(SchemaType.FLOAT)
                || types.contains(SchemaType.DOUBLE);
    }

    /**
     * Catch a seconds/milliseconds mix-up by looking at one real value.
     *
     * The schema calls both LONG, so this is the only place the mistake can be
     * seen. Left through, milliseconds multiplied to milliseconds bucket into the
     * year 56000: no error, a rendered chart, and nonsense.
     */
    private Mono<Boolean> sanityCheckBuckets(MongoCollection<Document> collection, AggregateQuery query) {
        if (query.getGroupBy() == null || query.getGroupBy().isEmpty()) return Mono.just(Boolean.TRUE);

        List<GroupByField> seconds = query.getGroupBy().stream()
                .filter(g -> g.getBucket() != null && g.getEncoding() == DateEncoding.EPOCH_SECONDS)
                .toList();

        if (seconds.isEmpty()) return Mono.just(Boolean.TRUE);

        return Flux.fromIterable(seconds)
                .concatMap(g -> Mono.from(collection
                                .find(Filters.exists(g.getField(), true))
                                .projection(Projections.include(g.getField()))
                                .first())
                        .flatMap(doc -> {
                            Object v = doc == null ? null : doc.get(g.getField());
                            if (v instanceof Number n && Math.abs(n.longValue()) > EPOCH_SECONDS_SANITY_CEILING)
                                return this.invalidAggregation("field '" + g.getField()
                                        + "' is declared EPOCH_SECONDS but holds " + n.longValue()
                                        + ", which is milliseconds; bucketing it as seconds would be wrong"
                                        + " by a factor of 1000");
                            return Mono.just(Boolean.TRUE);
                        })
                        .defaultIfEmpty(Boolean.TRUE))
                .then(Mono.just(Boolean.TRUE));
    }

    private <T> Mono<T> invalidAggregation(String reason) {
        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                CoreMessageResourceService.INVALID_AGGREGATION,
                reason);
    }

    @Override
    public Mono<Boolean> delete(String clientCode, Connection conn, Storage storage, String id, Boolean deleteVersion) {
        if (!ObjectId.isValid(id))
            return this.mongoObjectNotFound(
                    AbstractMongoMessageResourceService.OBJECT_NOT_FOUND, storage.getName(), id);

        BsonObjectId objectId = new BsonObjectId(new ObjectId(id));

        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,
                        ca -> this.getCollection(clientCode, conn, storage),
                        // Read before deleting, because the DELETE version row records the
                        // final state and findOneAndDelete would already have thrown it away.
                        (ca, collection) -> Mono.from(collection.find(Filters.eq(ID, objectId)).first()),
                        (ca, collection, doc) -> this.recordDelete(clientCode, conn, storage, ca, doc, id, deleteVersion),
                        (ca, collection, doc, versioned) -> Mono.from(
                                        collection.findOneAndDelete(Filters.eq(ID, objectId)))
                                .map(e -> Boolean.TRUE))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MongoAppDataService.delete"))
                .switchIfEmpty(this.mongoObjectNotFound(
                        AbstractMongoMessageResourceService.OBJECT_NOT_FOUND, storage.getName(), id));
    }

    @Override
    public Mono<Long> deleteByFilter(
            String clientCode, Connection conn, Storage storage, Query query, Boolean devMode, Boolean deleteVersion) {
        AbstractCondition condition = query.getCondition();

        return FlatMapUtil.flatMapMono(
                        () -> this.getCollection(clientCode, conn, storage),
                        collection -> this.filter(storage, condition),
                        (collection, bsonCondition) -> {
                            if (BooleanUtil.safeValueOf(devMode))
                                return Mono.from(collection.countDocuments(bsonCondition));

                            return this.recordBulkDelete(
                                            clientCode, conn, storage, collection, bsonCondition, deleteVersion)
                                    .then(Mono.from(collection.deleteMany(bsonCondition)))
                                    .map(DeleteResult::getDeletedCount);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MongoAppDataService.deleteByFilter"));
    }

    /**
     * Record one row's removal: either a DELETE version row, or the purge of its
     * history.
     *
     * Does nothing for a storage that asked for neither auditing nor versioning,
     * which is what keeps this off the hot path for the majority of storages.
     */
    private Mono<Boolean> recordDelete(
            String clientCode,
            Connection conn,
            Storage storage,
            ContextAuthentication ca,
            Document doc,
            String id,
            Boolean deleteVersion) {

        if (deleteVersion == null || !this.keepsHistory(storage) || doc == null) return Mono.just(Boolean.TRUE);

        if (BooleanUtil.safeValueOf(deleteVersion))
            return this.getVersionCollection(clientCode, conn, storage)
                    .flatMap(vc -> Mono.from(vc.deleteMany(Filters.eq(OBJECT_ID, id))))
                    .thenReturn(Boolean.TRUE);

        // addVersion strips _id from the document it is handed, which is fine here
        // only because the row is about to go.
        return this.addVersion(
                        clientCode, conn, storage, null, new BsonObjectId(new ObjectId(id)), ca, doc, "DELETE")
                .thenReturn(Boolean.TRUE)
                .defaultIfEmpty(Boolean.TRUE);
    }

    /**
     * The same for a filtered bulk delete, without ever holding the whole match set.
     *
     * Rows are streamed: the record path versions them one at a time, and the purge
     * path collects only ids and deletes their history in batches. Collecting every
     * matched Document first, as the obvious version of this does, turns a wide
     * delete into an out-of-memory risk.
     */
    private Mono<Boolean> recordBulkDelete(
            String clientCode,
            Connection conn,
            Storage storage,
            MongoCollection<Document> collection,
            Bson bsonCondition,
            Boolean deleteVersion) {

        if (deleteVersion == null || !this.keepsHistory(storage)) return Mono.just(Boolean.TRUE);

        if (BooleanUtil.safeValueOf(deleteVersion))
            return this.getVersionCollection(clientCode, conn, storage)
                    .flatMap(vc -> Flux.from(collection.find(bsonCondition).projection(Projections.include(ID)))
                            .map(d -> d.getObjectId(ID).toHexString())
                            .buffer(VERSION_PURGE_BATCH)
                            .concatMap(ids -> Mono.from(vc.deleteMany(Filters.in(OBJECT_ID, ids))))
                            .then(Mono.just(Boolean.TRUE)))
                    .defaultIfEmpty(Boolean.TRUE);

        return SecurityContextUtil.getUsersContextAuthentication()
                .flatMap(ca -> Flux.from(collection.find(bsonCondition))
                        .concatMap(d -> {
                            ObjectId oid = d.getObjectId(ID);
                            return this.addVersion(
                                    clientCode, conn, storage, null, new BsonObjectId(oid), ca, d, "DELETE");
                        })
                        .then(Mono.just(Boolean.TRUE)))
                .defaultIfEmpty(Boolean.TRUE);
    }

    /** A storage writes version rows when it asked for auditing, versioning, or both. */
    private boolean keepsHistory(Storage storage) {
        return BooleanUtil.safeValueOf(storage.getIsAudited()) || BooleanUtil.safeValueOf(storage.getIsVersioned());
    }

    @Override
    public Mono<Map<String, Object>> readVersion(String clientCode, Connection conn, Storage storage, String versionId) {
        if (!ObjectId.isValid(versionId))
            return this.mongoObjectNotFound(
                    AbstractMongoMessageResourceService.OBJECT_NOT_FOUND, storage.getName(), versionId);

        BsonObjectId objectId = new BsonObjectId(new ObjectId(versionId));

        // A version id identifies a document in the *_version collection, not in the data
        // collection, so reading it from the data collection could only ever 404.
        return FlatMapUtil.flatMapMono(() -> this.getVersionCollection(clientCode, conn, storage), collection -> Mono
                        .from(collection.find(Filters.eq(ID, objectId)).first())
                        .map(doc -> convertBisonIds(storage, doc, Boolean.TRUE)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MongoAppDataService.readVersion"))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                        AbstractMongoMessageResourceService.OBJECT_NOT_FOUND,
                        storage.getName(),
                        versionId));
    }

    @Override
    public Mono<Page<Map<String, Object>>> readPageVersion(
            String clientCode,
            Connection conn,
            Storage storage,
            String objectId,
            Query query,
            Boolean includeObject) {
        Query effective = this.versionQuery(query, includeObject);
        Pageable page = effective.getPageable();

        // addVersion stores objectId as the hex STRING, and filterConditionFilter only coerces
        // _id and relation fields to ObjectId, so an ObjectId here never matches anything.
        FilterCondition objectIdFilterCondition = new FilterCondition()
                .setField(OBJECT_ID)
                .setValue(objectId)
                .setOperator(FilterConditionOperator.EQUALS);

        AbstractCondition condition = effective.getCondition() == null
                ? objectIdFilterCondition
                : new ComplexCondition()
                .setConditions(List.of(objectIdFilterCondition, effective.getCondition()))
                .setOperator(ComplexConditionOperator.AND);

        Boolean count = effective.getCount();

        return FlatMapUtil.flatMapMono(
                        () -> this.getVersionCollection(clientCode, conn, storage),
                        vCollection -> this.filter(storage, condition),
                        (vCollection, bsonCondition) -> this.applyQueryOnElements(
                                        vCollection, effective, bsonCondition, page)
                                .map(doc -> this.convertBisonIds(storage, doc, Boolean.TRUE))
                                .collectList(),
                        (vCollection, bsonCondition, list) -> BooleanUtil.safeValueOf(count)
                                ? Mono.from(vCollection.countDocuments(bsonCondition))
                                : Mono.just(page.getOffset() + list.size()),
                        (vCollection, bsonCondition, list, cnt) ->
                                Mono.just(PageableExecutionUtils.getPage(list, page, cnt::longValue)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MongoAppDataService.readPageVersion"));
    }

    /**
     * Drop the {@code object} snapshot from a version read when the caller only
     * wants the audit trail.
     *
     * The snapshot is the bulk of a version row and a "who changed what, when" view
     * never looks at it. An explicit field list from the caller wins, because they
     * have already said exactly what they want.
     */
    private Query versionQuery(Query query, Boolean includeObject) {
        if (!BooleanUtil.safeValueOf(includeObject == null ? Boolean.TRUE : includeObject)
                && (query.getFields() == null || query.getFields().isEmpty()))
            return new Query()
                    .setCondition(query.getCondition())
                    .setPage(query.getPage())
                    .setSize(query.getSize())
                    .setSort(query.getSort())
                    .setCount(query.getCount())
                    .setFields(List.of(OBJECT))
                    .setExcludeFields(Boolean.TRUE);

        return query;
    }

    @Override
    public Mono<Boolean> checkIfExists(String clientCode, Connection conn, Storage storage, String id) {
        if (!ObjectId.isValid(id)) return Mono.just(Boolean.FALSE);

        BsonObjectId objectId = new BsonObjectId(new ObjectId(id));

        return FlatMapUtil.flatMapMono(() -> this.getCollection(clientCode, conn, storage), collection -> Mono.from(
                                collection.countDocuments(Filters.eq(ID, objectId)))
                        .map(e -> e > 0))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MongoAppDataService.checkIfExists"));
    }

    @Override
    public Mono<Boolean> deleteStorage(String clientCode, Connection conn, Storage storage) {
        return FlatMapUtil.flatMapMono(
                        () -> this.getCollection(clientCode, conn, storage).flatMap(collection -> Mono.from(collection.drop())
                                .then(Mono.just(Boolean.TRUE))),
                        deleted -> {
                            if (BooleanUtil.safeValueOf(storage.getIsVersioned())
                                    || BooleanUtil.safeValueOf(storage.getIsAudited()))
                                return this.getVersionCollection(clientCode, conn, storage)
                                        .flatMap(vCollection -> Mono.from(vCollection.drop()))
                                        .then(Mono.just(deleted));

                            return Mono.just(deleted);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MongoAppDataService.deleteStorage"));
    }

    @Override
    public Mono<Boolean> dropDraftStorage(String clientCode, Connection conn, Storage storage) {

        return SecurityContextUtil.getUsersContextAuthentication()
                .flatMap(ca -> {

                    MongoClient client = this.getMongoClient(conn);
                    if (client == null)
                        return Mono.just(Boolean.FALSE);

                    // Named explicitly rather than resolved from the ambient flag: this
                    // is called while deleting a definition, which happens on the live
                    // surface, so isDraft() would be false exactly when we need the
                    // draft namespace.
                    String dbName = databaseName(
                            BooleanUtil.safeValueOf(storage.getIsAppLevel()) ? ca.getUrlClientCode() : clientCode,
                            storage.getAppCode(), true);

                    var db = client.getDatabase(dbName);
                    return Mono.from(db.getCollection(storage.getUniqueName()).drop())
                            .then(Mono.from(db.getCollection(storage.getUniqueName() + "_version").drop()))
                            .thenReturn(Boolean.TRUE)
                            .onErrorReturn(Boolean.FALSE);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MongoAppDataService.dropDraftStorage"));
    }

    @Override
    public Mono<Boolean> dropDraftDatabase(Connection conn, String appCode, String clientCode) {

        if (StringUtil.safeIsBlank(appCode) || StringUtil.safeIsBlank(clientCode))
            return Mono.just(Boolean.FALSE);

        MongoClient client = this.getMongoClient(conn);
        if (client == null)
            return Mono.just(Boolean.FALSE);

        return Mono.from(client.getDatabase(databaseName(clientCode, appCode, true)).drop())
                .thenReturn(Boolean.TRUE)
                .onErrorReturn(Boolean.FALSE)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MongoAppDataService.dropDraftDatabase"));
    }

    @Override
    public Mono<Long> copyLiveToDraft(String clientCode, Connection conn, Storage storage, Boolean replace) {

        // Both surfaces are named by writing the flag onto each accessor's own
        // subscription, rather than reading the ambient one. Going through
        // getCollection (instead of client.getDatabase directly) is what keeps index
        // provisioning running on the draft side: it is memoised per
        // (uniqueName, appCode, clientCode, draftSuffix), so the draft collection has
        // to ask for it in its own right.
        Mono<MongoCollection<Document>> liveCollection = this.getCollection(clientCode, conn, storage)
                .contextWrite(Context.of(LogUtil.DRAFT_KEY, Boolean.FALSE));

        Mono<MongoCollection<Document>> draftCollection = this.getCollection(clientCode, conn, storage)
                .contextWrite(Context.of(LogUtil.DRAFT_KEY, Boolean.TRUE));

        return FlatMapUtil.<MongoCollection<Document>, MongoCollection<Document>, Long, Boolean, Long>flatMapMono(

                () -> liveCollection,

                live -> draftCollection,

                // Counted BEFORE anything is cleared. An empty source answers 0 and
                // changes nothing: clearing the draft collection and then reporting
                // "there was nothing to copy" would be the worst of both.
                //
                // 0 rather than an empty Mono, because genericOperation -- which every
                // caller goes through -- has its own switchIfEmpty that turns an empty
                // result into a 403. An empty here would surface as a denial.
                (live, draft) -> Mono.from(live.countDocuments()),

                // deleteMany, never drop(). A dropped collection with a warm index
                // cache never gets its indexes back, because manageIndexes has
                // already been memoised for this namespace.
                (live, draft, liveCount) -> liveCount > 0 && BooleanUtil.safeValueOf(replace)
                        ? Mono.from(draft.deleteMany(Filters.empty())).thenReturn(Boolean.TRUE)
                        : Mono.just(Boolean.TRUE),

                // _id is carried across unchanged, so relation fields on either surface
                // keep pointing at the row they named.
                //
                // Upserting rather than inserting is what makes this safe to run twice
                // and safe with replace = false: an insertMany over a draft collection
                // that already holds any of these ids fails on the duplicate key and
                // leaves the copy half done.
                (live, draft, liveCount, cleared) -> liveCount < 1
                        ? Mono.just(0L)
                        : Flux.from(live.find())
                                .buffer(COPY_BATCH_SIZE)
                                .concatMap(batch -> Mono.from(draft.bulkWrite(batch.stream()
                                                .map(doc -> (WriteModel<Document>) new ReplaceOneModel<>(
                                                        Filters.eq(ID, doc.get(ID)), doc, UPSERT))
                                                .toList()))
                                        .thenReturn((long) batch.size()))
                                .reduce(0L, Long::sum))

                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MongoAppDataService.copyLiveToDraft"));
    }

    private Flux<Document> applyQueryOnElements(
            MongoCollection<Document> collection, Query query, Bson bsonCondition, Pageable page) {
        if (query.getFields() == null || query.getFields().isEmpty()) {
            FindPublisher<Document> publisher = collection.find(bsonCondition);

            if (!Query.DEFAULT_SORT.equals(page.getSort())) publisher.sort(this.sort(page.getSort()));

            return Flux.from(publisher.skip((int) page.getOffset()).limit(page.getPageSize()));
        } else {
            List<Bson> pipeLines = new ArrayList<>(List.of(Aggregates.match(bsonCondition)));

            Bson sort = null;
            if (!Query.DEFAULT_SORT.equals(page.getSort())) sort = this.sort(page.getSort());

            if (sort != null) pipeLines.add(Aggregates.sort(sort));

            pipeLines.add(Aggregates.project(Projections.fields(
                    BooleanUtil.safeValueOf(query.getExcludeFields())
                            ? Projections.exclude(query.getFields())
                            : Projections.include(query.getFields()))));
            pipeLines.add(Aggregates.skip((int) page.getOffset()));
            pipeLines.add(Aggregates.limit(page.getPageSize()));

            return Flux.from(collection.aggregate(pipeLines));
        }
    }

    private Bson sort(Sort sort) {
        if (sort == null) return null;

        // An unsorted Sort would produce an empty $sort stage, which the aggregation
        // pipeline rejects with "must have at least one sort key". The find() path
        // tolerates it, so this only surfaces once a query projects fields.
        if (!sort.isSorted()) return null;

        if (sort.equals(Query.DEFAULT_SORT)) return null;

        return Sorts.orderBy(sort.stream()
                .map(e -> e.getDirection() == Direction.ASC
                        ? Sorts.ascending(e.getProperty())
                        : Sorts.descending(e.getProperty()))
                .toList());
    }

    private Mono<Bson> filter(Storage storage, AbstractCondition condition) {
        if (condition == null) return Mono.just(Filters.empty());

        Mono<Bson> built;

        if (condition instanceof ComplexCondition cc) built = this.complexConditionFilter(storage, cc);
        else if (condition instanceof FilterCondition fc) built = this.filterConditionFilter(storage, fc);
        else
            // AbstractConditionDeserializer builds a HavingCondition from any body carrying
            // aggregateFunction, and a GroupCondition from one carrying havingConditions.
            // Neither extends FilterCondition, so the blind cast this replaces turned a
            // merely unsupported request into a ClassCastException and a 500.
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    CoreMessageResourceService.UNSUPPORTED_CONDITION,
                    condition.getClass().getSimpleName());

        return built.defaultIfEmpty(Filters.empty());
    }

    private Mono<Bson> complexConditionFilter(Storage storage, ComplexCondition cc) {
        if (cc.getConditions() == null || cc.getConditions().isEmpty()) return Mono.empty();

        return Flux.concat(cc.getConditions().stream()
                        .map(e -> this.filter(storage, e))
                        .toList())
                .map(e -> cc.isNegate() ? Filters.not(e) : e)
                .collectList()
                .map(conditions -> {
                    if (cc.getOperator() == ComplexConditionOperator.AND)
                        return cc.isNegate() ? Filters.or(conditions) : Filters.and(conditions);

                    return cc.isNegate() ? Filters.and(conditions) : Filters.or(conditions);
                });
    }

    private Mono<Bson> filterConditionFilter(Storage storage, FilterCondition fc) {
        // to cover all operators, this kind of check is essential

        if (fc == null) return Mono.empty();

        if (fc.getOperator() == FilterConditionOperator.TEXT_SEARCH) {
            if (fc.getValue() == null) return Mono.empty();

            Document doc =
                    new Document(Map.of("$text", Map.of("$search", fc.getValue().toString())));
            return Mono.just(doc);
        }

        if (fc.getField() == null) return Mono.empty();

        if (fc.getOperator() == IS_FALSE || fc.getOperator() == IS_TRUE)
            return Mono.just(Filters.eq(
                    fc.getField(), fc.isNegate() ? fc.getOperator() == IS_FALSE : fc.getOperator() == IS_TRUE));

        if (fc.getOperator() == IS_NULL)
            return fc.isNegate()
                    ? Mono.just(Filters.ne(fc.getField(), null))
                    : Mono.just(Filters.or(Filters.eq(fc.getField(), null), Filters.exists(fc.getField(), false)));

        Object value = fc.getValue();
        boolean isObjectIdField = (fc.getField().equals("_id")
                || (storage.getRelations() != null && storage.getRelations().containsKey(fc.getField())));
        if (isObjectIdField && value != null) value = new ObjectId(value.toString());

        if (fc.getOperator() == IN) {
            if (value == null
                    && (fc.getMultiValue() == null || fc.getMultiValue().isEmpty())) return Mono.empty();

            BiFunction<String, Iterable<?>, Bson> function = fc.isNegate() ? Filters::nin : Filters::in;
            return Mono.just(function.apply(
                    fc.getField(), this.multiFieldValue(isObjectIdField, fc.getValue(), fc.getMultiValue())));
        }

        if (fc.getOperator() == FilterConditionOperator.MATCH) {
            if (value == null) return Mono.empty();

            Document doc = new Document(Map.of(
                    fc.getField(),
                    Map.of(
                            "$elemMatch",
                            Map.of(
                                    Objects.requireNonNull(CommonsUtil.nonNullValue(FILTER_MATCH_OPERATOR.get(fc.getMatchOperator()), "$eq")),
                                    value))));
            return Mono.just(doc);
        }

        if (fc.getOperator() == FilterConditionOperator.MATCH_ALL) {
            if (value == null
                    && (fc.getMultiValue() == null || fc.getMultiValue().isEmpty())) return Mono.empty();

            Document doc = new Document(
                    Map.of(fc.getField(), Map.of("$all", value == null ? fc.getMultiValue() : List.of(value))));

            return Mono.just(doc);
        }

        if (value == null) return Mono.empty();

        if (fc.getOperator() == BETWEEN) {
            var first = fc.isNegate() ? Filters.lt(fc.getField(), value) : Filters.gte(fc.getField(), value);
            var second = fc.isNegate()
                    ? Filters.gt(fc.getField(), fc.getToValue())
                    : Filters.lte(fc.getField(), fc.getToValue());

            if (fc.isNegate()) return Mono.just(Filters.and(first, second));
            else return Mono.just(Filters.or(first, second));
        }

        BiFunction<String, Object, Bson> function =
                switch (fc.getOperator()) {
                    case EQUALS -> fc.isNegate() ? Filters::ne : Filters::eq;
                    case GREATER_THAN -> fc.isNegate() ? Filters::lte : Filters::gt;
                    case GREATER_THAN_EQUAL -> fc.isNegate() ? Filters::lt : Filters::gte;
                    case LESS_THAN -> fc.isNegate() ? Filters::gte : Filters::lt;
                    case LESS_THAN_EQUAL -> fc.isNegate() ? Filters::gt : Filters::lte;
                    default -> null;
                };

        if (function != null) return Mono.just(function.apply(fc.getField(), value));

        Bson filter;

        switch (fc.getOperator()) {
            case LIKE -> {
                filter = Filters.regex(fc.getField(), Objects.requireNonNull(StringUtil.safeValueOf(value, "")));
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
            return !convertId
                    ? values
                    : values.stream().map(e -> new ObjectId(e.toString())).toList();

        if (objValue == null) return List.of();

        int from = 0;
        String iValue = objValue.toString().trim();

        List<Object> obj = new ArrayList<>();
        for (int i = 0; i < iValue.length(); i++) { // NOSONAR
            // Having multiple continue statements is not confusing

            if (iValue.charAt(i) != ',') continue;

            if (i != 0 && iValue.charAt(i - 1) == '\\') continue;

            String str = iValue.substring(from, i).trim();
            if (str.isEmpty()) continue;

            obj.add(!convertId ? str : new ObjectId(str));
            from = i + 1;
        }

        return obj;
    }

    private Mono<MongoCollection<Document>> getCollection(String clientCode, Connection conn, Storage storage) {
        return SecurityContextUtil.getUsersContextAuthentication()
                .flatMap(ca -> this.getCollection(
                        conn,
                        storage.getAppCode(),
                        storage.getIsAppLevel() ? ca.getUrlClientCode() : clientCode,
                        storage.getUniqueName(),
                        storage.getIndexes(),
                        storage.getTextIndexFields()));
    }

    private Mono<MongoCollection<Document>> getVersionCollection(String clientCode, Connection conn, Storage storage) {
        return SecurityContextUtil.getUsersContextAuthentication()
                .flatMap(ca -> this.getVersionCollection(
                        conn,
                        storage.getAppCode(),
                        storage.getIsAppLevel() ? ca.getUrlClientCode() : clientCode,
                        storage.getUniqueName()));
    }

    /**
     * The draft surface gets its own database, not its own collection names.
     *
     * Storage.uniqueName is the physical collection name and is generated once at
     * create time, so live and draft share it and differ only by database. That
     * means publishing a storage never has to rename or move anything, and a
     * storage created on the draft surface already has its final collection name.
     *
     * Mongo creates databases and collections lazily on first insert, so nothing
     * needs provisioning: the draft namespace comes into existence when something
     * is first written to it.
     */
    private static String databaseName(String clientCode, String appCode, boolean draft) {
        return clientCode + "_" + appCode + (draft ? IAppDataService.DRAFT_DB_SUFFIX : "");
    }

    private Mono<MongoCollection<Document>> getCollection(
            Connection conn,
            String appCode,
            String clientCode,
            String uniqueName,
            Map<String, StorageIndex> indexes,
            List<String> textIndexFields) {

        return LogUtil.isDraft().flatMap(draftFlag -> {

            boolean draft = Boolean.TRUE.equals(draftFlag);

            MongoClient client = this.getMongoClient(conn);

            if (client == null)
                throw msgService.nonReactiveMessage(
                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                        CoreMessageResourceService.CONNECTION_DETAILS_MISSING,
                        "url");

            final MongoCollection<Document> collection = client
                    .getDatabase(databaseName(clientCode, appCode, draft))
                    .getCollection(uniqueName);

            // The draft flag has to be part of this key too. Index provisioning runs
            // once per (storage, app, client) and is memoised; without the flag the
            // live namespace's run would satisfy the draft namespace's and the draft
            // collection would never get its indexes.
            return cacheService
                    .cacheValueOrGet(
                            uniqueName + IAppDataService.CACHE_SUFFIX_FOR_INDEX_CREATION,
                            () -> this.manageIndexes(collection, indexes, textIndexFields),
                            appCode,
                            clientCode,
                            draft ? IAppDataService.DRAFT_DB_SUFFIX : "")
                    .map(e -> collection);
        });
    }

    private Mono<MongoCollection<Document>> getVersionCollection(
            Connection conn, String appCode, String clientCode, String uniqueName) {

        return LogUtil.isDraft().map(draftFlag -> {

            MongoClient client = this.getMongoClient(conn);

            if (client == null)
                throw msgService.nonReactiveMessage(
                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                        CoreMessageResourceService.CONNECTION_DETAILS_MISSING,
                        "url");

            return client
                    .getDatabase(databaseName(clientCode, appCode, Boolean.TRUE.equals(draftFlag)))
                    .getCollection(uniqueName + "_version");
        });
    }

    /**
     * Estimated total document count across M's runtime database
     * {@code {clientCode}_{appCode}}, used for token metering (action
     * {@code core.storage.rows}). The Mongo client is resolved from the app's appData
     * {@link Connection} (null falls back to the platform default client). Sums
     * {@code estimatedDocumentCount()} over every data collection (excluding
     * {@code *_version} and {@code system.*}). No SecurityContext and no index
     * management; a per-collection failure counts as zero.
     */
    public Mono<Long> estimatedRowCount(Connection conn, String appCode, String clientCode) {
        if (StringUtil.safeIsBlank(appCode) || StringUtil.safeIsBlank(clientCode))
            return Mono.just(0L);

        MongoClient client = this.getMongoClient(conn);
        if (client == null)
            return Mono.just(0L);

        // Counts BOTH surfaces. Draft rows are real rows in a real database and are
        // billed like live ones.
        //
        // This is the one place in this class that must NOT consult LogUtil.isDraft().
        // The meter runs from a scheduled job (CoreBillingMeteringService's 15-minute
        // window and daily reconcile) with no inbound request, so the ambient flag is
        // always false here. It has to name both databases explicitly. Do not
        // "simplify" this into the draft-aware accessors.
        return Flux.just(databaseName(clientCode, appCode, false), databaseName(clientCode, appCode, true))
                .flatMap(dbName -> this.countRowsIn(client, dbName))
                .reduce(0L, (a, b) -> a + b)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MongoAppDataService.estimatedRowCount"));
    }

    /**
     * An absent database counts as zero rather than failing the metering window.
     * Most apps never have a draft surface, so the draft database usually does not
     * exist, and listCollectionNames on a missing database is the normal case here
     * rather than an error worth propagating.
     */
    private Mono<Long> countRowsIn(MongoClient client, String dbName) {

        var db = client.getDatabase(dbName);
        return Flux.from(db.listCollectionNames())
                .filter(name -> name != null && !name.endsWith("_version") && !name.startsWith("system."))
                .flatMap(name -> Mono.from(db.getCollection(name).estimatedDocumentCount())
                        .onErrorReturn(0L))
                .reduce(0L, (a, b) -> a + b)
                .onErrorReturn(0L);
    }

    private Map<String, Object> convertBisonIds(Storage storage, Document document, boolean isVersion) {
        this.convertBisonId(document, ID);

        if (isVersion) {
            this.convertBisonId(document, OBJECT_ID);
            this.convertVersionCreatedAt(document);
        }

        return storage.getRelations() != null ? this.updateDocWithIds(storage, document) : document;
    }

    private void convertBisonId(Document document, String key) {
        if (!document.containsKey(key)) return;

        // addVersion already writes objectId as a hex string, so this runs against values that
        // need no conversion. getObjectId would throw on those.
        if (!(document.get(key) instanceof ObjectId objectId)) return;

        this.removeKey(document, key);
        document.append(key, objectId.toHexString());
    }

    /**
     * Version rows carry a BSON date, but every other timestamp the client receives is epoch
     * SECONDS and its date formatter reads nothing else. Normalising here lets a version list be
     * formatted with the same binding as any other date, instead of each caller special-casing it.
     */
    private void convertVersionCreatedAt(Document document) {
        if (!(document.get(CREATED_AT) instanceof Date createdAt)) return;

        this.removeKey(document, CREATED_AT);
        document.append(CREATED_AT, createdAt.getTime() / 1000L);
    }

    private Document removeKey(Document document, String key) {
        document.remove(key);
        return document;
    }

    private Map<String, Object> updateDocWithIds(Storage storage, Document doc) {
        for (Entry<String, StorageRelation> relationEntry :
                storage.getRelations().entrySet()) {
            if (!doc.containsKey(relationEntry.getKey())) continue;

            Object v = doc.get(relationEntry.getKey());
            if (v instanceof ObjectId oId) doc.put(relationEntry.getKey(), oId.toHexString());
            else if (v instanceof List<?> list) {
                List<String> newList = new ArrayList<>();
                for (Object o : list) {
                    if (o instanceof ObjectId oId) newList.add(oId.toHexString());
                    else if (o != null) newList.add(o.toString());
                }
                doc.put(relationEntry.getKey(), newList);
            }
        }

        return doc;
    }

    private Mono<Boolean> manageIndexes(
            MongoCollection<Document> collection, Map<String, StorageIndex> indexes, List<String> textIndexFields) {
        return FlatMapUtil.flatMapMonoWithNull(
                        () -> this.dropRemovedIndexes(collection, indexes, textIndexFields),
                        x -> {
                            if (indexes == null || indexes.isEmpty()) return Mono.empty();

                            return Flux.fromIterable(indexes.entrySet())
                                    .flatMap(e -> {
                                        StorageIndex ins = e.getValue();

                                        if (ins.getFields().isEmpty()) return Mono.just(Boolean.TRUE);

                                        List<Bson> bDocs = ins.getFields().stream()
                                                .map(si -> si.getDirection() == Direction.ASC
                                                        ? Indexes.ascending(si.getFieldName())
                                                        : Indexes.descending(si.getFieldName()))
                                                .toList();

                                        Bson ind = bDocs.size() == 1 ? bDocs.getFirst() : Indexes.compoundIndex(bDocs);

                                        IndexOptions options = new IndexOptions()
                                                .name(e.getKey())
                                                .unique(ins.isUnique());
                                        return Mono.from(collection.createIndex(ind, options))
                                                .map(y -> Boolean.TRUE);
                                    })
                                    .collectList()
                                    .map(e -> Boolean.TRUE);
                        },
                        (x, ins) -> {
                            if (textIndexFields == null || textIndexFields.isEmpty()) return Mono.empty();

                            return Mono.from(collection.createIndex(
                                            new Document(textIndexFields.stream()
                                                    .collect(Collectors.toMap(field -> field, value -> "text"))),
                                            new IndexOptions().name(TEXT_INDEX_NAME)))
                                    .map(e -> Boolean.TRUE);
                        })
                .defaultIfEmpty(Boolean.TRUE)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MongoAppDataService.getCollection (Cache Supplier)"));
    }

    private Mono<Boolean> dropRemovedIndexes(
            MongoCollection<Document> collection, Map<String, StorageIndex> indexes, List<String> textIndexFields) {
        return Flux.from(collection.listIndexes())
                .filter(e -> {
                    String indexName = e.getString("name");
                    if (indexName.equals("_id_")) return Boolean.FALSE;

                    if (indexName.equals(TEXT_INDEX_NAME))
                        return (textIndexFields == null || textIndexFields.isEmpty());

                    if (indexes == null || indexes.isEmpty()) return Boolean.TRUE;

                    return !indexes.containsKey(indexName);
                })
                .flatMap(e -> Mono.from(collection.dropIndex(e.getString("name"))))
                .then(Mono.just(Boolean.TRUE));
    }

    private MongoClient getMongoClient(Connection connection) {
        return connection == null
                ? defaultClient
                : mongoClients.computeIfAbsent(
                getConnectionString(connection), key -> this.createMongoClient(connection));
    }

    private synchronized MongoClient createMongoClient(Connection conn) {
        if (conn.getConnectionDetails() == null
                || StringUtil.safeIsBlank(conn.getConnectionDetails().get("url")))
            throw msgService.nonReactiveMessage(
                    msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                    CoreMessageResourceService.CONNECTION_DETAILS_MISSING,
                    "url");

        return MongoClients.create(conn.getConnectionDetails().get("url").toString());
    }

    private String getConnectionString(Connection conn) {
        return "Connection : " + conn.getId();
    }
}
