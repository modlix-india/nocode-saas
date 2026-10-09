package com.fincity.saas.commons.core.service.connection.appdata;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.SelectConditionStep;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.document.Connection;
import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.model.DataObject;
import com.fincity.saas.commons.core.model.StorageColumnDefinition;
import com.fincity.saas.commons.core.model.StorageRelation;
import com.fincity.saas.commons.core.exception.StorageObjectNotFoundException;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import com.fincity.saas.commons.core.service.CoreSchemaService;
import com.fincity.saas.commons.core.service.StorageService;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.FanOutReport;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLDrift;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MigrationOutcome;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLColumn;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLColumnNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLFanOut;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLTableInspector;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.TenantPlan;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.TenantProgress;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLAggregateBuilder;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.JoinedTable;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLFieldResolver;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLFilterBuilder;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLForeignKeys;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLIndexes;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLJoinPlanner;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLSubQueryPlanner;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.SubQueryTable;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLMigrationSweeper;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.RecoveryReport;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLVersionTable;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLVersionTrim;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.UnsupportedFilterException;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLTablePlanner;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLTypeMapper;
import com.fincity.saas.commons.core.service.connection.appdata.mysql.MySQLValueCodec;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.AggregateQuery;
import com.fincity.saas.commons.model.Aggregation;
import com.fincity.saas.commons.model.condition.AggregateFunction;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.StorageJoin;
import com.fincity.saas.commons.model.StorageSubQuery;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.commons.util.UniqueUtil;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.ValidationDepth;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * App data on MySQL, selected per app by an APP_DATA connection with subtype MYSQL.
 *
 * Mirrors {@link MongoAppDataService}'s tenant layout exactly so publish, the draft
 * surface and storage naming all keep their existing meaning: one schema per
 * {@code <CLIENT>_<app>}, a sibling {@code _draft} schema, and the storage's
 * uniqueName as the table name on both surfaces.
 *
 * Row ids are ULIDs rather than an auto-increment key. See {@link UniqueUtil#ulid()}
 * for why.
 *
 * Only the operations needed to stand a tenant up are implemented. The rest fail
 * loudly rather than silently doing nothing, because a backend that quietly returns an
 * empty page is far worse to debug than one that says it cannot do something.
 */
@Service
public class MySQLAppDataService implements IAppDataService {

    private static final Logger logger = LoggerFactory.getLogger(MySQLAppDataService.class);

    private static final String BACKEND = "MySQL";

    /**
     * How many ids a history purge removes at a time.
     *
     * A filtered delete can match an unbounded number of rows, and an IN list of all
     * of them is both a packet limit and a memory problem on the one tenant where it
     * matters.
     */
    private static final int VERSION_PURGE_BATCH = 500;

    /**
     * App-data pools are per app, so the defaults are small on purpose.
     *
     * An idle app should cost nothing, and a busy one grows to maxSize on demand.
     */
    /**
     * Whether TEXT_SEARCH stems the query before searching.
     *
     * On, because InnoDB does not stem and Mongo does, so the same search over
     * the same rows answered differently depending on the backend. Off restores
     * the literal NATURAL LANGUAGE MODE search: stemming widens a term into a
     * prefix, and a prefix over-matches - "plan" also finds "planet" - so there
     * has to be a way to turn it off without a rebuild.
     */
    @org.springframework.beans.factory.annotation.Value("${core.appdata.mysql.textSearch.stemming:true}")
    private boolean textSearchStemming;

    @org.springframework.beans.factory.annotation.Value("${core.appdata.mysql.pool.initialSize:0}")
    private int poolInitialSize;

    @org.springframework.beans.factory.annotation.Value("${core.appdata.mysql.pool.maxSize:10}")
    private int poolMaxSize;

    @org.springframework.beans.factory.annotation.Value("${core.appdata.mysql.pool.maxIdleMinutes:5}")
    private long poolMaxIdleMinutes;

    /**
     * The platform's own datasource, used when a connection says
     * {@code useDefaultConnection}.
     *
     * Most installs keep app data on the same MySQL the platform already uses, and
     * making every app restate the same url, username and password is three chances
     * to get it wrong and a password copied into a definition document that gets
     * transported between environments. Pointing at what is already configured is
     * the common case, so it should need no configuration at all.
     */
    @org.springframework.beans.factory.annotation.Value("${spring.r2dbc.url:}")
    private String defaultUrl;

    @org.springframework.beans.factory.annotation.Value("${spring.r2dbc.username:}")
    private String defaultUsername;

    @org.springframework.beans.factory.annotation.Value("${spring.r2dbc.password:}")
    private String defaultPassword;

    private final StorageService storageService;
    private final CoreSchemaService schemaService;
    private final CoreMessageResourceService msgService;
    private final StorageWriteValidator writeValidator;
    private final CacheService cacheService;
    private final MySQLValueCodec codec;
    private final VersionRetentionDefaults retentionDefaults;

    /** One pool per connection document, same lifetime policy as the Mongo client cache. */
    private final Map<String, ConnectionPool> pools = new ConcurrentHashMap<>();


    public MySQLAppDataService(
            StorageService storageService,
            CoreSchemaService schemaService,
            CoreMessageResourceService msgService,
            StorageWriteValidator writeValidator,
            CacheService cacheService,
            ObjectMapper objectMapper,
            VersionRetentionDefaults retentionDefaults) {
        this.storageService = storageService;
        this.schemaService = schemaService;
        this.msgService = msgService;
        this.writeValidator = writeValidator;
        this.cacheService = cacheService;
        this.codec = new MySQLValueCodec(objectMapper);
        this.retentionDefaults = retentionDefaults;
    }

    // ------------------------------------------------------------------ connection

    private DSLContext context(Connection conn) {
        return DSL.using(this.pool(conn), SQLDialect.MYSQL);
    }

    /**
     * One pool per connection document, replaced when the document changes.
     *
     * Keyed on the id AND the details. Keyed on the id alone, editing an app's
     * appData connection - a new host, a rotated password - changed nothing until the
     * service was restarted, because the pool built from the old details was still
     * cached under the same id. The superseded pool is disposed rather than dropped,
     * or its connections stay open against a server nobody is using any more.
     */
    private ConnectionPool pool(Connection conn) {

        if (conn == null)
            throw this.msgService.nonReactiveMessage(
                    msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                    CoreMessageResourceService.CONNECTION_NOT_AVAILABLE,
                    BACKEND);

        String key = conn.getId() + "@" + fingerprint(conn.getConnectionDetails());

        ConnectionPool existing = this.pools.get(key);
        if (existing != null) return existing;

        synchronized (this.pools) {
            ConnectionPool found = this.pools.get(key);
            if (found != null) return found;

            ConnectionPool created = this.createPool(conn);
            this.pools.put(key, created);

            this.pools.entrySet().removeIf(e -> {
                if (e.getKey().equals(key) || !e.getKey().startsWith(conn.getId() + "@")) return false;
                e.getValue().dispose();
                return true;
            });

            return created;
        }
    }

    /** Where this connection's server details came from. */
    record Credentials(String url, String username, String password, boolean fromDefault) {}

    /**
     * Either what the connection states, or the platform's own datasource.
     *
     * {@code useDefaultConnection} exists because most installs keep app data on
     * the MySQL the platform already talks to. Restating the same url, username and
     * password on every app is three chances to get it wrong, and it copies a
     * password into a definition document that then gets TRANSPORTED between
     * environments - so the staging credentials follow the app into production.
     * Pointing at what is already configured needs none of that.
     *
     * The default url names the platform's OWN schema, which does not matter: every
     * statement addresses its tenant database by fully qualified name, so what is
     * taken from the url is the server. An explicitly configured url has always
     * ended in a database name and worked the same way.
     *
     * It is all or nothing on purpose. Taking the url from the default and the
     * password from the document would produce a combination nobody wrote down, and
     * the failure would read as a wrong password rather than a mixed-up source.
     */
    static Credentials credentials(
            Map<String, Object> details, String defaultUrl, String defaultUsername, String defaultPassword) {

        if (details != null && BooleanUtil.safeValueOf(details.get("useDefaultConnection")))
            return new Credentials(defaultUrl, defaultUsername, defaultPassword, true);

        if (details == null) return new Credentials(null, null, null, false);

        return new Credentials(text(details.get("url")), text(details.get("username")),
                text(details.get("password")), false);
    }

    private static String text(Object value) {
        return StringUtil.safeIsBlank(value) ? null : value.toString();
    }

    private synchronized ConnectionPool createPool(Connection conn) {

        Credentials creds = credentials(
                conn.getConnectionDetails(), this.defaultUrl, this.defaultUsername, this.defaultPassword);

        if (StringUtil.safeIsBlank(creds.url()))
            throw this.msgService.nonReactiveMessage(
                    msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                    CoreMessageResourceService.CONNECTION_DETAILS_MISSING,
                    creds.fromDefault() ? "spring.r2dbc.url" : "url");

        ConnectionFactoryOptions.Builder props =
                ConnectionFactoryOptions.parse(creds.url()).mutate();

        props.option(ConnectionFactoryOptions.DRIVER, "pool").option(ConnectionFactoryOptions.PROTOCOL, "mysql");

        if (!StringUtil.safeIsBlank(creds.username()))
            props.option(ConnectionFactoryOptions.USER, creds.username());
        if (!StringUtil.safeIsBlank(creds.password()))
            props.option(ConnectionFactoryOptions.PASSWORD, creds.password());

        ConnectionFactory factory = ConnectionFactories.get(props.build());

        // Sized deliberately rather than left at the r2dbc-pool defaults, which open
        // ten connections eagerly. That is per APP, and a node serving three hundred
        // apps would hold three thousand idle connections against MySQL's default
        // ceiling of 151 - so the first few apps work and the rest get "too many
        // connections" from a server that is doing nothing.
        return new ConnectionPool(ConnectionPoolConfiguration.builder(factory)
                .initialSize(this.poolInitialSize)
                .maxSize(this.poolMaxSize)
                .maxIdleTime(java.time.Duration.ofMinutes(this.poolMaxIdleMinutes))
                // Checked on the way OUT of the pool, not just on the way in.
                // Without this the pool hands back a connection whose channel the
                // server has already closed - after a MySQL restart, a failover, or
                // a wait_timeout - and every app-data call fails with netty's
                // "channel not registered to an event loop" until the SERVICE is
                // restarted. The database coming back is then not enough to recover,
                // which is the wrong failure mode for a pool that is per app and
                // long lived. Observed on 2026-10-04 when MySQL restarted under a
                // running core.
                .validationQuery("SELECT 1")
                .validationDepth(ValidationDepth.REMOTE)
                .build());
    }

    // ------------------------------------------------------------------ naming

    /**
     * Same rule as the Mongo backend: the draft surface is a separate database with the
     * same table names, so publishing never renames or moves anything.
     */
    /**
     * The tenant schema name, checked before it can become DDL.
     *
     * Both halves are platform-issued and conventionally alphanumeric, and neither
     * is typed in by the caller. That is a reason to expect the check to pass, not a
     * reason to leave it out: the name is concatenated into
     * {@code CREATE DATABASE ...} and {@code USE ...} between backticks, which
     * cannot be parameterised, so this layer would be relying on an invariant it
     * does not own and does not verify. If the invariant ever stops holding, the
     * consequence lands here.
     */
    static String databaseName(String clientCode, String appCode, boolean draft) {

        String name = clientCode + "_" + appCode + (draft ? IAppDataService.DRAFT_DB_SUFFIX : "");

        if (!DATABASE_NAME.matcher(name).matches())
            throw new IllegalArgumentException("not a usable tenant schema name: " + name);

        return name;
    }

    /** MySQL schema names, within its 64-character ceiling. */
    private static final java.util.regex.Pattern DATABASE_NAME =
            java.util.regex.Pattern.compile("^[A-Za-z0-9_]{1,64}$");

    private Mono<String> database(String clientCode, Storage storage) {
        return LogUtil.isDraft()
                .map(draft -> databaseName(clientCode, storage.getAppCode(), Boolean.TRUE.equals(draft)));
    }

    // ------------------------------------------------------------------ ddl

    /**
     * Create the tenant schema and the storage's table if they are not there yet.
     *
     * Both statements are IF NOT EXISTS, which is what makes re-running a plan the
     * recovery path rather than something to avoid. Altering an existing table to match
     * a changed definition is deliberately NOT done here: that needs the data check and
     * the migration journal, and silently widening a column on first touch would be the
     * wrong half of that feature.
     */
    private Mono<String> ensureTable(Connection conn, String clientCode, Storage storage) {

        return FlatMapUtil.flatMapMono(
                () -> this.database(clientCode, storage),
                db -> this.cacheService
                        .cacheValueOrGet(
                                tableCache(storage),
                                () -> this.createTable(conn, db, storage),
                                db,
                                storage.getVersion())
                        .thenReturn(db));
    }

    /**
     * Memoised in the shared cache rather than in a field on this bean.
     *
     * Not fussiness: an in-memory flag survives the table being dropped, and every
     * node keeps its own. Both failures look identical from outside - a write against
     * a table that is not there - and neither reproduces on the node you are looking
     * at. The version is part of the key so that editing a storage makes the check run
     * again instead of the cache hiding the change.
     *
     * Evicted wherever the index cache is, plus by {@link #deleteStorage}, which is
     * the one path that drops the table out from under it.
     */
    private Mono<Boolean> createTable(Connection conn, String db, Storage storage) {

        String table = storage.getUniqueName();
        DSLContext ctx = this.context(conn);

        return this.physicalSchema(storage).flatMap(schema -> {
            List<MySQLColumn> columns = MySQLTypeMapper.columns(schema, defs(storage));

            return Mono.from(ctx.query("CREATE DATABASE IF NOT EXISTS `" + db + "`"))
                    .then(Mono.from(ctx.query("USE `" + db + "`; " + MySQLTablePlanner.createTable(table, columns))))
                    // Indexes BEFORE keys. MySQL creates its own index for a
                    // foreign key when nothing suitable exists, named after the
                    // constraint and invisible to the definition; creating ours
                    // first means the constraint reuses it and there is one index
                    // on that column rather than two.
                    .then(this.syncIndexes(conn, db, storage, schema))
                    .then(this.syncForeignKeys(conn, db, storage))
                    .thenReturn(Boolean.TRUE);
        });
    }

    /**
     * Bring this table's foreign keys into line with the relations that declare one.
     *
     * Safe to run on any path, which is why it sits inside the same memoised step
     * as the create rather than only in the publish. Every statement it issues is
     * derived from a difference against what MySQL reports, so a second run issues
     * nothing, and an add is skipped rather than attempted when the orphan count
     * says it would fail.
     *
     * A relation with no constraint produces no key at all, so for every storage
     * that exists today this does one catalogue read and stops.
     */
    /**
     * Put the keys in place on every tenant, as part of the publish.
     *
     * The lazy create is not enough on its own, and the gap is not obvious. A
     * delete of a CATEGORY ensures the category table, not the blog table, so if
     * the blog table on that tenant has never been written to since the constraint
     * was declared, its foreign key does not exist yet - and the service loop has
     * already stood down on the grounds that the database is handling it. The
     * constraint would then be enforced by nobody, silently, which is the one
     * outcome worse than enforcing it twice.
     *
     * Failures are logged per tenant rather than failing the publish: the columns
     * are already migrated by this point, and rolling that back over a constraint
     * that can be added later would be the larger harm.
     *
     * One interaction is deliberately NOT smoothed over. A narrowing change to a
     * column that carries a foreign key is expand-contract, and MySQL refuses to
     * drop a column a constraint depends on, so that migration fails and the tenant
     * is reported as blocked. Dropping every key before the columns and putting them
     * back afterwards would make it pass, and would also mean a migration that
     * stopped half way left the constraints off with nothing saying so - deletes
     * that should be refused would quietly succeed until the next publish. A loud
     * failure that names the constraint is the better of the two.
     */
    /** The same, for every tenant, as part of a publish. */
    private Mono<Boolean> syncIndexesAcross(
            Connection conn, String appCode, Storage storage, List<String> tenants) {

        return Flux.fromIterable(tenants)
                .concatMap(db -> this.storageService
                        .readForTenant(storage.getName(), appCode, clientCodeOf(db, appCode))
                        .flatMap(tenantStorage -> this.physicalSchema(tenantStorage)
                                .flatMap(schema -> this.syncIndexes(conn, db, tenantStorage, schema)))
                        .contextWrite(Context.of(LogUtil.DRAFT_KEY, isDraft(db)))
                        .onErrorResume(e -> {
                            logger.error("Could not sync indexes for {} on {}", storage.getName(), db, e);
                            return Mono.just(Boolean.FALSE);
                        }))
                .then(Mono.just(Boolean.TRUE));
    }

    private Mono<Boolean> syncForeignKeysAcross(
            Connection conn, String appCode, Storage storage, List<String> tenants) {

        return Flux.fromIterable(tenants)
                .concatMap(db -> this.storageService
                        .readForTenant(storage.getName(), appCode, clientCodeOf(db, appCode))
                        .flatMap(tenantStorage -> this.syncForeignKeys(conn, db, tenantStorage))
                        // Each surface resolves its own definition, exactly as the
                        // column plan does, or the draft table gets the published
                        // constraints.
                        .contextWrite(Context.of(LogUtil.DRAFT_KEY, isDraft(db)))
                        .onErrorResume(e -> {
                            logger.error("Could not sync foreign keys for {} on {}", storage.getName(), db, e);
                            return Mono.just(Boolean.FALSE);
                        }))
                .then(Mono.just(Boolean.TRUE));
    }

    /**
     * Bring the table's indexes into line with what the definition declares.
     *
     * Mongo has always done this; this backend did not, so every table had one
     * index - the primary key - and every other filter was a full scan.
     *
     * Columns a foreign key depends on are read first and handed to the planner,
     * because MySQL refuses to drop the index backing a constraint and the attempt
     * would fail the write that happened to trigger the create.
     */
    private Mono<Boolean> syncIndexes(Connection conn, String db, Storage storage, Schema schema) {

        DSLContext ctx = this.context(conn);
        String table = storage.getUniqueName();

        return this.plannedIndexStatements(conn, db, storage, schema).flatMap(statements -> {
            if (statements.isEmpty()) return Mono.just(Boolean.TRUE);

            return Flux.fromIterable(statements)
                    .concatMap(sql -> Mono.from(ctx.query(sql))
                            .thenReturn(Boolean.TRUE)
                            // One index failing is not a reason to fail the
                            // write. A FULLTEXT index over a column that is
                            // not text is the usual cause, and the query
                            // that wanted it will say so far more clearly
                            // than a failed insert would.
                            .onErrorResume(e -> {
                                logger.error("Could not apply index on {}.{}: {}", db, table, sql, e);
                                return Mono.just(Boolean.FALSE);
                            }))
                    .then(Mono.just(Boolean.TRUE));
        });
    }

    /**
     * The index DDL this table is missing, without running any of it.
     *
     * Split out so the drift report and the publish path cannot disagree about what
     * is wrong: both read this one method, and a report that was computed a
     * different way from the repair is a report nobody should act on.
     */
    private Mono<List<String>> plannedIndexStatements(
            Connection conn, String db, Storage storage, Schema schema) {

        DSLContext ctx = this.context(conn);
        String table = storage.getUniqueName();

        Set<String> columns = new java.util.LinkedHashSet<>(parentColumns(schema, defs(storage)));

        List<MySQLIndexes.Index> desired = MySQLIndexes.desired(storage, columns);

        return FlatMapUtil.flatMapMono(
                () -> MySQLForeignKeys.existing(ctx, db, table),
                keys -> MySQLIndexes.existing(ctx, db, table),
                (keys, existing) -> {
                    Set<String> keyColumns = keys.stream()
                            .map(MySQLForeignKeys.ForeignKey::column)
                            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

                    return Mono.just(MySQLIndexes.sync(db, table, existing, desired, keyColumns));
                });
    }

    private Mono<Boolean> syncForeignKeys(Connection conn, String db, Storage storage) {

        return this.desiredForeignKeys(conn, db, storage)
                .flatMap(desired -> this.applyForeignKeys(conn, db, storage.getUniqueName(), desired));
    }

    /**
     * The constraints this table should carry, with each relation's target resolved
     * for this tenant.
     *
     * Separated from applying them so the drift report can ask what is missing
     * without installing anything. An empty list is a real answer, not "nothing to
     * do": a relation whose constraint was set back to NOTHING leaves a key behind
     * that has to come off, or it keeps refusing deletes nobody is asking it to
     * refuse.
     */
    private Mono<List<MySQLForeignKeys.ForeignKey>> desiredForeignKeys(
            Connection conn, String db, Storage storage) {

        Map<String, StorageRelation> enforceable = enforceableRelations(storage);
        if (enforceable.isEmpty()) return Mono.just(List.of());

        String clientCode = clientCodeOf(db, storage.getAppCode());

        return Flux.fromIterable(enforceable.entrySet())
                .concatMap(e -> this.storageService
                        .readForTenant(e.getValue().getStorageName(), storage.getAppCode(), clientCode)
                        .flatMap(target -> this.ensureTargetTable(conn, db, target)
                                .thenReturn(Tuples.of(e.getKey(), target.getUniqueName())))
                        // Not a reason to fail the write that triggered the create -
                        // the relation is refused at save time, so this can only be a
                        // definition that moved underneath an existing table. It IS a
                        // reason to say so loudly: the service stands down for a
                        // relation this backend claims to enforce, so a key that
                        // never gets installed is a constraint enforced by nobody.
                        .onErrorResume(err -> {
                            logger.error(
                                    "Relation {} on {} points at storage {}, which does not resolve for this"
                                            + " client. Its delete constraint is NOT being enforced.",
                                    e.getKey(),
                                    storage.getName(),
                                    e.getValue().getStorageName(),
                                    err);
                            return Mono.empty();
                        }))
                .collectMap(Tuple2::getT1, Tuple2::getT2)
                .map(targets -> MySQLForeignKeys.desired(storage.getUniqueName(), enforceable, targets));
    }

    /**
     * The constraint DDL this table is missing, without running any of it. The
     * report half of {@link #applyForeignKeys}, reading the same two inputs.
     */
    private Mono<List<String>> plannedForeignKeyStatements(Connection conn, String db, Storage storage) {

        DSLContext ctx = this.context(conn);
        String table = storage.getUniqueName();

        return this.desiredForeignKeys(conn, db, storage)
                .flatMap(desired -> MySQLForeignKeys.existing(ctx, db, table)
                        .map(existing -> MySQLForeignKeys.sync(db, table, existing, desired)));
    }

    static Map<String, StorageRelation> enforceableRelations(Storage storage) {

        Map<String, StorageRelation> out = new LinkedHashMap<>();
        if (storage.getRelations() == null) return out;

        storage.getRelations().forEach((field, relation) -> {
            if (MySQLForeignKeys.enforceable(storage, relation)) out.put(field, relation);
        });

        return out;
    }

    /**
     * The referenced table, created but not completed.
     *
     * Deliberately NOT ensureTable: that would resolve the target's own relations
     * and come straight back here, and two storages pointing at each other would
     * wait on each other inside the same memoised entry. Only the columns are
     * needed for a foreign key to be addable, and the target gets its own keys when
     * something touches it.
     */
    private Mono<Boolean> ensureTargetTable(Connection conn, String db, Storage target) {

        DSLContext ctx = this.context(conn);

        return this.physicalSchema(target)
                .flatMap(schema -> Mono.from(ctx.query("USE `" + db + "`; "
                                + MySQLTablePlanner.createTable(
                                        target.getUniqueName(),
                                        MySQLTypeMapper.columns(schema, defs(target)))))
                        .thenReturn(Boolean.TRUE))
                .defaultIfEmpty(Boolean.TRUE);
    }

    private Mono<Boolean> applyForeignKeys(
            Connection conn, String db, String table, List<MySQLForeignKeys.ForeignKey> desired) {

        DSLContext ctx = this.context(conn);

        return MySQLForeignKeys.existing(ctx, db, table).flatMap(existing -> {
            List<String> statements = MySQLForeignKeys.sync(db, table, existing, desired);
            if (statements.isEmpty()) return Mono.just(Boolean.TRUE);

            return Flux.fromIterable(statements)
                    .concatMap(sql -> this.foreignKeyStatement(ctx, db, table, sql, desired))
                    // The delete path reads this to decide whether to enforce the
                    // constraint itself. A stale yes there means nobody enforces it.
                    .then(this.cacheService.evictAll(table + IAppDataService.CACHE_SUFFIX_FOR_FOREIGN_KEYS))
                    .thenReturn(Boolean.TRUE);
        });
    }

    /**
     * One constraint statement, with the orphan check in front of every add.
     *
     * MySQL refuses the whole ALTER on a single dangling reference and names the
     * constraint rather than the row, so on a table with history the error is
     * useless. Counting first turns it into a log line that says how many rows and
     * which column, and leaves the table working without the key rather than
     * failing the operation that happened to trigger the create.
     */
    private Mono<Boolean> foreignKeyStatement(
            DSLContext ctx, String db, String table, String sql, List<MySQLForeignKeys.ForeignKey> desired) {

        MySQLForeignKeys.ForeignKey adding =
                desired.stream().filter(fk -> sql.contains("`" + fk.name() + "`")).findFirst().orElse(null);

        if (adding == null || !sql.contains("ADD CONSTRAINT"))
            return Mono.from(ctx.query(sql)).thenReturn(Boolean.TRUE);

        return Mono.from(ctx.resultQuery(MySQLForeignKeys.orphanCount(db, table, adding)))
                .map(r -> r.get(0) instanceof Number n ? n.longValue() : 0L)
                .defaultIfEmpty(0L)
                .flatMap(orphans -> {
                    if (orphans > 0) {
                        // ERROR rather than WARN, and worded as a gap rather than a
                        // skip: the service has already stood down for this relation
                        // on the grounds that the database enforces it, so right now
                        // nothing does.
                        logger.error(
                                "Foreign key {} on {}.{} NOT installed and its constraint is NOT being enforced:"
                                        + " {} row(s) reference a {} that is not there. Clear them and publish"
                                        + " again.",
                                adding.name(),
                                db,
                                table,
                                orphans,
                                adding.targetTable());
                        return Mono.just(Boolean.FALSE);
                    }
                    return Mono.from(ctx.query(sql)).thenReturn(Boolean.TRUE);
                });
    }

    private static String tableCache(Storage storage) {
        return storage.getUniqueName() + IAppDataService.CACHE_SUFFIX_FOR_TABLE_CREATION;
    }

    /**
     * A stable fingerprint of the connection details, used to key the pool.
     *
     * {@code Objects.hashCode} was 32 bits over a map that holds the host, the user
     * and the password. Two different connections colliding there would have shared
     * a pool - and therefore credentials - which is an unlikely accident with an
     * outcome bad enough to be worth not relying on luck for. A digest also does
     * not vary with the map's iteration order, which a hash of a HashMap does not
     * guarantee across implementations.
     */
    static String fingerprint(Map<String, Object> details) {

        if (details == null || details.isEmpty()) return "none";

        StringBuilder sb = new StringBuilder();
        new java.util.TreeMap<>(details)
                .forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));

        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 16; i++) hex.append(String.format("%02x", digest[i]));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private org.jooq.Table<?> table(String db, Storage storage) {
        return DSL.table(DSL.name(db, storage.getUniqueName()));
    }

    private static Field<Object> idField() {
        return DSL.field(DSL.name(MySQLTypeMapper.ID_COLUMN));
    }

    // ------------------------------------------------------------------ migration

    /**
     * Bring every tenant's table into line with the storage definition as it now
     * stands.
     *
     * This is the half of the backend that {@code ensureTable} deliberately does not
     * do. A create is safe to run on any code path; an ALTER is not, and needs the
     * data check, the journal and a pre-flight across every tenant before a single
     * statement is issued anywhere.
     *
     * Called when the definition changes, which is not only when the STORAGE is
     * edited. A schema the storage references can change underneath it, and so can a
     * client's override, and both change the table this tenant should have while the
     * storage's own version stays exactly where it was. That is why the journal keys
     * on the resolved shape rather than the version.
     *
     * Tenants are discovered from the schemas that actually hold the table rather
     * than from a client list, so one provisioned outside the normal path is migrated
     * too instead of being quietly left behind.
     */
    public Mono<FanOutReport> reconcile(Connection conn, String appCode, Storage storage, String appliedBy) {

        if (conn == null) return Mono.just(FanOutReport.empty());

        DSLContext ctx = this.context(conn);
        String table = storage.getUniqueName();

        return MySQLTableInspector.tenantsWithTable(ctx, appCode, table)
                .flatMap(tenants -> tenants.isEmpty()
                        ? Mono.just(FanOutReport.empty())
                        : this.desiredByTenant(appCode, storage, tenants)
                                .flatMap(desired -> this.applyBySurface(
                                        ctx, tenants, desired, storage, appCode, table, appliedBy))
                                // After the columns, because a foreign key cannot be
                                // added to a column the migration has not created yet.
                                .flatMap(report -> this.syncIndexesAcross(conn, appCode, storage, tenants)
                                        .then(this.syncForeignKeysAcross(conn, appCode, storage, tenants))
                                        .thenReturn(report)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MySQLAppDataService.reconcile"));
    }

    /**
     * Finish any migration on this connection that nobody is driving.
     *
     * Works entirely from the journal: the plan, the step it reached and the row's
     * identity all come out of the row itself, so this resolves no storage
     * definitions and reads no Mongo. Recovery runs when the rest of the system is
     * in a state nobody planned, and depending on less of it is the point.
     */
    public Mono<RecoveryReport> sweepInterrupted(Connection conn, int staleAfterSeconds) {

        if (conn == null) return Mono.just(RecoveryReport.empty());

        return MySQLMigrationSweeper.sweep(this.context(conn), staleAfterSeconds)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MySQLAppDataService.sweepInterrupted"));
    }

    /** How far each tenant got, read back from its own journal. */
    public Mono<List<TenantProgress>> migrationStatus(Connection conn, String appCode, Storage storage) {

        if (conn == null) return Mono.just(List.of());

        DSLContext ctx = this.context(conn);

        return MySQLTableInspector.tenantsWithTable(ctx, appCode, storage.getUniqueName())
                .flatMap(tenants -> MySQLFanOut.status(ctx, tenants, storage.getName()));
    }

    /**
     * What every tenant's table has that its definition does not, and the reverse.
     *
     * The gap {@code reconcile} leaves. A definition change reconciles the tenants
     * reachable at the time; one that was unreachable, or whose table was built by
     * hand, or that was transported in at an older shape, stays wrong and nothing
     * says so. Every other code path asks the DEFINITION what the table looks like,
     * so the table is only consulted when a query fails.
     *
     * Read only. Nothing here issues a statement, which is what makes it safe to run
     * on a fleet to find out how bad things are before deciding anything.
     */
    public Mono<List<MySQLDrift.Report>> drift(Connection conn, String appCode, Storage storage) {

        if (conn == null) return Mono.just(List.of());

        // Every schema the app has, not only the ones holding this table. A tenant
        // that MISSES the table is the case the reconciler exists for, and asking
        // information_schema for tables that exist is how it stayed invisible.
        return MySQLTableInspector.tenantSchemas(this.context(conn), appCode)
                .flatMapMany(Flux::fromIterable)
                .concatMap(db -> this.driftFor(conn, appCode, storage, db))
                .collectList()
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MySQLAppDataService.drift"));
    }

    /**
     * One tenant's report, built from the same four inputs the publish path uses.
     *
     * Deliberately reuses {@code plannedIndexStatements} and
     * {@code plannedForeignKeyStatements} rather than recomputing: a report derived
     * differently from the repair is a report that can be confidently wrong.
     *
     * A tenant whose definition does not resolve is left OUT of the list rather than
     * reported as drifted. "Nothing resolved" would diff every existing column as a
     * drop, and offering that as a repair is the one mistake here that cannot be
     * undone.
     */
    private Mono<MySQLDrift.Report> driftFor(Connection conn, String appCode, Storage storage, String db) {

        DSLContext ctx = this.context(conn);
        String table = storage.getUniqueName();

        return this.storageService
                .readForTenant(storage.getName(), appCode, clientCodeOf(db, appCode))
                .flatMap(tenantStorage -> FlatMapUtil.flatMapMono(
                        () -> this.physicalSchema(tenantStorage),
                        schema -> MySQLTableInspector.tableExists(ctx, db, table),
                        (schema, exists) -> Boolean.TRUE.equals(exists)
                                ? FlatMapUtil.flatMapMono(
                                        () -> MySQLTableInspector.columns(ctx, db, table),
                                        existing -> this.plannedIndexStatements(conn, db, tenantStorage, schema),
                                        (existing, indexes) ->
                                                this.plannedForeignKeyStatements(conn, db, tenantStorage),
                                        (existing, indexes, keys) -> Mono.just(MySQLDrift.of(
                                                db,
                                                table,
                                                true,
                                                existing,
                                                MySQLTypeMapper.columns(
                                                        schema, defs(tenantStorage)),
                                                indexes,
                                                keys)))
                                : Mono.just(MySQLDrift.of(
                                        db, table, false, List.of(), List.of(), List.of(), List.of()))))
                // Each surface resolves its own definition, exactly as the column
                // plan does, or the draft table is reported against the published
                // shape and looks drifted when it is not.
                .contextWrite(Context.of(LogUtil.DRAFT_KEY, isDraft(db)))
                .onErrorResume(e -> {
                    logger.error("Could not read drift for {} on {}", storage.getName(), db, e);
                    return Mono.empty();
                });
    }

    /**
     * Apply what the drift report found, on every tenant.
     *
     * Without {@code approved} this runs only the half that cannot lose anything -
     * widening columns, additive indexes and keys - and reports the rest as withheld
     * so somebody can read the actual statements before deciding. With it, the lot.
     *
     * Per tenant and statement by statement, because a fleet-wide repair where one
     * schema fails must not stop the others: they are independent databases and
     * partial completion is the normal state, exactly as it is for a migration.
     */
    public Mono<List<MySQLDrift.DriftRepair>> repairDrift(
            Connection conn, String appCode, Storage storage, boolean approved) {

        if (conn == null) return Mono.just(List.of());

        return this.drift(conn, appCode, storage)
                .flatMapMany(Flux::fromIterable)
                .concatMap(report -> this.repairOne(conn, report, approved))
                .collectList()
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MySQLAppDataService.repairDrift"));
    }

    private Mono<MySQLDrift.DriftRepair> repairOne(
            Connection conn, MySQLDrift.Report report, boolean approved) {

        List<String> plan = MySQLDrift.repairStatements(report, approved);
        List<String> withheld = MySQLDrift.withheldStatements(report, approved);

        // A missing table is never created here. Creating it is ensureTable's job and
        // it needs the whole definition, not a diff against nothing. It is still
        // carried on the result rather than reported as "nothing to do", because a
        // tenant with no table is the loudest thing a drift run can find.
        if (plan.isEmpty())
            return Mono.just(new MySQLDrift.DriftRepair(
                    report.db(), !report.tableExists(), List.of(), withheld, List.of()));

        DSLContext ctx = this.context(conn);
        List<String> applied = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        return Flux.fromIterable(plan)
                .concatMap(sql -> Mono.from(ctx.query(sql))
                        .then(Mono.fromRunnable(() -> applied.add(sql)))
                        // One statement failing is not a reason to abandon the rest.
                        // They are independent repairs, and the ones that CAN land
                        // should, with the failures named rather than implied by a
                        // shorter list.
                        .onErrorResume(e -> {
                            logger.error("Drift repair failed on {}: {}", report.db(), sql, e);
                            failed.add(sql);
                            return Mono.empty();
                        }))
                .then(Mono.fromSupplier(() ->
                        new MySQLDrift.DriftRepair(report.db(), false, applied, withheld, failed)));
    }

    /**
     * The columns each tenant should have, resolved separately for every one.
     *
     * Per client and not once, because the definition is overridable: SYSTEM, a
     * mid-level client and a leaf client resolve to different merged schemas and
     * therefore different tables. A descendant that pinned its own type for a field
     * is unaffected by the base changing it and must get an empty plan, not everyone
     * else's.
     */
    private Mono<Map<String, List<MySQLColumn>>> desiredByTenant(
            String appCode, Storage storage, List<String> tenants) {

        Map<String, List<MySQLColumn>> out = new LinkedHashMap<>();

        return Flux.fromIterable(tenants)
                .concatMap(db -> this.storageService
                        .readForTenant(storage.getName(), appCode, clientCodeOf(db, appCode))
                        .flatMap(tenantStorage -> this.physicalSchema(tenantStorage)
                                // The tenant's OWN definitions. They are overridable
                                // like everything else on a storage, so a client that
                                // pinned its own DECIMAL scale keeps it while the base
                                // moves under it.
                                .map(sc -> MySQLTypeMapper.columns(sc, defs(tenantStorage))))
                        // Each surface resolves its own definition. The draft document
                        // is a different document, so reading it under the live flag
                        // would shape the draft table from the published definition and
                        // undo the edit the author is still working on.
                        .contextWrite(Context.of(LogUtil.DRAFT_KEY, isDraft(db)))
                        .doOnNext(cols -> out.put(db, cols))
                        // A tenant whose client resolves no definition is left out
                        // entirely. Treating "nothing resolved" as "no columns wanted"
                        // would diff every existing column as a drop, which is the one
                        // mistake in this whole backend that cannot be undone.
                        .onErrorResume(e -> Mono.empty()))
                .then(Mono.just(out));
    }

    /**
     * Live and draft are migrated as two fan-outs and reported as one.
     *
     * Separately because each tenant's journal records the surface it was on, and a
     * row that cannot say whether it altered the real table or the sandbox copy is
     * not much of a record.
     */
    private Mono<FanOutReport> applyBySurface(
            DSLContext ctx,
            List<String> tenants,
            Map<String, List<MySQLColumn>> desired,
            Storage storage,
            String appCode,
            String table,
            String appliedBy) {

        List<String> unresolved =
                tenants.stream().filter(db -> !desired.containsKey(db)).toList();

        List<String> live = tenants.stream()
                .filter(desired::containsKey)
                .filter(db -> !isDraft(db))
                .toList();

        List<String> draft = tenants.stream()
                .filter(desired::containsKey)
                .filter(MySQLAppDataService::isDraft)
                .toList();

        int version = storage.getVersion();

        return this.surface(ctx, live, desired, storage, table, version, "LIVE", appliedBy)
                .flatMap(liveReport -> this.surface(
                                ctx, draft, desired, storage, table, version, "DRAFT", appliedBy)
                        .map(draftReport -> FanOutReport.merge(liveReport, draftReport)))
                .map(report -> unresolved.isEmpty() ? report : withUnresolved(report, unresolved));
    }

    private Mono<FanOutReport> surface(
            DSLContext ctx,
            List<String> tenants,
            Map<String, List<MySQLColumn>> desired,
            Storage storage,
            String table,
            int version,
            String surface,
            String appliedBy) {

        if (tenants.isEmpty()) return Mono.just(FanOutReport.empty());

        return MySQLFanOut.plan(ctx, tenants, table, version, desired::get)
                .flatMap(plans -> MySQLFanOut.apply(
                        ctx,
                        plans,
                        storage.getName(),
                        table,
                        // The version this tenant came FROM is not knowable here: the
                        // journal may have no row for it at all, and an override has
                        // its own history. It is written for a reader, not used for
                        // anything, so an honest null beats a plausible guess.
                        null,
                        version,
                        surface,
                        appliedBy));
    }

    private static FanOutReport withUnresolved(FanOutReport report, List<String> unresolved) {

        Map<String, MigrationOutcome> byTenant = new LinkedHashMap<>(report.byTenant());
        unresolved.forEach(db -> byTenant.put(
                db, MigrationOutcome.needsAttention("no storage definition resolves for this client")));

        List<String> blocked = new java.util.ArrayList<>(report.blocked());
        blocked.addAll(unresolved);

        return new FanOutReport(byTenant, blocked, report.noOp(), report.resumed());
    }

    static boolean isDraft(String db) {
        return db.endsWith(IAppDataService.DRAFT_DB_SUFFIX);
    }

    /**
     * The client a tenant schema belongs to, which is the database name with the app
     * and the draft marker taken back off.
     */
    static String clientCodeOf(String db, String appCode) {

        String name = isDraft(db) ? db.substring(0, db.length() - IAppDataService.DRAFT_DB_SUFFIX.length()) : db;
        String suffix = "_" + appCode;

        return name.endsWith(suffix) ? name.substring(0, name.length() - suffix.length()) : name;
    }

    // ------------------------------------------------------------------ operations

    @Override
    public Mono<Map<String, Object>> create(
            String clientCode, Connection conn, Storage storage, DataObject dataObject) {

        return FlatMapUtil.flatMapMono(
                        () -> this.ensureTable(conn, clientCode, storage),
                        db -> this.storageService.getSchema(storage),
                        (db, schema) ->
                                this.schemaService.getSchemaRepository(storage.getAppCode(), storage.getClientCode()),
                        (db, schema, repo) ->
                                this.writeValidator.validate(dataObject.getData(), storage, schema, repo),
                        (db, schema, repo, validated) -> this.physicalSchema(storage),
                        (db, schema, repo, validated, resolved) -> {
                            Map<String, Object> row = MySQLColumnNames.toColumns(dataObject.getData());

                            // A caller may supply its own id, exactly as the Mongo backend
                            // allows; otherwise mint one. Never an auto-increment key.
                            Object given = row.get(MySQLTypeMapper.ID_COLUMN);
                            String id = given == null
                                            || StringUtil.safeIsBlank(given.toString())
                                    ? UniqueUtil.ulid()
                                    : given.toString();
                            row.put(MySQLTypeMapper.ID_COLUMN, id);

                            Map<Field<?>, Object> values = new LinkedHashMap<>();
                            this.codec
                                    .encode(
                                            row,
                                            MySQLTypeMapper.jsonColumns(resolved, defs(storage)),
                                            MySQLTypeMapper.dateStringColumns(resolved, defs(storage)),
                                            MySQLTypeMapper.decimalColumns(resolved, defs(storage)))
                                    .forEach((k, v) -> values.put(DSL.field(DSL.name(k)), v));

                            return Mono.from(this.context(conn)
                                            .insertInto(this.table(db, storage))
                                            .set(values))
                                    .then(this.readRow(conn, db, storage, resolved, id))
                                    .flatMap(saved -> this.addVersion(
                                                    conn, db, storage, id, "CREATE", saved,
                                                    dataObject.getMessage())
                                            .thenReturn(saved));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MySQLAppDataService.create"));
    }

    @Override
    public Mono<Map<String, Object>> read(String clientCode, Connection conn, Storage storage, String id) {

        return FlatMapUtil.flatMapMono(
                        () -> this.ensureTable(conn, clientCode, storage),
                        db -> this.physicalSchema(storage),
                        (db, schema) -> this.readRow(conn, db, storage, schema, id))
                // A missing row must not complete empty: AppDataService.genericOperation turns
                // an empty result into FORBIDDEN_READ_STORAGE, a 403 for what is a 404. Raise
                // what the Mongo backend raises, which ReadStorageObject and friends catch.
                .switchIfEmpty(Mono.defer(() -> this.objectNotFound(storage, id)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MySQLAppDataService.read"));
    }

    /** The same not-found error MongoAppDataService raises for a missing object. */
    <T> Mono<T> objectNotFound(Storage storage, String id) {
        return this.msgService.throwMessage(
                msg -> new StorageObjectNotFoundException(HttpStatus.NOT_FOUND, msg),
                AbstractMongoMessageResourceService.OBJECT_NOT_FOUND,
                storage.getName(),
                id);
    }

    /**
     * One row, with its JSON columns turned back into the structures the caller put in.
     *
     * Takes the resolved schema rather than fetching it, because every caller here
     * already has it and a read is the one path where an extra round trip per row
     * would be felt.
     */
    private Mono<Map<String, Object>> readRow(
            Connection conn, String db, Storage storage, Schema schema, String id) {

        Map<String, StorageColumnDefinition> defs = defs(storage);
        Set<String> json = MySQLTypeMapper.jsonColumns(schema, defs);
        Set<String> dates = MySQLTypeMapper.dateStringColumns(schema, defs);
        Set<String> decimals = MySQLTypeMapper.decimalColumns(schema, defs);

        return Mono.from(this.context(conn)
                        .select()
                        .from(this.table(db, storage))
                        .where(idField().eq(id)))
                .map(r -> MySQLColumnNames.toFields(
                        this.codec.decode(new LinkedHashMap<>(r.intoMap()), json, dates, decimals), schema));
    }

    @Override
    public Mono<Map<String, Object>> update(
            String clientCode, Connection conn, Storage storage, DataObject dataObject, Boolean override) {

        Object given = dataObject.getData() == null ? null : dataObject.getData().get(MySQLTypeMapper.ID_COLUMN);
        if (given == null || StringUtil.safeIsBlank(given.toString()))
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    CoreMessageResourceService.ROW_ID_REQUIRED,
                    MySQLTypeMapper.ID_COLUMN);

        String id = given.toString();

        return FlatMapUtil.flatMapMono(
                        () -> this.ensureTable(conn, clientCode, storage),
                        db -> this.storageService.getSchema(storage),
                        (db, schema) ->
                                this.schemaService.getSchemaRepository(storage.getAppCode(), storage.getClientCode()),
                        (db, schema, repo) ->
                                this.writeValidator.validate(dataObject.getData(), storage, schema, repo),
                        (db, schema, repo, validated) -> this.physicalSchema(storage),
                        (db, schema, repo, validated, resolved) -> {
                            Map<Field<?>, Object> values = new LinkedHashMap<>();
                            this.codec
                                    .encode(
                                            MySQLColumnNames.toColumns(dataObject.getData()),
                                            MySQLTypeMapper.jsonColumns(resolved, defs(storage)),
                                            MySQLTypeMapper.dateStringColumns(resolved, defs(storage)),
                                            MySQLTypeMapper.decimalColumns(resolved, defs(storage)))
                                    .forEach((k, v) -> {
                                        if (!MySQLTypeMapper.ID_COLUMN.equals(k))
                                            values.put(DSL.field(DSL.name(k)), v);
                                    });

                            if (values.isEmpty()) return this.readRow(conn, db, storage, resolved, id);

                            return Mono.from(this.context(conn)
                                            .update(this.table(db, storage))
                                            .set(values)
                                            .where(idField().eq(id)))
                                    .then(this.readRow(conn, db, storage, resolved, id))
                                    // The row as it now stands, which is what Mongo
                                    // records too. A history of what things became
                                    // reads forwards; a history of what they were does
                                    // not line up with the row you are looking at.
                                    .flatMap(saved -> this.addVersion(
                                                    conn, db, storage, id, "UPDATE", saved,
                                                    dataObject.getMessage())
                                            .thenReturn(saved));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MySQLAppDataService.update"));
    }

    @Override
    public Mono<Boolean> delete(
            String clientCode, Connection conn, Storage storage, String id, Boolean deleteVersion) {

        return FlatMapUtil.flatMapMono(
                        () -> this.ensureTable(conn, clientCode, storage),
                        db -> this.physicalSchema(storage),
                        // Read before, write after. The version has to be built from
                        // the row that is about to go, so it is READ first - there is
                        // nothing to read afterwards. It is WRITTEN only once the
                        // delete has actually happened, which matters now that a
                        // delete can be refused: a foreign key with ON DELETE RESTRICT
                        // turns a delete into an error, and recording first would
                        // leave a version saying a surviving row was deleted - or,
                        // with deleteVersion set, would have thrown that row's entire
                        // history away for a delete that never took place. The second
                        // is not recoverable.
                        (db, schema) -> this.rowForVersion(conn, db, storage, schema, id, deleteVersion),
                        (db, schema, row) -> Mono.from(this.context(conn)
                                        .deleteFrom(this.table(db, storage))
                                        .where(idField().eq(id)))
                                .map(count -> count > 0)
                                .defaultIfEmpty(Boolean.FALSE)
                                .onErrorResume(e -> this.refusedByConstraint(e, storage)),
                        (db, schema, row, deleted) -> !Boolean.TRUE.equals(deleted)
                                ? Mono.just(Boolean.FALSE)
                                : this.recordDelete(conn, db, storage, id, deleteVersion, row)
                                        .thenReturn(Boolean.TRUE))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MySQLAppDataService.delete"));
    }

    /**
     * MySQL refusing a delete is a bad request, not a server error.
     *
     * Left alone the driver exception travels all the way out, and the caller gets a
     * 500 carrying the constraint name, the schema name and the table name. The
     * constraint did exactly its job; the response should say so.
     */
    private <T> Mono<T> refusedByConstraint(Throwable e, Storage storage) {

        // Both types, because jOOQ wraps the driver's and which one surfaces depends
        // on whether the statement went through jOOQ or was issued as text.
        if (!(e instanceof org.jooq.exception.IntegrityConstraintViolationException)
                && !(e instanceof io.r2dbc.spi.R2dbcDataIntegrityViolationException)
                && !(e.getCause() instanceof io.r2dbc.spi.R2dbcDataIntegrityViolationException))
            return Mono.error(e);

        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                CoreMessageResourceService.CANNOT_DELETE_STORAGE_WITH_RESTRICT,
                List.of(storage.getName() + " (other rows still reference this one)"));
    }

    /** The row a version will be built from, or nothing when no version is wanted. */
    private Mono<Map<String, Object>> rowForVersion(
            Connection conn, String db, Storage storage, Schema schema, String id, Boolean deleteVersion) {

        if (deleteVersion == null || BooleanUtil.safeValueOf(deleteVersion) || !keepsHistory(storage))
            return Mono.just(Map.of());

        return this.readRow(conn, db, storage, schema, id).defaultIfEmpty(Map.of());
    }

    @Override
    public Mono<Boolean> checkIfExists(String clientCode, Connection conn, Storage storage, String id) {

        return FlatMapUtil.flatMapMono(() -> this.ensureTable(conn, clientCode, storage), db -> Mono.from(
                                this.context(conn)
                                        .selectCount()
                                        .from(this.table(db, storage))
                                        .where(idField().eq(id)))
                        .map(r -> r.value1() > 0)
                        .defaultIfEmpty(Boolean.FALSE))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MySQLAppDataService.checkIfExists"));
    }

    /**
     * Yes only when the key is actually on the table.
     *
     * Two questions, deliberately: whether this relation is the kind MySQL can
     * enforce, and whether it is enforcing it. The first is free and answers no for
     * most relations, so the second is never asked for a TO_MANY, for a relation
     * pointing at anything but the row id, for NOTHING, or for a cascade whose child
     * owes triggers or versions - which is every relation in the fleet today.
     *
     * The second question exists because the first one can be true while the table
     * has no key: an ADD CONSTRAINT is refused over orphan rows, and skipped when
     * the target storage no longer resolves for a client. Both are logged, and both
     * used to leave the service standing down for a constraint nobody was enforcing.
     * Now the service takes it back.
     */
    @Override
    public Mono<Boolean> enforcesRelationConstraint(
            String clientCode, Connection conn, Storage child, String field, StorageRelation relation) {

        if (!MySQLForeignKeys.enforceable(child, relation)) return Mono.just(Boolean.FALSE);

        return this.installedForeignKeyColumns(conn, clientCode, child)
                .map(columns -> columns.contains(field))
                .defaultIfEmpty(Boolean.FALSE)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MySQLAppDataService.enforcesRelationConstraint"));
    }

    /**
     * The columns of this tenant's table that carry a foreign key.
     *
     * Cached on the storage version like the table itself, and evicted directly by
     * {@link #applyForeignKeys} whenever it changes anything - because a key can
     * appear without the version moving, which is exactly what happens when the
     * orphan rows that blocked it are cleared and the next publish installs it.
     */
    private Mono<Set<String>> installedForeignKeyColumns(Connection conn, String clientCode, Storage child) {

        return this.database(clientCode, child)
                .flatMap(db -> this.cacheService.cacheValueOrGet(
                        foreignKeyCache(child),
                        () -> MySQLForeignKeys.existing(this.context(conn), db, child.getUniqueName())
                                .map(keys -> keys.stream()
                                        .map(MySQLForeignKeys.ForeignKey::column)
                                        .collect(java.util.stream.Collectors.toCollection(
                                                java.util.LinkedHashSet::new))),
                        db,
                        child.getVersion()));
    }

    private static String foreignKeyCache(Storage storage) {
        return storage.getUniqueName() + IAppDataService.CACHE_SUFFIX_FOR_FOREIGN_KEYS;
    }

    /**
     * A TO_MANY relation is a JSON array here, so equality finds nothing.
     *
     * {@code JSON_CONTAINS} with {@code JSON_QUOTE} is the comparison that works:
     * the column holds {@code ["01H...", "01J..."]} and the needle has to be a
     * JSON string, not a bare one. Writing this as {@code field = id} would return
     * zero for every row, and a RESTRICT that counts zero is a RESTRICT that
     * allows the delete it exists to refuse.
     */
    private static org.jooq.Condition references(String field, boolean many, String id) {
        return many
                ? DSL.condition(
                        "JSON_CONTAINS({0}, JSON_QUOTE({1}))", DSL.field(DSL.name(field)), DSL.val(id))
                : DSL.field(DSL.name(field)).eq(id);
    }

    @Override
    public Mono<Long> countReferencing(
            String clientCode, Connection conn, Storage child, String field, boolean many, String id) {

        return this.ensureTable(conn, clientCode, child)
                .flatMap(db -> Mono.from(this.context(conn)
                                .selectCount()
                                .from(this.table(db, child))
                                .where(references(field, many, id)))
                        .map(r -> r.value1().longValue()))
                .defaultIfEmpty(0L)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MySQLAppDataService.countReferencing"));
    }

    @Override
    public Mono<List<String>> idsReferencing(
            String clientCode, Connection conn, Storage child, String field, boolean many, String id, int limit) {

        return this.ensureTable(conn, clientCode, child)
                .flatMapMany(db -> Flux.from(this.context(conn)
                        .select(idField())
                        .from(this.table(db, child))
                        .where(references(field, many, id))
                        .limit(limit)))
                .map(r -> String.valueOf(r.get(0)))
                .collectList()
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MySQLAppDataService.idsReferencing"));
    }

    @Override
    public Mono<Boolean> deleteStorage(String clientCode, Connection conn, Storage storage) {

        return FlatMapUtil.flatMapMono(
                        () -> this.database(clientCode, storage),
                        db -> Mono.from(this.context(conn)
                                        .query("DROP TABLE IF EXISTS `" + db + "`.`" + storage.getUniqueName() + "`"))
                                .thenReturn(Boolean.TRUE),
                        // The table has just gone, so anything still remembering that
                        // it was created is now wrong. Leaving it would make the next
                        // write to this storage skip the create and fail against a
                        // table that is not there.
                        (db, dropped) -> this.cacheService
                                .evictAll(tableCache(storage))
                                .thenReturn(dropped))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MySQLAppDataService.deleteStorage"));
    }

    // ------------------------------------------------------------------ joins

    /**
     * Resolve each requested relation into a table this query can join to.
     *
     * Per tenant, like everything else here: the target's definition is overridable
     * too, so the same relation can resolve to a table with different columns for two
     * clients.
     *
     * **Each joined storage is checked against its own readAuth.** The eager fetch
     * does not do this - it reads the target directly, so a caller who may read the
     * parent can already see related rows they have no grant for. That is a
     * pre-existing hole and this is deliberately not a copy of it: a join reads far
     * more of the other storage than eager does, and widening the hole to match would
     * be the wrong way round.
     */
    private Mono<List<JoinedTable>> resolveJoins(
            Connection conn, String db, String clientCode, Storage storage, Schema schema, List<StorageJoin> joins) {

        if (joins == null || joins.isEmpty()) return Mono.just(List.of());

        Set<String> parentColumns = parentColumns(schema, defs(storage));

        String invalid = MySQLJoinPlanner.check(
                storage, joins, parentColumns, MySQLTypeMapper.jsonColumns(schema, defs(storage)));
        if (invalid != null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    CoreMessageResourceService.INVALID_JOIN,
                    invalid);

        return Flux.fromIterable(joins)
                .concatMap(join -> this.resolveJoin(conn, db, clientCode, storage, join))
                .collectList();
    }

    private Mono<JoinedTable> resolveJoin(
            Connection conn, String db, String clientCode, Storage storage, StorageJoin join) {

        StorageRelation relation = storage.getRelations().get(join.getRelation());

        return FlatMapUtil.flatMapMono(
                () -> this.storageService
                        .readForTenant(relation.getStorageName(), storage.getAppCode(), clientCode)
                        .switchIfEmpty(this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                CoreMessageResourceService.INVALID_JOIN,
                                "relation '" + join.getRelation() + "' points at storage '"
                                        + relation.getStorageName() + "', which does not resolve for this client")),
                target -> this.canRead(target),
                (target, allowed) -> this.ensureTable(conn, clientCode, target),
                (target, allowed, targetDb) -> this.physicalSchema(target),
                (target, allowed, targetDb, targetSchema) -> Mono.just(new JoinedTable(
                        join.resolvedAlias(),
                        DSL.table(DSL.name(targetDb, target.getUniqueName())),
                        join.getRelation(),
                        StringUtil.safeIsBlank(relation.getFieldName())
                                ? MySQLTypeMapper.ID_COLUMN
                                : MySQLColumnNames.column(relation.getFieldName()),
                        join.getType() == null ? com.fincity.saas.commons.model.JoinType.LEFT : join.getType(),
                        columnTypes(targetSchema, defs(target)),
                        MySQLTypeMapper.jsonColumns(targetSchema, defs(target)),
                        MySQLTypeMapper.dateStringColumns(targetSchema, defs(target)),
                        MySQLColumnNames.fieldNames(targetSchema))));
    }

    /**
     * Resolve each subquery into a grouped child query.
     *
     * The relation is read off the CHILD, because that is the only side that
     * declares it, and the child storage is checked against its own readAuth exactly
     * as a joined one is: a subquery reads rows of another storage, and counting
     * them is still reading them.
     */
    private Mono<List<SubQueryTable>> resolveSubQueries(
            Connection conn,
            String db,
            String clientCode,
            Storage storage,
            Schema schema,
            List<StorageSubQuery> subQueries,
            Set<String> takenAliases) {

        if (subQueries == null || subQueries.isEmpty()) return Mono.just(List.of());

        return Flux.fromIterable(subQueries)
                .concatMap(sq -> this.storageService
                        .readForTenant(sq.getStorage(), storage.getAppCode(), clientCode)
                        .map(Optional::of)
                        .defaultIfEmpty(Optional.empty()))
                .collectList()
                .flatMap(resolved -> {

                    List<Storage> children =
                            resolved.stream().map(o -> o.orElse(null)).toList();

                    String invalid = MySQLSubQueryPlanner.check(
                            storage, subQueries, children, takenAliases, parentColumns(schema, defs(storage)));

                    if (invalid != null)
                        return this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                CoreMessageResourceService.INVALID_JOIN,
                                invalid);

                    return Flux.range(0, subQueries.size())
                            .concatMap(i -> this.buildSubQuery(
                                    conn, db, clientCode, subQueries.get(i), children.get(i)))
                            .collectList();
                });
    }

    private Mono<SubQueryTable> buildSubQuery(
            Connection conn, String db, String clientCode, StorageSubQuery sq, Storage child) {

        StorageRelation relation = child.getRelations().get(sq.getRelation());

        return FlatMapUtil.flatMapMono(
                () -> this.canRead(child),
                allowed -> this.ensureTable(conn, clientCode, child),
                (allowed, childDb) -> this.physicalSchema(child),
                (allowed, childDb, childSchema) -> {

                    Set<String> childJson = MySQLTypeMapper.jsonColumns(childSchema, defs(child));

                    org.jooq.Condition where;
                    org.jooq.Condition having;
                    try {
                        where = sq.getCondition() == null
                                ? null
                                : MySQLFilterBuilder.buildForRead(sq.getCondition(), MySQLFieldResolver.of(childJson));
                        // Over the measure aliases, which belong to no table.
                        having = sq.getHaving() == null
                                ? null
                                : MySQLFilterBuilder.build(sq.getHaving(), Set.of());
                    } catch (UnsupportedFilterException e) {
                        return this.<SubQueryTable>unsupportedFilter(e);
                    }

                    org.jooq.Table<?> derived = MySQLSubQueryPlanner.derived(
                            this.context(conn),
                            DSL.table(DSL.name(childDb, child.getUniqueName())),
                            sq,
                            sq.getRelation(),
                            where,
                            having,
                            childJson);

                    return Mono.just(new SubQueryTable(
                            sq.resolvedAlias(),
                            derived,
                            MySQLSubQueryPlanner.KEY,
                            StringUtil.safeIsBlank(relation.getFieldName())
                                    ? MySQLTypeMapper.ID_COLUMN
                                    : MySQLColumnNames.column(relation.getFieldName()),
                            MySQLSubQueryPlanner.measures(sq),
                            !Boolean.FALSE.equals(sq.getRequired())));
                });
    }

    private Mono<Boolean> canRead(Storage target) {
        return SecurityContextUtil.getUsersContextAuthentication()
                .flatMap(ca -> SecurityContextUtil.hasAuthority(
                                target.getReadAuth(), ca.getUser().getAuthorities())
                        ? Mono.just(Boolean.TRUE)
                        : this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                CoreMessageResourceService.FORBIDDEN_READ_STORAGE,
                                target.getName()));
    }

    /**
     * Everything a query attaches to the parent: joins towards the parents, and
     * subqueries towards the children.
     *
     * Resolved together because their aliases share one namespace - two of them
     * answering to the same name would make {@code x.y} mean whichever happened to
     * be checked first.
     */
    private record Attached(List<JoinedTable> joins, List<SubQueryTable> subQueries) {

        static final Attached NONE = new Attached(List.of(), List.of());

        boolean isEmpty() {
            return this.joins.isEmpty() && this.subQueries.isEmpty();
        }
    }

    private Mono<Attached> resolveAttached(
            Connection conn, String db, String clientCode, Storage storage, Schema schema,
            List<StorageJoin> joins, List<StorageSubQuery> subQueries) {

        if ((joins == null || joins.isEmpty()) && (subQueries == null || subQueries.isEmpty()))
            return Mono.just(Attached.NONE);

        return FlatMapUtil.flatMapMono(
                () -> this.resolveJoins(conn, db, clientCode, storage, schema, joins),
                resolvedJoins -> this.resolveSubQueries(
                        conn, db, clientCode, storage, schema, subQueries,
                        resolvedJoins.stream().map(JoinedTable::alias).collect(
                                java.util.stream.Collectors.toSet())),
                (resolvedJoins, resolvedSubs) -> Mono.just(new Attached(resolvedJoins, resolvedSubs)));
    }

    private org.jooq.Table<?> fromClause(String db, Storage storage, Attached attached) {

        if (attached.isEmpty()) return this.table(db, storage);

        return MySQLSubQueryPlanner.attach(
                MySQLJoinPlanner.from(this.table(db, storage), attached.joins()),
                MySQLJoinPlanner.PARENT,
                attached.subQueries());
    }

    private MySQLFieldResolver resolver(
            Schema schema, Map<String, StorageColumnDefinition> defs, Attached attached) {
        return resolver(schema, defs, attached, Set.of());
    }

    // Not static any more: the resolver now carries the stemming switch, which is
    // configuration and therefore instance state.
    private MySQLFieldResolver resolver(
            Schema schema, Map<String, StorageColumnDefinition> defs, Attached attached, Set<String> textColumns) {

        MySQLFieldResolver resolver = attached.isEmpty()
                ? MySQLFieldResolver.of(MySQLTypeMapper.jsonColumns(schema, defs))
                : MySQLFieldResolver.of(
                        MySQLJoinPlanner.PARENT,
                        MySQLTypeMapper.jsonColumns(schema, defs),
                        byAlias(attached.joins()),
                        MySQLSubQueryPlanner.byAlias(attached.subQueries()));

        return resolver.withTextColumns(textColumns).withStemming(this.textSearchStemming);
    }

    /** The fields the storage declared text-indexed, filtered to columns that exist. */
    private static Set<String> textColumns(Storage storage, Schema schema, Map<String, StorageColumnDefinition> defs) {

        if (storage.getTextIndexFields() == null || storage.getTextIndexFields().isEmpty()) return Set.of();

        Set<String> columns = parentColumns(schema, defs);
        return storage.getTextIndexFields().stream()
                .map(MySQLColumnNames::column)
                .filter(columns::contains)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /** The id plus everything the schema declares, which is what a row of it is. */
    private static Set<String> parentColumns(Schema schema, Map<String, StorageColumnDefinition> defs) {
        return columnTypes(schema, defs).keySet();
    }

    /**
     * What a storage declares about how its fields should physically be stored.
     *
     * Threaded everywhere the schema is, and never defaulted to the parent's.
     * Each side of a join is a different storage with its own definitions, and
     * using one side's answer for the other is how a DECIMAL on one table silently
     * becomes text on the next.
     */
    private static Map<String, StorageColumnDefinition> defs(Storage storage) {
        return storage == null ? null : MySQLColumnNames.columnDefinitions(storage.getColumnDefinitions());
    }

    /**
     * The resolved schema keyed by column rather than by field.
     *
     * The only way this backend reads a schema, so that a field whose name is not an
     * identifier ("IFSC Code") reaches the DDL, the indexes and the drift check as
     * the column it is stored in. Rows are translated back with
     * {@link MySQLColumnNames#toFields} before they leave.
     */
    private Mono<Schema> physicalSchema(Storage storage) {
        return this.storageService.getResolvedSchema(storage).map(MySQLColumnNames::physical);
    }

    private static Map<String, String> columnTypes(Schema schema, Map<String, StorageColumnDefinition> defs) {
        Map<String, String> out = new LinkedHashMap<>();
        out.put(MySQLTypeMapper.ID_COLUMN, MySQLTypeMapper.ID_TYPE);
        MySQLTypeMapper.columns(schema, defs).forEach(c -> out.put(c.name(), c.type()));
        return out;
    }

    private static Map<String, JoinedTable> byAlias(List<JoinedTable> joins) {
        Map<String, JoinedTable> m = new LinkedHashMap<>();
        joins.forEach(j -> m.put(j.alias(), j));
        return m;
    }

    // ------------------------------------------------------------------ reads

    @Override
    public Mono<Page<Map<String, Object>>> readPage(
            String clientCode, Connection conn, Storage storage, Query query) {

        Pageable page = query.getPageable();

        return FlatMapUtil.flatMapMono(
                        () -> this.ensureTable(conn, clientCode, storage),
                        db -> this.physicalSchema(storage),
                        (db, schema) -> this.resolveAttached(
                                conn, db, clientCode, storage, schema, query.getJoins(), query.getSubQueries()),
                        (db, schema, joins) ->
                                this.rows(conn, db, storage, schema, query, page, joins).collectList(),
                        (db, schema, joins, rows) -> BooleanUtil.safeValueOf(query.getCount())
                                ? this.count(conn, db, storage, schema, query, joins)
                                : Mono.just(page.getOffset() + rows.size()),
                        (db, schema, joins, rows, total) ->
                                Mono.just(PageableExecutionUtils.getPage(rows, page, total::longValue)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MySQLAppDataService.readPage"));
    }

    @Override
    public Flux<Map<String, Object>> readPageAsFlux(
            String clientCode, Connection conn, Storage storage, Query query) {

        Pageable page = query.getPageable();

        return this.ensureTable(conn, clientCode, storage)
                .flatMapMany(db -> this.physicalSchema(storage)
                        .flatMapMany(schema -> this
                                .resolveAttached(
                                        conn, db, clientCode, storage, schema, query.getJoins(),
                                        query.getSubQueries())
                                .flatMapMany(attached ->
                                        this.rows(conn, db, storage, schema, query, page, attached))))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MySQLAppDataService.readPageAsFlux"));
    }

    private Flux<Map<String, Object>> rows(
            Connection conn,
            String db,
            Storage storage,
            Schema schema,
            Query query,
            Pageable page,
            Attached attached) {

        Map<String, StorageColumnDefinition> defs = defs(storage);
        Set<String> json = MySQLTypeMapper.jsonColumns(schema, defs);
        Set<String> dates = MySQLTypeMapper.dateStringColumns(schema, defs);
        Set<String> decimals = MySQLTypeMapper.decimalColumns(schema, defs);
        MySQLFieldResolver resolver = resolver(schema, defs, attached, textColumns(storage, schema, defs));

        // A plain read generates exactly the SQL it always did. A query that asked
        // for nothing extra should not start carrying a table alias because the
        // feature exists.
        if (attached.isEmpty()) {
            List<Field<?>> projection = projection(schema, query);

            SelectConditionStep<? extends Record> where = (projection.isEmpty()
                            ? this.context(conn).select(DSL.asterisk())
                            : this.context(conn).select(projection))
                    .from(this.table(db, storage))
                    .where(this.condition(query, resolver));

            List<OrderField<?>> order = order(schema, page.getSort(), resolver);

            return Flux.from(order.isEmpty()
                            ? where.limit(page.getPageSize()).offset((int) page.getOffset())
                            : where.orderBy(order).limit(page.getPageSize()).offset((int) page.getOffset()))
                    .map(r -> MySQLColumnNames.toFields(
                        this.codec.decode(new LinkedHashMap<>(r.intoMap()), json, dates, decimals), schema));
        }

        List<Field<?>> selection =
                MySQLJoinPlanner.selection(selectedParentColumns(schema, query), attached.joins());
        selection.addAll(MySQLSubQueryPlanner.selection(attached.subQueries()));

        SelectConditionStep<? extends Record> where = this.context(conn)
                .select(selection)
                .from(this.fromClause(db, storage, attached))
                .where(this.condition(query, resolver));

        List<OrderField<?>> order = joinedOrder(schema, page.getSort(), resolver, attached);

        return Flux.from(order.isEmpty()
                        ? where.limit(page.getPageSize()).offset((int) page.getOffset())
                        : where.orderBy(order).limit(page.getPageSize()).offset((int) page.getOffset()))
                .map(r -> MySQLColumnNames.toFields(
                        this.decodeJoined(new LinkedHashMap<>(r.intoMap()), json, dates, decimals, attached), schema));
    }

    /**
     * Decode each side with its own schema, then fold the joined columns into objects.
     *
     * Per side because the two storages are unrelated: a column called {@code data}
     * may be JSON on one and a string on the other, and decoding both with the
     * parent's answer would corrupt whichever disagreed.
     */
    private Map<String, Object> decodeJoined(
            Map<String, Object> flat,
            Set<String> json,
            Set<String> dates,
            Set<String> decimals,
            Attached attached) {

        Map<String, Object> decoded = this.codec.decode(flat, json, dates, decimals);

        for (JoinedTable join : attached.joins()) {
            Set<String> joinJson = prefixed(join, join.jsonColumns());
            Set<String> joinDates = prefixed(join, join.dateColumns());
            // Derived from the types the join already carries rather than from a
            // fourth set on JoinedTable: a DECIMAL column is exactly one whose type
            // says so, and the record already knows every column's type.
            Set<String> joinDecimals = prefixed(join, decimalsOf(join));
            if (!joinJson.isEmpty() || !joinDates.isEmpty() || !joinDecimals.isEmpty())
                decoded = this.codec.decode(decoded, joinJson, joinDates, joinDecimals);
        }

        // Subquery measures nest under their alias too, so a caller reads
        // `orders.count` the same way it reads a joined column.
        return MySQLJoinPlanner.nest(decoded, attached.joins(), attached.subQueries());
    }

    private static Set<String> decimalsOf(JoinedTable join) {
        Set<String> out = new java.util.LinkedHashSet<>();
        join.columnTypes().forEach((name, type) -> {
            if (MySQLTypeMapper.isDecimal(type)) out.add(name);
        });
        return out;
    }

    private static Set<String> prefixed(JoinedTable join, Set<String> columns) {
        Set<String> out = new java.util.LinkedHashSet<>();
        columns.forEach(c -> out.add(join.outputKey(c)));
        return out;
    }

    /**
     * The parent columns this query wants, with any field selection already applied.
     *
     * A joined read names its columns rather than using an asterisk, so the include
     * and exclude lists have to be resolved here instead of becoming a projection.
     */
    static Set<String> selectedParentColumns(Schema schema, Query query) {

        Set<String> all = new java.util.LinkedHashSet<>();
        all.add(MySQLTypeMapper.ID_COLUMN);
        MySQLTypeMapper.columns(schema).forEach(c -> all.add(c.name()));

        if (query.getFields() == null || query.getFields().isEmpty()) return all;

        Set<String> named = columnsNamed(query.getFields());

        if (BooleanUtil.safeValueOf(query.getExcludeFields())) {
            all.removeIf(named::contains);
            all.add(MySQLTypeMapper.ID_COLUMN);
            return all;
        }

        Set<String> out = new java.util.LinkedHashSet<>();
        out.add(MySQLTypeMapper.ID_COLUMN);
        named.stream().filter(all::contains).forEach(out::add);
        return out;
    }

    /**
     * Sorting across a join, restricted to columns that exist on either side.
     *
     * Same reason as the unjoined case: Query.DEFAULT_SORT is {@code updatedAt DESC}
     * and most storages have no such field, so a sort on a name nothing declares is
     * dropped rather than failing the read.
     */
    static List<OrderField<?>> joinedOrder(
            Schema schema, Sort sort, MySQLFieldResolver resolver, Attached attached) {

        if (sort == null || sort.isUnsorted()) return List.of();

        Set<String> known = new java.util.LinkedHashSet<>();
        known.add(MySQLTypeMapper.ID_COLUMN);
        MySQLTypeMapper.columns(schema).forEach(c -> known.add(c.name()));
        for (JoinedTable join : attached.joins()) join.columns().forEach(c -> known.add(join.outputKey(c)));
        // Sorting customers by how many orders they have is most of the point.
        for (SubQueryTable sq : attached.subQueries())
            sq.measures().forEach(m -> known.add(sq.outputKey(m)));

        Set<String> json = MySQLTypeMapper.jsonColumns(schema);

        List<OrderField<?>> out = new java.util.ArrayList<>();
        for (Sort.Order o : sort) {
            if (!addressable(o.getProperty(), known, json)) continue;
            Field<Object> f = resolver.resolve(o.getProperty());
            out.add(o.isAscending() ? f.asc() : f.desc());
        }
        return out;
    }

    private Mono<Long> count(
            Connection conn, String db, Storage storage, Schema schema, Query query, Attached attached) {

        MySQLFieldResolver resolver =
                resolver(schema, defs(storage), attached, textColumns(storage, schema, defs(storage)));

        return Mono.from(this.context(conn)
                        .selectCount()
                        .from(this.fromClause(db, storage, attached))
                        .where(this.condition(query, resolver)))
                .map(r -> r.value1().longValue())
                .defaultIfEmpty(0L);
    }

    /**
     * Converts the filter, turning a filter this backend cannot express into a clear
     * 501 rather than letting it reach SQL as "every row".
     */
    private org.jooq.Condition condition(Query query, MySQLFieldResolver resolver) {
        try {
            return MySQLFilterBuilder.buildForRead(query.getCondition(), resolver);
        } catch (UnsupportedFilterException e) {
            // A malformed condition is the CALLER's mistake and gets a 400, the same
            // code and the same sentence the Mongo backend gives for it. Only a real
            // gap in this backend is a 501, which is a statement about the backend
            // rather than about the request.
            if (e.isMalformed())
                throw this.msgService.nonReactiveMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        CoreMessageResourceService.UNSUPPORTED_CONDITION,
                        e.getDetail());

            throw this.msgService.nonReactiveMessage(
                    msg -> new GenericException(HttpStatus.NOT_IMPLEMENTED, msg),
                    CoreMessageResourceService.UNSUPPORTED_ON_BACKEND,
                    e.getDetail(),
                    BACKEND);
        }
    }

    /** Empty means select everything, which is what the Mongo backend does with no fields. */
    static List<Field<?>> projection(Schema schema, Query query) {

        if (query.getFields() == null || query.getFields().isEmpty()) return List.of();

        Set<String> named = columnsNamed(query.getFields());

        if (!BooleanUtil.safeValueOf(query.getExcludeFields())) {
            List<Field<?>> out = new java.util.ArrayList<>();
            // The id is always returned; a caller asking for a subset still needs to
            // identify the rows it got back.
            out.add(idField());
            named.stream()
                    .filter(f -> !MySQLTypeMapper.ID_COLUMN.equals(f))
                    .forEach(f -> out.add(DSL.field(DSL.name(f))));
            return out;
        }

        List<Field<?>> out = new java.util.ArrayList<>();
        out.add(idField());
        for (MySQLColumn c : MySQLTypeMapper.columns(schema))
            if (!named.contains(c.name())) out.add(DSL.field(DSL.name(c.name())));
        return out;
    }

    /**
     * Sorting is restricted to columns that exist.
     *
     * Query.DEFAULT_SORT is {@code updatedAt DESC} and most storages never declare such
     * a field. Mongo quietly ignores a sort on a missing field; MySQL would fail the
     * whole query, so a default that was invisible on one backend would break every
     * read on the other.
     */
    /**
     * Whether a sort can address this name at all.
     *
     * A dotted name whose head is a JSON column is a path into that column, and the
     * resolver already knows how to read one - filtering has accepted them all
     * along. Only the gate was missing, so a sort by {@code address.city} was
     * dropped on the floor and the rows came back in whatever order the table felt
     * like, with nothing to say the sort had been ignored.
     */
    private static boolean addressable(String name, Set<String> known, Set<String> jsonColumns) {

        // Known holds columns, and a sort names fields: "IFSC Code" is known as
        // IFSC_Code, and on a joined side as alias.IFSC_Code.
        if (known.contains(name) || known.contains(MySQLColumnNames.column(name))) return true;

        int dot = name.indexOf('.');
        if (dot <= 0 || dot >= name.length() - 1) return false;

        String head = name.substring(0, dot);
        return jsonColumns.contains(MySQLColumnNames.column(head))
                || known.contains(head + "." + MySQLColumnNames.column(name.substring(dot + 1)));
    }

    /** The columns a field list names, in order. */
    private static Set<String> columnsNamed(List<String> fields) {
        Set<String> out = new java.util.LinkedHashSet<>();
        fields.forEach(f -> out.add(MySQLColumnNames.column(f)));
        return out;
    }

    static List<OrderField<?>> order(Schema schema, Sort sort) {
        return order(schema, sort, MySQLFieldResolver.of(MySQLTypeMapper.jsonColumns(schema)));
    }

    static List<OrderField<?>> order(Schema schema, Sort sort, MySQLFieldResolver resolver) {

        if (sort == null || sort.isUnsorted()) return List.of();

        Set<String> known = new java.util.LinkedHashSet<>();
        known.add(MySQLTypeMapper.ID_COLUMN);
        MySQLTypeMapper.columns(schema).forEach(c -> known.add(c.name()));

        Set<String> json = MySQLTypeMapper.jsonColumns(schema);

        List<OrderField<?>> out = new java.util.ArrayList<>();
        for (Sort.Order o : sort) {
            if (!addressable(o.getProperty(), known, json)) continue;
            // Through the resolver, because a dotted name is a JSON path and
            // DSL.name would render it as a qualified column instead.
            Field<Object> f = resolver.resolve(o.getProperty());
            out.add(o.isAscending() ? f.asc() : f.desc());
        }
        return out;
    }

    // ------------------------------------------------------------------ aggregate

    /**
     * A grouped read, which is the thing this whole backend exists for.
     *
     * On Mongo this is a pipeline that can only ever see one collection. Here it is
     * a SELECT, and a SELECT is one join away from spanning two - which was the
     * argument for moving in the first place.
     */
    @Override
    public Mono<Page<Map<String, Object>>> aggregate(
            String clientCode, Connection conn, Storage storage, AggregateQuery query) {

        String invalid = AggregateQueryValidator.check(query);
        if (invalid != null) return this.invalidAggregation(invalid);

        Pageable page = query.getPageable();

        return FlatMapUtil.flatMapMono(
                        () -> this.ensureTable(conn, clientCode, storage),
                        db -> this.physicalSchema(storage),
                        (db, schema) -> this.resolveAttached(
                                conn, db, clientCode, storage, schema, query.getJoins(), query.getSubQueries()),
                        (db, schema, joins) -> this.checkAggregateFields(storage, schema, query, joins),
                        (db, schema, joins, fieldsOk) -> this.checkZones(conn, query),
                        (db, schema, joins, fieldsOk, zonesOk) ->
                                this.runAggregate(conn, db, storage, schema, query, page, joins))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MySQLAppDataService.aggregate"));
    }

    private Mono<Page<Map<String, Object>>> runAggregate(
            Connection conn,
            String db,
            Storage storage,
            Schema schema,
            AggregateQuery query,
            Pageable page,
            Attached attached) {

        DSLContext ctx = this.context(conn);
        Set<String> json = MySQLTypeMapper.jsonColumns(schema, defs(storage));
        MySQLFieldResolver resolver =
                resolver(schema, defs(storage), attached, textColumns(storage, schema, defs(storage)));

        org.jooq.Table<?> from = this.fromClause(db, storage, attached);

        org.jooq.Condition where;
        org.jooq.Condition having;
        try {
            where = MySQLFilterBuilder.buildForRead(query.getCondition(), resolver);
            // Over aliases, not columns: after grouping the only names that exist are
            // the ones this query invented, and they belong to no table.
            having = query.getHaving() == null ? null : MySQLFilterBuilder.build(query.getHaving(), Set.of());
        } catch (UnsupportedFilterException e) {
            return this.unsupportedFilter(e);
        }

        Set<String> jsonAliases = jsonAliases(query, json);

        Mono<List<Map<String, Object>>> rows = Flux.from(
                        MySQLAggregateBuilder.page(ctx, from, query, where, having, page, resolver))
                .map(r -> this.codec.decode(new LinkedHashMap<>(r.intoMap()), jsonAliases))
                .collectList();

        return rows.flatMap(list -> BooleanUtil.safeValueOf(query.getCount())
                ? Mono.from(MySQLAggregateBuilder.count(ctx, from, query, where, having, resolver))
                        .map(r -> r.get(0) instanceof Number n ? n.longValue() : 0L)
                        .defaultIfEmpty(0L)
                        .map(total -> PageableExecutionUtils.getPage(list, page, () -> total))
                : Mono.just(PageableExecutionUtils.getPage(
                        list, page, () -> page.getOffset() + (long) list.size())));
    }

    /**
     * Group keys that are a whole JSON column rather than a path into one.
     *
     * Grouping on a JSON column is a strange thing to do and perfectly legal, and
     * the value comes back as JSON text. Everything else an aggregate returns is a
     * scalar.
     */
    private static Set<String> jsonAliases(AggregateQuery query, Set<String> jsonColumns) {

        Set<String> out = new java.util.LinkedHashSet<>();
        if (query.getGroupBy() == null) return out;

        for (var g : query.getGroupBy())
            if (jsonColumns.contains(g.getField())) out.add(g.resolvedAlias());

        return out;
    }

    /**
     * Every referenced field has to be a column this storage declares.
     *
     * The validator has already checked the names are identifiers; this is the part
     * that can only be done against the resolved schema. The bucket rules are where
     * this backend differs from Mongo on purpose: a real date column needs no
     * encoding and must not be given one, because an encoding there would be applied
     * to something that is not a number and the result would be silently wrong.
     */
    private Mono<Boolean> checkAggregateFields(
            Storage storage, Schema schema, AggregateQuery query, Attached attached) {

        Map<String, String> columns = new LinkedHashMap<>();
        columns.put(MySQLTypeMapper.ID_COLUMN, MySQLTypeMapper.ID_TYPE);
        for (MySQLColumn c : MySQLTypeMapper.columns(schema, defs(storage))) columns.put(c.name(), c.type());

        // A joined column is addressed by its alias, and is as real a column as any
        // on this side. Checking only the parent would refuse every query the join
        // was added for.
        for (JoinedTable join : attached.joins())
            join.columnTypes().forEach((c, type) -> columns.put(join.outputKey(c), type));

        // A subquery measure is a number by construction, so grouping and summing it
        // are both legal - "revenue of customers with more than three orders" needs
        // the second.
        for (SubQueryTable sq : attached.subQueries())
            sq.measures().forEach(m -> columns.put(sq.outputKey(m), "DOUBLE"));

        if (query.getGroupBy() != null)
            for (var g : query.getGroupBy()) {
                String head = head(g.getField(), columns);
                String type = columns.get(head);

                if (type == null)
                    return this.invalidAggregation(
                            "storage " + storage.getName() + " declares no field '" + g.getField() + "'");

                if (g.getBucket() == null) continue;

                boolean date = isDateType(type);
                boolean numeric = isNumericType(type);

                if (!date && !numeric)
                    return this.invalidAggregation("field '" + g.getField() + "' is " + type
                            + ", which is neither a date nor a number, so it cannot be bucketed by "
                            + g.getBucket());

                if (date && g.getEncoding() != null)
                    return this.invalidAggregation("field '" + g.getField() + "' is a " + type
                            + " column, so it needs no encoding; an encoding here would be applied to"
                            + " something that is not an epoch number");

                if (numeric && g.getEncoding() == null)
                    return this.invalidAggregation("field '" + g.getField() + "' is " + type
                            + ", so bucketing it needs an encoding (EPOCH_SECONDS or EPOCH_MILLIS);"
                            + " seconds cannot be told from milliseconds by the type");
            }

        for (Aggregation a : query.getAggregations()) {
            if (a.getField() == null || a.getField().isBlank()) continue;

            String head = head(a.getField(), columns);
            String type = columns.get(head);

            if (type == null)
                return this.invalidAggregation(
                        "storage " + storage.getName() + " declares no field '" + a.getField() + "'");

            // CAST('abc' AS DOUBLE) is 0 in MySQL, with a warning nobody reads. A
            // total that is quietly short is worse than a request that is refused.
            if ((a.getFunction() == AggregateFunction.SUM || a.getFunction() == AggregateFunction.AVG)
                    && !isNumericType(type)
                    && !MySQLTypeMapper.isJson(type))
                return this.invalidAggregation("cannot " + a.getFunction() + " field '" + a.getField()
                        + "', which is " + type + "; a non-numeric value casts to zero rather than failing");
        }

        return Mono.just(Boolean.TRUE);
    }

    /**
     * The head of a field reference, which is a column unless it names a join.
     *
     * {@code address.city} is a path into one column; {@code customer.region} is a
     * column on another table. The join aliases decide which, and they cannot
     * collide with a parent column because the planner refuses that.
     */
    static String head(String field, Map<String, String> columns) {
        if (columns.containsKey(field)) return field;

        // The query names fields and the map holds columns: "IFSC Code" is known as
        // IFSC_Code, and on a joined side as alias.IFSC_Code.
        String column = MySQLColumnNames.column(field);
        if (columns.containsKey(column)) return column;

        int dot = field.indexOf('.');
        if (dot < 0) return column;

        String joined = field.substring(0, dot) + "." + MySQLColumnNames.column(field.substring(dot + 1));
        return columns.containsKey(joined) ? joined : MySQLColumnNames.column(field.substring(0, dot));
    }



    private static boolean isDateType(String type) {
        String t = type.toUpperCase();
        return t.startsWith("DATE") || t.startsWith("TIMESTAMP");
    }

    private static boolean isNumericType(String type) {
        String t = type.toUpperCase();
        return t.startsWith("INT") || t.startsWith("BIGINT") || t.startsWith("TINYINT")
                || t.startsWith("SMALLINT") || t.startsWith("MEDIUMINT") || t.startsWith("DOUBLE")
                || t.startsWith("FLOAT") || t.startsWith("DECIMAL");
    }

    /**
     * Ask the server whether it knows the zones this query names, before trusting it.
     *
     * CONVERT_TZ with a named zone returns NULL when MySQL's time zone tables were
     * never loaded, which on a default install they are not. The query would succeed
     * and every bucket would be null, so it is better to refuse with the reason than
     * to return a page of nothing.
     */
    private Mono<Boolean> checkZones(Connection conn, AggregateQuery query) {

        Set<String> zones = MySQLAggregateBuilder.namedZones(query);
        if (zones.isEmpty()) return Mono.just(Boolean.TRUE);

        DSLContext ctx = this.context(conn);

        return Flux.fromIterable(zones)
                .concatMap(zone -> Mono.from(ctx.resultQuery(MySQLAggregateBuilder.zoneCheck(zone)))
                        .map(r -> r.get(0) instanceof Number n && n.intValue() == 1)
                        .defaultIfEmpty(Boolean.FALSE)
                        .flatMap(known -> Boolean.TRUE.equals(known)
                                ? Mono.just(Boolean.TRUE)
                                : this.<Boolean>invalidAggregation("this MySQL server does not know the timezone '"
                                        + zone + "'; its time zone tables have not been loaded, so bucketing in"
                                        + " that zone would silently return nothing. Load them with"
                                        + " mysql_tzinfo_to_sql, or use UTC")))
                .then(Mono.just(Boolean.TRUE));
    }

    // ------------------------------------------------------------------ bulk delete

    @Override
    public Mono<Long> deleteByFilter(
            String clientCode, Connection conn, Storage storage, Query query, Boolean devMode, Boolean deleteVersion) {

        // A multi-table DELETE is a different statement with different semantics
        // about which side rows come off, and it is not built. Ignoring the joins
        // would delete by a condition that was never applied.
        if (query.getJoins() != null && !query.getJoins().isEmpty())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.NOT_IMPLEMENTED, msg),
                    CoreMessageResourceService.UNSUPPORTED_ON_BACKEND,
                    "a delete with joins",
                    BACKEND);

        return FlatMapUtil.flatMapMono(
                        () -> this.ensureTable(conn, clientCode, storage),
                        db -> this.physicalSchema(storage),
                        (db, schema) -> {
                            Set<String> json = MySQLTypeMapper.jsonColumns(schema, defs(storage));

                            org.jooq.Condition where;
                            try {
                                // Through a resolver rather than the bare column
                                // set, so a filtered delete understands the same
                                // filters a read does - TEXT_SEARCH included.
                                where = MySQLFilterBuilder.build(
                                        query.getCondition(),
                                        MySQLFieldResolver.of(json)
                                                .withTextColumns(textColumns(storage, schema, defs(storage))));
                            } catch (UnsupportedFilterException e) {
                                return this.<Long>unsupportedFilter(e);
                            }

                            DSLContext ctx = this.context(conn);

                            // devMode asks what WOULD go, and must not take it.
                            if (BooleanUtil.safeValueOf(devMode))
                                return Mono.from(ctx.selectCount()
                                                .from(this.table(db, storage))
                                                .where(where))
                                        .map(r -> r.value1().longValue())
                                        .defaultIfEmpty(0L);

                            return this.recordBulkDelete(conn, db, storage, where, deleteVersion)
                                    .then(Mono.from(
                                            ctx.deleteFrom(this.table(db, storage)).where(where)))
                                    .map(Long::valueOf)
                                    .defaultIfEmpty(0L);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MySQLAppDataService.deleteByFilter"));
    }

    // ------------------------------------------------------------------ versions

    /** A storage writes version rows when it asked for auditing, versioning, or both. */
    private static boolean keepsHistory(Storage storage) {
        return BooleanUtil.safeValueOf(storage.getIsAudited()) || BooleanUtil.safeValueOf(storage.getIsVersioned());
    }

    private org.jooq.Table<?> versionTable(String db, Storage storage) {
        return DSL.table(DSL.name(db, MySQLVersionTable.nameFor(storage.getUniqueName())));
    }

    /**
     * Created on first use rather than alongside the data table.
     *
     * Most storages keep no history at all, and an empty table per storage per tenant
     * is 580 collections' worth of clutter for nothing.
     */
    private Mono<Boolean> ensureVersionTable(Connection conn, String db, Storage storage) {
        return this.cacheService.cacheValueOrGet(
                versionCache(storage),
                () -> Mono.from(this.context(conn)
                                .query(MySQLVersionTable.createTable(db, storage.getUniqueName())))
                        .thenReturn(Boolean.TRUE)
                        .defaultIfEmpty(Boolean.TRUE),
                db);
    }

    private static String versionCache(Storage storage) {
        return MySQLVersionTable.nameFor(storage.getUniqueName()) + IAppDataService.CACHE_SUFFIX_FOR_TABLE_CREATION;
    }

    /**
     * One history row for one change.
     *
     * The snapshot is only written when the storage asked to be VERSIONED. An audited
     * storage records who did what and when, which is a far smaller row, and storing
     * the body for it would multiply the tenant's data size by the number of edits.
     */
    private Mono<Boolean> addVersion(
            Connection conn, String db, Storage storage, String id, String operation,
            Map<String, Object> snapshot, String message) {

        if (!keepsHistory(storage)) return Mono.just(Boolean.TRUE);

        return FlatMapUtil.flatMapMono(
                () -> this.ensureVersionTable(conn, db, storage),
                ensured -> SecurityContextUtil.getUsersContextAuthentication()
                        .map(ca -> ca.getUser() == null ? null : ca.getUser().getId())
                        .map(Object.class::cast)
                        .defaultIfEmpty(""),
                (ensured, by) -> {

                    Map<Field<?>, Object> values = new LinkedHashMap<>();
                    values.put(DSL.field(DSL.name(MySQLTypeMapper.ID_COLUMN)), UniqueUtil.ulid());
                    values.put(DSL.field(DSL.name(MySQLVersionTable.OBJECT_ID)), id);
                    values.put(DSL.field(DSL.name(MySQLVersionTable.MESSAGE)), message);
                    values.put(
                            DSL.field(DSL.name(MySQLVersionTable.CREATED_AT)),
                            java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
                    values.put(DSL.field(DSL.name(MySQLVersionTable.OPERATION)), operation);
                    values.put(
                            DSL.field(DSL.name(MySQLVersionTable.CREATED_BY)),
                            by instanceof java.math.BigInteger b ? b.longValue() : null);

                    if (BooleanUtil.safeValueOf(storage.getIsVersioned()))
                        values.put(
                                DSL.field(DSL.name(MySQLVersionTable.OBJECT)),
                                this.codec.toJson(snapshot == null ? Map.of() : snapshot));

                    return Mono.from(this.context(conn)
                                    .insertInto(this.versionTable(db, storage))
                                    .set(values))
                            .then(this.trimVersions(conn, db, storage, id))
                            .thenReturn(Boolean.TRUE)
                            .defaultIfEmpty(Boolean.TRUE);
                });
    }

    /**
     * Keep one row's history inside its retention policy.
     *
     * The statements live in {@link MySQLVersionTrim} so the integration test can
     * drive exactly what the write path drives.
     *
     * A failure here is swallowed deliberately. Trimming is housekeeping; losing it
     * costs disk, while letting it fail the caller would turn a successful write
     * into an error the caller can do nothing about.
     */
    private Mono<Boolean> trimVersions(Connection conn, String db, Storage storage, String id) {

        return MySQLVersionTrim.trim(
                        this.context(conn),
                        this.versionTable(db, storage),
                        id,
                        this.retentionDefaults.forStorage(storage))
                .onErrorResume(e -> Mono.just(Boolean.TRUE))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MySQLAppDataService.trimVersions"));
    }

    /**
     * Record one row's removal: either a DELETE version row, or the purge of its
     * history.
     *
     * Mirrors the Mongo contract exactly, including the oddity that a null
     * deleteVersion means "do nothing either way". The two backends disagreeing about
     * what happens to history on delete would be a difference nobody discovers until
     * they need the history.
     */
    private Mono<Boolean> recordDelete(
            Connection conn,
            String db,
            Storage storage,
            String id,
            Boolean deleteVersion,
            Map<String, Object> row) {

        if (deleteVersion == null || !keepsHistory(storage)) return Mono.just(Boolean.TRUE);

        if (BooleanUtil.safeValueOf(deleteVersion))
            return this.ensureVersionTable(conn, db, storage)
                    .then(Mono.from(this.context(conn)
                            .deleteFrom(this.versionTable(db, storage))
                            .where(DSL.field(DSL.name(MySQLVersionTable.OBJECT_ID)).eq(id))))
                    .thenReturn(Boolean.TRUE)
                    .defaultIfEmpty(Boolean.TRUE);

        if (row == null || row.isEmpty()) return Mono.just(Boolean.TRUE);

        return this.addVersion(conn, db, storage, id, "DELETE", row, null).defaultIfEmpty(Boolean.TRUE);
    }

    /**
     * The same for a filtered delete, without ever holding the whole match set.
     *
     * Ids are streamed and their history removed in batches. Collecting every matched
     * row first, as the obvious version does, turns a wide delete into an
     * out-of-memory risk on exactly the tenant where it matters most.
     */
    private Mono<Boolean> recordBulkDelete(
            Connection conn, String db, Storage storage, org.jooq.Condition where, Boolean deleteVersion) {

        if (deleteVersion == null || !keepsHistory(storage)) return Mono.just(Boolean.TRUE);

        DSLContext ctx = this.context(conn);

        if (BooleanUtil.safeValueOf(deleteVersion))
            return this.ensureVersionTable(conn, db, storage)
                    .thenMany(Flux.from(ctx.select(idField())
                                    .from(this.table(db, storage))
                                    .where(where))
                            .map(r -> String.valueOf(r.get(0)))
                            .buffer(VERSION_PURGE_BATCH)
                            .concatMap(ids -> Mono.from(ctx.deleteFrom(this.versionTable(db, storage))
                                    .where(DSL.field(DSL.name(MySQLVersionTable.OBJECT_ID)).in(ids)))))
                    .then(Mono.just(Boolean.TRUE));

        return Flux.from(ctx.select().from(this.table(db, storage)).where(where))
                .concatMap(r -> {
                    Map<String, Object> row = new LinkedHashMap<>(r.intoMap());
                    return this.addVersion(
                            conn, db, storage, String.valueOf(row.get(MySQLTypeMapper.ID_COLUMN)), "DELETE", row,
                            null);
                })
                .then(Mono.just(Boolean.TRUE));
    }

    @Override
    public Mono<Map<String, Object>> readVersion(
            String clientCode, Connection conn, Storage storage, String versionId) {

        return FlatMapUtil.flatMapMono(
                        () -> this.database(clientCode, storage),
                        db -> this.ensureVersionTable(conn, db, storage),
                        (db, ensured) -> Mono.from(this.context(conn)
                                        .select()
                                        .from(this.versionTable(db, storage))
                                        .where(idField().eq(versionId)))
                                .map(r -> this.codec.decode(
                                        new LinkedHashMap<>(r.intoMap()), Set.of(MySQLVersionTable.OBJECT))))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                        AbstractMongoMessageResourceService.OBJECT_NOT_FOUND,
                        storage.getName(),
                        versionId))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MySQLAppDataService.readVersion"));
    }

    @Override
    public Mono<Page<Map<String, Object>>> readPageVersion(
            String clientCode,
            Connection conn,
            Storage storage,
            String objectId,
            Query query,
            Boolean includeObject) {

        boolean withObject = !Boolean.FALSE.equals(includeObject);
        Pageable page = query.getPageable();

        return FlatMapUtil.flatMapMono(
                        () -> this.database(clientCode, storage),
                        db -> this.ensureVersionTable(conn, db, storage),
                        (db, ensured) -> {

                            org.jooq.Condition where =
                                    DSL.field(DSL.name(MySQLVersionTable.OBJECT_ID)).eq(objectId);

                            try {
                                if (query.getCondition() != null)
                                    where = where.and(MySQLFilterBuilder.buildForRead(query.getCondition(), Set.of()));
                            } catch (UnsupportedFilterException e) {
                                return this.<Page<Map<String, Object>>>unsupportedFilter(e);
                            }

                            DSLContext ctx = this.context(conn);
                            final org.jooq.Condition finalWhere = where;

                            // The snapshot is the bulk of a version row and a "who
                            // changed what, when" view never looks at it.
                            // Every column, or every column but the snapshot. Named
                            // rather than an asterisk minus one, because there is no
                            // such thing in SQL.
                            List<Field<?>> fields = new java.util.ArrayList<>();
                            MySQLVersionTable.AUDIT_FIELDS.forEach(f -> fields.add(DSL.field(DSL.name(f))));
                            if (withObject) fields.add(DSL.field(DSL.name(MySQLVersionTable.OBJECT)));

                            return Flux.from(ctx.select(fields)
                                            .from(this.versionTable(db, storage))
                                            .where(finalWhere)
                                            .orderBy(DSL.field(DSL.name(MySQLVersionTable.CREATED_AT)).desc())
                                            .limit(page.getPageSize())
                                            .offset((int) page.getOffset()))
                                    .map(r -> this.codec.decode(
                                            new LinkedHashMap<>(r.intoMap()), Set.of(MySQLVersionTable.OBJECT)))
                                    .collectList()
                                    .flatMap(list -> BooleanUtil.safeValueOf(query.getCount())
                                            ? Mono.from(ctx.selectCount()
                                                            .from(this.versionTable(db, storage))
                                                            .where(finalWhere))
                                                    .map(r -> r.value1().longValue())
                                                    .defaultIfEmpty(0L)
                                                    .map(total -> PageableExecutionUtils.getPage(
                                                            list, page, () -> total))
                                            : Mono.just(PageableExecutionUtils.getPage(
                                                    list, page, () -> page.getOffset() + (long) list.size())));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MySQLAppDataService.readPageVersion"));
    }

    // ------------------------------------------------------------------ draft surface

    @Override
    public Mono<Boolean> dropDraftStorage(String clientCode, Connection conn, Storage storage) {

        return SecurityContextUtil.getUsersContextAuthentication()
                .flatMap(ca -> {
                    // Named explicitly rather than taken from the ambient flag: this is
                    // called while deleting a definition, which happens on the live
                    // surface, so isDraft() would be false exactly when the draft
                    // namespace is the one to drop.
                    String db = databaseName(
                            BooleanUtil.safeValueOf(storage.getIsAppLevel()) ? ca.getUrlClientCode() : clientCode,
                            storage.getAppCode(),
                            true);

                    DSLContext ctx = this.context(conn);
                    String table = storage.getUniqueName();

                    return Mono.from(ctx.query("DROP TABLE IF EXISTS `" + db + "`.`" + table + "`"))
                            .then(Mono.from(ctx.query("DROP TABLE IF EXISTS `" + db + "`.`"
                                    + MySQLVersionTable.nameFor(table) + "`")))
                            .then(this.cacheService.evictAll(tableCache(storage)))
                            .then(this.cacheService.evictAll(versionCache(storage)))
                            .thenReturn(Boolean.TRUE)
                            .onErrorReturn(Boolean.FALSE);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MySQLAppDataService.dropDraftStorage"));
    }

    @Override
    public Mono<Boolean> dropDraftDatabase(Connection conn, String appCode, String clientCode) {

        if (StringUtil.safeIsBlank(appCode) || StringUtil.safeIsBlank(clientCode)) return Mono.just(Boolean.FALSE);

        return Mono.from(this.context(conn)
                        .query("DROP DATABASE IF EXISTS `" + databaseName(clientCode, appCode, true) + "`"))
                .thenReturn(Boolean.TRUE)
                .onErrorReturn(Boolean.FALSE)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MySQLAppDataService.dropDraftDatabase"));
    }

    /**
     * Refill the draft table from the live one.
     *
     * Columns are intersected rather than assumed equal. The two surfaces can be on
     * different shapes - that is the normal state while a draft edit is unpublished -
     * and {@code INSERT ... SELECT *} would fail on the mismatch or, worse, line the
     * wrong columns up.
     */
    @Override
    public Mono<Long> copyLiveToDraft(String clientCode, Connection conn, Storage storage, Boolean replace) {

        String liveDb = databaseName(clientCode, storage.getAppCode(), false);
        String draftDb = databaseName(clientCode, storage.getAppCode(), true);
        String table = storage.getUniqueName();

        DSLContext ctx = this.context(conn);

        return FlatMapUtil.flatMapMono(
                        () -> this.ensureTable(conn, clientCode, storage)
                                .contextWrite(Context.of(LogUtil.DRAFT_KEY, Boolean.TRUE)),
                        // Counted BEFORE anything is cleared. An empty source answers 0
                        // and changes nothing: clearing the draft table and then saying
                        // there was nothing to copy would be the worst of both.
                        // A live table that was never created has nothing to copy,
                        // and asking it for a count is an error rather than a zero.
                        draft -> MySQLTableInspector.tableExists(ctx, liveDb, table)
                                .flatMap(exists -> !exists
                                        ? Mono.just(0L)
                                        : Mono.from(ctx.resultQuery(
                                                        "SELECT COUNT(*) FROM `" + liveDb + "`.`" + table + "`"))
                                                .map(r -> r.get(0) instanceof Number n ? n.longValue() : 0L)
                                                .defaultIfEmpty(0L)),
                        (draft, total) -> total == 0
                                ? Mono.just(List.<String>of())
                                : this.sharedColumns(ctx, liveDb, draftDb, table),
                        (draft, total, columns) -> {
                            if (total == 0 || columns.isEmpty()) return Mono.just(0L);

                            String cols = columns.stream()
                                    .map(c -> "`" + c + "`")
                                    .collect(java.util.stream.Collectors.joining(", "));

                            Mono<Void> clear = BooleanUtil.safeValueOf(replace)
                                    ? Mono.from(ctx.query("DELETE FROM `" + draftDb + "`.`" + table + "`")).then()
                                    : Mono.empty();

                            return clear.then(Mono.from(ctx.query("INSERT INTO `" + draftDb + "`.`" + table + "` ("
                                            + cols + ") SELECT " + cols + " FROM `" + liveDb + "`.`" + table + "`")))
                                    .thenReturn(total);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MySQLAppDataService.copyLiveToDraft"));
    }

    private Mono<List<String>> sharedColumns(DSLContext ctx, String liveDb, String draftDb, String table) {
        return FlatMapUtil.flatMapMono(
                () -> MySQLTableInspector.columns(ctx, liveDb, table),
                liveCols -> MySQLTableInspector.columns(ctx, draftDb, table),
                (liveCols, draftCols) -> {
                    Set<String> inDraft = draftCols.stream()
                            .map(MySQLColumn::name)
                            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

                    List<String> shared = new java.util.ArrayList<>();
                    shared.add(MySQLTypeMapper.ID_COLUMN);
                    liveCols.stream()
                            .map(MySQLColumn::name)
                            .filter(inDraft::contains)
                            .forEach(shared::add);

                    return Mono.just(shared);
                });
    }

    private <T> Mono<T> invalidAggregation(String reason) {
        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                CoreMessageResourceService.INVALID_AGGREGATION,
                reason);
    }

    private <T> Mono<T> unsupportedFilter(UnsupportedFilterException e) {

        if (e.isMalformed())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    CoreMessageResourceService.UNSUPPORTED_CONDITION,
                    e.getDetail());

        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.NOT_IMPLEMENTED, msg),
                CoreMessageResourceService.UNSUPPORTED_ON_BACKEND,
                e.getDetail(),
                BACKEND);
    }

}
