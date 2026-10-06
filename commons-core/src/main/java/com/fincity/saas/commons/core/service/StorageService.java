package com.fincity.saas.commons.core.service;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.reactive.ReactiveSchemaUtil;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.reactive.ReactiveHybridRepository;
import com.fincity.nocode.kirun.engine.repository.reactive.KIRunReactiveSchemaRepository;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.enums.StorageTriggerType;
import com.fincity.saas.commons.core.kirun.repository.CoreSchemaRepository;
import com.fincity.saas.commons.core.model.StorageRelation;
import com.fincity.saas.commons.core.repository.StorageRepository;
import com.fincity.saas.commons.core.service.connection.appdata.AppDataService;
import com.fincity.saas.commons.core.service.connection.appdata.IAppDataService;
import com.fincity.saas.commons.core.service.connection.appdata.SchemaRefResolver;
import com.fincity.saas.commons.core.service.connection.appdata.StorageColumnDefinitionValidator;
import com.fincity.saas.commons.core.service.connection.appdata.StorageFieldNames;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.mongo.util.SchemaRefs;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.commons.util.UniqueUtil;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class StorageService extends AbstractOverridableDataService<Storage, StorageRepository> {
    /**
     * Core objects are drafted, exactly like the ui ones.
     *
     * A page's draft is worth little on its own: it usually depends on a storage
     * whose schema changed, a connection, a template or an event that changed with
     * it. Without this the draft surface could preview the page and nothing it
     * talks to, so a change that spanned both had to be published to be seen at all,
     * which is what the draft surface exists to avoid.
     *
     * The five draft routes are inherited from AbstractOverridableDataController and
     * were answering 405 until this flag flipped; the core DraftService bean already
     * existed and was unused.
     */
    @Override
    protected boolean isDraftable() {
        return true;
    }


    /**
     * Cache names are per storage, not global, and that is the whole point.
     *
     * A schema cache keyed by document id can only be evicted for the document that
     * was edited. Editing a BASE storage changes the merged schema of every
     * descendant, whose own documents were not touched and whose ids therefore say
     * nothing has changed - so each descendant keeps serving the schema it had before.
     * The symptom is a child client's table never being rebuilt, or being validated
     * against a definition that no longer exists.
     *
     * The uniqueName is inherited down the chain, so one evictAll on it clears every
     * client's entry at once. Same pattern as the index-creation cache.
     */
    public static final String CACHE_SUFFIX_STORAGE_SCHEMA = "_storage_schema";

    public static final String CACHE_SUFFIX_STORAGE_SCHEMA_RESOLVED = "_storage_schema_resolved";

    /**
     * Which storages point AT a given one, per app.
     *
     * One entry for the whole app rather than one per storage, because it is built
     * by reading every storage document of the app and splitting that work by
     * target would mean doing it once per target.
     */
    public static final String CACHE_NAME_STORAGE_REFERENCES = "storageReferences";

    private static final Logger logger = LoggerFactory.getLogger(StorageService.class);

    private final CoreMessageResourceService coreMsgService;
    private final CoreSchemaService coreSchemaService;
    private final CoreFunctionService coreFunctionService;

    private final Gson gson;

    /**
     * Lazy on purpose: MongoAppDataService takes StorageService in its constructor,
     * so a normal injection here is a cycle. Only used on delete.
     */
    @Autowired
    @Lazy
    private AppDataService appDataService;

    protected StorageService(
            CoreMessageResourceService coreMsgService,
            CoreSchemaService coreSchemaService,
            CoreFunctionService coreFunctionService,
            Gson gson) {
        super(Storage.class);
        this.gson = gson;
        this.coreMsgService = coreMsgService;
        this.coreSchemaService = coreSchemaService;
        this.coreFunctionService = coreFunctionService;
    }

    @Override
    public Mono<Storage> create(Storage entity) {
        entity.setUniqueName(UniqueUtil.uniqueName(32, entity.getAppCode(), entity.getClientCode(), entity.getName()));

        Mono<Storage> creationMono;

        if (entity.getBaseClientCode() != null) {
            creationMono = FlatMapUtil.flatMapMono(
                            SecurityContextUtil::getUsersContextAuthentication,
                            ca -> this.getMergedSources(entity),
                            (ca, merged) -> {
                                if (BooleanUtil.safeValueOf(merged.getIsAppLevel()))
                                    return this.securityService
                                            .hasWriteAccess(entity.getAppCode(), entity.getClientCode())
                                            .flatMap(access -> {
                                                if (!BooleanUtil.safeValueOf(access))
                                                    return coreMsgService.throwMessage(
                                                            msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                                            CoreMessageResourceService.STORAGE_IS_APP_LEVEL);

                                                return this.localCreate(entity);
                                            });

                                return this.localCreate(entity);
                            })
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "StorageService.create"));
        } else creationMono = this.localCreate(entity);

        return creationMono.flatMap(e -> this.cacheService
                .evictAll(e.getUniqueName() + IAppDataService.CACHE_SUFFIX_FOR_INDEX_CREATION)
                .map(x -> e));
    }

    private Mono<Storage> localCreate(Storage entity) {
        return FlatMapUtil.flatMapMono(() -> this.validate(entity), super::create)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "StorageService.localCreate"));
    }

    public Mono<Storage> validate(Storage storage) { // NOSONAR
        // Cannot split for just one point increase in complexity.

        // uniqueName IS the physical Mongo collection name, on both the live and the
        // draft surface. It is generated once at create and never regenerated, and
        // updatableEntity's whitelist deliberately excludes it. But a null one is
        // reachable: Storage.subApplyOverride materialises a base's uniqueName into a
        // derived document when the derived value is null, which would silently point
        // two clients at one collection. Saving without one is never correct, so fail
        // loudly here rather than orphan or share data.
        if (StringUtil.safeIsBlank(storage.getUniqueName()))
            return this.messageResourceService.throwMessage(
                    msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                    AbstractMongoMessageResourceService.NAME_MISSING, "uniqueName");

        return FlatMapUtil.flatMapMono(
                        () -> this.coreSchemaService.getSchemaRepository(storage.getAppCode(), storage.getClientCode()),
                        appSchemaRepo -> {
                            Schema schema;
                            try {
                                schema = gson.fromJson(gson.toJsonTree(storage.getSchema()), Schema.class);
                            } catch (RuntimeException e) {
                                // The type adapter throws on a type it does not
                                // know, and nothing caught it, so an author who
                                // mistyped one got a 500 carrying an enum constant
                                // name and no clue which field it came from. The
                                // adapter's own message names the value, which is
                                // the useful half, so it is passed along.
                                return this.messageResourceService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        CoreMessageResourceService.INVALID_STORAGE_SCHEMA,
                                        e.getMessage() == null ? e.toString() : e.getMessage());
                            }

                            if (schema.getRef() == null) return Mono.just(schema);

                            return ReactiveSchemaUtil.getSchemaFromRef(
                                            schema,
                                            new ReactiveHybridRepository<>(
                                                    new KIRunReactiveSchemaRepository(),
                                                    new CoreSchemaRepository(),
                                                    appSchemaRepo),
                                            schema.getRef())
                                    .defaultIfEmpty(schema);
                        },
                        (appSchemaRepo, schema) -> {
                            // Only when the document actually says something about
                            // the schema. A derived storage stores its DIFFERENCE
                            // from the base, so a client overriding an index, an
                            // auth or a column definition has no schema of its own
                            // and therefore no type - and reading getType()
                            // unconditionally turned every such save into a 500 with
                            // nothing to say why. That is the ordinary shape of an
                            // override, not an edge case.
                            //
                            // The check still applies wherever it means something:
                            // a document that does declare a schema must declare an
                            // object.
                            if (schema.getType() != null
                                    && (schema.getType().getAllowedSchemaTypes().size() != 1
                                            || !schema.getType()
                                                    .getAllowedSchemaTypes()
                                                    .contains(SchemaType.OBJECT)))
                                return this.messageResourceService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        CoreMessageResourceService.STORAGE_SCHEMA_ALWAYS_OBJECT);

                            // Before everything else, because a field name is the
                            // one part of a storage definition that is concatenated
                            // into DDL rather than bound. See StorageFieldNames.
                            List<String> nameProblems = StorageFieldNames.problems(
                                    schema,
                                    storage.getRelations() == null
                                            ? Set.of()
                                            : storage.getRelations().keySet());

                            if (!nameProblems.isEmpty())
                                return this.messageResourceService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        CoreMessageResourceService.INVALID_COLUMN_DEFINITION,
                                        String.join("; ", nameProblems));

                            // Before the relation checks, and before the early
                            // return below: a storage with no relations can still
                            // carry column definitions, and a bad one would reach
                            // every tenant of the app before anyone noticed.
                            List<String> columnProblems = StorageColumnDefinitionValidator.problems(
                                    storage.getColumnDefinitions(),
                                    schema,
                                    storage.getRelations() == null
                                            ? Set.of()
                                            : storage.getRelations().keySet());

                            if (!columnProblems.isEmpty())
                                return this.messageResourceService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        CoreMessageResourceService.INVALID_COLUMN_DEFINITION,
                                        String.join("; ", columnProblems));

                            if (storage.getRelations() == null
                                    || storage.getRelations().isEmpty()) return Mono.just(storage);

                            // Null for a derived storage, which stores only its
                            // difference from the base and so declares no schema of
                            // its own. Same cause as the type check above: the
                            // relation collision test is about the schema this
                            // document declares, and a document declaring none
                            // cannot collide with it. Reading it straight made
                            // every save of a derived storage WITH RELATIONS a 500.
                            Map<String, Schema> declared =
                                    schema.getProperties() == null ? Map.of() : schema.getProperties();

                            for (Entry<String, StorageRelation> relationEntry :
                                    storage.getRelations().entrySet()) {
                                if (declared.containsKey(relationEntry.getKey()))
                                    return this.messageResourceService.throwMessage(
                                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                            CoreMessageResourceService.STORAGE_SCHEMA_FIELD_ALREADY_EXISTS,
                                            relationEntry.getKey());

                                if (relationEntry.getValue().getUniqueRelationId() != null) continue;

                                relationEntry
                                        .getValue()
                                        .setUniqueRelationId(UniqueUtil.uniqueName(
                                                32,
                                                storage.getAppCode(),
                                                storage.getClientCode(),
                                                storage.getName(),
                                                relationEntry.getKey()));
                            }

                            return Flux.fromIterable(storage.getRelations().values())
                                    .flatMap(relation -> this.read(
                                                    relation.getStorageName(),
                                                    storage.getAppCode(),
                                                    storage.getClientCode())
                                            .switchIfEmpty(this.messageResourceService.throwMessage(
                                                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                                    CoreMessageResourceService.NO_STORAGE_FOUND_WITH_NAME,
                                                    relation.getStorageName())))
                                    .collectList()
                                    .map(e -> storage);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "StorageService.validate"));
    }

    @Override
    public Mono<Storage> update(Storage entity) {
        return FlatMapUtil.flatMapMono(
                        () -> this.validate(entity),
                        storage -> this.read(entity.getId()),
                        (storage, existing) -> {
                            if (existing.getTriggers() == null
                                    || existing.getTriggers().get(StorageTriggerType.BEFORE_UPDATE_STORAGE) == null
                                    || existing.getTriggers()
                                            .get(StorageTriggerType.BEFORE_UPDATE_STORAGE)
                                            .isEmpty()) return Mono.just(true);

                            Map<String, JsonElement> args =
                                    Map.of("existing", gson.toJsonTree(existing), "creating", gson.toJsonTree(storage));

                            return Flux.fromIterable(
                                            existing.getTriggers().get((StorageTriggerType.BEFORE_UPDATE_STORAGE)))
                                    .flatMap(trigger -> this.coreFunctionService.execute(
                                            trigger.substring(0, trigger.lastIndexOf('.')),
                                            trigger.substring(trigger.lastIndexOf('.') + 1),
                                            entity.getAppCode(),
                                            entity.getClientCode(),
                                            args,
                                            null))
                                    .collectList()
                                    .map(e -> true);
                        },
                        (storage, existing, beforeExecuted) -> super.update(storage),
                        (storage, existing, beforeExecuted, created) -> {
                            if (existing.getTriggers() == null
                                    || existing.getTriggers().get(StorageTriggerType.AFTER_UPDATE_STORAGE) == null
                                    || existing.getTriggers()
                                            .get(StorageTriggerType.AFTER_UPDATE_STORAGE)
                                            .isEmpty()) return Mono.just(true);

                            Map<String, JsonElement> args =
                                    Map.of("existing", gson.toJsonTree(existing), "created", gson.toJsonTree(created));

                            return Flux.fromIterable(
                                            existing.getTriggers().get((StorageTriggerType.AFTER_UPDATE_STORAGE)))
                                    .flatMap(trigger -> this.coreFunctionService.execute(
                                            trigger.substring(0, trigger.lastIndexOf('.')),
                                            trigger.substring(trigger.lastIndexOf('.') + 1),
                                            entity.getAppCode(),
                                            entity.getClientCode(),
                                            args,
                                            null))
                                    .collectList()
                                    .map(e -> true);
                        },
                        // The schema caches and the table rebuild are not here: they
                        // hang off evictRecursively, which every mutating path already
                        // calls. Doing it per method is how the next one gets missed.
                        (storage, existing, beforeExecuted, created, afterExecuted) -> this.cacheService
                                .evictAll(created.getUniqueName() + IAppDataService.CACHE_SUFFIX_FOR_INDEX_CREATION)
                                .flatMap(x -> this.cacheService.evictAll(
                                        created.getUniqueName() + IAppDataService.CACHE_SUFFIX_FOR_TABLE_CREATION))
                                .map(e -> created)
                                .map(Storage.class::cast))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "StorageService.update"));
    }

    @Override
    public Mono<Boolean> delete(String id) {
        return FlatMapUtil.flatMapMono(
                        () -> this.read(id),
                        storage -> {
                            if (storage.getTriggers() == null
                                    || storage.getTriggers().get(StorageTriggerType.BEFORE_DELETE_STORAGE) == null
                                    || storage.getTriggers()
                                            .get(StorageTriggerType.BEFORE_DELETE_STORAGE)
                                            .isEmpty()) return Mono.just(true);

                            Map<String, JsonElement> args = Map.of("storage", gson.toJsonTree(storage));

                            return Flux.fromIterable(
                                            storage.getTriggers().get((StorageTriggerType.BEFORE_DELETE_STORAGE)))
                                    .flatMap(trigger -> this.coreFunctionService.execute(
                                            trigger.substring(0, trigger.lastIndexOf('.')),
                                            trigger.substring(trigger.lastIndexOf('.') + 1),
                                            storage.getAppCode(),
                                            storage.getClientCode(),
                                            args,
                                            null))
                                    .collectList()
                                    .map(e -> true);
                        },
                        (storage, beforeExecuted) -> super.delete(id),
                        (storage, beforeExecuted, deleted) -> {
                            if (storage.getTriggers() == null
                                    || storage.getTriggers().get(StorageTriggerType.AFTER_DELETE_STORAGE) == null
                                    || storage.getTriggers()
                                            .get(StorageTriggerType.AFTER_DELETE_STORAGE)
                                            .isEmpty()) return Mono.just(true);

                            Map<String, JsonElement> args = Map.of("storage", gson.toJsonTree(storage));

                            return Flux.fromIterable(
                                            storage.getTriggers().get((StorageTriggerType.AFTER_DELETE_STORAGE)))
                                    .flatMap(trigger -> this.coreFunctionService.execute(
                                            trigger.substring(0, trigger.lastIndexOf('.')),
                                            trigger.substring(trigger.lastIndexOf('.') + 1),
                                            storage.getAppCode(),
                                            storage.getClientCode(),
                                            args,
                                            null))
                                    .collectList()
                                    .map(e -> true);
                        },
                        (storage, beforeExecuted, deleted, afterExecuted) -> this
                                .evictSchemaCaches(storage.getUniqueName())
                                .map(e -> deleted),
                        // Draft rows are sandbox data and mean nothing once the
                        // definition is gone, so they go with it. The LIVE collection
                        // is deliberately left behind: not dropping it on a definition
                        // delete is long-standing behaviour, and changing that would
                        // silently destroy customer data.
                        (storage, beforeExecuted, deleted, afterExecuted, evicted) -> this.appDataService
                                .dropDraftStorageData(storage.getAppCode(), storage.getClientCode(), storage)
                                // Best effort: the definition is already gone, so failing
                                // the whole delete here would leave a worse state than an
                                // orphaned sandbox collection. Logged so it is findable.
                                .onErrorResume(err -> {
                                    logger.error("Could not drop the draft collection for storage {} in {}/{}",
                                            storage.getUniqueName(), storage.getClientCode(), storage.getAppCode(),
                                            err);
                                    return Mono.just(Boolean.FALSE);
                                })
                                .map(dropped -> deleted))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "StorageService.delete"));
    }

    @Override
    protected Mono<Storage> updatableEntity(Storage entity) {
        return FlatMapUtil.flatMapMono(() -> this.read(entity.getId()), existing -> {
                    if (existing.getVersion() != entity.getVersion())
                        return this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                                AbstractMongoMessageResourceService.VERSION_MISMATCH);

                    existing.setSchema(entity.getSchema())
                            .setIsAudited(entity.getIsAudited())
                            .setIsVersioned(entity.getIsVersioned())
                            .setIsAppLevel(entity.getIsAppLevel())
                            .setCreateAuth(entity.getCreateAuth())
                            .setReadAuth(entity.getReadAuth())
                            .setUpdateAuth(entity.getUpdateAuth())
                            .setDeleteAuth(entity.getDeleteAuth())
                            .setGenerateEvents(entity.getGenerateEvents())
                            .setTriggers(entity.getTriggers())
                            .setRelations(entity.getRelations())
                            .setFieldDefinitionMap(entity.getFieldDefinitionMap())
                            .setColumnDefinitions(entity.getColumnDefinitions())
                            .setVersionRetentionDays(entity.getVersionRetentionDays())
                            .setVersionRetentionCount(entity.getVersionRetentionCount())
                            .setOnlyThruKIRun(entity.getOnlyThruKIRun())
                            .setIndexes(entity.getIndexes())
                            .setTextIndexFields(entity.getTextIndexFields());

                    existing.setVersion(existing.getVersion() + 1);

                    return this.validate(existing);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "StorageService.updatableEntity"));
    }

    /**
     * The other storages this one's relations point at.
     * <p>
     * These are plain, non namespaced storage names resolved within the same
     * app and client, and {@link #validate(Storage)} reads every one of them
     * before the save goes through, so a transport has to get them in first.
     * <p>
     * The map key is the field name; {@code StorageRelation.fieldName} is
     * written and diffed but never read anywhere, so it is not a source here.
     */
    @Override
    public Collection<String> getTransportDependencies(Storage entity) {

        if (entity == null || entity.getRelations() == null || entity.getRelations().isEmpty()) return List.of();

        return entity.getRelations().values().stream()
                .map(StorageRelation::getStorageName)
                .filter(name -> !StringUtil.safeIsBlank(name))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * A copy without relations, so storages that point at each other can be
     * saved in two passes.
     * <p>
     * Relations are the only thing making one storage depend on another, and
     * they are a plain link rather than part of the storage's shape, so
     * dropping them for a first pass and putting them back on a second leaves
     * exactly the state a single pass would have produced.
     */
    @Override
    public Storage stripTransportDependencies(Storage entity) {

        if (entity == null || entity.getRelations() == null || entity.getRelations().isEmpty()) return null;

        return new Storage(entity).setRelations(null);
    }

    /**
     * The surface is part of the key, and it has to be.
     *
     * A draft document carries the SAME id as the live one - that is what makes
     * reading either of them by id work - so a cache keyed on the id alone serves
     * whichever surface populated it first. Two things then go wrong at once: a write
     * to the draft surface is validated against the published definition, and on
     * MySQL the draft table is built from it too, so a draft edit appears to do
     * nothing at all.
     */
    public Mono<Schema> getSchema(Storage storage) {
        return LogUtil.isDraft()
                .flatMap(draft -> cacheService.cacheEmptyValueOrGet(
                        storage.getUniqueName() + CACHE_SUFFIX_STORAGE_SCHEMA,
                        () -> Mono.just(gson.fromJson(gson.toJsonTree(storage.getSchema()), Schema.class)),
                        storage.getId(),
                        draft));
    }

    /**
     * Every path that changes this storage's live definition rebuilds its tables.
     *
     * Hooked on the eviction rather than on each method for the same reason as
     * {@code CoreSchemaService}: create, update, updateBlueprint and publish all end
     * here because all four have to drop the same caches, and wiring the rebuild to
     * the ones that exist today is how the next one gets missed.
     *
     * On Mongo this is a no-op, because a collection has no declared shape. On MySQL
     * the table has to be altered to match, and until it is, every write of a newly
     * typed field fails against a definition that says it should work.
     */
    @Override
    protected Mono<Boolean> evictRecursively(String appCode, String clientCode, String name) {
        return super.evictRecursively(appCode, clientCode, name)
                .flatMap(evicted -> this.cacheService
                        .evictAll(CACHE_NAME_STORAGE_REFERENCES)
                        .thenReturn(evicted))
                .flatMap(evicted -> this.rebuildTables(appCode, clientCode, name).thenReturn(evicted));
    }

    /** A draft save changes the draft definition, so the draft tables follow it. */
    @Override
    protected Mono<Boolean> evictDraft(String appCode, String clientCode, String name) {
        return super.evictDraft(appCode, clientCode, name)
                .flatMap(evicted -> this.cacheService
                        .evictAll(CACHE_NAME_STORAGE_REFERENCES)
                        .thenReturn(evicted))
                .flatMap(evicted -> this.rebuildTables(appCode, clientCode, name).thenReturn(evicted));
    }

    /**
     * The storages whose relations point at this one.
     *
     * The reverse of {@code storage.relations}, and the direction a delete
     * constraint is actually asked about: deleting a category has to know about
     * the blogs that reference it, and the blog is the only side that says so.
     *
     * Every client document is scanned, not only the base, for the same reason
     * {@link #storagesUsing} does it: a relation introduced by an override lives
     * in that document alone. The result is therefore a superset for any one
     * client, and the caller resolves each name as that client before acting on
     * it, so a relation a client does not have is dropped there rather than here.
     */
    public Mono<List<String>> storagesReferencing(String appCode, String storageName) {

        if (StringUtil.safeIsBlank(appCode) || StringUtil.safeIsBlank(storageName)) return Mono.just(List.of());

        return this.cacheService
                .cacheValueOrGet(CACHE_NAME_STORAGE_REFERENCES, () -> this.referenceIndex(appCode), appCode)
                .map(index -> index.getOrDefault(storageName, List.of()));
    }

    private Mono<Map<String, List<String>>> referenceIndex(String appCode) {

        return this.mongoTemplate
                .find(
                        new org.springframework.data.mongodb.core.query.Query(
                                org.springframework.data.mongodb.core.query.Criteria.where("appCode")
                                        .is(appCode)),
                        Storage.class,
                        this.getCollectionName())
                .filter(s -> s.getRelations() != null && !s.getRelations().isEmpty())
                .collectList()
                .map(list -> {
                    Map<String, List<String>> index = new java.util.LinkedHashMap<>();
                    for (Storage s : list)
                        for (StorageRelation relation : s.getRelations().values()) {
                            if (relation == null || StringUtil.safeIsBlank(relation.getStorageName())) continue;
                            List<String> names =
                                    index.computeIfAbsent(relation.getStorageName(), k -> new ArrayList<>());
                            if (!names.contains(s.getName())) names.add(s.getName());
                        }
                    return index;
                });
    }

    private Mono<Boolean> rebuildTables(String appCode, String clientCode, String name) {

        return this.readInternal(name, appCode, clientCode)
                .map(ObjectWithUniqueID::getObject)
                .flatMap(storage -> this.evictSchemaCaches(storage.getUniqueName())
                        .flatMap(x -> this.appDataService.reconcileStorageDdl(appCode, storage)))
                .thenReturn(Boolean.TRUE)
                // A storage that no longer resolves has nothing to rebuild, and a save
                // that worked must not be reported as failed because the rebuild after
                // it did not.
                .onErrorResume(e -> {
                    logger.error("Could not rebuild tables for storage {} in {}", name, appCode, e);
                    return Mono.just(Boolean.TRUE);
                })
                .defaultIfEmpty(Boolean.TRUE);
    }

    /**
     * Clear both schema caches for every client of this storage.
     *
     * One call rather than one per document, because the set of descendants is not
     * known here and looking it up would be a Mongo query on a path that runs on
     * every save.
     */
    private Mono<Boolean> evictSchemaCaches(String uniqueName) {
        return this.cacheService
                .evictAll(uniqueName + CACHE_SUFFIX_STORAGE_SCHEMA)
                .flatMap(x -> this.cacheService.evictAll(uniqueName + CACHE_SUFFIX_STORAGE_SCHEMA_RESOLVED));
    }

    /**
     * One tenant's merged definition, resolved as that tenant rather than as whoever
     * is logged in.
     *
     * {@link #read(String, String, String)} cannot be used for this. It takes the
     * urlClientCode from the ambient security context and resolves the inheritance
     * chain with it, which is right for a user reading a document and wrong for a
     * fan-out: migrating seventy tenants would resolve all seventy against the chain
     * of whichever client happened to trigger the publish, and quietly give some of
     * them a table shape belonging to somebody else.
     *
     * It also skips the access check on purpose. This is the system reconciling
     * tables that already exist, not one client reading another client's definition.
     */
    public Mono<Storage> readForTenant(String name, String appCode, String clientCode) {
        return this.readInternal(name, appCode, clientCode).map(ObjectWithUniqueID::getObject);
    }

    /**
     * A schema document changed; rebuild whatever storages were standing on it.
     *
     * The case this exists for: a storage is written as {@code {"ref": "App.Order"}},
     * {@code App.Order} gains a field, and nothing about the storage has been touched.
     * Its cached resolved schema is stale and its MySQL table is a column short, and
     * neither notices, because every cache and every version number in the chain is
     * keyed on the storage. The first write to the new field is where it surfaces.
     *
     * Deliberately broad. A storage whose shape has not actually changed costs one
     * information_schema query per tenant and produces an empty plan; one that is
     * missed stays wrong until somebody edits it for an unrelated reason. The two
     * mistakes are not the same size.
     *
     * Never fails the caller. The schema really was saved, and reporting that as a
     * failure because one tenant out of seventy could not be migrated would be a lie
     * about what happened.
     */
    public Mono<List<String>> reconcileForSchemaChange(String appCode, String clientCode, String schemaName) {

        return this.coreSchemaService
                .referencingClosure(appCode, schemaName)
                .flatMap(closure -> this.storagesUsing(appCode, closure))
                .flatMapMany(Flux::fromIterable)
                .concatMap(storage -> this.evictSchemaCaches(storage.getUniqueName())
                        .flatMap(x -> this.appDataService.reconcileStorageDdl(appCode, storage))
                        .thenReturn(storage.getName()))
                .distinct()
                .collectList()
                .onErrorResume(e -> {
                    logger.error("Could not reconcile storages for schema {} in {}", schemaName, appCode, e);
                    return Mono.just(List.of());
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "StorageService.reconcileForSchemaChange"));
    }

    /**
     * Storages whose definition mentions any of these schemas.
     *
     * Every client's document is scanned, not only the base. A derived storage holds
     * just its difference from its base, so a reference introduced by an override
     * lives in that document alone and reading the base would not see it.
     */
    private Mono<List<Storage>> storagesUsing(String appCode, Set<String> schemaNames) {

        if (schemaNames.isEmpty()) return Mono.just(List.of());

        return this.mongoTemplate
                .find(new org.springframework.data.mongodb.core.query.Query(
                                org.springframework.data.mongodb.core.query.Criteria.where("appCode").is(appCode)),
                        Storage.class,
                        this.getCollectionName())
                .filter(storage -> SchemaRefs.collect(storage.getSchema()).stream().anyMatch(schemaNames::contains))
                .collectList();
    }

    /**
     * The same schema with its references followed, which is what a backend that
     * builds tables needs.
     *
     * {@link #getSchema} deliberately stays as it is: it returns the definition as
     * written, which is what every caller that round-trips or re-saves a storage
     * wants, and resolving refs there would quietly bake a referenced type into the
     * document the next time one of them saved.
     *
     * Cached separately from the unresolved one, keyed by surface for the same reason
     * {@link #getSchema} is, and evicted wherever that one is - including by
     * {@code CoreSchemaService}, so an edit to a REFERENCED schema reaches it too.
     */
    public Mono<Schema> getResolvedSchema(Storage storage) {
        return LogUtil.isDraft().flatMap(draft -> this.resolveSchema(storage, draft));
    }

    private Mono<Schema> resolveSchema(Storage storage, Boolean draft) {
        return cacheService.cacheEmptyValueOrGet(
                storage.getUniqueName() + CACHE_SUFFIX_STORAGE_SCHEMA_RESOLVED,
                () -> FlatMapUtil.flatMapMono(
                        () -> this.getSchema(storage),
                        schema -> this.coreSchemaService.getSchemaRepository(
                                storage.getAppCode(), storage.getClientCode()),
                        (schema, repo) -> SchemaRefResolver.resolve(
                                        schema,
                                        new ReactiveHybridRepository<>(
                                                new KIRunReactiveSchemaRepository(), new CoreSchemaRepository(),
                                                repo))
                                .map(resolved -> withRelations(storage, resolved))),
                storage.getId(),
                draft);
    }

    /**
     * Relation fields are part of a row, and a backend with real columns has to know
     * that.
     *
     * They are deliberately NOT schema properties: {@link #validate} refuses a
     * storage that declares one, because the relation map is where their target and
     * cardinality live. That works on Mongo, which stores whatever arrives. On MySQL
     * it meant no column was ever created for them, so every one of the 27
     * relation-bearing storages would fail its first write with "Unknown column", and
     * a join would reference a column that does not exist.
     *
     * Added to the RESOLVED copy only. The stored document keeps the platform's rule;
     * this is the shape the table is built from, which is a different question.
     */
    private static Schema withRelations(Storage storage, Schema resolved) {

        if (storage.getRelations() == null || storage.getRelations().isEmpty()) return resolved;

        Map<String, Schema> props = new java.util.LinkedHashMap<>(
                resolved.getProperties() == null ? Map.of() : resolved.getProperties());

        storage.getRelations().forEach((field, relation) -> {
            if (relation == null || props.containsKey(field)) return;

            props.put(
                    field,
                    relation.getRelationType() == com.fincity.saas.commons.core.enums.StorageRelationType.TO_MANY
                            // A list of ids, which is a JSON column and the reason a
                            // to-many relation cannot be joined through with an index.
                            ? Schema.ofArray(field, Schema.ofString(field)
                                    .setFormat(com.fincity.nocode.kirun.engine.json.schema.string.StringFormat.ID))
                            // One id, fixed width, so it joins against the primary key
                            // it points at without a conversion.
                            : Schema.ofString(field)
                                    .setFormat(com.fincity.nocode.kirun.engine.json.schema.string.StringFormat.ID));
        });

        return new Schema(resolved).setProperties(props);
    }
}
