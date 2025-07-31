package com.fincity.saas.commons.core.service.connection.appdata;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.array.ArraySchemaType;
import com.fincity.nocode.kirun.engine.json.schema.reactive.ReactiveSchemaUtil;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.reactive.ReactiveHybridRepository;
import com.fincity.nocode.kirun.engine.repository.reactive.KIRunReactiveSchemaRepository;
import com.fincity.nocode.kirun.engine.runtime.expression.tokenextractor.ObjectValueSetterExtractor;
import com.fincity.nocode.kirun.engine.util.primitive.PrimitiveUtil;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.document.Connection;
import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.enums.ConnectionSubType;
import com.fincity.saas.commons.core.enums.ConnectionType;
import com.fincity.saas.commons.core.enums.StorageRelationConstraint;
import com.fincity.saas.commons.core.enums.StorageRelationType;
import com.fincity.saas.commons.core.enums.StorageTriggerType;
import com.fincity.saas.commons.core.kirun.repository.CoreSchemaRepository;
import com.fincity.saas.commons.core.model.DataObject;
import com.fincity.saas.commons.core.model.StorageRelation;
import com.fincity.saas.commons.core.service.ConnectionService;
import com.fincity.saas.commons.core.service.CoreFunctionService;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import com.fincity.saas.commons.core.service.CoreSchemaService;
import com.fincity.saas.commons.core.service.EventDefinitionService;
import com.fincity.saas.commons.core.service.StorageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.file.DataFileReader;
import com.fincity.saas.commons.file.DataFileWriter;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.util.CloneUtil;
import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.DataFileType;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.MapUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.expression.ParseException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ZeroCopyHttpOutputMessage;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

@Service
public class AppDataService {

        private static final ConnectionSubType DEFAULT_APP_DATA_SERVICE = ConnectionSubType.MONGO;
        private static final Logger logger = LoggerFactory.getLogger(AppDataService.class);
        private static final String DATA_OBJECT_KEY = "dataObject";
        private static final String EXISTING_DATA_OBJECT_KEY = "existingDataObject";
        private final EnumMap<ConnectionSubType, IAppDataService> services = new EnumMap<>(ConnectionSubType.class);

        @Autowired
        private ConnectionService connectionService;

        @Autowired
        @Lazy
        private StorageService storageService;

        @Autowired
        private MongoAppDataService mongoAppDataService;

        @Autowired
        private CoreSchemaService schemaService;

        @Autowired
        private CoreMessageResourceService msgService;

        @Autowired
        @Lazy
        private CoreFunctionService functionService;

        @Autowired
        @Lazy
        private EventDefinitionService eventDefinitionService;

        @Autowired
        @Lazy
        private EventCreationService ecService;

        @Autowired
        private Gson gson;

        private static Object getElementBySchemaType(Set<SchemaType> schemaTypes, String value) {
                if (StringUtil.safeIsBlank(value) || "null".equalsIgnoreCase(value))
                        return null;

                if (schemaTypes == null)
                        return value;

                if (schemaTypes.contains(SchemaType.STRING))
                        return value;

                if (schemaTypes.contains(SchemaType.BOOLEAN)) {
                        try {
                                return BooleanUtil.parse(value);
                        } catch (ParseException pe) {
                                return PrimitiveUtil.findPrimitiveNumberType(new JsonPrimitive(value))
                                                .getT2();
                        }
                }

                return PrimitiveUtil.findPrimitiveNumberType(new JsonPrimitive(value)).getT2();
        }

        @PostConstruct
        public void init() {
                this.services.put(ConnectionSubType.MONGO, mongoAppDataService);
        }

