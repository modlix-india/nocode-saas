package com.fincity.saas.commons.core.service.connection.appdata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
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
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import com.fincity.saas.commons.core.service.connection.appdata.mysql.FanOutReport;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLDrift;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.TenantProgress;
import com.fincity.saas.commons.core.service.CoreFunctionService;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import com.fincity.saas.commons.core.service.CoreSchemaService;
import com.fincity.saas.commons.core.service.EventDefinitionService;
import com.fincity.saas.commons.core.service.StorageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.file.DataFileReader;
import com.fincity.saas.commons.file.DataFileWriter;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.model.AggregateQuery;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.CloneUtil;
import com.fincity.saas.commons.util.DataFileType;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.MapUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Data;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

@Service
public class AppDataService {

    /** Reactor context key for how deep a chain of cascading deletes has gone. */
    private static final String CASCADE_DEPTH = "appdata.cascade.depth";

    private static final int MAX_CASCADE_DEPTH = 8;

    /**
     * How many relation hops one eager request may ask for.
     *
     * Relations point at each other freely, so a dotted path can describe a loop.
     * The bound is on the PATH, not on data volume: each level is still one query
     * per relation over the distinct objects of the level above.
     */
    private static final int MAX_EAGER_DEPTH = 5;

    /** Rows per round of a cascade. Large enough to be one query for any normal fan-out. */
    private static final int CASCADE_BATCH = 500;

    private static final ConnectionSubType DEFAULT_APP_DATA_SERVICE = ConnectionSubType.MONGO;
    private static final Logger logger = LoggerFactory.getLogger(AppDataService.class);
    private static final String DATA_OBJECT_KEY = "dataObject";
    private static final String EXISTING_DATA_OBJECT_KEY = "existingDataObject";
    private final EnumMap<ConnectionSubType, IAppDataService> services = new EnumMap<>(ConnectionSubType.class);

    /**
     * Applications whose SESSIONS may read and write an {@code onlyThruKIRun} storage
     * over the raw data API.
     *
     * Matched against {@code ContextAuthentication.verifiedAppCode}, which is the app
     * the user actually authenticated into. NOT {@code urlAppCode}: that one is set by
     * {@code JWTTokenFilter} from the {@code appCode} REQUEST HEADER, and the builder
     * deliberately sends the code of the app being EDITED, so it reads `leadzump` while
     * editing leadzump and would never match. {@code verifiedAppCode} is stamped at
     * login and rides in the signed token, so a caller cannot claim it with a header.
     */
    @Value("${core.storage.onlyThruKIRun.builderAppCodes:appbuilder,sitezump}")
    private Set<String> builderAppCodes;

    @Autowired
    private ConnectionService connectionService;

    @Autowired
    @Lazy
    private StorageService storageService;

    @Autowired
    private MongoAppDataService mongoAppDataService;

    @Autowired
    private MySQLAppDataService mySQLAppDataService;

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
    @Lazy
    private IFeignSecurityService securityService;

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
        this.services.put(ConnectionSubType.MYSQL, mySQLAppDataService);
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
                (ca, ac) -> this.clientCode(clientCode),
                (ca, ac, cc) -> connectionService.read("appData", ac, cc, ConnectionType.APP_DATA),
                (ca, ac, cc, conn) -> Mono.just(
                        this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),
                (ca, ac, cc, conn, dataService) -> getStorageWithKIRunValidation(storageName, ac, cc)
                        .map(ObjectWithUniqueID::getObject),
                (ca, ac, cc, conn, dataService, storage) -> this.<Map<String, Object>>genericOperation(
                        storage,
                        (contextAuth, hasAccess) -> FlatMapUtil.flatMapMono(
                                () -> this.processRelationsForCreate(ac, cc, storage, dataObject, dataService, conn),
                                updatedDataObject -> this.createWithTriggers(cc, dataService, conn, storage,
                                        updatedDataObject),
                                (updatedDataObject, created) -> {
                                    if (!BooleanUtil.safeValueOf(storage.getGenerateEvents()))
                                        return Mono.just(created);

                                    return this.generateEvent(ca, ac, cc, storage, "Create", created, null);
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
                        this.resolveEagerFields(storage, eager, eagerFields)));

        return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.create"));
    }

    /**
     * Estimated total row count across M's runtime data for an app, for token
     * metering (action {@code core.storage.rows}). Resolves the app's appData
     * connection (so a custom Mongo cluster is honoured, not just the default
     * client) and delegates the count to the resolved data service. Connection
     * resolution does not require an authenticated context, so this is safe from a
     * worker-triggered meter. Only the Mongo data service is implemented today.
     */
    /**
     * Drop an app's draft data when the app itself is being deleted.
     *
     * Draft rows are sandbox data: once the app is gone they mean nothing, and
     * leaving them behind would keep an unreachable database on the cluster for
     * good. The LIVE database is deliberately untouched, because orphaning it is
     * long-standing behaviour and changing that is a separate decision.
     */
    public Mono<Boolean> dropDraftData(String appCode, String clientCode) {

        return this.connectionService.read("appData", appCode, clientCode, ConnectionType.APP_DATA)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                // Dispatched like every other operation. Hardcoding Mongo here meant
                // deleting a MySQL-backed app dropped a Mongo database that was never
                // there and left the tenant's draft schemas standing.
                .flatMap(conn -> this.services
                        .get(conn.isEmpty() ? DEFAULT_APP_DATA_SERVICE : conn.get().getConnectionSubType())
                        .dropDraftDatabase(conn.orElse(null), appCode, clientCode))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.dropDraftData"));
    }

    /**
     * Bring a storage's tables into line with its definition, on whichever backend
     * the app is on.
     *
     * Mongo needs nothing: a collection has no declared shape, so a definition change
     * is already in effect the moment it is saved. MySQL does, and the gap between
     * the two is the whole reason this method exists rather than the publish path
     * simply assuming the data follows the definition.
     *
     * Returns a report rather than a boolean because a MySQL app's storage is spread
     * over one schema per tenant, each migrated independently, and partial completion
     * across that fan-out is a normal state rather than a failure.
     *
     * Never fails the caller. A definition save that succeeded must not be reported
     * as having failed because one tenant out of seventy could not be migrated: the
     * definition really is saved, the report says which tenants are behind, and
     * re-running is what carries them forward.
     */
    public Mono<FanOutReport> reconcileStorageDdl(String appCode, Storage storage) {

        if (storage == null || StringUtil.safeIsBlank(storage.getUniqueName()))
            return Mono.just(FanOutReport.empty());

        return SecurityContextUtil.getUsersContextAuthentication()
                .map(ca -> ca.getUser() == null ? null : ca.getUser().getUserName())
                .defaultIfEmpty("system")
                // EVERY server the app uses, not the saving client's. A client may
                // bring its own database, and the fan-out discovers tenants from the
                // schemas on ONE server - so reading a single connection migrated
                // whoever happened to share a host with the author and silently left
                // the rest on the old shape. Deduplicated by connection details, so
                // the ordinary single-server app still does exactly one sweep.
                .flatMap(by -> this.connectionService
                        .allAppData(ConnectionSubType.MYSQL, appCode)
                        .concatMap(conn -> this.mySQLAppDataService
                                .reconcile(conn, appCode, storage, by)
                                // One unreachable server must not stop the others.
                                // They are independent databases, and a report that
                                // covers three of four is worth more than an error.
                                .onErrorResume(e -> {
                                    logger.error(
                                            "Could not reconcile {} on connection {}",
                                            storage.getName(),
                                            conn.getId(),
                                            e);
                                    return Mono.just(FanOutReport.empty());
                                }))
                        .reduce(FanOutReport.empty(), FanOutReport::merge))
                .onErrorResume(e -> {
                    logger.error("Could not reconcile tables for storage {} in {}", storage.getName(), appCode, e);
                    return Mono.just(FanOutReport.empty());
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.reconcileStorageDdl"));
    }

    /**
     * How far each tenant got with this storage's last migration.
     *
     * Read only, and gated on the same readAuth as reading the rows: it exposes
     * tenant schema names and migration state, which is less than the data itself
     * but is still this storage's business.
     *
     * The sweep logs when it finds something, which answers "did anything break" for
     * whoever is watching the logs at the time. This answers "is anything behind
     * RIGHT NOW", which is the question asked after a deploy, usually by somebody
     * who was not watching.
     */
    public Mono<List<TenantProgress>> migrationStatus(String appCode, String clientCode, String storageName) {

        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,
                        ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),
                        (ca, ac) -> this.clientCode(clientCode),
                        (ca, ac, cc) -> connectionService.read("appData", ac, cc, ConnectionType.APP_DATA),
                        (ca, ac, cc, conn) -> getStorageWithKIRunValidation(storageName, ac, cc)
                                .map(ObjectWithUniqueID::getObject),
                        (ca, ac, cc, conn, storage) -> this.<List<TenantProgress>>genericOperation(
                                storage,
                                (contextAuth, hasAccess) -> conn != null
                                                && conn.getConnectionSubType() == ConnectionSubType.MYSQL
                                        ? this.mySQLAppDataService.migrationStatus(conn, ac, storage)
                                        // Mongo has no migrations, so there is nothing
                                        // to be behind on. An empty list says that
                                        // better than a 501 would.
                                        : Mono.just(List.<TenantProgress>of()),
                                Storage::getReadAuth,
                                CoreMessageResourceService.FORBIDDEN_READ_STORAGE))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.migrationStatus"));
    }