        public Mono<Map<String, Object>> create(
                        String appCode,
                        String clientCode,
                        String storageName,
                        DataObject dataObject,
                        Boolean eager,
                        List<String> eagerFields) {
                Mono<Map<String, Object>> mono = FlatMapUtil.flatMapMonoWithNull(
                                SecurityContextUtil::getUsersContextAuthentication,
                                ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),
                                (ca, ac) -> Mono.just(clientCode == null ? ca.getUrlClientCode() : clientCode),
                                (ca, ac, cc) -> connectionService.read("appData", ac, cc, ConnectionType.APP_DATA),
                                (ca, ac, cc, conn) -> Mono.just(
                                                this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE
                                                                : conn.getConnectionSubType())),
                                (ca, ac, cc, conn, dataService) -> getStorageWithKIRunValidation(storageName, ac, cc)
                                                .map(ObjectWithUniqueID::getObject),
                                (ca, ac, cc, conn, dataService, storage) -> this.<Map<String, Object>>genericOperation(
                                                storage,
                                                (cona, hasAccess) -> FlatMapUtil.flatMapMono(
                                                                () -> this.processRelationsForCreate(ac, cc, storage,
                                                                                dataObject, dataService, conn),
                                                                updatedDataObject -> this.createWithTriggers(
                                                                                dataService, conn, storage,
                                                                                updatedDataObject),
                                                                (updatedDataObject, created) -> {
                                                                        if (!BooleanUtil.safeValueOf(
                                                                                        storage.getGenerateEvents()))
                                                                                return Mono.just(created);

                                                                        return this.generateEvent(ca, ac, cc, storage,
                                                                                        "Create", created, null);
                                                                }),
                                                Storage::getCreateAuth,
                                                CoreMessageResourceService.FORBIDDEN_CREATE_STORAGE),
                                (ca, ac, cc, conn, dataService, storage, created) -> this.fillRelatedObjects(
                                                ac,
                                                cc,
                                                storage,
                                                created,
                                                dataService,
                                                conn,
                                                BooleanUtil.safeValueOf(eager)
                                                                ? storage.getRelations().keySet().stream().toList()
                                                                : eagerFields));

                return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.create"));
        }

        public Mono<List<Map<String, Object>>> createMany(
                        String appCode,
                        String clientCode,
                        String storageName,
                        List<DataObject> dataArray,
                        Boolean eager,
                        List<String> eagerFields) {
                Mono<List<Map<String, Object>>> mono = FlatMapUtil.flatMapMonoWithNull(
                                SecurityContextUtil::getUsersContextAuthentication,
                                ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),
                                (ca, ac) -> Mono.just(clientCode == null ? ca.getUrlClientCode() : clientCode),
                                (ca, ac, cc) -> connectionService.read("appData", ac, cc, ConnectionType.APP_DATA),
                                (ca, ac, cc, conn) -> Mono.just(
                                                this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE
                                                                : conn.getConnectionSubType())),
                                (ca, ac, cc, conn, dataService) -> getStorageWithKIRunValidation(storageName, ac, cc)
                                                .map(ObjectWithUniqueID::getObject),
                                (ca, ac, cc, conn, dataService, storage) -> Flux.fromIterable(dataArray)
                                                .flatMapSequential(dataObject -> FlatMapUtil.flatMapMono(
                                                                () -> this.processRelationsForCreate(
                                                                                ac, cc, storage, dataObject,
                                                                                dataService, conn),
                                                                updatedDataObject -> this.createWithTriggers(
                                                                                dataService, conn, storage,
                                                                                updatedDataObject))
                                                                .flatMap(createdObj -> {
                                                                        if (BooleanUtil.safeValueOf(
                                                                                        storage.getGenerateEvents())) {
                                                                                return this.generateEvent(
                                                                                                ca,
                                                                                                ac,
                                                                                                cc,
                                                                                                storage,
                                                                                                "Create",
                                                                                                Map.of("dataArr", List
                                                                                                                .of(createdObj)),
                                                                                                null);
                                                                        }
                                                                        return Mono.just(createdObj);
                                                                })
                                                                .flatMap(createdObj -> this.fillRelatedObjects(
                                                                                ac,
                                                                                cc,
                                                                                storage,
                                                                                createdObj,
                                                                                dataService,
                                                                                conn,
                                                                                BooleanUtil.safeValueOf(eager)
                                                                                                ? storage.getRelations()
                                                                                                                .keySet()
                                                                                                                .stream()
                                                                                                                .toList()
                                                                                                : eagerFields)))
                                                .collectList());
                return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.createMany"));
        }

        private Mono<Map<String, Object>> fillRelatedObjects(
                        String appCode,
                        String clientCode,
                        Storage storage,
                        Map<String, Object> created,
                        IAppDataService dataService,
                        Connection conn,
                        List<String> eagerFields) {
                if ((storage.getRelations() == null || storage.getRelations().isEmpty())
                                || (eagerFields == null || eagerFields.isEmpty()))
                        return Mono.just(created);

                List<Mono<Tuple3<String, StorageRelationType, List<Map<String, Object>>>>> relationList = prepareMonosForPage(
                                appCode, clientCode, storage, dataService, conn, eagerFields, created);

                return FlatMapUtil.flatMapMono(
                                () -> Flux.fromIterable(relationList).flatMap(e -> e).collectList(), tuples -> {
                                        for (Tuple3<String, StorageRelationType, List<Map<String, Object>>> tuple : tuples) {
                                                if (tuple.getT2() == StorageRelationType.TO_MANY) {
                                                        List<String> oldList = (List<String>) created
                                                                        .get(tuple.getT1());
                                                        created.put(
                                                                        tuple.getT1(),
                                                                        tuple.getT3().stream()
                                                                                        .sorted((a, b) -> oldList
                                                                                                        .indexOf(a.get("_id"))
                                                                                                        - oldList.indexOf(
                                                                                                                        b.get("_id")))
                                                                                        .toList());
                                                } else {
                                                        if (tuple.getT3().isEmpty())
                                                                created.remove(tuple.getT1());
                                                        else
                                                                created.put(tuple.getT1(), tuple.getT3().getFirst());
                                                }
                                        }

                                        return Mono.just(created);
                                })
                                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.fillRelatedObjects"));
        }

        private List<Mono<Tuple3<String, StorageRelationType, List<Map<String, Object>>>>> prepareMonosForPage(
                        String appCode,
                        String clientCode,
                        Storage storage,
                        IAppDataService dataService,
                        Connection conn,
                        List<String> eagerFields,
                        Map<String, Object> created) {
                List<Mono<Tuple3<String, StorageRelationType, List<Map<String, Object>>>>> relationList = new ArrayList<>();

                for (String key : eagerFields) {
                        StorageRelation relation = storage.getRelations().get(key);
                        Query query = new Query();
                        query.setSize(50);
                        List<String> value = new ArrayList<>();
                        if (created.get(key) instanceof List<?> lst) {
                                value = lst.stream().map(Object::toString).toList();
                        } else if (created.get(key) instanceof String id) {
                                value = List.of(id);
                        }
                        query.setCondition(new FilterCondition()
                                        .setField("_id")
                                        .setOperator(FilterConditionOperator.IN)
                                        .setMultiValue(value));
                        relationList.add(FlatMapUtil.flatMapMono(
                                        () -> this.getStorageWithKIRunValidation(relation.getStorageName(), appCode,
                                                        clientCode)
                                                        .map(ObjectWithUniqueID::getObject),
                                        storageObj -> dataService
                                                        .readPageAsFlux(conn, storageObj, query)
                                                        .collectList()
                                                        .map(e -> Tuples.of(key, relation.getRelationType(), e))));
                }
                return relationList;
        }

        private Mono<Map<String, Object>> generateEvent(
                        ContextAuthentication ca,
                        String appCode,
                        String clientCode,
                        Storage storage,
                        String operation,
                        Map<String, Object> data,
                        Map<String, Object> existing) {
                if (storage.getGenerateEvents() == null || !storage.getGenerateEvents())
                        return Mono.just(data);

                String eventName = "Storage." + storage.getName() + "." + operation;

                return FlatMapUtil.flatMapMono(
                                () -> this.eventDefinitionService
                                                .read(eventName, appCode, clientCode)
                                                .map(Optional::of)
                                                .defaultIfEmpty(Optional.empty()),
                                op -> {
                                        if (op.isEmpty())
                                                return Mono.just(data);

                                        HashMap<String, Object> eventData = new HashMap<>();

                                        eventData.put(DATA_OBJECT_KEY, data);
                                        if (BooleanUtil.safeValueOf(
                                                        op.get().getObject().getIncludeContextAuthentication()))
                                                eventData.put("authentication", ca);

                                        if (existing != null)
                                                eventData.put(EXISTING_DATA_OBJECT_KEY, existing);

                                        return this.ecService
                                                        .createEvent(new EventQueObject()
                                                                        .setAppCode(appCode)
                                                                        .setClientCode(clientCode)
                                                                        .setEventName(eventName)
                                                                        .setData(eventData))
                                                        .map(e -> data);
                                });
        }

        private Mono<Boolean> executeTriggers(
                        Storage storage, StorageTriggerType triggerType, Map<String, JsonElement> args) {
                return Flux.fromIterable(storage.getTriggers().get(triggerType))
                                .flatMap(trigger -> this.functionService.execute(
                                                trigger.substring(0, trigger.lastIndexOf('.')),
                                                trigger.substring(trigger.lastIndexOf('.') + 1),
                                                storage.getAppCode(),
                                                storage.getClientCode(),
                                                args,
                                                null))
                                .collectList()
                                .map(e -> true);
        }

        private Mono<Map<String, Object>> createWithTriggers(
                        IAppDataService dataService, Connection conn, Storage storage, DataObject dataObject) {
                boolean noBeforeCreate = storage.getTriggers() == null
                                || storage.getTriggers().get(StorageTriggerType.BEFORE_CREATE) == null
                                || storage.getTriggers().get(StorageTriggerType.BEFORE_CREATE).isEmpty();

                boolean noAfterCreate = storage.getTriggers() == null
                                || storage.getTriggers().get(StorageTriggerType.AFTER_CREATE) == null
                                || storage.getTriggers().get(StorageTriggerType.AFTER_CREATE).isEmpty();

                if (noBeforeCreate && noAfterCreate)
                        return dataService.create(conn, storage, dataObject);

                return FlatMapUtil.flatMapMono(
                                () -> {
                                        if (noBeforeCreate)
                                                return Mono.just(true);

                                        return this.executeTriggers(
                                                        storage,
                                                        StorageTriggerType.BEFORE_CREATE,
                                                        Map.of(DATA_OBJECT_KEY,
                                                                        this.gson.toJsonTree(dataObject.getData())));
                                },
                                beforeCreate -> dataService.create(conn, storage, dataObject),
                                (beforeCreate, created) -> {
                                        if (noAfterCreate)
                                                return Mono.just(created);

                                        return this.executeTriggers(
                                                        storage,
                                                        StorageTriggerType.AFTER_CREATE,
                                                        Map.of(DATA_OBJECT_KEY, this.gson.toJsonTree(created)))
                                                        .map(e -> created);
                                })
                                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.genericCreate"));
        }

        private Mono<Tuple2<Boolean, String>> checkOrCreateRelatedObject(
                        String appCode,
                        String clientCode,
                        IAppDataService dataService,
                        Connection conn,
                        String storageName,
                        Map<String, Object> map) {
                return FlatMapUtil.flatMapMono(
                                () -> this.getStorageWithKIRunValidation(storageName, appCode, clientCode)
                                                .map(ObjectWithUniqueID::getObject),
                                storage -> {
                                        if (!StringUtil.safeIsBlank(map.get("_id")))
                                                return dataService
                                                                .checkifExists(
                                                                                conn, storage,
                                                                                map.get("_id").toString())
                                                                .flatMap(e -> {
                                                                        if (e)
                                                                                return Mono.just(Tuples.of(
                                                                                                false,
                                                                                                map.get("_id").toString()));

                                                                        return this.msgService.throwMessage(
                                                                                        msg -> new GenericException(
                                                                                                        HttpStatus.NOT_FOUND,
                                                                                                        msg),
                                                                                        AbstractMongoMessageResourceService.OBJECT_NOT_FOUND,
                                                                                        storageName,
                                                                                        map.get("_id").toString());
                                                                });

                                        return this.create(
                                                        appCode,
                                                        clientCode,
                                                        storageName,
                                                        new DataObject().setData(map),
                                                        false,
                                                        List.of())
                                                        .map(e -> Tuples.of(true, e.get("_id").toString()));
                                })
                                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                                                "AppDataService.checkOrCreateRelatedObject"));
        }

        private Mono<DataObject> processRelationsForCreate(
                        String appCode,
                        String clientCode,
                        Storage storage,
                        DataObject dataObject,
                        IAppDataService dataService,
                        Connection conn) {
                if (storage.getRelations() == null || storage.getRelations().isEmpty())
                        return Mono.just(dataObject);

                Map<String, Object> dob = CloneUtil.cloneMapObject(dataObject.getData());
                List<Mono<RelationDataObject>> relationList = getRelationDataObjectList(appCode, clientCode, storage,
                                dataService, conn, dob);

                if (relationList.isEmpty())
                        return Mono.just(dataObject);

                return FlatMapUtil.flatMapMono(
                                () -> Flux.fromIterable(relationList).flatMap(e -> e).collectList(), list -> {
                                        List<RelationDataObject> errorObjects = list.stream()
                                                        .filter(e -> !Objects.isNull(e.getException()))
                                                        .toList();
                                        if (!errorObjects.isEmpty())
                                                return this.checkAndDeleteCreatedObjects(
                                                                appCode, clientCode, storage, dataService, conn, list,
                                                                errorObjects);

                                        for (Entry<String, List<RelationDataObject>> e : list.stream()
                                                        .collect(Collectors
                                                                        .groupingBy(RelationDataObject::getFieldName))
                                                        .entrySet()) {
                                                if (e.getValue() == null || e.getValue().isEmpty()) {
                                                        dob.remove(e.getKey());
                                                        continue;
                                                }

                                                StorageRelation relation = storage.getRelations().get(e.getKey());

                                                if (relation.getRelationType() == StorageRelationType.TO_MANY) {
                                                        List<String> ids = e.getValue().stream()
                                                                        .map(RelationDataObject::getId)
                                                                        .toList();
                                                        dob.put(e.getKey(), ids);
                                                } else {
                                                        dob.put(e.getKey(), e.getValue().getFirst().getId());
                                                }
                                        }

                                        dataObject.setData(dob);
                                        return Mono.just(dataObject);
                                })
                                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.processRelations"));
        }

        private Mono<Boolean> deleteCreatedRelatedObject(
                        String appCode,
                        String clientCode,
                        IAppDataService dataService,
                        Connection conn,
                        String storageName,
                        String id) {
                return FlatMapUtil.flatMapMono(
                                () -> this.getStorageWithKIRunValidation(storageName, appCode, clientCode)
                                                .map(ObjectWithUniqueID::getObject),
                                storage -> dataService.delete(conn, storage, id))
                                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                                                "AppDataService.deleteCreatedRelatedObject"));
        }

        private Mono<DataObject> checkAndDeleteCreatedObjects(
                        String appCode,
                        String clientCode,
                        Storage storage,
                        IAppDataService dataService,
                        Connection conn,
                        List<RelationDataObject> list,
                        List<RelationDataObject> errorObjects) {
                List<RelationDataObject> createdList = list.stream()
                                .filter(e -> Objects.isNull(e.getException()))
                                .filter(RelationDataObject::isNew)
                                .toList();

                if (createdList.isEmpty())
                        return this.msgService.throwMessage(
                                        msg -> new GenericException(
                                                        HttpStatus.BAD_REQUEST, msg,
                                                        errorObjects.getFirst().getException()),
                                        CoreMessageResourceService.INVALID_RELATION_DATA,
                                        errorObjects.getFirst().getFieldName(),
                                        errorObjects.getFirst().getData().toString());

                return Flux.fromIterable(createdList)
                                .flatMap(e -> this.deleteCreatedRelatedObject(
                                                appCode,
                                                clientCode,
                                                dataService,
                                                conn,
                                                storage.getRelations().get(e.getFieldName()).getStorageName(),
                                                e.getId())
                                                .onErrorResume(th -> Mono.just(true)))
                                .collectList()
                                .flatMap(e -> this.msgService.throwMessage(
                                                msg -> new GenericException(
                                                                HttpStatus.BAD_REQUEST,
                                                                msg,
                                                                errorObjects.getFirst().getException()),
                                                CoreMessageResourceService.INVALID_RELATION_DATA,
                                                errorObjects.getFirst().getFieldName(),
                                                errorObjects.getFirst().getData().toString()));
        }

        private List<Mono<RelationDataObject>> getRelationDataObjectList(
                        String appCode,
                        String clientCode,
                        Storage storage,
                        IAppDataService dataService,
                        Connection conn,
                        Map<String, Object> dob) {
                List<Mono<RelationDataObject>> relationList = new ArrayList<>();

                for (Entry<String, StorageRelation> relation : storage.getRelations().entrySet()) {
                        String key = relation.getKey();

                        if (dob.get(key) == null)
                                continue;

                        List<Map<String, Object>> list = convertForeignKeyValuesToObjects(dob, relation, key);

                        if (!list.isEmpty()) {
                                for (Map<String, Object> map : list) {
                                        relationList.add(this.checkOrCreateRelatedObject(
                                                        appCode,
                                                        clientCode,
                                                        dataService,
                                                        conn,
                                                        relation.getValue().getStorageName(),
                                                        map)
                                                        .map(e -> new RelationDataObject(key, e.getT1(), map, e.getT2(),
                                                                        null))
                                                        .onErrorResume(e -> Mono.just(new RelationDataObject(key, false,
                                                                        map, null, e))));
                                }
                        }
                }
                return relationList;
        }

        private List<Map<String, Object>> convertForeignKeyValuesToObjects(
                        Map<String, Object> dob, Entry<String, StorageRelation> relation, String key) {
                List<Map<String, Object>> list = List.of();
                if (relation.getValue().getRelationType() == StorageRelationType.TO_MANY) {
                        if (dob.get(key) instanceof List<?> lst)
                                list = lst.stream()
                                                .map(e -> e instanceof Map ? (Map<String, Object>) e
                                                                : Map.of("_id", (Object) e.toString()))
                                                .toList();
                        else if (dob.get(key) instanceof String ids)
                                list = Stream.of(ids.split(","))
                                                .map(String::trim)
                                                .map(id -> Map.of("_id", (Object) id))
                                                .toList();
                } else {
                        if (dob.get(key) instanceof Map<?, ?>)
                                list = List.of((Map<String, Object>) dob.get(key));
                        else
                                list = List.of(Map.of("_id", dob.get(key).toString()));
                }
                return list;
        }

        public Mono<Map<String, Object>> update(
                        String appCode,
                        String clientCode,
                        String storageName,
                        DataObject dataObject,
                        Boolean override,
                        Boolean eager,
                        List<String> eagerFields) {
                Mono<Map<String, Object>> mono = FlatMapUtil.flatMapMonoWithNull(
                                SecurityContextUtil::getUsersContextAuthentication,
                                ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),
                                (ca, ac) -> Mono.just(clientCode == null ? ca.getUrlClientCode() : clientCode),
                                (ca, ac, cc) -> connectionService.read("appData", ac, cc, ConnectionType.APP_DATA),
                                (ca, ac, cc, conn) -> Mono.just(
                                                this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE
                                                                : conn.getConnectionSubType())),
                                (ca, ac, cc, conn, dataService) -> getStorageWithKIRunValidation(storageName, ac, cc)
                                                .map(ObjectWithUniqueID::getObject),
                                (ca, ac, cc, conn, dataService, storage) -> this.genericOperation(
                                                storage,
                                                (cona, hasAccess) -> FlatMapUtil.flatMapMono(
                                                                () -> this.processRelationsForUpdate(
                                                                                ac, cc, dataService, conn, storage,
                                                                                dataObject, override),
                                                                updatedDataObject -> this.updateWithTriggers(
                                                                                ac, cc, dataService, conn, storage,
                                                                                updatedDataObject, override),
                                                                (updatedDataObject, e) -> this.generateEvent(
                                                                                ca,
                                                                                appCode,
                                                                                clientCode,
                                                                                storage,
                                                                                "Update",
                                                                                e.getT1(),
                                                                                e.getT2().orElse(null))),
                                                Storage::getUpdateAuth,
                                                CoreMessageResourceService.FORBIDDEN_UPDATE_STORAGE),
                                (ca, ac, cc, conn, dataService, storage, updated) -> this.fillRelatedObjects(
                                                ac,
                                                cc,
                                                storage,
                                                updated,
                                                dataService,
                                                conn,
                                                BooleanUtil.safeValueOf(eager)
                                                                ? storage.getRelations().keySet().stream().toList()
                                                                : eagerFields));

                return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.update"));
        }

        private Mono<DataObject> processRelationsForUpdate(
                        String appCode,
                        String clientCode,
                        IAppDataService dataService,
                        Connection conn,
                        Storage storage,
                        DataObject dataObject,
                        Boolean override) {
                if (storage.getRelations() == null || storage.getRelations().isEmpty())
                        return Mono.just(dataObject);

                final Map<String, Object> dob = CloneUtil.cloneMapObject(dataObject.getData());

                List<Mono<RelationDataObject>> relationList = getRelationDataObjectList(appCode, clientCode, storage,
                                dataService, conn, dob);

                return FlatMapUtil.flatMapMono(
                                () -> this.read(
                                                appCode, clientCode, storage.getName(), dob.get("_id").toString(),
                                                false, List.of()),
                                existing -> Flux.fromIterable(relationList).flatMap(e -> e).collectList(),
                                (existing, list) -> {
                                        List<RelationDataObject> errorObjects = list.stream()
                                                        .filter(e -> !Objects.isNull(e.getException()))
                                                        .toList();
                                        if (!errorObjects.isEmpty())
                                                return this.checkAndDeleteCreatedObjects(
                                                                appCode, clientCode, storage, dataService, conn, list,
                                                                errorObjects);

                                        List<Mono<Boolean>> removalList = new ArrayList<>();

                                        for (Entry<String, List<RelationDataObject>> e : list.stream()
                                                        .collect(Collectors
                                                                        .groupingBy(RelationDataObject::getFieldName))
                                                        .entrySet()) {
                                                if ((override && !dob.containsKey(e.getKey())
                                                                && existing.get(e.getKey()) == null)
                                                                || (!override && !dob.containsKey(e.getKey())))
                                                        continue;

                                                StorageRelation relation = storage.getRelations().get(e.getKey());

                                                if (relation.getUpdateConstraint() == StorageRelationConstraint.CASCADE) {
                                                        if (!override)
                                                                continue;

                                                        Set<Tuple2<String, String>> allIds = new HashSet<>();
                                                        if (existing.get(e.getKey()) instanceof List<?> lst) {
                                                                allIds = lst.stream()
                                                                                .map(id -> Tuples.of(relation
                                                                                                .getStorageName(),
                                                                                                id.toString()))
                                                                                .collect(Collectors.toCollection(
                                                                                                HashSet::new));
                                                        } else if (existing.get(e.getKey()) instanceof String id) {
                                                                allIds.add(Tuples.of(relation.getStorageName(), id));
                                                        }

                                                        if (dob.get(e.getKey()) instanceof List<?> lst) {
                                                                for (Object id : lst) {
                                                                        Tuple2<String, String> tup = Tuples.of(
                                                                                        relation.getStorageName(),
                                                                                        id.toString());
                                                                        allIds.remove(tup);
                                                                }
                                                        } else if (dob.get(e.getKey()) instanceof String id) {
                                                                Tuple2<String, String> tup = Tuples
                                                                                .of(relation.getStorageName(), id);
                                                                allIds.remove(tup);
                                                        }

                                                        if (!allIds.isEmpty()) {
                                                                for (Tuple2<String, String> id : allIds) {
                                                                        removalList.add(this.deleteCreatedRelatedObject(
                                                                                        appCode, clientCode,
                                                                                        dataService, conn, id.getT1(),
                                                                                        id.getT2())
                                                                                        .onErrorResume(th -> Mono
                                                                                                        .just(true)));
                                                                }
                                                        }
                                                }

                                                if (relation.getRelationType() == StorageRelationType.TO_MANY) {
                                                        List<String> ids = e.getValue().stream()
                                                                        .map(RelationDataObject::getId)
                                                                        .toList();
                                                        dob.put(e.getKey(), ids);
                                                } else {
                                                        dob.put(e.getKey(), e.getValue().getFirst().getId());
                                                }
                                        }

                                        dataObject.setData(dob);
                                        return Flux.fromIterable(removalList)
                                                        .flatMap(e -> e)
                                                        .collectList()
                                                        .map(e -> dataObject);
                                });
        }

        private Mono<Tuple2<Map<String, Object>, Optional<Map<String, Object>>>> updateWithTriggers(
                        String appCode,
                        String clientCode,
                        IAppDataService dataService,
                        Connection conn,
                        Storage storage,
                        DataObject dataObject,
                        Boolean override) {
                boolean noBeforeUpdate = storage.getTriggers() == null
                                || storage.getTriggers().get(StorageTriggerType.BEFORE_UPDATE) == null
                                || storage.getTriggers().get(StorageTriggerType.BEFORE_UPDATE).isEmpty();

                boolean noAfterUpdate = storage.getTriggers() == null
                                || storage.getTriggers().get(StorageTriggerType.AFTER_UPDATE) == null
                                || storage.getTriggers().get(StorageTriggerType.AFTER_UPDATE).isEmpty();

                if (noBeforeUpdate && noAfterUpdate && !BooleanUtil.safeValueOf(storage.getGenerateEvents()))
                        return dataService.update(conn, storage, dataObject, override)
                                        .map(e -> Tuples.of(e, Optional.empty()));

                String id = StringUtil.safeValueOf(dataObject.getData().get("_id"));

                if (noBeforeUpdate && noAfterUpdate) {
                        return this.read(appCode, clientCode, storage.getName(), id, false, List.of())
                                        .flatMap(existing -> dataService
                                                        .update(conn, storage, dataObject, override)
                                                        .map(e -> Tuples.of(e, Optional.of(existing))));
                }

                return FlatMapUtil.flatMapMono(
                                () -> this.read(appCode, clientCode, storage.getName(), id, false, List.of()),
                                existing -> {
                                        if (existing == null)
                                                return this.msgService.throwMessage(
                                                                msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                                                AbstractMongoMessageResourceService.OBJECT_NOT_FOUND,
                                                                storage.getName(),
                                                                id);

                                        if (noBeforeUpdate)
                                                return Mono.just(true);

                                        Map<String, JsonElement> args = Map.of(
                                                        DATA_OBJECT_KEY,
                                                        this.gson.toJsonTree(dataObject.getData()),
                                                        EXISTING_DATA_OBJECT_KEY,
                                                        this.gson.toJsonTree(existing));

                                        return this.executeTriggers(storage, StorageTriggerType.BEFORE_UPDATE, args);
                                },
                                (existing, beforeUpdate) -> dataService.update(conn, storage, dataObject, override),
                                (existing, beforeUpdate, updated) -> {
                                        if (noAfterUpdate)
                                                return Mono.just(Tuples
                                                                .<Map<String, Object>, Optional<Map<String, Object>>>of(
                                                                                updated, Optional.of(existing)));

                                        Map<String, JsonElement> args = Map.of(
                                                        DATA_OBJECT_KEY,
                                                        this.gson.toJsonTree(updated),
                                                        EXISTING_DATA_OBJECT_KEY,
                                                        this.gson.toJsonTree(existing));

                                        return this.executeTriggers(storage, StorageTriggerType.AFTER_UPDATE, args)
                                                        .map(e -> Tuples.<Map<String, Object>, Optional<Map<String, Object>>>of(
                                                                        updated, Optional.of(existing)));
                                })
                                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.updateWithTriggers"));
        }

        public Mono<Map<String, Object>> read(
                        String appCode, String clientCode, String storageName, String id, Boolean eager,
                        List<String> eagerFields) {
                Mono<Map<String, Object>> mono = FlatMapUtil.flatMapMonoWithNull(
                                SecurityContextUtil::getUsersContextAuthentication,
                                ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),
                                (ca, ac) -> Mono.just(clientCode == null ? ca.getUrlClientCode() : clientCode),
                                (ca, ac, cc) -> connectionService.read("appData", ac, cc, ConnectionType.APP_DATA),
                                (ca, ac, cc, conn) -> Mono.just(
                                                this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE
                                                                : conn.getConnectionSubType())),
                                (ca, ac, cc, conn, dataService) -> getStorageWithKIRunValidation(storageName, ac, cc)
                                                .map(ObjectWithUniqueID::getObject),
                                (ca, ac, cc, conn, dataService, storage) -> this
                                                .<Map<String, Object>>genericOperation(
                                                                storage,
                                                                (cona, hasAccess) -> dataService.read(conn,
                                                                                storage, id),
                                                                Storage::getReadAuth,
                                                                CoreMessageResourceService.FORBIDDEN_READ_STORAGE),
                                (ca, ac, cc, conn, dataService, storage, read) -> this.fillRelatedObjects(
                                                ac,
                                                cc,
                                                storage,
                                                read,
                                                dataService,
                                                conn,
                                                BooleanUtil.safeValueOf(eager)
                                                                ? storage.getRelations().keySet().stream().toList()
                                                                : eagerFields));

                return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.read"));
        }

        public Mono<Page<Map<String, Object>>> readPage(
                        String appCode, String clientCode, String storageName, Query query) {
                Mono<Page<Map<String, Object>>> mono = FlatMapUtil.flatMapMonoWithNull(
                                SecurityContextUtil::getUsersContextAuthentication,
                                ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),
                                (ca, ac) -> Mono.just(clientCode == null ? ca.getUrlClientCode() : clientCode),
                                (ca, ac, cc) -> connectionService.read("appData", ac, cc, ConnectionType.APP_DATA),
                                (ca, ac, cc, conn) -> Mono.just(
                                                this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE
                                                                : conn.getConnectionSubType())),
                                (ca, ac, cc, conn, dataService) -> getStorageWithKIRunValidation(storageName, ac, cc)
                                                .map(ObjectWithUniqueID::getObject),
                                (ca, ac, cc, conn, dataService, storage) -> this.genericOperation(
                                                storage,
                                                (cona, hasAccess) -> dataService.readPage(conn, storage, query),
                                                Storage::getReadAuth,
                                                CoreMessageResourceService.FORBIDDEN_READ_STORAGE),
                                (ca, ac, cc, conn, dataService, storage, page) -> {
                                        if (storage.getRelations() == null || storage.getRelations().isEmpty())
                                                return Mono.just(page);

                                        return FlatMapUtil.flatMapMono(
                                                        () -> Flux.fromIterable(page.getContent())
                                                                        .flatMap(e -> this.fillRelatedObjects(
                                                                                        ac,
                                                                                        cc,
                                                                                        storage,
                                                                                        e,
                                                                                        dataService,
                                                                                        conn,
                                                                                        BooleanUtil.safeValueOf(query
                                                                                                        .getEager())
                                                                                                                        ? storage.getRelations()
                                                                                                                                        .keySet()
                                                                                                                                        .stream()
                                                                                                                                        .toList()
                                                                                                                        : query.getEagerFields()))
                                                                        .collectList(),
                                                        list -> Mono.just(
                                                                        PageableExecutionUtils.getPage(list,
                                                                                        page.getPageable(),
                                                                                        page::getTotalElements)));
                                });

                return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.readPage"));
        }

        public Mono<Boolean> delete(String appCode, String clientCode, String storageName, String id) {
                Mono<Boolean> mono = FlatMapUtil.flatMapMonoWithNull(
                                SecurityContextUtil::getUsersContextAuthentication,
                                ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),
                                (ca, ac) -> Mono.just(clientCode == null ? ca.getUrlClientCode() : clientCode),
                                (ca, ac, cc) -> connectionService.read("appData", ac, cc, ConnectionType.APP_DATA),
                                (ca, ac, cc, conn) -> Mono.just(
                                                this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE
                                                                : conn.getConnectionSubType())),
                                (ca, ac, cc, conn, dataService) -> getStorageWithKIRunValidation(storageName, ac, cc)
                                                .map(ObjectWithUniqueID::getObject),
                                (ca, ac, cc, conn, dataService, storage) -> this.genericOperation(
                                                storage,
                                                (cona, hasAccess) -> FlatMapUtil.flatMapMono(
                                                                () -> this.deleteRelatedObjects(appCode, clientCode,
                                                                                dataService, conn, storage, id),
                                                                deleted -> this.deleteWithTriggers(appCode, clientCode,
                                                                                dataService, conn, storage, id),
                                                                (deleted, e) -> {
                                                                        if (e.getT2().isEmpty())
                                                                                return Mono.just(e.getT1());

                                                                        return this.generateEvent(
                                                                                        ca,
                                                                                        appCode,
                                                                                        clientCode,
                                                                                        storage,
                                                                                        "Delete",
                                                                                        e.getT2().orElse(null),
                                                                                        null)
                                                                                        .map(x -> e.getT1());
                                                                }),
                                                Storage::getDeleteAuth,
                                                CoreMessageResourceService.FORBIDDEN_DELETE_STORAGE));

                return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.delete"));
        }

        public Mono<Long> deleteByFilter(
                        String appCode, String clientCode, String storageName, Query query, Boolean devMode) {
                Mono<Long> mono = FlatMapUtil.flatMapMonoWithNull(
                                SecurityContextUtil::getUsersContextAuthentication,
                                ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),
                                (ca, ac) -> Mono.just(clientCode == null ? ca.getUrlClientCode() : clientCode),
                                (ca, ac, cc) -> connectionService.read("appData", ac, cc, ConnectionType.APP_DATA),
                                (ca, ac, cc, conn) -> Mono.just(
                                                this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE
                                                                : conn.getConnectionSubType())),
                                (ca, ac, cc, conn, dataService) -> getStorageWithKIRunValidation(storageName, ac, cc)
                                                .map(ObjectWithUniqueID::getObject),
                                (ca, ac, cc, conn, dataService, storage) -> this.<Long>genericOperation(
                                                storage,
                                                (cona, hasAccess) -> dataService.deleteByFilter(conn, storage, query,
                                                                devMode),
                                                Storage::getDeleteAuth,
                                                CoreMessageResourceService.FORBIDDEN_DELETE_STORAGE),
                                (ca, ac, cc, conn, dataService, storage, deletedCount) -> {
                                        return Mono.just(deletedCount);
                                });

                return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.deleteByFilter"));
        }

        private Mono<Boolean> deleteRelatedObjects(
                        String appCode,
                        String clientCode,
                        IAppDataService dataService,
                        Connection conn,
                        Storage storage,
                        String id) {
                if (storage.getRelations() == null || storage.getRelations().isEmpty())
                        return Mono.just(true);

                return FlatMapUtil.flatMapMono(
                                () -> this.read(appCode, clientCode, storage.getName(), id, false, null),
                                obj -> {
                                        List<Mono<Tuple3<Boolean, String, String>>> restrictList = new ArrayList<>();

                                        for (Entry<String, StorageRelation> relation : storage.getRelations()
                                                        .entrySet()) {
                                                if (relation.getValue()
                                                                .getDeleteConstraint() == StorageRelationConstraint.NOTHING
                                                                || relation.getValue()
                                                                                .getDeleteConstraint() == StorageRelationConstraint.CASCADE
                                                                || obj.get(relation.getKey()) == null)
                                                        continue;

                                                if (obj.get(relation.getKey()) instanceof List lst) {
                                                        for (Object o : lst)
                                                                restrictList.add(this.storageService
                                                                                .read(relation.getValue()
                                                                                                .getStorageName(),
                                                                                                appCode, clientCode)
                                                                                .map(ObjectWithUniqueID::getObject)
                                                                                .flatMap(inStorage -> dataService
                                                                                                .checkifExists(conn,
                                                                                                                inStorage,
                                                                                                                o.toString()))
                                                                                .map(s -> Tuples.of(
                                                                                                s,
                                                                                                relation.getValue()
                                                                                                                .getStorageName(),
                                                                                                o.toString())));
                                                } else {
                                                        restrictList.add(this.storageService
                                                                        .read(relation.getValue().getStorageName(),
                                                                                        appCode, clientCode)
                                                                        .map(ObjectWithUniqueID::getObject)
                                                                        .flatMap(inStorage -> dataService
                                                                                        .checkifExists(
                                                                                                        conn,
                                                                                                        inStorage,
                                                                                                        obj.get(relation.getKey())
                                                                                                                        .toString())
                                                                                        .map(s -> Tuples.of(
                                                                                                        s,
                                                                                                        relation.getValue()
                                                                                                                        .getStorageName(),
                                                                                                        obj.get(relation.getKey())
                                                                                                                        .toString()))));
                                                }
                                        }

                                        return Flux.fromIterable(restrictList)
                                                        .flatMap(e -> e)
                                                        .collectList()
                                                        .flatMap(lst -> {
                                                                List<Tuple3<Boolean, String, String>> errorList = lst
                                                                                .stream()
                                                                                .filter(Tuple2::getT1)
                                                                                .toList();

                                                                if (errorList.isEmpty())
                                                                        return Mono.just(true);

                                                                return this.msgService.throwMessage(
                                                                                msg -> new GenericException(
                                                                                                HttpStatus.BAD_REQUEST,
                                                                                                msg),
                                                                                CoreMessageResourceService.CANNOT_DELETE_STORAGE_WITH_RESTRICT,
                                                                                errorList.stream()
                                                                                                .map(e -> e.getT2()
                                                                                                                + ":"
                                                                                                                + e.getT3())
                                                                                                .toList());
                                                        });
                                },
                                (obj, restrict) -> {
                                        List<Mono<Boolean>> deleteList = new ArrayList<>();

                                        for (Entry<String, StorageRelation> relation : storage.getRelations()
                                                        .entrySet()) {
                                                if (relation.getValue()
                                                                .getDeleteConstraint() != StorageRelationConstraint.CASCADE
                                                                || obj.get(relation.getKey()) == null)
                                                        continue;

                                                if (obj.get(relation.getKey()) instanceof List lst) {
                                                        for (Object o : lst)
                                                                deleteList.add(this.delete(
                                                                                appCode,
                                                                                clientCode,
                                                                                relation.getValue().getStorageName(),
                                                                                o.toString()));
                                                } else {
                                                        deleteList.add(this.delete(
                                                                        appCode,
                                                                        clientCode,
                                                                        relation.getValue().getStorageName(),
                                                                        obj.get(relation.getKey()).toString()));
                                                }
                                        }

                                        return Flux.fromIterable(deleteList)
                                                        .flatMap(e -> e)
                                                        .collectList()
                                                        .map(e -> true);
                                })
                                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.deleteRelatedObjects"));
        }

        private Mono<Tuple2<Boolean, Optional<Map<String, Object>>>> deleteWithTriggers(
                        String appCode,
                        String clientCode,
                        IAppDataService dataService,
                        Connection conn,
                        Storage storage,
                        String id) {
                boolean noBeforeDelete = storage.getTriggers() == null
                                || storage.getTriggers().get(StorageTriggerType.BEFORE_DELETE) == null
                                || storage.getTriggers().get(StorageTriggerType.BEFORE_DELETE).isEmpty();

                boolean noAfterDelete = storage.getTriggers() == null
                                || storage.getTriggers().get(StorageTriggerType.AFTER_DELETE) == null
                                || storage.getTriggers().get(StorageTriggerType.AFTER_DELETE).isEmpty();

                if (noBeforeDelete && noAfterDelete && !BooleanUtil.safeValueOf(storage.getGenerateEvents()))
                        return dataService.delete(conn, storage, id).map(e -> Tuples.of(e, Optional.empty()));

                if (noBeforeDelete && noAfterDelete)
                        return this.read(appCode, clientCode, storage.getName(), id, false, List.of())
                                        .flatMap(existing -> dataService.delete(conn, storage, id)
                                                        .map(e -> Tuples.of(e, Optional.of(existing))));

                return FlatMapUtil.flatMapMono(
                                () -> this.read(appCode, clientCode, storage.getName(), id, false, List.of()),
                                existing -> {
                                        if (existing == null)
                                                return this.msgService.throwMessage(
                                                                msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                                                AbstractMongoMessageResourceService.OBJECT_NOT_FOUND,
                                                                storage.getName(),
                                                                id);

                                        if (noBeforeDelete)
                                                return Mono.just(true);

                                        Map<String, JsonElement> args = Map.of(DATA_OBJECT_KEY,
                                                        this.gson.toJsonTree(existing));

                                        return this.executeTriggers(storage, StorageTriggerType.BEFORE_DELETE, args);
                                },
                                (existing, beforeDelete) -> dataService.delete(conn, storage, id),
                                (existing, beforeDelete, deleted) -> {
                                        if (noAfterDelete)
                                                return Mono.just(Tuples.<Boolean, Optional<Map<String, Object>>>of(
                                                                deleted, Optional.of(existing)));

                                        Map<String, JsonElement> args = Map.of(DATA_OBJECT_KEY,
                                                        this.gson.toJsonTree(existing));

                                        return this.executeTriggers(storage, StorageTriggerType.AFTER_DELETE, args)
                                                        .map(e -> Tuples.<Boolean, Optional<Map<String, Object>>>of(
                                                                        deleted, Optional.of(existing)));
                                })
                                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.genericDelete"));
        }

        public Mono<Void> downloadData(
                        String appCode,
                        String clientCode,
                        String storageName,
                        Query query,
                        DataFileType fileType,
                        ServerHttpResponse response) {
                Mono<Void> mono = FlatMapUtil.flatMapMonoWithNull(
                                SecurityContextUtil::getUsersContextAuthentication,
                                ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),
                                (ca, ac) -> Mono.just(clientCode == null ? ca.getUrlClientCode() : clientCode),
                                (ca, ac, cc) -> connectionService.read("appData", ac, cc, ConnectionType.APP_DATA),
                                (ca, ac, cc, conn) -> Mono.just(
                                                this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE
                                                                : conn.getConnectionSubType())),
                                (ca, ac, cc, conn, dataService) -> getStorageWithKIRunValidation(storageName, ac, cc)
                                                .map(ObjectWithUniqueID::getObject),
                                (ca, ac, cc, conn, dataService, storage) -> this.genericOperation(
                                                storage,
                                                (cas, hasAccess) -> this.writeDataToResponse(
                                                                storage,
                                                                dataService.readPageAsFlux(conn, storage, query),
                                                                fileType, response),
                                                Storage::getReadAuth,
                                                CoreMessageResourceService.FORBIDDEN_READ_STORAGE));

                return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.downloadData"));
        }

        private Mono<Void> writeDataToResponse(
                        Storage storage, Flux<Map<String, Object>> dataFlux, DataFileType fileType,
                        ServerHttpResponse response) {
                return FlatMapUtil.flatMapMonoWithNull(
                                () -> fileType.isNestedStructure() ? Mono.<Schema>empty()
                                                : storageService.getSchema(storage),
                                schema -> schema != null ? this.getHeaders(null, storage, schema)
                                                : Mono.just(List.of()),
                                (schema, dataHeaders) -> {
                                        String file = storage.getName() + "_data." + fileType.toString().toLowerCase();
                                        try {
                                                Path fPath = Files.createTempFile(file, "");
                                                DataFileWriter dfw = new DataFileWriter(
                                                                dataHeaders,
                                                                fileType,
                                                                Files.newOutputStream(
                                                                                fPath, StandardOpenOption.WRITE,
                                                                                StandardOpenOption.TRUNCATE_EXISTING));

                                                ObjectValueSetterExtractor ovs = new ObjectValueSetterExtractor(
                                                                new JsonObject(), "Data.");
                                                Gson gson = this.gson;

                                                return fluxToFile(dataFlux, fileType, dataHeaders, dfw, ovs, gson)
                                                                .flatMap(e -> flieToResponse(fileType, response, file,
                                                                                fPath, dfw));
                                        } catch (Exception ex) {
                                                return this.msgService.throwMessage(
                                                                msg -> new GenericException(
                                                                                HttpStatus.INTERNAL_SERVER_ERROR, msg,
                                                                                ex),
                                                                CoreMessageResourceService.NOT_ABLE_TO_DOWNLOAD_DATA,
                                                                file);
                                        }
                                });
        }

        private Mono<Void> flieToResponse(
                        DataFileType fileType, ServerHttpResponse response, String file, Path fPath,
                        DataFileWriter dfw) {
                try {
                        dfw.flush();
                        dfw.close();
                        ZeroCopyHttpOutputMessage zeroCopyResponse = (ZeroCopyHttpOutputMessage) response;
                        long length = Files.size(fPath);
                        HttpHeaders headers = response.getHeaders();
                        headers.setContentLength(length);
                        headers.add("content-type", fileType.getMimeType());
                        headers.setContentDisposition(
                                        ContentDisposition.attachment().filename(file).build());

                        return zeroCopyResponse.writeWith(fPath, 0, length);
                } catch (Exception ex) {
                        return this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg, ex),
                                        CoreMessageResourceService.NOT_ABLE_TO_DOWNLOAD_DATA,
                                        file);
                }
        }

        private Mono<Boolean> fluxToFile(
                        Flux<Map<String, Object>> dataFlux,
                        DataFileType fileType,
                        List<String> dataHeaders,
                        DataFileWriter dfw,
                        ObjectValueSetterExtractor ovs,
                        Gson gson) {
                return dataFlux.reduce(Boolean.TRUE, (db, e) -> {
                        Map<String, Object> newMap = e;
                        if (!fileType.isNestedStructure()) {
                                JsonElement job = gson.toJsonTree(e);
                                ovs.setStore(job);
                                newMap = dataHeaders.stream()
                                                .map(head -> {
                                                        JsonElement ele = ovs.getValue("Data." + head);
                                                        return Tuples.of(head, ele == null ? "" : ele.getAsString());
                                                })
                                                .collect(Collectors.toMap(Tuple2::getT1, Tuple2::getT2));
                        }

                        try {
                                dfw.write(newMap);
                        } catch (IOException e1) {
                                throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to create file",
                                                e1);
                        }
                        return true;
                });
        }

        public Mono<byte[]> downloadTemplate(String appCode, String clientCode, String storageName,
                        DataFileType fileType) {
                return FlatMapUtil.flatMapMonoWithNull(
                                () -> connectionService
                                                .read("appData", appCode, clientCode)
                                                .map(ObjectWithUniqueID::getObject),
                                conn -> Mono.just(this.services.get(
                                                conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),
                                (conn, dataService) -> getStorageWithKIRunValidation(storageName, appCode, clientCode)
                                                .map(ObjectWithUniqueID::getObject),
                                (conn, dataService, storage) -> this.genericOperation(
                                                storage,
                                                (ca, hasAccess) -> downloadTemplate(storage, fileType, "notghjin"),
                                                Storage::getCreateAuth,
                                                CoreMessageResourceService.FORBIDDEN_CREATE_STORAGE)
                                                .switchIfEmpty(this.msgService.throwMessage(
                                                                msg -> new GenericException(HttpStatus.BAD_REQUEST,
                                                                                msg),
                                                                CoreMessageResourceService.NOT_ABLE_TO_OPEN_FILE_ERROR)))
                                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.downloadTemplate"));
        }

        public Mono<Boolean> uploadData(
                        String appCode, String clientCode, String storageName, DataFileType fileType, FilePart file) {
                Mono<Boolean> mono = FlatMapUtil.flatMapMonoWithNull(
                                SecurityContextUtil::getUsersContextAuthentication,
                                ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),
                                (ca, ac) -> Mono.just(clientCode == null ? ca.getUrlClientCode() : clientCode),
                                (ca, ac, cc) -> connectionService.read("appData", ac, cc, ConnectionType.APP_DATA),
                                (ca, ac, cc, conn) -> Mono.just(
                                                this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE
                                                                : conn.getConnectionSubType())),
                                (ca, ac, cc, conn, dataService) -> getStorageWithKIRunValidation(storageName, ac, cc)
                                                .map(ObjectWithUniqueID::getObject),
                                (ca, ac, cc, conn, dataService, storage) -> this.genericOperation(
                                                storage,
                                                (cona, hasAccess) -> uploadDataInternal(conn, storage, fileType, file,
                                                                dataService),
                                                Storage::getCreateAuth,
                                                CoreMessageResourceService.FORBIDDEN_CREATE_STORAGE));

                return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.uploadData"));
        }

        private <T> Mono<T> genericOperation(
                        Storage storage,
                        BiFunction<ContextAuthentication, Boolean, Mono<T>> bifun,
                        Function<Storage, String> authFun,
                        String msgString) {
                if (storage == null)
                        return msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                        CoreMessageResourceService.STORAGE_NOT_FOUND);

                return FlatMapUtil.flatMapMono(
                                SecurityContextUtil::getUsersContextAuthentication,
                                ca -> Mono.justOrEmpty(
                                                SecurityContextUtil.hasAuthority(
                                                                authFun.apply(storage),
                                                                ca.getUser().getAuthorities())
                                                                                ? Boolean.TRUE
                                                                                : null),
                                bifun)
                                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.genericOperation"))
                                .switchIfEmpty(this.msgService.throwMessage(
                                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg), msgString,
                                                storage.getName()));
        }

        private Mono<byte[]> downloadTemplate(Storage storage, DataFileType type, String temp) { // NOSONAR
                if (type.isNestedStructure())
                        return Mono.just(new byte[0]);

                return FlatMapUtil.flatMapMonoWithNull(
                                () -> storageService.getSchema(storage),
                                storageSchema -> (storageSchema.getRef() != null)
                                                || (storageSchema.getType() != null
                                                                && storageSchema
                                                                                .getType()
                                                                                .getAllowedSchemaTypes()
                                                                                .size() == 1
                                                                && storageSchema
                                                                                .getType()
                                                                                .getAllowedSchemaTypes()
                                                                                .contains(SchemaType.OBJECT))
                                                                                                ? this.getHeaders(null,
                                                                                                                storage,
                                                                                                                storageSchema)
                                                                                                : Mono.empty(),
                                (storageSchema, acutalHeaders) -> {
                                        try {
                                                ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                                                DataFileWriter writer = new DataFileWriter(acutalHeaders, type,
                                                                byteStream);
                                                writer.write(Map.of());
                                                writer.flush();
                                                writer.close();
                                                byteStream.flush();
                                                byteStream.close();
                                                byte[] bytes = byteStream.toByteArray();
                                                return Mono.just(bytes);
                                        } catch (Exception e) {
                                                return this.msgService.throwMessage(
                                                                msg -> new GenericException(
                                                                                HttpStatus.INTERNAL_SERVER_ERROR, msg),
                                                                CoreMessageResourceService.TEMPLATE_GENERATION_ERROR,
                                                                type.toString());
                                        }
                                })
                                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.downloadTemplate"));
        }

        private Mono<Map<String, Set<SchemaType>>> getHeadersSchemaType(
                        String prefix, Storage storage, Schema schema, int level) {
                return FlatMapUtil.flatMapMono(
                                () -> this.schemaService.getSchemaRepository(storage.getAppCode(),
                                                storage.getClientCode()),
                                appSchemaRepo -> {
                                        if (schema.getRef() == null)
                                                return Mono.just(schema);

                                        return ReactiveSchemaUtil.getSchemaFromRef(
                                                        schema,
                                                        new ReactiveHybridRepository<>(
                                                                        new KIRunReactiveSchemaRepository(),
                                                                        new CoreSchemaRepository(), appSchemaRepo),
                                                        schema.getRef());
                                },
                                (appSchemaRepo, rSchema) -> {
                                        if (rSchema.getType().contains(SchemaType.OBJECT)) {
                                                return getSchemaHeadersIfObject(prefix, storage, level, rSchema);
                                        } else if (rSchema.getType().contains(SchemaType.ARRAY)) {
                                                return getSchemaHeadersIfArray(prefix, storage, level, rSchema);
                                        }

                                        return Mono.just(Map.of(prefix, rSchema.getType().getAllowedSchemaTypes()));
                                });
        }

        private Mono<Map<String, Set<SchemaType>>> getSchemaHeadersIfArray(
                        String prefix, Storage storage, int level, Schema rSchema) {
                if (level > 2 || rSchema.getItems() == null)
                        return Mono.just(Map.of());

                ArraySchemaType aType = rSchema.getItems();

                if (aType.getSingleSchema() != null) {
                        return Flux.range(0, 2)
                                        .map(e -> getPrefixArrayName(prefix, e))
                                        .flatMap(e -> this
                                                        .getHeadersSchemaType(e, storage, aType.getSingleSchema(),
                                                                        level + 1)
                                                        .map(Map::entrySet)
                                                        .flatMapMany(Flux::fromIterable))
                                        .collectMap(Entry::getKey, Entry::getValue);
                } else if (aType.getTupleSchema() != null) {
                        return Flux.<Tuple2<Integer, Schema>>create(sink -> {
                                for (int i = 0; i < aType.getTupleSchema().size(); i++)
                                        sink.next(Tuples.of(i, aType.getTupleSchema().get(i)));

                                sink.complete();
                        })
                                        .flatMap(tup -> this.getHeadersSchemaType(
                                                        getPrefixArrayName(prefix, tup.getT1()), storage, tup.getT2(),
                                                        level + 1)
                                                        .map(Map::entrySet)
                                                        .flatMapMany(Flux::fromIterable))
                                        .collectMap(Entry::getKey, Entry::getValue);
                }

                return Mono.just(Map.of());
        }

        private Mono<Map<String, Set<SchemaType>>> getSchemaHeadersIfObject(
                        String prefix, Storage storage, int level, Schema rSchema) {
                if (level >= 2 || rSchema.getProperties() == null)
                        return Mono.just(Map.of());

                return Flux.fromIterable(rSchema.getProperties().entrySet())
                                .flatMap(e -> this.getHeadersSchemaType(
                                                getFlattenedObjectName(prefix, e), storage, e.getValue(), level + 1)
                                                .map(Map::entrySet)
                                                .flatMapMany(Flux::fromIterable))
                                .collectMap(Entry::getKey, Entry::getValue);
        }

        private Mono<List<String>> getHeaders(String prefix, Storage storage, Schema sch) { // NOSONAR
                return this.getHeadersSchemaType(prefix, storage, sch, 0)
                                .flatMapMany(e -> Flux.fromIterable(e.keySet()))
                                .sort((a, b) -> {
                                        int aCount = StringUtils.countOccurrencesOf(a, ".");
                                        int bCount = StringUtils.countOccurrencesOf(b, ".");
                                        if (aCount == bCount)
                                                return a.compareToIgnoreCase(b);

                                        return aCount - bCount;
                                })
                                .collectList();
        }

        private String getPrefixArrayName(String prefix, int e) {
                return prefix == null ? "[" + e + "]" : prefix + "[" + e + "]";
        }

        private String getFlattenedObjectName(String prefix, Entry<String, Schema> e) {
                return prefix == null ? e.getKey() : prefix + "." + e.getKey();
        }

        // add a check for storage schema is only object
        private Mono<Boolean> uploadDataInternal(
                        Connection conn, Storage storage, DataFileType fileType, FilePart filePart,
                        IAppDataService dataService) {
                return FlatMapUtil.flatMapMono(
                                () -> storageService.getSchema(storage),
                                storageSchema -> fileType.isNestedStructure()
                                                ? Mono.just(Map.of())
                                                : this.getHeadersSchemaType(null, storage, storageSchema, 0),
                                (storageSchema, headers) -> {
                                        List<Mono<Boolean>> monoList = (fileType == DataFileType.JSON
                                                        || fileType == DataFileType.JSONL)
                                                                        ? nestedFileToDB(conn, storage, fileType,
                                                                                        filePart, dataService)
                                                                        : flatFileToDB(conn, storage, fileType,
                                                                                        filePart, dataService, headers);

                                        return Flux.concat(monoList).collectList().map(e -> true);
                                })
                                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.uploadDataInternal"))
                                .switchIfEmpty(msgService.throwMessage(
                                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                                CoreMessageResourceService.NOT_ABLE_TO_READ_FILE_FORMAT,
                                                fileType));
        }

        private List<Mono<Boolean>> nestedFileToDB(
                        Connection conn, Storage storage, DataFileType fileType, FilePart filePart,
                        IAppDataService dataService) {
                List<Mono<Boolean>> monoList = new ArrayList<>();

                Map<String, Object> job;
                try (DataFileReader reader = new DataFileReader(filePart, fileType)) {
                        while ((job = reader.readObject()) != null)
                                monoList.add(dataService
                                                .create(conn, storage, new DataObject().setData(job))
                                                .map(v -> true));
                } catch (Exception ex) {
                        logger.debug("Error while reading upload file. ", ex);
                }

                return monoList;
        }

        private List<Mono<Boolean>> flatFileToDB(
                        Connection conn,
                        Storage storage,
                        DataFileType fileType,
                        FilePart filePart,
                        IAppDataService dataService,
                        Map<String, Set<SchemaType>> headers) {
                List<Mono<Boolean>> monoList = new ArrayList<>();

                try (DataFileReader reader = new DataFileReader(filePart, fileType)) {
                        List<String> row;

                        do {
                                row = reader.readRow();
                                if (row != null && !row.isEmpty()) {
                                        Map<String, Object> rowMap = new HashMap<>();
                                        for (int i = 0; i < reader.getHeaders().size() && i < row.size(); i++) {
                                                if (StringUtil.safeIsBlank(row.get(i)))
                                                        continue;
                                                MapUtil.setValueInMap(
                                                                rowMap,
                                                                reader.getHeaders().get(i),
                                                                getElementBySchemaType(
                                                                                headers.get(reader.getHeaders().get(i)),
                                                                                row.get(i)));
                                        }
                                        monoList.add(dataService
                                                        .create(conn, storage, new DataObject().setData(rowMap))
                                                        .map(v -> true));
                                }
                        } while (row != null && !row.isEmpty());
                } catch (Exception ex) {
                        logger.debug("Error while reading upload file. ", ex);
                }

                return monoList;
        }

        public Mono<Map<String, Object>> readVersion(
                        String appCode, String clientCode, String storageName, String versionId) {
                Mono<Map<String, Object>> mono = FlatMapUtil.flatMapMonoWithNull(
                                SecurityContextUtil::getUsersContextAuthentication,
                                ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),
                                (ca, ac) -> Mono.just(clientCode == null ? ca.getUrlClientCode() : clientCode),
                                (ca, ac, cc) -> connectionService.read("appData", ac, cc, ConnectionType.APP_DATA),
                                (ca, ac, cc, conn) -> Mono.just(
                                                this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE
                                                                : conn.getConnectionSubType())),
                                (ca, ac, cc, conn, dataService) -> getStorageWithKIRunValidation(storageName, ac, cc)
                                                .map(ObjectWithUniqueID::getObject),
                                (ca, ac, cc, conn, dataService, storage) -> this.genericOperation(
                                                storage,
                                                (cona, hasAccess) -> dataService.readVersion(conn, storage, versionId),
                                                Storage::getReadAuth,
                                                CoreMessageResourceService.FORBIDDEN_READ_STORAGE));

                return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.readVersion"));
        }

        public Mono<Page<Map<String, Object>>> readPageVersion(
                        String appCode, String clientCode, String storageName, String versionId, Query query) {
                Mono<Page<Map<String, Object>>> mono = FlatMapUtil.flatMapMonoWithNull(
                                SecurityContextUtil::getUsersContextAuthentication,
                                ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),
                                (ca, ac) -> Mono.just(clientCode == null ? ca.getUrlClientCode() : clientCode),
                                (ca, ac, cc) -> connectionService.read("appData", ac, cc, ConnectionType.APP_DATA),
                                (ca, ac, cc, conn) -> Mono.just(
                                                this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE
                                                                : conn.getConnectionSubType())),
                                (ca, ac, cc, conn, dataService) -> getStorageWithKIRunValidation(storageName, ac, cc)
                                                .map(ObjectWithUniqueID::getObject),
                                (ca, ac, cc, conn, dataService, storage) -> this.genericOperation(
                                                storage,
                                                (cona, hasAccess) -> dataService.readPageVersion(conn, storage,
                                                                versionId, query),
                                                Storage::getReadAuth,
                                                CoreMessageResourceService.FORBIDDEN_READ_STORAGE));

                return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.readPageVersion"));
        }

        public Mono<Boolean> deleteStorage(String appCode, String clientCode, String storageName) {
                Mono<Boolean> mono = FlatMapUtil.flatMapMonoWithNull(
                                SecurityContextUtil::getUsersContextAuthentication,
                                ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),
                                (ca, ac) -> Mono.just(clientCode == null ? ca.getUrlClientCode() : clientCode),
                                (ca, ac, cc) -> connectionService.read("appData", ac, cc, ConnectionType.APP_DATA),
                                (ca, ac, cc, conn) -> Mono.just(
                                                this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE
                                                                : conn.getConnectionSubType())),
                                (ca, ac, cc, conn, dataService) -> getStorageWithKIRunValidation(storageName, ac, cc)
                                                .map(ObjectWithUniqueID::getObject),
                                (ca, ac, cc, conn, dataService, storage) -> this.genericOperation(
                                                storage,
                                                (cona, hasAccess) -> dataService.deleteStorage(conn, storage),
                                                Storage::getDeleteAuth,
                                                CoreMessageResourceService.FORBIDDEN_DELETE_STORAGE));
                return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.deleteStorage"));
        }

        private Mono<ObjectWithUniqueID<Storage>> getStorageWithKIRunValidation(
                        String name, String appCode, String clientCode) {
                return storageService.read(name, appCode, clientCode).flatMap(e -> {
                        if (!BooleanUtil.safeValueOf(e.getObject().getOnlyThruKIRun()))
                                return Mono.just(e);

                        return Mono.deferContextual(cv -> {
                                if ("true".equals(cv.get(DefinitionFunction.CONTEXT_KEY)))
                                        return Mono.just(e);
                                return Mono.empty();
                        });
                });
        }

        @Data
        @AllArgsConstructor
        private static class RelationDataObject {

                private String fieldName;
                private boolean isNew;
                private Map<String, Object> data;
                private String id;
                private Throwable exception;
        }
}