    /**
     * Where each tenant's table has come apart from its definition.
     *
     * Answers the question {@code migrationStatus} cannot: that one reads the
     * journal, so it knows about migrations that were attempted. This reads the
     * schemas themselves, so it also finds the tenant nobody migrated - unreachable
     * at the time, built by hand, or provisioned before the storage existed - which
     * is the case the journal has nothing to say about.
     *
     * Read only. Nothing here issues a statement.
     *
     * Gated on write access to the APP, not on the storage's {@code readAuth}, for
     * the reasons in {@link #builderOnly}. The short version is that this reports on
     * every client's schema at once, so a per-client runtime authority is not a
     * description of what it can see.
     */
    public Mono<List<MySQLDrift.Report>> drift(String appCode, String clientCode, String storageName) {

        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,
                        ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),
                        (ca, ac) -> this.clientCode(clientCode),
                        (ca, ac, cc) -> this.builderOnly(ac),
                        (ca, ac, cc, allowed) -> getStorageWithKIRunValidation(storageName, ac, cc)
                                .map(ObjectWithUniqueID::getObject),
                        // Every server the app uses. A client may bring its own
                        // database, and inspecting one server would report the rest
                        // as fine because it never looked at them - the same blind
                        // spot reconcileStorageDdl had. An app with no MySQL
                        // connection yields nothing here, which is the right answer
                        // for a Mongo app rather than a 501.
                        (ca, ac, cc, allowed, storage) -> this.connectionService
                                .allAppData(ConnectionSubType.MYSQL, ac)
                                .concatMap(conn -> this.mySQLAppDataService.drift(conn, ac, storage))
                                .collectList()
                                .map(lists -> lists.stream()
                                        .flatMap(List::stream)
                                        .toList()))
                // An app with no appData connection runs on Mongo and has no table to
                // drift. Without this the chain completes empty and the caller gets a
                // 200 with no body at all, which reads like a fault rather than "none".
                .defaultIfEmpty(List.of())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.drift"));
    }

    /**
     * Put the drift right.
     *
     * {@code approved} is the human decision and it is one of the two controls:
     * without it only the additive half runs and the rest comes back in
     * {@code withheld} for somebody to read. Nothing infers approval - a caller that
     * does not pass it does not get the destructive statements, however drifted the
     * table is.
     *
     * The other control is who may call at all, and that is {@link #builderOnly}.
     */
    public Mono<List<MySQLDrift.DriftRepair>> repairDrift(
            String appCode, String clientCode, String storageName, boolean approved) {

        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,
                        ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),
                        (ca, ac) -> this.clientCode(clientCode),
                        (ca, ac, cc) -> this.builderOnly(ac),
                        (ca, ac, cc, allowed) -> getStorageWithKIRunValidation(storageName, ac, cc)
                                .map(ObjectWithUniqueID::getObject),
                        (ca, ac, cc, allowed, storage) -> this.connectionService
                                .allAppData(ConnectionSubType.MYSQL, ac)
                                .concatMap(conn -> this.mySQLAppDataService.repairDrift(conn, ac, storage, approved))
                                .collectList()
                                .map(lists -> lists.stream()
                                        .flatMap(List::stream)
                                        .toList()))
                .defaultIfEmpty(List.of())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.repairDrift"));
    }

    /**
     * Write access to the application, and nothing else will do.
     *
     * Not {@link #genericOperation} with the storage's own authority, and not
     * {@link #builderOrAuthorised} either, for three reasons that only apply to the
     * drift pair:
     *
     * <ol>
     * <li><b>A blank authority admits everyone.</b>
     * {@code SecurityContextUtil.hasAuthority(null, ...)} returns TRUE by design, and
     * a storage states no {@code deleteAuth} unless somebody wrote one - which is
     * most of them. On a row delete that is the intended default. On a statement that
     * can {@code DROP COLUMN} it means the gate is not there at all.</li>
     * <li><b>The blast radius is every client, not the caller's.</b> Tenants are
     * discovered from the schemas the app has on the server, so one call reports on -
     * and an approved one alters - schemas belonging to clients the caller may never
     * have heard of. A per-client runtime authority does not describe that, so it
     * cannot gate it.</li>
     * <li><b>It reaches the draft surface.</b> Draft schemas are in the same sweep,
     * and every other route that can touch draft goes through {@link #onSurface},
     * which requires exactly this check.</li>
     * </ol>
     *
     * An OR with the storage's authority - what {@code clearAllRows} does - would
     * undo all three, so this is a replacement rather than an addition. There is no
     * runtime path into either route to keep working: nothing in a running app asks
     * about its own table's shape.
     */
    private Mono<Boolean> builderOnly(String appCode) {

        return SecurityContextUtil.getUsersContextAuthentication()
                .flatMap(ca -> this.securityService
                        .hasWriteAccess(appCode, ca.getClientCode())
                        .defaultIfEmpty(Boolean.FALSE))
                .flatMap(hasAccess -> BooleanUtil.safeValueOf(hasAccess)
                        ? Mono.just(Boolean.TRUE)
                        : this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                CoreMessageResourceService.FORBIDDEN_UPDATE_STORAGE, appCode))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.builderOnly"));
    }

    /**
     * Drop one storage's draft collection when its definition is deleted.
     *
     * Called from StorageService.delete, which runs on the live surface, so the
     * draft namespace has to be named rather than inferred from the ambient flag.
     */
    public Mono<Boolean> dropDraftStorageData(String appCode, String clientCode, Storage storage) {

        return this.connectionService.read("appData", appCode, clientCode, ConnectionType.APP_DATA)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(conn -> this.services
                        .get(conn.isEmpty() ? DEFAULT_APP_DATA_SERVICE : conn.get().getConnectionSubType())
                        .dropDraftStorage(clientCode, conn.orElse(null), storage))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.dropDraftStorageData"));
    }

    /**
     * Run one app-data operation against an explicitly named surface.
     *
     * Which surface a data call hits is normally ambient: {@code LogUtil.isDraft()},
     * which the gateway sets from the resolved hostname and strips on the way in, so
     * no caller can forge it. That is deliberate, and it stays the default -- with
     * {@code draft == null} this returns the operation untouched.
     *
     * The builder is the case the ambient rule cannot serve. It runs on the live
     * host, and still has to be able to read, seed and clear an app's draft sandbox.
     * So it may name the surface, the same way a definition write already names its
     * target with {@code ?draft=true}.
     *
     * Both values are gated, not just TRUE. Forcing {@code false} from the draft host
     * is the mirror-image danger -- a draft-surface page writing into live data -- and
     * one bar closes both. The bar is write access to the app, because otherwise
     * anyone who can read a storage's rows could read its unpublished ones, and the
     * draft hostname would stop being the credential that gates unpublished work.
     */
    public <T> Mono<T> onSurface(String appCode, Boolean draft, Mono<T> operation) {

        if (draft == null)
            return operation;

        Mono<T> denied = this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                CoreMessageResourceService.FORBIDDEN_EXPLICIT_DRAFT_SURFACE, appCode);

        return SecurityContextUtil.getUsersContextAuthentication()
                // Only the missing-context case falls through here. An empty result
                // from the operation itself must stay empty, which is why this sits on
                // the authentication step rather than on the whole chain.
                .switchIfEmpty(Mono.defer(() -> this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        CoreMessageResourceService.FORBIDDEN_EXPLICIT_DRAFT_SURFACE, appCode)))
                .flatMap(ca -> this.securityService
                        .hasWriteAccess(appCode == null ? ca.getUrlAppCode() : appCode, ca.getClientCode())
                        .defaultIfEmpty(Boolean.FALSE)
                        .flatMap(hasAccess -> BooleanUtil.safeValueOf(hasAccess)
                                ? operation.contextWrite(Context.of(LogUtil.DRAFT_KEY, draft))
                                : denied))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.onSurface"));
    }

    /**
     * Empty a storage from the BUILDER: every row, collection and history kept.
     *
     * Deliberately not {@link #deleteByFilter}, which is the same delete but gated on
     * the storage's own {@code deleteAuth} because its caller is the running app
     * through {@code CoreServices.Storage.DeleteByFilter}. That bar is right there and
     * wrong here; see {@link #builderOrAuthorised}.
     *
     * @param dryRun count the rows instead of deleting them
     */
    public Mono<Long> clearAllRows(String appCode, String clientCode, String storageName, Boolean dryRun) {

        Mono<Long> mono = FlatMapUtil.flatMapMonoWithNull(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),
                (ca, ac) -> this.clientCode(clientCode),
                (ca, ac, cc) -> connectionService.read("appData", ac, cc, ConnectionType.APP_DATA),
                (ca, ac, cc, conn) -> Mono.just(
                        this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),
                (ca, ac, cc, conn, dataService) -> getStorageWithKIRunValidation(storageName, ac, cc)
                        .map(ObjectWithUniqueID::getObject),
                (ca, ac, cc, conn, dataService, storage) -> this.<Long>builderOrAuthorised(
                        storage, ac,
                        // null: a builder clear-all empties rows and keeps history, per this
                        // method's contract. It is not a user deleting a record.
                        () -> dataService.deleteByFilter(cc, conn, storage, new Query(), dryRun, null),
                        Storage::getDeleteAuth,
                        CoreMessageResourceService.FORBIDDEN_DELETE_STORAGE));

        return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.clearAllRows"));
    }

    /**
     * A storage operation a BUILDER may perform, on top of whoever the storage's own
     * authority already admits.
     *
     * The storage's {@code createAuth} / {@code deleteAuth} answer a RUNTIME question:
     * may this app's user create or delete rows. They are routinely written in the
     * app's own role namespace -- {@code Authorities.CXAPP.ROLE_Super_Admin} on the
     * `rim` app's Project storage, for one -- and a builder working on that app from
     * the workspace holds none of it and never will. It is not a user of the app. So
     * gating a builder action on those made the action unreachable for any app that
     * sets them, which is most real apps.
     *
     * The bar that fits is write access to the APPLICATION. It is what definition
     * writes already use (`AbstractOverridableDataService.accessCheck`) and what naming
     * a data surface uses, so this makes the three consistent. It also grants nothing
     * in substance: anyone with app write access can PUT the storage definition,
     * including the very auth expression being checked here, so a bar the caller can
     * rewrite in one call was never a bar.
     *
     * An OR rather than a replacement, because `DELETE {storage}?deleteAll=true` -- the
     * DROP, which is strictly more destructive -- already admits a caller holding only
     * `deleteAuth`. The stricter operation must not end up with the looser gate.
     */
    private <T> Mono<T> builderOrAuthorised(
            Storage storage,
            String appCode,
            Supplier<Mono<T>> operation,
            Function<Storage, String> authFun,
            String msgString) {

        if (storage == null)
            return msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                    CoreMessageResourceService.STORAGE_NOT_FOUND);

        return SecurityContextUtil.getUsersContextAuthentication()
                .flatMap(ca -> {
                    if (SecurityContextUtil.hasAuthority(
                            authFun.apply(storage), ca.getUser().getAuthorities()))
                        return Mono.just(Boolean.TRUE);

                    return this.securityService.hasWriteAccess(appCode, ca.getClientCode())
                            .defaultIfEmpty(Boolean.FALSE);
                })
                // The refusal is raised HERE, not as a switchIfEmpty over the whole
                // chain: an operation that legitimately answers nothing must not be
                // reported as a denial. That mistake is already in genericOperation and
                // has cost one debugging session.
                .flatMap(allowed -> BooleanUtil.safeValueOf(allowed)
                        ? operation.get()
                        : this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                msgString, storage.getName()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.builderOrAuthorised"));
    }

    /**
     * Seed one storage's draft rows from its live rows.
     *
     * Publish promotes definitions and never promotes data, so a draft surface starts
     * empty and stays empty. This is how a sandbox gets realistic rows.
     *
     * Gated on write access to the app alone, NOT on the storage's `createAuth`: see
     * {@link #builderOrAuthorised} for why that is the wrong question. There is no
     * runtime path into this route to preserve, so unlike a clear it is not an OR.
     *
     * It runs with no ambient flag of its own: both namespaces are named explicitly,
     * the same discipline dropDraftStorage and estimatedRowCount already follow.
     */
    public Mono<Long> copyLiveDataToDraft(String appCode, String clientCode, String storageName, Boolean replace) {

        Mono<Long> mono = FlatMapUtil.flatMapMonoWithNull(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),
                (ca, ac) -> this.clientCode(clientCode),
                (ca, ac, cc) -> this.securityService.hasWriteAccess(ac, ca.getClientCode())
                        .defaultIfEmpty(Boolean.FALSE)
                        .flatMap(hasAccess -> BooleanUtil.safeValueOf(hasAccess)
                                ? Mono.just(Boolean.TRUE)
                                : this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                        CoreMessageResourceService.FORBIDDEN_EXPLICIT_DRAFT_SURFACE, ac)),
                (ca, ac, cc, canWrite) -> connectionService.read("appData", ac, cc, ConnectionType.APP_DATA),
                (ca, ac, cc, canWrite, conn) -> Mono.just(
                        this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),
                (ca, ac, cc, canWrite, conn, dataService) -> getStorageWithKIRunValidation(storageName, ac, cc)
                        .map(ObjectWithUniqueID::getObject),
                (ca, ac, cc, canWrite, conn, dataService, storage) ->
                        dataService.copyLiveToDraft(cc, conn, storage, replace));

        // A zero can only mean the live collection was empty, because the count is
        // taken before anything is written, and in that case nothing was changed --
        // the draft rows that were there are still there.
        //
        // Reported as a bad request rather than a successful copy of nothing, because
        // "Copied 0 rows" reads as a bug in the copy.
        return mono.flatMap(copied -> copied < 1
                        ? this.msgService.<Long>throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                CoreMessageResourceService.DRAFT_COPY_SOURCE_EMPTY, storageName)
                        // The 404 the dropped genericOperation used to raise. Safe as a
                        // switchIfEmpty only because it sits AFTER this flatMap, which
                        // either errors or emits: the sole way to arrive empty is a
                        // storage that did not resolve, or an onlyThruKIRun one.
                        : Mono.just(copied))
                .switchIfEmpty(Mono.defer(() -> this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                        CoreMessageResourceService.STORAGE_NOT_FOUND)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.copyLiveDataToDraft"));
    }

    public Mono<Long> estimatedRowCount(String appCode, String clientCode) {
        return this.connectionService.read("appData", appCode, clientCode, ConnectionType.APP_DATA)
                .flatMap(conn -> this.mongoAppDataService.estimatedRowCount(conn, appCode, clientCode))
                .switchIfEmpty(Mono.defer(() -> this.mongoAppDataService.estimatedRowCount(null, appCode, clientCode)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.estimatedRowCount"));
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
                (ca, ac) -> this.clientCode(clientCode),
                (ca, ac, cc) -> connectionService.read("appData", ac, cc, ConnectionType.APP_DATA),
                (ca, ac, cc, conn) -> Mono.just(
                        this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),
                (ca, ac, cc, conn, dataService) -> getStorageWithKIRunValidation(storageName, ac, cc)
                        .map(ObjectWithUniqueID::getObject),
                // Gated on createAuth, exactly as create is. Writing many rows is not a
                // reason to write them unauthorised, and this path had no check at all.
                (ca, ac, cc, conn, dataService, storage) -> this.<List<Map<String, Object>>>genericOperation(
                        storage,
                        (contextAuth, hasAccess) -> Flux.fromIterable(dataArray)
                                .flatMapSequential(dataObject -> FlatMapUtil.flatMapMono(
                                                () -> this.processRelationsForCreate(
                                                        ac, cc, storage, dataObject, dataService, conn),
                                                updatedDataObject -> this.createWithTriggers(
                                                        cc, dataService, conn, storage, updatedDataObject))
                                        .flatMap(createdObj -> {
                                            if (BooleanUtil.safeValueOf(storage.getGenerateEvents())) {
                                                return this.generateEvent(
                                                        ca,
                                                        ac,
                                                        cc,
                                                        storage,
                                                        "Create",
                                                        Map.of("dataArr", List.of(createdObj)),
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
                                                this.resolveEagerFields(storage, eager, eagerFields))))
                                .collectList(),
                        Storage::getCreateAuth,
                        CoreMessageResourceService.FORBIDDEN_CREATE_STORAGE));
        return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.createMany"));
    }

    /**
     * Which relations to load eagerly, without assuming the storage has any.
     *
     * {@code storage.getRelations()} is null for a storage that declares none, and
     * the callers that dereferenced it directly threw an NPE on every eager read of
     * such a storage. readPage guarded this; create, createMany, update and read
     * did not.
     */
    /**
     * What to expand: what the caller NAMED, or every relation if they just said
     * "eager".
     *
     * An explicit list now wins over the flag. It used to be the other way round -
     * eager=true threw the named fields away and substituted every top-level
     * relation - so a caller who passed both silently got something other than what
     * they asked for, and a nested path like {@code category.parent} was flattened
     * to {@code category} before anything could act on it.
     *
     * eager=true on its own still means every relation, one level deep, which is
     * what it has always meant and what every existing caller relies on.
     */
    private List<String> resolveEagerFields(Storage storage, Boolean eager, List<String> eagerFields) {

        if (eagerFields != null && !eagerFields.isEmpty()) return eagerFields;

        if (!BooleanUtil.safeValueOf(eager)) return eagerFields;

        return storage.getRelations() == null
                ? List.of()
                : storage.getRelations().keySet().stream().toList();
    }

    /** One row, filled the same way a page is. */
    private Mono<Map<String, Object>> fillRelatedObjects(
            String appCode,
            String clientCode,
            Storage storage,
            Map<String, Object> created,
            IAppDataService dataService,
            Connection conn,
            List<String> eagerFields) {

        return this.fillRelatedObjects(
                        appCode, clientCode, storage, List.of(created), dataService, conn, eagerFields)
                .map(rows -> rows.isEmpty() ? created : rows.getFirst());
    }

    /**
     * Expand the named relations of every row in one pass.
     *
     * Per PAGE rather than per row, which is the whole shape of this method. Run
     * per row it issued one query for every row and every eager field - twenty rows
     * with two relations was forty round trips to fetch, at most, forty distinct
     * objects. Gathering the ids first collapses that to one query per field, and
     * the saving grows with the page rather than staying constant.
     *
     * Two things that are not optimisations:
     *
     * The target storage is read through {@link #genericOperation} against its own
     * {@code readAuth}. It used to be handed straight to the backend, so a storage
     * the caller could not read was readable through anything that pointed at it.
     * Joins and subqueries have always checked; this was the one that did not.
     *
     * The fetch is no longer capped. It used to ask for fifty, applied to a lookup
     * BY ID, so a row with more children than that silently lost the rest and the
     * response looked exactly like a row that only had fifty. The bound now is the
     * number of ids the rows actually hold, which is the honest one.
     */
    @SuppressWarnings("unchecked")
    private Mono<List<Map<String, Object>>> fillRelatedObjects(
            String appCode,
            String clientCode,
            Storage storage,
            List<Map<String, Object>> rows,
            IAppDataService dataService,
            Connection conn,
            List<String> eagerFields) {

        return this.fillRelatedObjects(
                appCode, clientCode, storage, rows, dataService, conn, eagerTree(eagerFields), 0);
    }

    /**
     * A relation only has to be named once however many rows reference it.
     *
     * {@code eagerFields} are PATHS, so {@code category.parent} expands the category
     * of every row and then the parent of every category. The nesting is free in the
     * output because {@link #applyRelated} inlines the very objects this map holds -
     * expanding them expands what the caller already has.
     *
     * Each level costs one query per relation, NOT one per row and certainly not one
     * per row per level: the recursion runs over the DISTINCT objects fetched, so a
     * category shared by a hundred rows has its own parent fetched once. A page of
     * twenty rows two levels deep is two queries, which is the only reason depth is
     * affordable at all.
     */
    private Mono<List<Map<String, Object>>> fillRelatedObjects(
            String appCode,
            String clientCode,
            Storage storage,
            List<Map<String, Object>> rows,
            IAppDataService dataService,
            Connection conn,
            Map<String, Map<String, Object>> tree,
            int depth) {

        if (rows == null || rows.isEmpty()) return Mono.just(rows == null ? List.of() : rows);

        if (storage.getRelations() == null || storage.getRelations().isEmpty() || tree == null || tree.isEmpty())
            return Mono.just(rows);

        // Relations can point at each other - a book's author's latest book - so a
        // path deep enough to loop has to stop somewhere. It stops rather than
        // erroring: the caller asked for too much expansion, not for a failure, and
        // the rows they get back are correct to the depth they were given.
        if (depth >= MAX_EAGER_DEPTH) return Mono.just(rows);

        List<String> fields =
                tree.keySet().stream().filter(f -> storage.getRelations().containsKey(f)).toList();

        if (fields.isEmpty()) return Mono.just(rows);

        return Flux.fromIterable(fields)
                .concatMap(field -> this.relatedObjectsFor(appCode, clientCode, storage, rows, dataService, conn,
                                field)
                        .flatMap(fetched -> {
                            applyRelated(rows, fetched.field(), fetched.type(), fetched.byId());

                            Map<String, Object> nested = tree.get(field);
                            if (nested == null || nested.isEmpty() || fetched.byId().isEmpty())
                                return Mono.just(Boolean.TRUE);

                            return this.fillRelatedObjects(
                                            appCode,
                                            clientCode,
                                            fetched.target(),
                                            List.copyOf(fetched.byId().values()),
                                            dataService,
                                            conn,
                                            (Map<String, Map<String, Object>>) (Map<String, ?>) nested,
                                            depth + 1)
                                    .thenReturn(Boolean.TRUE);
                        }))
                .then(Mono.just(rows))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.fillRelatedObjects"));
    }

    /**
     * Turn {@code ["category.parent", "category.owner", "author"]} into a tree.
     *
     * Paths rather than a nested request object because eagerFields is already a
     * flat list of strings on every caller and every KIRun signature, and a dotted
     * path extends that without changing any of them. Siblings merge, so naming
     * {@code category.parent} and {@code category.owner} expands category once.
     */
    static Map<String, Map<String, Object>> eagerTree(List<String> eagerFields) {

        Map<String, Map<String, Object>> root = new java.util.LinkedHashMap<>();
        if (eagerFields == null) return root;

        for (String path : eagerFields) {
            if (path == null || path.isBlank()) continue;

            Map<String, Map<String, Object>> level = root;
            for (String segment : path.split("\\.")) {
                String name = segment.trim();
                if (name.isEmpty()) break;
                level = (Map<String, Map<String, Object>>) (Map<String, ?>)
                        level.computeIfAbsent(name, k -> new java.util.LinkedHashMap<>());
            }
        }

        return root;
    }

    /** One relation's objects, by id, with the storage they came from. */
    private record RelatedFetch(
            String field,
            StorageRelationType type,
            Map<String, Map<String, Object>> byId,
            Storage target) {}

    /** Every object the page references through one relation, by id. */
    private Mono<RelatedFetch> relatedObjectsFor(
            String appCode,
            String clientCode,
            Storage storage,
            List<Map<String, Object>> rows,
            IAppDataService dataService,
            Connection conn,
            String field) {

        StorageRelation relation = storage.getRelations().get(field);

        Set<String> ids = new java.util.LinkedHashSet<>();
        for (Map<String, Object> row : rows) collectIds(row.get(field), ids);

        if (ids.isEmpty())
            return this.getStorageForRelation(relation.getStorageName(), appCode, clientCode)
                    .map(ObjectWithUniqueID::getObject)
                    .map(target -> new RelatedFetch(field, relation.getRelationType(), Map.of(), target))
                    .defaultIfEmpty(new RelatedFetch(field, relation.getRelationType(), Map.of(), null));

        Query query = new Query();
        // Exactly as many as are referenced. A lookup by id cannot return more.
        query.setSize(ids.size());
        query.setCondition(new FilterCondition()
                .setField("_id")
                .setOperator(FilterConditionOperator.IN)
                .setMultiValue(List.copyOf(ids)));

        return FlatMapUtil.flatMapMono(
                        () -> this.getStorageForRelation(relation.getStorageName(), appCode, clientCode)
                                .map(ObjectWithUniqueID::getObject),
                        target -> this.<List<Map<String, Object>>>genericOperation(
                                target,
                                (ca, hasAccess) -> dataService
                                        .readPageAsFlux(clientCode, conn, target, query)
                                        .collectList(),
                                Storage::getReadAuth,
                                CoreMessageResourceService.FORBIDDEN_READ_STORAGE),
                        (target, objects) -> {
                            Map<String, Map<String, Object>> byId = new java.util.LinkedHashMap<>();
                            for (Map<String, Object> o : objects) {
                                Object id = o.get("_id");
                                if (id != null) byId.put(id.toString(), o);
                            }
                            return Mono.just(new RelatedFetch(field, relation.getRelationType(), byId, target));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.relatedObjectsFor"));
    }

    private static void collectIds(Object value, Set<String> into) {

        if (value instanceof List<?> list) {
            for (Object o : list) if (o != null) into.add(o.toString());
            return;
        }

        if (value != null) into.add(value.toString());
    }

    /**
     * Put the fetched objects back, each row in the order that row declared.
     *
     * Order is a property of the row, not of the fetch, which is why it is applied
     * here rather than by sorting the result set. The previous version sorted the
     * fetched list by its position in one row's id list - correct for a single row,
     * and meaningless once the same fetch serves a page, which is how an ordinary
     * five-element relation came back shuffled.
     *
     * An id with no object is dropped rather than left as a bare id: the field is
     * either expanded or it is not, and a list holding both shapes is worse than
     * one missing an element that no longer exists.
     */
    @SuppressWarnings("unchecked")
    private static void applyRelated(
            List<Map<String, Object>> rows,
            String field,
            StorageRelationType type,
            Map<String, Map<String, Object>> byId) {

        for (Map<String, Object> row : rows) {
            Object value = row.get(field);
            if (value == null) continue;

            if (type == StorageRelationType.TO_MANY) {
                List<Object> declared = value instanceof List<?> list ? (List<Object>) list : List.of(value);
                row.put(
                        field,
                        declared.stream()
                                .filter(java.util.Objects::nonNull)
                                .map(id -> byId.get(id.toString()))
                                .filter(java.util.Objects::nonNull)
                                .toList());
                continue;
            }

            Map<String, Object> object = byId.get(value.toString());
            if (object == null) row.remove(field);
            else row.put(field, object);
        }
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
                    if (BooleanUtil.safeValueOf(op.get().getObject().getIncludeContextAuthentication()))
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
            String clientCode, IAppDataService dataService, Connection conn, Storage storage, DataObject dataObject) {
        boolean noBeforeCreate = storage.getTriggers() == null
                || storage.getTriggers().get(StorageTriggerType.BEFORE_CREATE) == null
                || storage.getTriggers().get(StorageTriggerType.BEFORE_CREATE).isEmpty();

        boolean noAfterCreate = storage.getTriggers() == null
                || storage.getTriggers().get(StorageTriggerType.AFTER_CREATE) == null
                || storage.getTriggers().get(StorageTriggerType.AFTER_CREATE).isEmpty();

        if (noBeforeCreate && noAfterCreate)
            return dataService.create(clientCode, conn, storage, dataObject);

        return FlatMapUtil.flatMapMono(
                () -> {
                    if (noBeforeCreate)
                        return Mono.just(true);

                    return this.executeTriggers(
                            storage,
                            StorageTriggerType.BEFORE_CREATE,
                            Map.of(DATA_OBJECT_KEY, this.gson.toJsonTree(dataObject.getData())));
                },
                beforeCreate -> dataService.create(clientCode, conn, storage, dataObject),
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
                                .checkIfExists(
                                        clientCode, conn, storage, map.get("_id").toString())
                                .flatMap(e -> {
                                    if (e)
                                        return Mono.just(Tuples.of(
                                                false, map.get("_id").toString()));

                                    return this.msgService.throwMessage(
                                            msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
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
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.checkOrCreateRelatedObject"));
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
                // concatMap, not flatMap. The ids are written back to the row in the
                // order they arrive, so interleaving them shuffles the stored array -
                // and the order of a TO_MANY is the author's data, not an accident of
                // how fast each related object happened to be created.
                () -> Flux.fromIterable(relationList).concatMap(e -> e).collectList(), list -> {
                    List<RelationDataObject> errorObjects = list.stream()
                            .filter(e -> !Objects.isNull(e.getException()))
                            .toList();
                    if (!errorObjects.isEmpty())
                        return this.checkAndDeleteCreatedObjects(
                                appCode, clientCode, storage, dataService, conn, list, errorObjects);

                    for (Entry<String, List<RelationDataObject>> e : list.stream()
                            .collect(Collectors.groupingBy(RelationDataObject::getFieldName))
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
                // null: this is rollback of a half-made create, not a delete worth auditing.
                storage -> dataService.delete(clientCode, conn, storage, id, null))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.deleteCreatedRelatedObject"));
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
                            HttpStatus.BAD_REQUEST, msg, errorObjects.getFirst().getException()),
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
                            .map(e -> new RelationDataObject(key, e.getT1(), map, e.getT2(), null))
                            .onErrorResume(e -> Mono.just(new RelationDataObject(key, false, map, null, e))));
                }
            }
        }
        return relationList;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> convertForeignKeyValuesToObjects(
            Map<String, Object> dob, Entry<String, StorageRelation> relation, String key) {
        List<Map<String, Object>> list = List.of();
        if (relation.getValue().getRelationType() == StorageRelationType.TO_MANY) {
            if (dob.get(key) instanceof List<?> lst)
                list = lst.stream()
                        .map(e -> e instanceof Map ? (Map<String, Object>) e : Map.of("_id", (Object) e.toString()))
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
                (ca, ac) -> this.clientCode(clientCode),
                (ca, ac, cc) -> connectionService.read("appData", ac, cc, ConnectionType.APP_DATA),
                (ca, ac, cc, conn) -> Mono.just(
                        this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),
                (ca, ac, cc, conn, dataService) -> getStorageWithKIRunValidation(storageName, ac, cc)
                        .map(ObjectWithUniqueID::getObject),
                (ca, ac, cc, conn, dataService, storage) -> this.genericOperation(
                        storage,
                        (contextAuth, hasAccess) -> FlatMapUtil.flatMapMono(
                                () -> this.processRelationsForUpdate(
                                        ac, cc, dataService, conn, storage, dataObject, override),
                                updatedDataObject -> this.updateWithTriggers(
                                        ac, cc, dataService, conn, storage, updatedDataObject, override),
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
                        this.resolveEagerFields(storage, eager, eagerFields)));

        return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.update"));
    }

    /**
     * Update many rows in one call, the counterpart to {@link #createMany}.
     *
     * Gated on {@code updateAuth} through genericOperation, same as {@link #update}.
     * Rows are applied sequentially rather than concurrently so that two entries
     * touching the same row keep their order, and so triggers see a predictable
     * sequence.
     */
    public Mono<List<Map<String, Object>>> updateMany(
            String appCode,
            String clientCode,
            String storageName,
            List<DataObject> dataArray,
            Boolean override,
            Boolean eager,
            List<String> eagerFields) {

        Mono<List<Map<String, Object>>> mono = FlatMapUtil.flatMapMonoWithNull(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),
                (ca, ac) -> this.clientCode(clientCode),
                (ca, ac, cc) -> connectionService.read("appData", ac, cc, ConnectionType.APP_DATA),
                (ca, ac, cc, conn) -> Mono.just(
                        this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),
                (ca, ac, cc, conn, dataService) -> getStorageWithKIRunValidation(storageName, ac, cc)
                        .map(ObjectWithUniqueID::getObject),
                (ca, ac, cc, conn, dataService, storage) -> this.<List<Map<String, Object>>>genericOperation(
                        storage,
                        (contextAuth, hasAccess) -> Flux.fromIterable(dataArray)
                                .flatMapSequential(dataObject -> FlatMapUtil.flatMapMono(
                                                () -> this.processRelationsForUpdate(
                                                        ac, cc, dataService, conn, storage, dataObject, override),
                                                updatedDataObject -> this.updateWithTriggers(
                                                        ac, cc, dataService, conn, storage, updatedDataObject,
                                                        override),
                                                (updatedDataObject, e) -> this.generateEvent(
                                                        ca,
                                                        ac,
                                                        cc,
                                                        storage,
                                                        "Update",
                                                        e.getT1(),
                                                        e.getT2().orElse(null)))
                                        .flatMap(updated -> this.fillRelatedObjects(
                                                ac,
                                                cc,
                                                storage,
                                                updated,
                                                dataService,
                                                conn,
                                                this.resolveEagerFields(storage, eager, eagerFields))))
                                .collectList(),
                        Storage::getUpdateAuth,
                        CoreMessageResourceService.FORBIDDEN_UPDATE_STORAGE));

        return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.updateMany"));
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
                        appCode, clientCode, storage.getName(), dob.get("_id").toString(), false, List.of()),
                existing -> Flux.fromIterable(relationList).concatMap(e -> e).collectList(),
                (existing, list) -> {
                    List<RelationDataObject> errorObjects = list.stream()
                            .filter(e -> !Objects.isNull(e.getException()))
                            .toList();
                    if (!errorObjects.isEmpty())
                        return this.checkAndDeleteCreatedObjects(
                                appCode, clientCode, storage, dataService, conn, list, errorObjects);

                    List<Mono<Boolean>> removalList = new ArrayList<>();

                    for (Entry<String, List<RelationDataObject>> e : list.stream()
                            .collect(Collectors.groupingBy(RelationDataObject::getFieldName))
                            .entrySet()) {
                        if ((override && !dob.containsKey(e.getKey()) && existing.get(e.getKey()) == null)
                                || (!override && !dob.containsKey(e.getKey())))
                            continue;

                        StorageRelation relation = storage.getRelations().get(e.getKey());

                        // NOT the SQL ON UPDATE, and deliberately left as it is.
                        // SQL fires ON UPDATE when the REFERENCED key changes, and
                        // the referenced key here is _id: a ULID assigned at insert
                        // that nothing rewrites, so a real ON UPDATE clause can
                        // never fire on either backend. What this does instead is
                        // delete the row that an override update just detached,
                        // which is an ownership operation no database can do for us
                        // and is genuinely useful. The name is the problem rather
                        // than the behaviour; every relation in the fleet declares
                        // NOTHING, so nothing depends on it yet either way.
                        if (relation.getUpdateConstraint() == StorageRelationConstraint.CASCADE) {
                            if (!override)
                                continue;

                            Set<Tuple2<String, String>> allIds = new HashSet<>();
                            if (existing.get(e.getKey()) instanceof List<?> lst) {
                                allIds = lst.stream()
                                        .map(id -> Tuples.of(relation.getStorageName(), id.toString()))
                                        .collect(Collectors.toCollection(HashSet::new));
                            } else if (existing.get(e.getKey()) instanceof String id) {
                                allIds.add(Tuples.of(relation.getStorageName(), id));
                            }

                            if (dob.get(e.getKey()) instanceof List<?> lst) {
                                for (Object id : lst) {
                                    Tuple2<String, String> tup = Tuples.of(relation.getStorageName(), id.toString());
                                    allIds.remove(tup);
                                }
                            } else if (dob.get(e.getKey()) instanceof String id) {
                                Tuple2<String, String> tup = Tuples.of(relation.getStorageName(), id);
                                allIds.remove(tup);
                            }

                            if (!allIds.isEmpty()) {
                                for (Tuple2<String, String> id : allIds) {
                                    removalList.add(this.deleteCreatedRelatedObject(
                                            appCode, clientCode, dataService, conn, id.getT1(), id.getT2())
                                            .onErrorResume(th -> Mono.just(true)));
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
            return dataService.update(clientCode, conn, storage, dataObject, override)
                    .map(e -> Tuples.of(e, Optional.empty()));

        String id = StringUtil.safeValueOf(dataObject.getData().get("_id"));

        if (noBeforeUpdate && noAfterUpdate) {
            return this.read(appCode, clientCode, storage.getName(), id, false, List.of())
                    .flatMap(existing -> dataService
                            .update(clientCode, conn, storage, dataObject, override)
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
                (existing, beforeUpdate) -> dataService.update(clientCode, conn, storage, dataObject, override),
                (existing, beforeUpdate, updated) -> {
                    if (noAfterUpdate)
                        return Mono.just(Tuples.<Map<String, Object>, Optional<Map<String, Object>>>of(
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
            String appCode, String clientCode, String storageName, String id, Boolean eager, List<String> eagerFields) {
        Mono<Map<String, Object>> mono = FlatMapUtil.flatMapMonoWithNull(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),
                (ca, ac) -> this.clientCode(clientCode),
                (ca, ac, cc) -> connectionService.read("appData", ac, cc, ConnectionType.APP_DATA),
                (ca, ac, cc, conn) -> Mono.just(
                        this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),
                (ca, ac, cc, conn, dataService) -> getStorageWithKIRunValidation(storageName, ac, cc)
                        .map(ObjectWithUniqueID::getObject),
                (ca, ac, cc, conn, dataService, storage) -> this.<Map<String, Object>>genericOperation(
                        storage,
                        (contextAuth, hasAccess) -> dataService.read(cc, conn, storage, id),
                        Storage::getReadAuth,
                        CoreMessageResourceService.FORBIDDEN_READ_STORAGE),
                (ca, ac, cc, conn, dataService, storage, read) -> this.fillRelatedObjects(
                        ac,
                        cc,
                        storage,
                        read,
                        dataService,
                        conn,
                        this.resolveEagerFields(storage, eager, eagerFields)));

        return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.read"));
    }

    public Mono<Page<Map<String, Object>>> readPage(
            String appCode, String clientCode, String storageName, Query query) {
        logger.info("App code: {}, client code: {} in storage: {} with query: {}", appCode, clientCode,
                storageName, query);
        Mono<Page<Map<String, Object>>> mono = FlatMapUtil.flatMapMonoWithNull(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),
                (ca, ac) -> this.clientCode(clientCode),
                (ca, ac, cc) -> connectionService.read("appData", ac, cc, ConnectionType.APP_DATA),
                (ca, ac, cc, conn) -> Mono.just(
                        this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),
                (ca, ac, cc, conn, dataService) -> getStorageWithKIRunValidation(storageName, ac, cc)
                        .map(ObjectWithUniqueID::getObject),
                (ca, ac, cc, conn, dataService, storage) -> this.<Page<Map<String, Object>>>genericOperation(
                        storage,
                        (contextAuth, hasAccess) -> dataService.readPage(cc, conn, storage, query),
                        Storage::getReadAuth,
                        CoreMessageResourceService.FORBIDDEN_READ_STORAGE),
                (ca, ac, cc, conn, dataService, storage, page) -> {
                    if (storage.getRelations() == null || storage.getRelations().isEmpty())
                        return Mono.just(page);

                    return this.fillRelatedObjects(
                                    ac,
                                    cc,
                                    storage,
                                    page.getContent(),
                                    dataService,
                                    conn,
                                    this.resolveEagerFields(storage, query.getEager(), query.getEagerFields()))
                            .map(list -> PageableExecutionUtils.getPage(
                                    list, page.getPageable(), page::getTotalElements));
                });

        return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.readPage"));
    }

    /**
     * A grouped read over a storage.
     *
     * Gated on {@code readAuth}, the same bar as {@link #readPage}: an aggregate is
     * a read, and anyone who can page the rows can already compute these numbers
     * client side. No relation filling, because once rows are collapsed into groups
     * there is no row left to hang a relation off.
     */
    public Mono<Page<Map<String, Object>>> aggregate(
            String appCode, String clientCode, String storageName, AggregateQuery query) {

        Mono<Page<Map<String, Object>>> mono = FlatMapUtil.flatMapMonoWithNull(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),
                (ca, ac) -> this.clientCode(clientCode),
                (ca, ac, cc) -> connectionService.read("appData", ac, cc, ConnectionType.APP_DATA),
                (ca, ac, cc, conn) -> Mono.just(
                        this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),
                (ca, ac, cc, conn, dataService) -> getStorageWithKIRunValidation(storageName, ac, cc)
                        .map(ObjectWithUniqueID::getObject),
                (ca, ac, cc, conn, dataService, storage) -> this.<Page<Map<String, Object>>>genericOperation(
                        storage,
                        (contextAuth, hasAccess) -> dataService.aggregate(cc, conn, storage, query),
                        Storage::getReadAuth,
                        CoreMessageResourceService.FORBIDDEN_READ_STORAGE));

        return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.aggregate"));
    }

    public Mono<Boolean> delete(
            String appCode, String clientCode, String storageName, String id, Boolean deleteVersion) {
        Mono<Boolean> mono = FlatMapUtil.flatMapMonoWithNull(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),
                (ca, ac) -> this.clientCode(clientCode),
                (ca, ac, cc) -> connectionService.read("appData", ac, cc, ConnectionType.APP_DATA),
                (ca, ac, cc, conn) -> Mono.just(
                        this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),
                (ca, ac, cc, conn, dataService) -> getStorageWithKIRunValidation(storageName, ac, cc)
                        .map(ObjectWithUniqueID::getObject),
                (ca, ac, cc, conn, dataService, storage) -> this.genericOperation(
                        storage,
                        // ac/cc, NOT the raw method parameters. Every sibling method
                        // uses the resolved values; these two did not, so a KIRun
                        // Storage.Delete with a blank clientCode passed null through to
                        // the collection resolver and produced the literal database
                        // "null_<appCode>". The delete then silently 404d.
                        (contextAuth, hasAccess) -> FlatMapUtil.flatMapMono(
                                () -> this.deleteRelatedObjects(ac, cc, dataService, conn, storage, id, deleteVersion),
                                deleted -> this.deleteWithTriggers(ac, cc, dataService, conn, storage, id, deleteVersion),
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
            String appCode,
            String clientCode,
            String storageName,
            Query query,
            Boolean devMode,
            Boolean deleteVersion) {
        Mono<Long> mono = FlatMapUtil.flatMapMonoWithNull(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),
                (ca, ac) -> this.clientCode(clientCode),
                (ca, ac, cc) -> connectionService.read("appData", ac, cc, ConnectionType.APP_DATA),
                (ca, ac, cc, conn) -> Mono.just(
                        this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),
                (ca, ac, cc, conn, dataService) -> getStorageWithKIRunValidation(storageName, ac, cc)
                        .map(ObjectWithUniqueID::getObject),
                (ca, ac, cc, conn, dataService, storage) -> this.<Long>genericOperation(
                        storage,
                        (contextAuth, hasAccess) -> dataService.deleteByFilter(cc, conn, storage, query, devMode,
                                deleteVersion),
                        Storage::getDeleteAuth,
                        CoreMessageResourceService.FORBIDDEN_DELETE_STORAGE),
                (ca, ac, cc, conn, dataService, storage, deletedCount) -> {
                    return Mono.just(deletedCount);
                });

        return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.deleteByFilter"));
    }

    /**
     * Enforce the delete constraints that point AT this row, before it goes.
     *
     * The direction is the one SQL uses, and it is the opposite of what this method
     * used to do. A constraint is declared on the relation that holds the reference -
     * {@code blogs.category -> blogCategories} - and it protects the REFERENCED row:
     * deleting a category is what RESTRICT refuses and what CASCADE propagates from.
     * Written the other way round, deleting a blog would have deleted its category,
     * which is not what either word means to anyone who has used a database and is
     * not something a foreign key could ever express.
     *
     * Nothing in the fleet moves as a result. Every relation that exists declares
     * NOTHING, so the first storage to mean either word gets the meaning it expects
     * rather than inheriting one from before.
     *
     * The relation is found by looking backwards, because the referencing side is the
     * only side that declares it; {@code storagesReferencing} is cached per app for
     * exactly that reason.
     */
    private Mono<Boolean> deleteRelatedObjects(
            String appCode,
            String clientCode,
            IAppDataService dataService,
            Connection conn,
            Storage storage,
            String id,
            Boolean deleteVersion) {

        return this.storageService
                .storagesReferencing(appCode, storage.getName())
                .flatMap(names -> names.isEmpty()
                        ? Mono.just(Boolean.TRUE)
                        : this.enforceReferencingConstraints(
                                appCode, clientCode, dataService, conn, storage, names, id, deleteVersion))
                .defaultIfEmpty(Boolean.TRUE)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.deleteRelatedObjects"));
    }

    private Mono<Boolean> enforceReferencingConstraints(
            String appCode,
            String clientCode,
            IAppDataService dataService,
            Connection conn,
            Storage storage,
            List<String> referencing,
            String id,
            Boolean deleteVersion) {

        return Flux.fromIterable(referencing)
                // A name the index carries that this client does not resolve comes
                // back EMPTY and is skipped by concatMap on its own. Errors are
                // deliberately NOT swallowed here: a constraint that quietly stops
                // running because Mongo hiccuped is a constraint that is not a
                // constraint, and the caller would see the delete succeed.
                .concatMap(name ->
                        this.storageService.read(name, appCode, clientCode).map(ObjectWithUniqueID::getObject))
                .flatMapIterable(child -> declaredRelations(child, storage.getName()))
                // Asked of the backend, per relation, and asked about the TABLE
                // rather than the definition. A relation that could be a foreign key
                // but has none - because orphan rows blocked the ADD CONSTRAINT, or
                // because the target stopped resolving - comes back false here and
                // the service enforces it after all. Answered from the definition
                // alone, this is where a constraint quietly became nobody's job.
                .filterWhen(r -> dataService
                        .enforcesRelationConstraint(clientCode, conn, r.child(), r.field(), r.relation())
                        .map(enforced -> !enforced))
                .collectList()
                .flatMap(all -> this.applyConstraints(appCode, clientCode, dataService, conn, all, id, deleteVersion));
    }

    /**
     * Every relation pointing at this storage that asks for something on delete.
     *
     * Only the declaration is read here; who carries it out is decided one step
     * later, because that question needs the database and this one does not. A
     * relation declaring NOTHING is dropped now so the backend is never asked about
     * it - which, for every relation in the fleet as it stands, means never asked at
     * all.
     */
    private static List<ReferencingRelation> declaredRelations(Storage child, String targetStorageName) {

        List<ReferencingRelation> out = new ArrayList<>();
        if (child.getRelations() == null) return out;

        child.getRelations().forEach((field, relation) -> {
            if (relation == null || !targetStorageName.equals(relation.getStorageName())) return;

            StorageRelationConstraint constraint = relation.getDeleteConstraint();
            if (constraint == null || constraint == StorageRelationConstraint.NOTHING) return;

            out.add(new ReferencingRelation(child, field, relation));
        });

        return out;
    }

    /**
     * Every RESTRICT is counted before any CASCADE deletes anything.
     *
     * Two passes rather than one loop, because a cascade that has already removed
     * half a storage cannot be undone when the next relation turns out to refuse the
     * delete. The counts are also reported together, so an author fixing this is told
     * about all of the blocking storages instead of one per attempt.
     */
    private Mono<Boolean> applyConstraints(
            String appCode,
            String clientCode,
            IAppDataService dataService,
            Connection conn,
            List<ReferencingRelation> relations,
            String id,
            Boolean deleteVersion) {

        if (relations.isEmpty()) return Mono.just(Boolean.TRUE);

        List<ReferencingRelation> restrict = relations.stream()
                .filter(r -> r.constraint() == StorageRelationConstraint.RESTRICT)
                .toList();

        List<ReferencingRelation> cascade = relations.stream()
                .filter(r -> r.constraint() == StorageRelationConstraint.CASCADE)
                .toList();

        return Flux.fromIterable(restrict)
                .concatMap(r -> dataService
                        .countReferencing(clientCode, conn, r.child(), r.field(), r.many(), id)
                        .map(count -> Tuples.of(r.child().getName() + "." + r.field(), count)))
                .filter(t -> t.getT2() > 0)
                .collectList()
                .flatMap(blocking -> {
                    if (!blocking.isEmpty())
                        return this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                CoreMessageResourceService.CANNOT_DELETE_STORAGE_WITH_RESTRICT,
                                blocking.stream()
                                        .map(t -> t.getT1() + " (" + t.getT2() + ")")
                                        .toList());

                    // Every cascade target is checked for permission BEFORE any of
                    // them loses a row. Checked only as each delete ran - which is
                    // what happens inside delete() - a caller who may remove orders
                    // but not invoices would take the orders out and then be
                    // refused, leaving a half-cascaded parent that is now harder to
                    // reason about than either outcome.
                    //
                    // The database path cannot ask this question at all: ON DELETE
                    // CASCADE has no caller. That difference is real and documented
                    // rather than papered over, which is also why a cascade whose
                    // child owes triggers or versions stays here in the first place.
                    return this.cascadesAllowed(cascade)
                            .flatMap(allowed -> Flux.fromIterable(cascade)
                                    .concatMap(r -> this.cascadeDelete(
                                            appCode, clientCode, dataService, conn, r, id, deleteVersion))
                                    .then(Mono.just(Boolean.TRUE)));
                });
    }

    /**
     * Delete the referencing rows in batches, through the ordinary delete.
     *
     * Through {@link #delete} and not straight at the backend, so a cascaded row gets
     * its own triggers, its own event, its own version row and its own constraints -
     * a chain of cascades is the normal case, not an exotic one. The depth guard is
     * there because a pair of storages can point at each other, and two CASCADEs
     * facing each other would otherwise walk until the stack gave out.
     */
    private Mono<Boolean> cascadeDelete(
            String appCode,
            String clientCode,
            IAppDataService dataService,
            Connection conn,
            ReferencingRelation r,
            String id,
            Boolean deleteVersion) {

        return Mono.deferContextual(ctx -> {
            int depth = ctx.getOrDefault(CASCADE_DEPTH, 0);

            if (depth >= MAX_CASCADE_DEPTH)
                return this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        CoreMessageResourceService.CANNOT_DELETE_STORAGE_WITH_RESTRICT,
                        List.of(r.child().getName() + "." + r.field() + " (cascade deeper than "
                                + MAX_CASCADE_DEPTH + ", the relations point at each other)"));

            return dataService
                    .idsReferencing(clientCode, conn, r.child(), r.field(), r.many(), id, CASCADE_BATCH)
                    .flatMap(ids -> ids.isEmpty()
                            ? Mono.just(Boolean.TRUE)
                            : Flux.fromIterable(ids)
                                    .concatMap(childId -> this.delete(
                                            appCode, clientCode, r.child().getName(), childId, deleteVersion))
                                    .then()
                                    // Around the child deletes ONLY. Wrapped around
                                    // the next batch as well, the depth would count
                                    // batches rather than nesting, and a cascade over
                                    // more than eight batches of children would abort
                                    // claiming the relations point at each other.
                                    .contextWrite(c -> c.put(CASCADE_DEPTH, depth + 1))
                                    .then(ids.size() < CASCADE_BATCH
                                            ? Mono.just(Boolean.TRUE)
                                            : Mono.defer(() -> this.cascadeDelete(
                                                    appCode, clientCode, dataService, conn, r, id, deleteVersion))));
        });
    }

    /**
     * Whether the caller may delete from every storage a cascade would reach.
     *
     * One pass over the distinct child storages rather than one per row, because
     * the answer cannot change between rows of the same storage.
     */
    private Mono<Boolean> cascadesAllowed(List<ReferencingRelation> cascade) {

        if (cascade.isEmpty()) return Mono.just(Boolean.TRUE);

        Map<String, Storage> byName = new java.util.LinkedHashMap<>();
        for (ReferencingRelation r : cascade) byName.putIfAbsent(r.child().getName(), r.child());

        return SecurityContextUtil.getUsersContextAuthentication().flatMap(ca -> {
            List<String> refused = byName.values().stream()
                    .filter(child -> !SecurityContextUtil.hasAuthority(
                            child.getDeleteAuth(), ca.getUser().getAuthorities()))
                    .map(Storage::getName)
                    .toList();

            if (refused.isEmpty()) return Mono.just(Boolean.TRUE);

            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                    CoreMessageResourceService.FORBIDDEN_DELETE_STORAGE,
                    String.join(", ", refused));
        });
    }

    /** One relation that points at the row being deleted, and what it asks for. */
    private record ReferencingRelation(Storage child, String field, StorageRelation relation) {

        /** A JSON array on MySQL and a real array on Mongo; either way, not a scalar. */
        boolean many() {
            return this.relation.getRelationType() == StorageRelationType.TO_MANY;
        }

        StorageRelationConstraint constraint() {
            return this.relation.getDeleteConstraint();
        }
    }


    private Mono<Tuple2<Boolean, Optional<Map<String, Object>>>> deleteWithTriggers(
            String appCode,
            String clientCode,
            IAppDataService dataService,
            Connection conn,
            Storage storage,
            String id,
            Boolean deleteVersion) {
        boolean noBeforeDelete = storage.getTriggers() == null
                || storage.getTriggers().get(StorageTriggerType.BEFORE_DELETE) == null
                || storage.getTriggers().get(StorageTriggerType.BEFORE_DELETE).isEmpty();

        boolean noAfterDelete = storage.getTriggers() == null
                || storage.getTriggers().get(StorageTriggerType.AFTER_DELETE) == null
                || storage.getTriggers().get(StorageTriggerType.AFTER_DELETE).isEmpty();

        if (noBeforeDelete && noAfterDelete && !BooleanUtil.safeValueOf(storage.getGenerateEvents()))
            return dataService.delete(clientCode, conn, storage, id, deleteVersion)
                    .map(e -> Tuples.of(e, Optional.empty()));

        if (noBeforeDelete && noAfterDelete)
            return this.read(appCode, clientCode, storage.getName(), id, false, List.of())
                    .flatMap(existing -> dataService.delete(clientCode, conn, storage, id, deleteVersion)
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

                    Map<String, JsonElement> args = Map.of(DATA_OBJECT_KEY, this.gson.toJsonTree(existing));

                    return this.executeTriggers(storage, StorageTriggerType.BEFORE_DELETE, args);
                },
                (existing, beforeDelete) -> dataService.delete(clientCode, conn, storage, id, deleteVersion),
                (existing, beforeDelete, deleted) -> {
                    if (noAfterDelete)
                        return Mono.just(Tuples.<Boolean, Optional<Map<String, Object>>>of(
                                deleted, Optional.of(existing)));

                    Map<String, JsonElement> args = Map.of(DATA_OBJECT_KEY, this.gson.toJsonTree(existing));

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
                (ca, ac) -> this.clientCode(clientCode),
                (ca, ac, cc) -> connectionService.read("appData", ac, cc, ConnectionType.APP_DATA),
                (ca, ac, cc, conn) -> Mono.just(
                        this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),
                (ca, ac, cc, conn, dataService) -> getStorageWithKIRunValidation(storageName, ac, cc)
                        .map(ObjectWithUniqueID::getObject),
                (ca, ac, cc, conn, dataService, storage) -> this.genericOperation(
                        storage,
                        (cas, hasAccess) -> this.writeDataToResponse(
                                storage, dataService.readPageAsFlux(cc, conn, storage, query), fileType, response),
                        Storage::getReadAuth,
                        CoreMessageResourceService.FORBIDDEN_READ_STORAGE));

        return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.downloadData"));
    }

    private Mono<Void> writeDataToResponse(
            Storage storage, Flux<Map<String, Object>> dataFlux, DataFileType fileType, ServerHttpResponse response) {
        return FlatMapUtil.flatMapMonoWithNull(
                () -> fileType.isNestedStructure() ? Mono.<Schema>empty() : storageService.getSchema(storage),
                schema -> schema != null ? this.getHeaders(null, storage, schema) : Mono.just(List.of()),
                (schema, dataHeaders) -> {
                    String file = storage.getName() + "_data." + fileType.toString().toLowerCase();
                    try {
                        Path fPath = Files.createTempFile(file, "");
                        DataFileWriter dfw = new DataFileWriter(
                                dataHeaders,
                                fileType,
                                Files.newOutputStream(
                                        fPath, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING));

                        ObjectValueSetterExtractor ovs = new ObjectValueSetterExtractor(new JsonObject(), "Data.");
                        Gson gson = this.gson;

                        return fluxToFile(dataFlux, fileType, dataHeaders, dfw, ovs, gson)
                                .flatMap(e -> fileToResponse(fileType, response, file, fPath, dfw));
                    } catch (Exception ex) {
                        return this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg, ex),
                                CoreMessageResourceService.NOT_ABLE_TO_DOWNLOAD_DATA,
                                file);
                    }
                });
    }

    private Mono<Void> fileToResponse(
            DataFileType fileType, ServerHttpResponse response, String file, Path fPath, DataFileWriter dfw) {
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
                throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to create file", e1);
            }
            return true;
        });
    }

    public Mono<byte[]> downloadTemplate(String appCode, String clientCode, String storageName, DataFileType fileType) {
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
                        (ca, hasAccess) -> downloadTemplate(storage, fileType),
                        Storage::getCreateAuth,
                        CoreMessageResourceService.FORBIDDEN_CREATE_STORAGE)
                        .switchIfEmpty(this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                CoreMessageResourceService.NOT_ABLE_TO_OPEN_FILE_ERROR)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.downloadTemplate"));
    }

    public Mono<Boolean> uploadData(
            String appCode, String clientCode, String storageName, DataFileType fileType, FilePart file) {
        Mono<Boolean> mono = FlatMapUtil.flatMapMonoWithNull(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),
                (ca, ac) -> this.clientCode(clientCode),
                (ca, ac, cc) -> connectionService.read("appData", ac, cc, ConnectionType.APP_DATA),
                (ca, ac, cc, conn) -> Mono.just(
                        this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),
                (ca, ac, cc, conn, dataService) -> getStorageWithKIRunValidation(storageName, ac, cc)
                        .map(ObjectWithUniqueID::getObject),
                (ca, ac, cc, conn, dataService, storage) -> this.genericOperation(
                        storage,
                        (contextAuth, hasAccess) -> uploadDataInternal(cc, conn, storage, fileType, file, dataService),
                        Storage::getCreateAuth,
                        CoreMessageResourceService.FORBIDDEN_CREATE_STORAGE));

        return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.uploadData"));
    }

    private <T> Mono<T> genericOperation(
            Storage storage,
            BiFunction<ContextAuthentication, Boolean, Mono<T>> biFunction,
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
                biFunction)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.genericOperation"))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg), msgString, storage.getName()));
    }

    private Mono<byte[]> downloadTemplate(Storage storage, DataFileType type) { // NOSONAR
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
                                                ? this.getHeaders(null, storage, storageSchema)
                                                : Mono.empty(),
                (storageSchema, actualHeaders) -> {
                    try {
                        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                        DataFileWriter writer = new DataFileWriter(actualHeaders, type, byteStream);
                        writer.write(Map.of());
                        writer.flush();
                        writer.close();
                        byteStream.flush();
                        byteStream.close();
                        byte[] bytes = byteStream.toByteArray();
                        return Mono.just(bytes);
                    } catch (Exception e) {
                        return this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                                CoreMessageResourceService.TEMPLATE_GENERATION_ERROR,
                                type.toString());
                    }
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.downloadTemplate"));
    }

    private Mono<Map<String, Set<SchemaType>>> getHeadersSchemaType(
            String prefix, Storage storage, Schema schema, int level) {
        return FlatMapUtil.flatMapMono(
                () -> this.schemaService.getSchemaRepository(storage.getAppCode(), storage.getClientCode()),
                appSchemaRepo -> {
                    if (schema.getRef() == null)
                        return Mono.just(schema);

                    return ReactiveSchemaUtil.getSchemaFromRef(
                            schema,
                            new ReactiveHybridRepository<>(
                                    new KIRunReactiveSchemaRepository(), new CoreSchemaRepository(), appSchemaRepo),
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
                    .flatMap(e -> this.getHeadersSchemaType(e, storage, aType.getSingleSchema(), level + 1)
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
                            getPrefixArrayName(prefix, tup.getT1()), storage, tup.getT2(), level + 1)
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
            String clientCode, Connection conn, Storage storage, DataFileType fileType, FilePart filePart,
            IAppDataService dataService) {
        return FlatMapUtil.flatMapMono(
                () -> storageService.getSchema(storage),
                storageSchema -> fileType.isNestedStructure()
                        ? Mono.just(Map.of())
                        : this.getHeadersSchemaType(null, storage, storageSchema, 0),
                (storageSchema, headers) -> {
                    List<Mono<Boolean>> monoList = (fileType == DataFileType.JSON || fileType == DataFileType.JSONL)
                            ? nestedFileToDB(clientCode, conn, storage, fileType, filePart, dataService)
                            : flatFileToDB(clientCode, conn, storage, fileType, filePart, dataService, headers);

                    return Flux.concat(monoList).collectList().map(e -> true);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.uploadDataInternal"))
                .switchIfEmpty(msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        CoreMessageResourceService.NOT_ABLE_TO_READ_FILE_FORMAT,
                        fileType));
    }

    private List<Mono<Boolean>> nestedFileToDB(
            String clientCode, Connection conn, Storage storage, DataFileType fileType, FilePart filePart,
            IAppDataService dataService) {
        List<Mono<Boolean>> monoList = new ArrayList<>();

        Map<String, Object> job;
        try (DataFileReader reader = new DataFileReader(filePart, fileType)) {
            while ((job = reader.readObject()) != null)
                monoList.add(dataService
                        .create(clientCode, conn, storage, new DataObject().setData(job))
                        .map(v -> true));
        } catch (GenericException ex) {
            throw ex;
        } catch (Exception ex) {
            throw this.unreadableFile(fileType, ex);
        }

        return monoList;
    }

    /**
     * The whole file is read before any row is written, so a read failure has inserted nothing and
     * has to be answered as a failure. Swallowing it answered the upload with true for a file none
     * of which was read.
     */
    private GenericException unreadableFile(DataFileType fileType, Exception ex) {
        logger.error("Error while reading upload file. ", ex);

        return new GenericException(
                HttpStatus.BAD_REQUEST, "Unable to read the uploaded " + fileType + " file.", ex);
    }

    private List<Mono<Boolean>> flatFileToDB(
            String clientCode,
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
                                        headers.get(reader.getHeaders().get(i)), row.get(i)));
                    }
                    monoList.add(dataService
                            .create(clientCode, conn, storage, new DataObject().setData(rowMap))
                            .map(v -> true));
                }
            } while (row != null && !row.isEmpty());
        } catch (GenericException ex) {
            throw ex;
        } catch (Exception ex) {
            throw this.unreadableFile(fileType, ex);
        }

        return monoList;
    }

    public Mono<Map<String, Object>> readVersion(
            String appCode, String clientCode, String storageName, String versionId) {
        Mono<Map<String, Object>> mono = FlatMapUtil.flatMapMonoWithNull(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),
                (ca, ac) -> this.clientCode(clientCode),
                (ca, ac, cc) -> connectionService.read("appData", ac, cc, ConnectionType.APP_DATA),
                (ca, ac, cc, conn) -> Mono.just(
                        this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),
                (ca, ac, cc, conn, dataService) -> getStorageWithKIRunValidation(storageName, ac, cc)
                        .map(ObjectWithUniqueID::getObject),
                (ca, ac, cc, conn, dataService, storage) -> this.genericOperation(
                        storage,
                        (contextAuth, hasAccess) -> dataService.readVersion(cc, conn, storage, versionId),
                        Storage::getReadAuth,
                        CoreMessageResourceService.FORBIDDEN_READ_STORAGE));

        return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.readVersion"));
    }

    public Mono<Page<Map<String, Object>>> readPageVersion(
            String appCode,
            String clientCode,
            String storageName,
            String versionId,
            Query query,
            Boolean includeObject) {
        Mono<Page<Map<String, Object>>> mono = FlatMapUtil.flatMapMonoWithNull(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),
                (ca, ac) -> this.clientCode(clientCode),
                (ca, ac, cc) -> connectionService.read("appData", ac, cc, ConnectionType.APP_DATA),
                (ca, ac, cc, conn) -> Mono.just(
                        this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),
                (ca, ac, cc, conn, dataService) -> getStorageWithKIRunValidation(storageName, ac, cc)
                        .map(ObjectWithUniqueID::getObject),
                (ca, ac, cc, conn, dataService, storage) -> this.genericOperation(
                        storage,
                        // cc, not the raw parameter: readVersion above already does
                        // this and this one was inconsistent with it.
                        (contextAuth, hasAccess) -> dataService.readPageVersion(
                                cc, conn, storage, versionId, query, includeObject),
                        Storage::getReadAuth,
                        CoreMessageResourceService.FORBIDDEN_READ_STORAGE));

        return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.readPageVersion"));
    }

    public Mono<Boolean> deleteStorage(String appCode, String clientCode, String storageName) {
        Mono<Boolean> mono = FlatMapUtil.flatMapMonoWithNull(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),
                (ca, ac) -> this.clientCode(clientCode),
                (ca, ac, cc) -> connectionService.read("appData", ac, cc, ConnectionType.APP_DATA),
                (ca, ac, cc, conn) -> Mono.just(
                        this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),
                (ca, ac, cc, conn, dataService) -> getStorageWithKIRunValidation(storageName, ac, cc)
                        .map(ObjectWithUniqueID::getObject),
                (ca, ac, cc, conn, dataService, storage) -> this.genericOperation(
                        storage,
                        (contextAuth, hasAccess) -> dataService.deleteStorage(cc, conn, storage),
                        Storage::getDeleteAuth,
                        CoreMessageResourceService.FORBIDDEN_DELETE_STORAGE));
        return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.deleteStorage"));
    }

    /**
     * Resolve a storage, refusing an {@code onlyThruKIRun} one unless the caller is
     * entitled to it. THROWS on refusal; see {@link #getStorageForRelation} for the
     * lenient variant.
     *
     * Throwing rather than returning empty is load-bearing. Every public entry point
     * here chains through {@code FlatMapUtil.flatMapMonoWithNull}, which is
     * {@code .map(Optional::of).defaultIfEmpty(Optional.empty())} followed by
     * {@code .orElse(null)}. So an empty does NOT short-circuit: it arrives as a null
     * {@code storage} and the chain runs on. {@code readPage} then answered a
     * misleading "Storage not found" 404 for a storage that plainly exists, and
     * {@code copyLiveDataToDraft} handed the null straight to
     * {@code MongoAppDataService.getCollection}, which NPE'd on
     * {@code storage.getAppCode()} and surfaced as a 500 with a 31KB stack trace.
     *
     * That is the second time this deny path has been fixed. The previous attempt
     * swapped a thrown {@code NoSuchElementException} (from {@code ContextView.get}
     * on an absent key) for {@code Mono.empty()} so the intended 404 would be
     * reachable. It never was, because WithNull swallows the empty. An explicit throw
     * does not depend on emptiness surviving the chain.
     */
    private Mono<ObjectWithUniqueID<Storage>> getStorageWithKIRunValidation(
            String name, String appCode, String clientCode) {
        return this.resolveStorage(name, appCode, clientCode, true);
    }

    /**
     * Resolve a storage for EAGER RELATION loading, skipping an {@code onlyThruKIRun}
     * one instead of failing the parent read.
     *
     * {@code prepareMonosForPage} builds one mono per eager field with
     * {@code FlatMapUtil.flatMapMono} (no WithNull), so an empty here short-circuits
     * that relation alone and the parent row still comes back, just without it. The
     * strict variant would turn a relation pointing at a KIRun-only storage into a 403
     * on the whole read, which is a regression on every page doing that today.
     */
    private Mono<ObjectWithUniqueID<Storage>> getStorageForRelation(
            String name, String appCode, String clientCode) {
        return this.resolveStorage(name, appCode, clientCode, false);
    }

    private Mono<ObjectWithUniqueID<Storage>> resolveStorage(
            String name, String appCode, String clientCode, boolean refuseLoudly) {
        return storageService.read(name, appCode, clientCode).flatMap(e -> {
            if (!BooleanUtil.safeValueOf(e.getObject().getOnlyThruKIRun()))
                return Mono.just(e);

            // ContextView.get(key) THROWS NoSuchElementException when the key is
            // absent, which is the normal case for a non-KIRun caller, so this stays
            // on getOrDefault.
            return Mono.deferContextual(cv -> {
                if ("true".equals(cv.getOrDefault(DefinitionFunction.CONTEXT_KEY, null)))
                    return Mono.just(e);

                // A builder SESSION may reach these rows directly, on either surface.
                // The flag says "route app traffic through a KIRun function"; it was
                // never meant to hide the rows from whoever is building the app, and
                // it did: the workspace data browser could not show them at all.
                //
                // Keyed on verifiedAppCode, stamped at login and carried in the signed
                // token, so it cannot be claimed with a request header. An empty or
                // unknown value falls through to the refusal, which is fail-safe.
                return SecurityContextUtil.getUsersContextAuthentication()
                        .map(ca -> StringUtil.safeIsBlank(ca.getVerifiedAppCode())
                                ? ""
                                : ca.getVerifiedAppCode())
                        .defaultIfEmpty("")
                        .flatMap(verifiedAppCode -> {
                            if (this.builderAppCodes.contains(verifiedAppCode))
                                return Mono.just(e);
                            if (!refuseLoudly)
                                return Mono.empty();
                            return this.msgService.<ObjectWithUniqueID<Storage>>throwMessage(
                                    msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                    CoreMessageResourceService.STORAGE_ONLY_THRU_KIRUN, name);
                        });
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

    // This will give the right client code.
    // If the user is not signed in, then take the externally sent client code if
    // sent, else take the url client code.
    // Else, if the client code is blank, use the logged-in user's client code.
    // Else, check client code is in the hierarchy or not and use it.
    private Mono<String> clientCode(String clientCode) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,

                ca -> {
                    boolean blank = StringUtil.safeIsBlank(clientCode);
                    if (!ca.isAuthenticated())
                        return Mono.just(blank ? ca.getUrlClientCode() : clientCode);

                    if (blank)
                        return Mono.just(ca.getClientCode());

                    // defaultIfEmpty on BOTH legs. doesClientManageClientCode answers
                    // EMPTY, not false, for a client that does not exist, so without
                    // these the whole chain completed empty and the refusal below was
                    // never reached - which is how an unknown clientCode stayed a 500.
                    return this.securityService
                            .doesClientManageClientCode(clientCode, ca.getClientCode())
                            .defaultIfEmpty(Boolean.FALSE)
                            .flatMap(e -> Boolean.TRUE.equals(e)
                                    ? Mono.just(Boolean.TRUE)
                                    : this.securityService
                                            .doesClientManageClientCode(ca.getClientCode(), clientCode)
                                            .defaultIfEmpty(Boolean.FALSE))
                            .defaultIfEmpty(Boolean.FALSE)
                            // Refused, not dropped. Returning empty left the caller's
                            // clientCode NULL, and the data read builds its chain with
                            // flatMapMonoWithNull, which passes a null straight on to
                            // Mono.just - so an unknown or unmanaged clientCode header
                            // came back as a 500 NullPointerException rather than a
                            // refusal. The endpoints built with flatMapMono answered
                            // 200 with an empty body for the very same header, which
                            // is the other half of the same mistake.
                            .flatMap(e -> Boolean.TRUE.equals(e)
                                    ? Mono.just(clientCode)
                                    : this.msgService.<String>throwMessage(
                                            msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                            CoreMessageResourceService.FORBIDDEN_CLIENT_CODE,
                                            clientCode));
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.clientCode"));
    }
}
