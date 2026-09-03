package com.fincity.saas.commons.mongo.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.*;
import static com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService.*;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.model.dto.AbstractOverridableDTO;
import com.fincity.saas.commons.mongo.document.Draft;
import com.fincity.saas.commons.mongo.document.Version;
import com.fincity.saas.commons.mongo.model.ListResultObject;
import com.fincity.saas.commons.mongo.model.TransportObject;
import com.fincity.saas.commons.mongo.repository.IOverridableDataRepository;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.commons.util.UniqueUtil;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public abstract class AbstractOverridableDataService<D extends AbstractOverridableDTO<D>, R extends IOverridableDataRepository<D>>
        extends AbstractMongoUpdatableDataService<String, D, R> {

    private static final String ABSTRACT_OVERRIDABLE_SERVICE = "AbstractOverridableService (";
    private static final String READ_PAGE = "_READ_PAGE";
    private static final String CLIENT_CODE = "clientCode";
    private static final String APP_CODE = "appCode";
    private static final String NOT_OVERRIDABLE = "notOverridable";

    protected static final String CREATE = "CREATE";
    protected static final String UPDATE = "UPDATE";
    protected static final String READ = "READ";
    protected static final String DELETE = "DELETE";

    protected static final String CACHE_NAME = "Cache";

    /** Keeps the draft surface's cached reads out of the live surface's caches. */
    protected static final String DRAFT_CACHE_SUFFIX = "_DRAFT";

    /**
     * Every persisted field a {@link ListResultObject} can hold, and nothing else.
     *
     * readPageFilterLRO reads into ListResultObject, which only carries the
     * AbstractOverridableDTO header fields. Without a projection the driver decodes
     * each whole document — the entire `definition` / `styles` payload included —
     * into nested org.bson.Document trees before the converter throws all of it
     * away. The query is not paged at the database either (see below), so one list
     * call decoded an app's whole page corpus: on dev that is 117 documents and
     * 33.7 MiB of BSON for cxapp alone, and the ui service OOM'd repeatedly on
     * 2026-09-03 with a heap full of raw org.bson.Document trees.
     *
     * Listing what LRO keeps, rather than excluding the big fields, is deliberate:
     * the heavy field differs per subclass (Page.definition, Style.styles, ...) and
     * an exclude list would silently miss any field added later. `data` is absent
     * on purpose — it is never persisted, only populated from a separate read when
     * eager is set.
     */
    private static final String[] LRO_FIELDS = { "_id", "name", "message", CLIENT_CODE, "permission", APP_CODE,
            "baseClientCode", NOT_OVERRIDABLE, "description", "title", "published", "version", "createdAt",
            "createdBy", "updatedAt", "updatedBy" };

    /**
     * One page big enough to be every object of one type in one app. An index is
     * not paged: a tree that showed the first 1,000 storages and silently dropped
     * the rest would be worse than one that did not exist, so this is the point at
     * which the shape has to change rather than the number get bigger.
     */
    private static final Pageable INDEX_PAGE = PageRequest.of(0, 1000);

    @Autowired // NOSONAR
    protected CacheService cacheService;

    @Autowired // NOSONAR
    protected ObjectMapper objectMapper;

    @Autowired // NOSONAR
    protected AbstractMongoMessageResourceService messageResourceService;

    @Autowired // NOSONAR
    protected AbstractVersionService versionService;

    @Autowired(required = false) // NOSONAR
    protected AbstractDraftService draftService;

    @Autowired // NOSONAR
    protected FeignAuthenticationService securityService;

    @Autowired // NOSONAR
    protected com.fincity.saas.commons.mongo.repository.InheritanceService inheritanceService;

    private static final Set<String> READ_LRO_PARAMETERS_IGNORE = Set.of(CLIENT_CODE, APP_CODE, "size", "page", "sort",
            "eager");

    protected static final TypeReference<Map<String, Object>> TYPE_REFERENCE_MAP = new TypeReference<Map<String, Object>>() {
    };

    protected AbstractOverridableDataService(Class<D> pojoClass) {
        super(pojoClass);
    }

    @Override
    public Mono<D> create(D entity) {

        @SuppressWarnings("unchecked")
        Mono<D> crtEnt = FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> (entity.getClientCode() == null) ? Mono.just((D) entity.setClientCode(ca.getClientCode()))
                        : Mono.just(entity),

                (ca, ent) -> this.checkIfExists(ent),

                (ca, ent, cent) -> this.accessCheck(ca, CREATE, ent.getAppCode(), ent.getClientCode(), true),

                (ca, ent, cent, hasSecurity) -> BooleanUtil.safeValueOf(hasSecurity) ? Mono.just(cent) : Mono.empty())
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        ABSTRACT_OVERRIDABLE_SERVICE + this.getObjectName() + "Service).create (accessCheck)"))
                .switchIfEmpty(messageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg), FORBIDDEN_CREATE,
                        this.getObjectName()));

        return FlatMapUtil.flatMapMonoWithNull(

                () -> crtEnt,

                this::getMergedSources,

                this::extractOverride,

                (cEntity, merged, overridden) -> super.create(overridden),

                (cEntity, merged, overridden,
                        created) -> isVersionable()
                                ? versionService.create(new Version().setClientCode(cEntity.getClientCode())
                                        .setObjectName(entity.getName())
                                        .setObjectAppCode(entity.getAppCode())
                                        .setObjectType(this.getObjectName()
                                                .toUpperCase())
                                        .setVersionNumber(created.getVersion())
                                        .setMessage(entity.getMessage())
                                        .setObject(this.objectMapper.convertValue(entity, TYPE_REFERENCE_MAP)))
                                : Mono.empty(),

                (cEntity, merged, overridden, created, version) -> this.read(created.getId()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        ABSTRACT_OVERRIDABLE_SERVICE + this.getObjectName() + "Service).create"))
                .flatMap(this::evictRecursively)
                .switchIfEmpty(messageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg), FORBIDDEN_CREATE,
                        this.getObjectName()));
    }

    protected Mono<Boolean> accessCheck(ContextAuthentication ca, String method, String appCode, String clientCode, // NOSONAR
            boolean checkAppWriteAccess) {

        // Just two complexity points is not a reason to break this function

        if (StringUtil.safeIsBlank(clientCode) || StringUtil.safeIsBlank(appCode))
            return Mono.just(false);

        return FlatMapUtil.flatMapMono(
                () -> SecurityContextUtil.hasAuthority("Authorities." + this.getAccessCheckName() + "_" + method,
                        ca.getAuthorities()) ? Mono.just(true) : Mono.empty(),

                access -> this.securityService.getAppExplicitInfoByCode(appCode),

                (access, explicitApp) -> {
                    if (ca.getClientCode()
                            .equals(clientCode))
                        return Mono.just(true);

                    if ("EXPLICIT".equals(explicitApp.getAppAccessType())) {

                        return Mono.just(clientCode.equals(explicitApp.getClientCode())
                                || clientCode.equals(explicitApp.getExplicitOwnerClientCode()));
                    }

                    if (checkAppWriteAccess)
                        return this.securityService.doesClientManageClientCode(ca.getClientCode(), clientCode);
                    else
                        return this.inheritanceService
                                .order(appCode, ca.getUrlClientCode(), ca.getClientCode())
                                .map(e -> e.contains(ca.getClientCode()) && e.contains(clientCode));
                },

                (access, explicitApp, managed) -> {

                    if (!BooleanUtil.safeValueOf(managed))
                        return Mono.empty();

                    return checkAppWriteAccess
                            ? this.securityService.hasWriteAccess(appCode, ca.getClientCode())
                            : this.securityService.hasReadAccess(appCode, ca.getClientCode());
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        ABSTRACT_OVERRIDABLE_SERVICE + this.getObjectName() + "Service).accessCheck"))
                .defaultIfEmpty(false);
    }

    public String getAccessCheckName() {
        return this.getObjectName();
    }

    public String getObjectName() {
        return this.pojoClass.getSimpleName();
    }

    private Mono<D> checkIfExists(D cca) {

        return this.mongoTemplate.count(new Query(new Criteria().andOperator(

                Criteria.where("name")
                        .is(cca.getName()),
                Criteria.where(APP_CODE)
                        .is(cca.getAppCode()),
                Criteria.where(CLIENT_CODE)
                        .is(cca.getClientCode())

        )), this.pojoClass)
                .flatMap(c -> c > 0
                        ? messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.CONFLICT,
                                msg), AbstractMongoMessageResourceService.ALREADY_EXISTS, this.getObjectName(),
                                cca.getName())
                        : Mono.just(cca));
    }

    @Override
    public Mono<D> read(String id) {

        return FlatMapUtil.flatMapMonoWithNull(

                () -> super.read(id),

                entity -> SecurityContextUtil.getUsersContextAuthentication(),

                (entity, ca) -> this.accessCheck(ca, READ, entity == null ? null : entity.getAppCode(),
                        entity == null ? null : entity.getClientCode(), false),

                (entity, ca, hasAccess) -> BooleanUtil.safeValueOf(hasAccess) ? this.getMergedSources(entity)
                        : Mono.empty(),

                (entity, ca, hasAccess, merged) -> BooleanUtil.safeValueOf(hasAccess)
                        ? this.applyOverride(entity, merged)
                        : Mono.empty())
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        ABSTRACT_OVERRIDABLE_SERVICE + this.getObjectName() + "Service).read"))
                .switchIfEmpty(
                        this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND,
                                msg), AbstractMongoMessageResourceService.OBJECT_NOT_FOUND, this.getObjectName(), id));
    }

    public Mono<D> readInternal(String id) {

        return flatMapMonoWithNull(

                () -> super.read(id),

                this::getMergedSources,

                this::applyOverride)
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        ABSTRACT_OVERRIDABLE_SERVICE + this.getObjectName() + "Service).readInternal (id)"));
    }

    @Override
    public Mono<D> update(D entity) {

        Mono<D> crtEnt = flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.accessCheck(ca, UPDATE, entity == null ? null : entity.getAppCode(),
                        entity == null ? null : entity.getClientCode(), true),

                (ca, hasAccess) -> BooleanUtil.safeValueOf(hasAccess) ? Mono.just(entity) : Mono.empty())
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        ABSTRACT_OVERRIDABLE_SERVICE + this.getObjectName() + "Service).update"));

        return crtEnt.flatMap(e -> flatMapMonoWithNull(

                () -> this.getMergedSources(e),

                merged -> this.extractOverride(e, merged),

                (merged, overridden) -> super.update(overridden),

                (merged, overridden,
                        created) -> isVersionable()
                                ? versionService.create(new Version().setClientCode(e.getClientCode())
                                        .setObjectName(e.getName())
                                        .setObjectAppCode(e.getAppCode())
                                        .setObjectType(this.getObjectName()
                                                .toUpperCase())
                                        .setVersionNumber(created.getVersion())
                                        .setMessage(e.getMessage())
                                        .setObject(this.objectMapper.convertValue(e, TYPE_REFERENCE_MAP)))
                                : Mono.empty(),

                (merged, overridden, created, version) -> this.read(created.getId()),

                (m, o, c, v, f) -> this.evictRecursively(f))
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        ABSTRACT_OVERRIDABLE_SERVICE + this.getObjectName() + "Service).update")));
    }

    // ── Draft and publish ──────────────────────────────────────────
    //
    // Writes say where they go: a caller asks for a draft explicitly, it is never
    // inferred from ambient request state. A page running on the draft surface
    // doing an ordinary SendData PUT must not silently divert into a draft it
    // never asked for.
    //
    // Reads are the other half and live on the runtime path, which switches on the
    // gateway-set flag only, never on anything a caller controls.

    /** Opt in per service. Off by default so commons-core objects are unaffected. */
    protected boolean isDraftable() {
        return false;
    }

    private <T> Mono<T> notDraftable() {
        return this.messageResourceService.throwMessage(
                msg -> new GenericException(HttpStatus.METHOD_NOT_ALLOWED, msg),
                AbstractMongoMessageResourceService.OBJECT_NOT_FOUND, "Draft", this.getObjectName());
    }

    /**
     * Store the entity as unpublished work without touching the live document.
     *
     * The content is stored exactly as sent, not as a post-extractOverride delta,
     * so publish can run the ordinary update pipeline over it instead of
     * reimplementing the diffing. No live DOCUMENT changes, which is what makes a
     * draft save incapable of leaking; the draft surface's own caches do have to be
     * cleared, and that is what evictDraft is for.
     */
    public Mono<Draft> saveDraft(D entity) {
        return this.saveDraft(entity, null);
    }

    /**
     * @param expectedDraftVersion the draft version the caller last saw, from the
     *                             `X-Draft-Version` response header on the draft
     *                             read. Null skips the check and keeps the old
     *                             last-write-wins behaviour, so existing callers are
     *                             unaffected until they opt in.
     */
    public Mono<Draft> saveDraft(D entity, Integer expectedDraftVersion) {

        if (!this.isDraftable())
            return this.notDraftable();

        // Load FIRST, then authorize the stored document, exactly as discardDraft
        // does below. Authorizing entity.getAppCode()/getClientCode() checked the
        // codes the caller put in the request body while the write was keyed on the
        // codes of whatever the id resolved to, and nothing compared the two: a
        // caller passed the check on an app they legitimately manage and wrote into
        // someone else's draft, destroying their unpublished work via the upsert.
        //
        // This does not restrict legitimate cross-tenant editing. accessCheck still
        // lets a managing client through via doesClientManageClientCode; the only
        // change is that the decision is now about the object being written.
        return FlatMapUtil.<ContextAuthentication, D, Boolean, Draft>flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> entity == null || entity.getId() == null ? Mono.empty() : this.repo.findById(entity.getId()),

                (ca, stored) -> this.accessCheck(ca, UPDATE, stored.getAppCode(), stored.getClientCode(), true),

                (ca, stored, hasAccess) -> BooleanUtil.safeValueOf(hasAccess)
                        ? this.draftVersionCheck(stored, expectedDraftVersion)
                                .flatMap(ok -> this.draftService.upsert(this.getObjectName().toUpperCase(),
                                        stored.getAppCode(), stored.getName(), stored.getClientCode(),
                                        stored.getId(),
                                        this.objectMapper.convertValue(entity, TYPE_REFERENCE_MAP),
                                        stored.getVersion(), entity.getMessage()))
                                .flatMap(saved -> this.evictDraft(stored.getAppCode(), stored.getClientCode(),
                                        stored.getName()).thenReturn(saved))
                        : Mono.empty())
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        ABSTRACT_OVERRIDABLE_SERVICE + this.getObjectName() + "Service).saveDraft"))
                .switchIfEmpty(messageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg), FORBIDDEN_PERMISSION,
                        this.getObjectName()));
    }

    /**
     * Create an object that exists but has never gone live.
     *
     * Creation itself is never drafted: this writes a real live document with a
     * real id, so everything that resolves an object by id keeps working, from the
     * component PATCH endpoints to version history to the builder tree. Only the
     * never-published marker distinguishes it, and only until first publish.
     *
     * The marker is set here rather than accepted from the body, since `published`
     * is read-only over the API.
     */
    public Mono<D> createUnpublished(D entity) {

        if (!this.isDraftable())
            return this.notDraftable();

        entity.setPublished(Boolean.FALSE);
        return this.create(entity);
    }

    /**
     * Refuse a save whose expected draft version has moved on.
     *
     * A draft row is keyed on (app, type, name, clientCode), so it belongs to a
     * CLIENT and two people editing the same object share it. Without this the
     * second save simply replaced the first's content through the upsert and
     * neither person was told.
     *
     * `baseVersion` cannot do this job. It is the LIVE document's version, and both
     * editors read the same live document, so both send the same number and the
     * check passes for both. Only the draft's own counter moves when someone else
     * saves.
     *
     * Absent expectation means no check, so every existing caller keeps working
     * until it starts round-tripping the version. 0 means "there was no draft when
     * I started", which is a real assertion and fails once one has appeared.
     */
    private Mono<Boolean> draftVersionCheck(D stored, Integer expected) {

        if (expected == null)
            return Mono.just(Boolean.TRUE);

        return this.draftService
                .find(this.getObjectName().toUpperCase(), stored.getAppCode(), stored.getName(),
                        stored.getClientCode())
                .map(Draft::getVersion)
                .defaultIfEmpty(0)
                .flatMap(current -> expected.intValue() == current ? Mono.just(Boolean.TRUE)
                        : this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                                AbstractMongoMessageResourceService.VERSION_MISMATCH));
    }

    /**
     * The object as the editor should see it: the draft when one exists, otherwise
     * the live document.
     */
    public Mono<D> readDraft(String id) {
        return this.readDraftWithVersion(id).map(Tuple2::getT1);
    }

    /**
     * The draft plus its own version, which the caller sends back on save so a
     * second editor cannot silently overwrite the first.
     *
     * Version 0 means there is no draft and this is the live document. Sending 0
     * back on a save is therefore also meaningful: it asserts "no draft existed
     * when I started", and the check fails if one has appeared since.
     */
    public Mono<Tuple2<D, Integer>> readDraftWithVersion(String id) {

        if (!this.isDraftable())
            return this.notDraftable();

        // read(id) first, so the draft path runs exactly the same accessCheck as a
        // live read. A ?draft=true parameter must never be a way around it.
        return this.read(id)
                .flatMap(live -> this.draftService.findByObjectId(id)
                        .map(draft -> Tuples.of(
                                this.objectMapper.convertValue(draft.getContent(), this.pojoClass),
                                draft.getVersion()))
                        .defaultIfEmpty(Tuples.of(live, 0)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        ABSTRACT_OVERRIDABLE_SERVICE + this.getObjectName() + "Service).readDraft"));
    }

    /**
     * Promote a draft to live.
     *
     * Deliberately routed through this.update(), not repo.save. Eviction in this
     * codebase is layered across per-service update() overrides: the ui base class
     * evicts applicationOUICache and the SSR caches, ApplicationService also clears
     * indexNewCache, the manifest and cacheProperties, Style/StyleTheme clear their
     * OUI caches and URIPathService clears URIPatternCache. A base-class publish
     * writing straight to the repository would skip every one of them, and
     * publishing an Application would appear to do nothing at all.
     *
     * Going through update() also reuses accessCheck, getMergedSources,
     * extractOverride, the Version snapshot and the optimistic-lock check, so a
     * publish whose base moved on underneath it is rejected rather than silently
     * overwriting.
     */
    public Mono<D> publish(String id, String message) {

        if (!this.isDraftable())
            return this.notDraftable();

        return FlatMapUtil.<Draft, D, D, D>flatMapMono(

                () -> this.draftService.findByObjectId(id)
                        .switchIfEmpty(this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                AbstractMongoMessageResourceService.OBJECT_NOT_FOUND, "Draft", id)),

                // An orphaned draft has to fail loudly. An empty Mono here made
                // publishAll drop the object from its own report: it claimed
                // attempted=0 with a draft still pending, so nothing told the caller
                // the object could not be shipped.
                draft -> this.repo.findById(id)
                        .switchIfEmpty(this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                AbstractMongoMessageResourceService.OBJECT_NOT_FOUND, this.getObjectName(), id)),

                (draft, stored) -> {
                    D entity = this.objectMapper.convertValue(draft.getContent(), this.pojoClass);
                    // setId is declared on AbstractDTO, so it returns that type and
                    // cannot join a chain of the overridable setters.
                    entity.setId(id);
                    // Identity comes from the STORED document, never from draft
                    // content, which is caller supplied. Otherwise update()'s own
                    // access check runs against codes the caller chose.
                    entity.setAppCode(stored.getAppCode())
                            .setClientCode(stored.getClientCode())
                            .setName(stored.getName())
                            .setBaseClientCode(stored.getBaseClientCode())
                            .setVersion(draft.getBaseVersion())
                            .setMessage(message == null ? draft.getMessage() : message);
                    return this.update(entity);
                },

                (draft, stored, published) -> this.markPublished(id, stored)
                        .then(this.draftService.discardByObjectId(id))
                        .thenReturn(published))
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        ABSTRACT_OVERRIDABLE_SERVICE + this.getObjectName() + "Service).publish"));
    }

    /**
     * Flip the never-published marker, and only that.
     *
     * `published` is not carried through update(): it is server state, and letting
     * a request body set it meant an ordinary save could hide a live object. So the
     * one legitimate writer sets it directly, on the stored document, after the
     * content update has already succeeded. A no-op for anything already visible,
     * which is almost every publish.
     */
    private Mono<Boolean> markPublished(String id, D stored) {

        if (!Boolean.FALSE.equals(stored.getPublished()))
            return Mono.just(Boolean.TRUE);

        return this.repo.findById(id)
                .flatMap(fresh -> {
                    fresh.setPublished(Boolean.TRUE);
                    return this.repo.save(fresh);
                })
                .thenReturn(Boolean.TRUE)
                .flatMap(done -> this.evictRecursively(stored.getAppCode(), stored.getClientCode(),
                        stored.getName()));
    }

    /** Throw away unpublished work. The live document is untouched. */
    public Mono<Boolean> discardDraft(String id) {

        if (!this.isDraftable())
            return this.notDraftable();

        return FlatMapUtil.<ContextAuthentication, D, Boolean, Boolean>flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.repo.findById(id),

                (ca, stored) -> this.accessCheck(ca, UPDATE, stored.getAppCode(), stored.getClientCode(), true),

                (ca, stored, hasAccess) -> BooleanUtil.safeValueOf(hasAccess)
                        ? this.draftService.discardByObjectId(id)
                                .flatMap(discarded -> this.evictDraft(stored.getAppCode(), stored.getClientCode(),
                                        stored.getName()).thenReturn(discarded))
                        : Mono.empty())
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        ABSTRACT_OVERRIDABLE_SERVICE + this.getObjectName() + "Service).discardDraft"))
                .switchIfEmpty(messageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg), FORBIDDEN_PERMISSION,
                        this.getObjectName()));
    }

    /**
     * Clear the caches the DRAFT surface reads for one object.
     *
     * A draft save leaves the live document alone, so for a long time it evicted
     * nothing at all. That was wrong: the draft surface has caches of its own, and
     * they are filled by the first draft READ, not by the save. Whatever was cached
     * before the save went on being served indefinitely, so the draft host answered
     * with pre-draft content and the draft looked like it had never been written.
     * Measured on appbuilder: a draft moving `defaultPage` to `builderHome` still
     * served `landing` until `ApplicationCache_appbuilder_appbuilder_DRAFT` was
     * evicted by hand.
     *
     * Overrides may also clear caches shared by both surfaces -- the ones whose
     * surface dimension lives in the KEY (the `d-` marked uniqueId, the `-draft`
     * suffix on the index HTML key) rather than in the cache NAME, which cannot be
     * cleared one surface at a time. That costs the live surface a re-read and
     * cannot serve it draft content: every repopulating read caches under the
     * surface it was made on.
     */
    protected Mono<Boolean> evictDraft(String appCode, String clientCode, String name) { // NOSONAR
        // clientCode is unused here and present for the overrides, matching
        // evictRecursively below.
        return cacheService.evictAll(this.getCacheName(appCode, name, true))
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        ABSTRACT_OVERRIDABLE_SERVICE + this.getObjectName() + "Service).evictDraft"));
    }

    protected Mono<D> evictRecursively(D f) {
        return this.evictRecursively(f.getAppCode(), f.getClientCode(), f.getName()).map(e -> f);
    }

    protected Mono<Boolean> evictRecursively(String appCode, String clientCode, String name) {
        // Both surfaces are evicted on every write. A draft save comes through
        // evictDraft above instead, since it never touches the live document; but a
        // publish must clear the draft surface too, or the draft cache keeps serving
        // content that has already gone live and the two disagree.
        return FlatMapUtil.flatMapMono(() -> cacheService.evictAll(this.getCacheName(appCode, name)),

                evict1 -> cacheService.evictAll(this.getCacheName(appCode, name, true)),

                (evict1, evictDraft) -> cacheService
                        .evictAll(this.getCacheName(appCode, this.getObjectName()) + READ_PAGE),

                (evict1, evictDraft, evict2) -> Mono.just(evict1 && evict2))
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        ABSTRACT_OVERRIDABLE_SERVICE + this.getObjectName() + "Service).evictRecursively"));
    }

    @Override
    public Mono<Boolean> delete(String id) {

        Mono<D> exists = this.repo.findById(id)
                .switchIfEmpty(messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND,
                        msg), AbstractMongoMessageResourceService.OBJECT_NOT_FOUND, this.getObjectName(), id));

        return FlatMapUtil.flatMapMono(

                () -> exists,

                entity -> this.repo.countByNameAndAppCodeAndBaseClientCode(entity.getName(), entity.getAppCode(),
                        entity.getClientCode()),

                (entity, count) -> SecurityContextUtil.getUsersContextAuthentication(),

                (entity, count, ca) -> this.accessCheck(ca, DELETE, entity == null ? null : entity.getAppCode(),
                        entity == null ? null : entity.getClientCode(), true),

                (entity, count, ca, hasAccess) -> {

                    if (!BooleanUtil.safeValueOf(hasAccess))
                        return Mono.empty();

                    if (count > 0l)
                        return messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                AbstractMongoMessageResourceService.UNABLE_TO_DELETE, this.getObjectName(), id);

                    return super.delete(id)

                            .flatMap(e -> cacheService
                                    .evict(this.getCacheName(entity.getAppCode(), entity.getName()),
                                            entity.getClientCode())
                                    .map(x -> e))

                            // Both surfaces, matching evictRecursively. Without this a
                            // deleted object kept being served from the draft cache.
                            .flatMap(e -> cacheService
                                    .evict(this.getCacheName(entity.getAppCode(), entity.getName(), true),
                                            entity.getClientCode())
                                    .map(x -> e))

                            .flatMap(e -> cacheService
                                    .evictAll(this.getCacheName(entity.getAppCode(), this.getObjectName()) + READ_PAGE)
                                    .map(x -> e))

                            // The draft goes with the object it drafts. Without this
                            // the row outlived its object and stayed in the pending
                            // list forever, unpublishable (publish reads the stored
                            // document by id, which is gone) and undiscardable through
                            // the UI. Worse, the draft lookup is keyed on NAME, not id,
                            // so creating a new object with the deleted one's name
                            // resurrected the dead draft's content on the draft surface
                            // under the new object.
                            //
                            // Keyed by name for exactly that reason: it is the key the
                            // read path uses, so this is the one that guarantees no
                            // resurrection even if a row's objectId were stale.
                            //
                            // Deliberately NOT gated on isDraftable(). Nothing can
                            // write a draft for a non-draftable service today, so this
                            // is a no-op query for them, but "a deleted object never
                            // leaves a draft" is worth having as an unconditional
                            // invariant rather than one contingent on a flag that a
                            // later service could flip off with rows already stored.
                            // One indexed lookup on a delete is a fair price.
                            .flatMap(e -> this.draftService == null ? Mono.just(e)
                                    : this.draftService
                                            .discard(this.getObjectName().toUpperCase(), entity.getAppCode(),
                                                    entity.getName(), entity.getClientCode())
                                            .map(x -> e));
                })

                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        ABSTRACT_OVERRIDABLE_SERVICE + this.getObjectName() + "Service).delete"))
                .switchIfEmpty(
                        this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                AbstractMongoMessageResourceService.UNABLE_TO_DELETE, this.getObjectName(), id));
    }

    protected Mono<D> getMergedSources(D entity) {

        if (entity == null)
            return Mono.empty();

        if (entity.getBaseClientCode() == null)
            return Mono.empty();

        Flux<D> x = Mono.just(entity)
                .expandDeep(e -> e.getBaseClientCode() == null ? Mono.empty()
                        : this.repo.findOneByNameAndAppCodeAndClientCode(e.getName(),
                                e.getAppCode(),
                                e.getBaseClientCode()));

        return x.collectList()
                .flatMap(list -> {

                    // expandDeep yields [self, ...ancestors..., root]. The contract of
                    // this method is "everything below me, merged, excluding me", so the
                    // fold seeds at the root and stops at index 1.
                    //
                    // It previously seeded at size-2 and ran down to index 0, which both
                    // dropped the root from any chain of three or more AND returned the
                    // caller's own entity, since applyOverride mutates and returns `this`.
                    // In update() that made mergedSources == entity, so extractOverride
                    // diffed the object against itself and persisted an empty override.
                    if (list.size() == 1)
                        return Mono.empty();

                    Mono<D> current = Mono.just(list.get(list.size() - 1));

                    for (int i = list.size() - 2; i >= 1; i--) {
                        final int fi = i;
                        current = current.flatMap(b -> list.get(fi)
                                .applyActualOverride(b));
                    }

                    return current;
                });
    }

    protected boolean isVersionable() {
        return true;
    }

    protected Mono<D> extractOverride(D entity, D mergedSources) {
        if (entity == null)
            return Mono.empty();

        if (mergedSources == null)
            return Mono.just(entity);

        return entity.makeActualOverride(mergedSources);
    }

    protected Mono<D> applyOverride(D entity, D mergedSources) {
        if (entity == null)
            return Mono.empty();

        if (mergedSources == null)
            return Mono.just(entity);

        return entity.applyActualOverride(mergedSources);
    }

    @Override
    protected Mono<String> getLoggedInUserId() {

        return SecurityContextUtil.getUsersContextAuthentication()
                .map(ContextAuthentication::getUser)
                .map(ContextUser::getId)
                .map(Object::toString);
    }

    // While making the transport object, we are converting the entity to a map
    public Flux<D> readForTransport(String appCode, String clientCode, List<String> names) {

        if (StringUtil.safeIsBlank(appCode) || StringUtil.safeIsBlank((clientCode)))
            return this.messageResourceService.throwFluxMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                    AbstractMongoMessageResourceService.FORBIDDEN_APP_ACCESS, appCode);

        Mono<Tuple2<Boolean, String>> accessCheck = accessCheckForTransport(appCode, clientCode);

        LinkedMultiValueMap<String, String> mMap = new LinkedMultiValueMap<>();
        mMap.put(CLIENT_CODE, List.of(clientCode));
        mMap.put(APP_CODE, List.of(appCode));
        if (names != null && !names.isEmpty())
            mMap.put("name", names);

        return accessCheck.flatMap(e -> this.paramToConditionLRO(mMap, appCode))
                .flatMap(e -> this.filter(e.getT1()))
                .flatMapMany(e -> {
                    // Only getId() is used below — readInternal then fetches each object
                    // properly (through the cache, with overrides applied). Projecting to
                    // _id keeps this from decoding every full definition twice.
                    Query query = new Query(new Criteria().andOperator(e,
                            new Criteria().orOperator(Criteria.where(NOT_OVERRIDABLE)
                                    .ne(Boolean.TRUE),
                                    Criteria.where(CLIENT_CODE)
                                            .is(clientCode))));
                    query.fields().include("_id");

                    return this.mongoTemplate.find(query, this.pojoClass, this.getCollectionName());
                })
                .flatMap(e -> this.readInternal(e.getId()))
                .filter(e -> e.getClientCode()
                        .equals(clientCode));
    }

    private Mono<Tuple2<Boolean, String>> accessCheckForTransport(String appCode, String clientCode) {
        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> {
                    if (!ca.isAuthenticated())
                        return Mono.empty();

                    if (ca.isSystemClient())
                        return Mono.just(Tuples.of(true, clientCode));

                    if (clientCode == null || ca.getClientCode()
                            .equals(clientCode))
                        return this.securityService.hasReadAccess(appCode, ca.getClientCode())
                                .map(e -> Tuples.of(e, ca.getClientCode()));

                    return this.securityService.doesClientManageClientCode(ca.getClientCode(), clientCode)
                            .flatMap(e -> !BooleanUtil.safeValueOf(e) ? Mono.empty()
                                    : this.securityService.hasReadAccess(appCode, clientCode))
                            .map(e -> Tuples.of(e, clientCode));
                },

                (ca, access) -> BooleanUtil.safeValueOf(access.getT1()) ? Mono.just(access) : Mono.empty())
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        ABSTRACT_OVERRIDABLE_SERVICE + this.getObjectName() + "Service).accessCheckForTransport"))
                .switchIfEmpty(Mono.defer(() -> this.messageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        AbstractMongoMessageResourceService.FORBIDDEN_APP_ACCESS, appCode)));
    }

    protected String getCollectionName() {
        String cName = this.getObjectName();
        return Character.toLowerCase(cName.charAt(0)) + cName.substring(1);
    }

    public Mono<Page<ListResultObject<D>>> readPageFilterLRO(boolean eager, boolean clientOnly, Pageable pageable,
            MultiValueMap<String, String> params) { // NOSONAR

        final String appCode = params.getFirst(APP_CODE) == null ? "" : params.getFirst(APP_CODE);
        final String clientCode = params.getFirst(CLIENT_CODE);

        int ignoreCount = 1;
        if (params.containsKey("page"))
            ignoreCount++;
        if (params.containsKey("size"))
            ignoreCount++;
        if (params.containsKey("sort"))
            ignoreCount++;
        if (params.containsKey("eager"))
            ignoreCount++;
        if (params.containsKey("clientOnly"))
            ignoreCount++;

        Mono<Tuple2<Boolean, String>> accessCheck = FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> {
                    if (!ca.isAuthenticated())
                        return Mono.empty();

                    if (clientCode == null || ca.getClientCode()
                            .equals(clientCode))
                        return this.securityService.hasReadAccess(appCode, ca.getClientCode())
                                .map(e -> Tuples.of(e, ca.getClientCode()));

                    return this.securityService.doesClientManageClientCode(ca.getClientCode(), clientCode)
                            .flatMap(e -> !BooleanUtil.safeValueOf(e) ? Mono.empty()
                                    : this.securityService.hasReadAccess(appCode, clientCode))
                            .map(e -> Tuples.of(e, clientCode));
                },

                (ca, access) -> BooleanUtil.safeValueOf(access.getT1()) ? Mono.just(access) : Mono.empty())
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        ABSTRACT_OVERRIDABLE_SERVICE + this.getObjectName()
                                + "Service).readPageFilterLRO (accessCheck)"))
                .switchIfEmpty(Mono.defer(() -> this.messageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        AbstractMongoMessageResourceService.FORBIDDEN_APP_ACCESS, appCode)));

        Mono<Page<ListResultObject<D>>> returnList = FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> accessCheck,

                (ca, ac) -> this.paramToConditionLRO(params, appCode),

                (ca, ac, tup) -> this.filter(tup.getT1()),

                (ca, ac, tup, crit) -> {

                    List<Criteria> conditions = new ArrayList<>();
                    conditions.add(crit);
                    conditions.add(new Criteria().orOperator(Criteria.where(NOT_OVERRIDABLE).ne(Boolean.TRUE),
                            Criteria.where(CLIENT_CODE).is(ac.getT2())));

                    if (clientOnly)
                        conditions.add(Criteria.where(CLIENT_CODE).is(ac.getT2()));

                    // Deliberately unpaged at the database: the override chain is resolved
                    // below by picking one winner per name across every client in the chain,
                    // so a skip/limit here would page over pre-dedup rows and drop names.
                    // LRO_FIELDS is what keeps that affordable — the rows come back as
                    // headers, not whole definitions.
                    Query query = new Query(new Criteria().andOperator(conditions))
                            .with(pageable.getSort());
                    query.fields().include(LRO_FIELDS);

                    return this.mongoTemplate.find(
                            query,
                            ListResultObject.class, this.getCollectionName()).map(obj -> (ListResultObject<D>) obj)
                            .collectList();
                },

                (ca, ac, tup, crit, list) -> {

                    Map<String, ListResultObject<D>> things = new HashMap<>();

                    String inClientCode = tup.getT2().isEmpty() ? null : tup.getT2().get(tup.getT2().size() - 1);

                    for (ListResultObject<D> lro : list) {

                        if (!things.containsKey(lro.getName())) {
                            things.put(lro.getName(), lro);
                            continue;
                        }

                        if (lro.getClientCode().equals(inClientCode)) {
                            things.put(lro.getName(), lro);
                        }
                    }

                    Tuple2<Integer, List<ListResultObject<D>>> nList = filterBasedOnPageSize(pageable, list, things);

                    if (!eager)
                        return Mono.just(
                                (Page<ListResultObject<D>>) new PageImpl<>(nList.getT2(), pageable, nList.getT1()));

                    return Flux.fromIterable(nList.getT2())
                            .flatMap(e -> this.read(e.getId()).map(e::setData))
                            .collectList()
                            .map(e -> (Page<ListResultObject<D>>) new PageImpl<>(e, pageable, nList.getT1()));
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        ABSTRACT_OVERRIDABLE_SERVICE + this.getObjectName() + "Service).readPageFilterLRO"))
                .defaultIfEmpty(new PageImpl<>(List.of(), pageable, 0));

        if (((params.size() == ignoreCount) || params.isEmpty()))
            return FlatMapUtil.flatMapMono(

                    SecurityContextUtil::getUsersContextAuthentication,

                    ca -> this.cacheService.cacheValueOrGet(
                            this.getCacheName(appCode, this.getObjectName()) + READ_PAGE, () -> returnList,
                            ca.getClientCode(),
                            ":",
                            "" + pageable.getPageNumber(),
                            ":",
                            "" + pageable.getPageSize(),
                            ":" + eager + ":",
                            pageable.getSort().toString()));

        return returnList;
    }

    /**
     * Every object of this type the given client should see in an app, as
     * headers: one row per name, the override chain already resolved.
     *
     * <p>
     * This exists so an index (the builder's object tree, the cross-service
     * {@code multi} index) reads through exactly the same resolution the list
     * route uses, rather than querying Mongo on {@code appCode} alone. A raw
     * {@code readPageFilter} on {@code appCode} returns every client's copy of
     * every name as its own row, which shows the same page two or three times in
     * a tree and leaks the names and ids of clients outside the caller's
     * inheritance chain.
     *
     * <p>
     * {@code clientCode} blank means the caller's own client, which is also the
     * only shape that hits the {@code READ_PAGE} cache: naming a client is a
     * managed-client read and stays uncached.
     */
    public Mono<Page<ListResultObject<D>>> readIndexLRO(String appCode, String clientCode) {

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(APP_CODE, appCode);
        if (!StringUtil.safeIsBlank(clientCode))
            params.add(CLIENT_CODE, clientCode);

        return this.readPageFilterLRO(false, false, INDEX_PAGE, params)
                .defaultIfEmpty(Page.empty());
    }

    private Tuple2<Integer, List<ListResultObject<D>>> filterBasedOnPageSize(Pageable pageable,
            List<ListResultObject<D>> list,
            Map<String, ListResultObject<D>> things) {

        Set<String> ids = things.values()
                .stream()
                .map(ListResultObject::getId)
                .collect(Collectors.toSet());

        List<ListResultObject<D>> nList = list.stream()
                .sequential()
                .filter(e -> ids.contains(e.getId()))
                .toList();

        int from = (int) pageable.getOffset();
        int to = (int) pageable.getOffset() + pageable.getPageSize();

        int size = nList.size();

        if (nList.size() > from)
            return Tuples.of(size, nList.subList(from, to >= nList.size() ? nList.size() : to));

        return Tuples.of(size, List.of());
    }

    private Mono<Tuple2<ComplexCondition, List<String>>> paramToConditionLRO(MultiValueMap<String, String> params,
            final String appCode) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> {
                    if (params.containsKey(CLIENT_CODE) && !ca.isSystemClient())
                        return this.securityService.doesClientManageClientCode(ca.getClientCode(),
                                params.getFirst(CLIENT_CODE));

                    return Mono.just(Boolean.TRUE);
                },

                (ca, isBeingManaged) -> {

                    if (!BooleanUtil.safeValueOf(isBeingManaged))
                        return Mono.empty();

                    String cc = params.getFirst(CLIENT_CODE);
                    return Mono.just(cc == null ? ca.getClientCode() : cc);
                },

                (ca, isBeingManaged, finClientCode) -> this.inheritanceService.order(appCode, ca.getClientCode(),
                        finClientCode),

                (ca, isBeingManaged, finClientCode, inheritance) -> {

                    List<AbstractCondition> conditions = new ArrayList<>();

                    if (inheritance.size() == 1)
                        conditions.add(new FilterCondition().setField(CLIENT_CODE)
                                .setOperator(FilterConditionOperator.EQUALS)
                                .setValue(inheritance.get(0)));
                    else
                        conditions.add(new FilterCondition().setField(CLIENT_CODE)
                                .setOperator(FilterConditionOperator.IN)
                                .setValue(inheritance.stream()
                                        .collect(Collectors.joining(","))));

                    String applicationName = params.getFirst(APP_CODE);
                    conditions.add(new FilterCondition().setField(APP_CODE)
                            .setOperator(FilterConditionOperator.EQUALS)
                            .setValue(applicationName));

                    conditions.addAll(params.entrySet()
                            .stream()
                            .filter(e -> !READ_LRO_PARAMETERS_IGNORE.contains(e.getKey()))
                            .filter(e -> Objects.nonNull(e.getValue()))
                            .filter(e -> !e.getValue()
                                    .isEmpty())
                            .map(e -> {
                                FilterCondition fc = new FilterCondition().setField(e.getKey());
                                if (e.getValue()
                                        .size() == 1)
                                    return fc.setOperator(FilterConditionOperator.STRING_LOOSE_EQUAL)
                                            .setValue(e.getValue()
                                                    .get(0));
                                List<Object> values = e.getValue()
                                        .stream()
                                        .map(Object.class::cast)
                                        .toList();
                                return fc.setOperator(FilterConditionOperator.IN)
                                        .setMultiValue(values);
                            })
                            .toList());

                    Tuple2<ComplexCondition, List<String>> tup = Tuples
                            .of(new ComplexCondition().setConditions(conditions)
                                    .setOperator(ComplexConditionOperator.AND), inheritance);
                    return Mono.just(tup);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        ABSTRACT_OVERRIDABLE_SERVICE + this.getObjectName() + "Service).paramToConditionLRO"));
    }

    // While transporting the object to find the actual object.
    public Mono<Tuple2<Integer, String>> readToTransport(String name, String appCode, String clientCode) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.accessCheck(ca, CREATE, appCode, clientCode, true),

                (ca, hasAccess) -> {

                    if (!BooleanUtil.safeValueOf(hasAccess))
                        return Mono.empty();

                    return this.repo.findOneByNameAndAppCodeAndClientCode(name, appCode, clientCode);
                },

                (ca, hasAccess, entity) -> Mono.just(Tuples.of(entity.getVersion(), entity.getId())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        ABSTRACT_OVERRIDABLE_SERVICE + this.getObjectName() + "Service).readToTrasnport"));
    }

    public Mono<ObjectWithUniqueID<D>> read(String name, String appCode, String clientCode) {

        return FlatMapUtil.flatMapMonoWithNull(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> ca == null || ca.getUrlClientCode() == null ? Mono.just(clientCode)
                        : Mono.just(ca.getUrlClientCode()),

                (ca, uClientCode) -> this.readInternal(name, appCode, uClientCode, clientCode))
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        ABSTRACT_OVERRIDABLE_SERVICE + this.getObjectName()
                                + "Service).read (name, appCode, clientCode)"));
    }

    protected Mono<ObjectWithUniqueID<D>> readInternal(String name, String appCode, String clientCode) {
        return this.readInternal(name, appCode, clientCode, clientCode)
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        this.getObjectName() + "Service).readInternal (name, appCode, clientCode)"));
    }

    protected Mono<ObjectWithUniqueID<D>> readInternal(String name, String appCode, String urlClientCode,
            String clientCode) {

        return LogUtil.isDraft()
                .flatMap(draft -> this.readInternal(name, appCode, urlClientCode, clientCode,
                        Boolean.TRUE.equals(draft)));
    }

    protected Mono<ObjectWithUniqueID<D>> readInternal(String name, String appCode, String urlClientCode, // NOSONAR
            String clientCode, boolean draft) {
        // Just one complexity point is not a reason to break this function

        return FlatMapUtil.flatMapMonoWithNull(

                () -> Mono.just(clientCode),

                // Live and draft never share a cache entry. The suffix is on the
                // cache NAME rather than the key so that evicting on publish can
                // clear both with the evictAll calls that already exist.
                key -> cacheService.<ObjectWithUniqueID<D>>get(this.getCacheName(appCode, name, draft), key),

                (key, cEntity) -> cEntity != null ? Mono.just(cEntity.getObject())
                        : this.readIfExistsInBase(name, appCode, urlClientCode, clientCode, draft),

                (key, cEntity, bEntity) -> bEntity == null ? Mono.empty()
                        : this.readDrafted(bEntity, draft, clientCode),

                (key, cEntity, bEntity, mEntity) -> {
                    if (cEntity == null && mEntity == null)
                        return Mono.empty();

                    try {
                        return Mono.just(this.pojoClass.getConstructor(this.pojoClass)
                                .newInstance(cEntity != null ? cEntity.getObject() : mEntity));
                    } catch (IllegalAccessException | IllegalArgumentException | InstantiationException
                            | NoSuchMethodException | SecurityException | InvocationTargetException e) {
                        return this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg, e),
                                AbstractMongoMessageResourceService.UNABLE_TO_CREATE_OBJECT, this.getObjectName());
                    }
                },

                (key, cEntity, bEntity, mEntity, clonedEntity) -> {
                    if (clonedEntity == null)
                        return Mono.empty();

                    String checksumCode = cEntity == null ? UniqueUtil.shortUUID() : cEntity.getUniqueId();

                    // The write has to use the same draft-aware name as the read
                    // above. Using the live name unconditionally meant a draft read
                    // populated the LIVE cache with draft content, so whichever
                    // surface was read first won for both.
                    if (cEntity == null && mEntity != null)
                        cacheService.put(this.getCacheName(appCode, name, draft),
                                new ObjectWithUniqueID<>(mEntity, checksumCode), key);

                    return this.applyChange(name, appCode, clientCode, clonedEntity, checksumCode);
                }).contextWrite(Context.of(LogUtil.METHOD_NAME,
                        this.getObjectName() + "Service).readInternal (name, appCode, urlClientCode, clientCode)"));
    }

    /**
     * The object as the draft surface should serve it: the draft content when one
     * exists for this exact document, otherwise the ordinary merged read.
     *
     * The draft holds the full effective object the editor was working on, not a
     * delta, so it is returned as-is rather than re-merged against its base.
     *
     * Substitution happens for the document being served, not for every ancestor
     * in the override chain. A draft taken at a base client is therefore visible
     * on that client's own draft surface but does not re-merge into a derived
     * client's. Doing that correctly means re-deriving a delta from the draft and
     * folding it, which is the whole update pipeline, and getting it subtly wrong
     * would silently resurrect keys a draft had deleted.
     */
    /**
     * Protected rather than private so ApplicationService.readProperties can use it.
     * That method reimplements readInternal's shape for its own cache, and calling
     * readInternal(id) there meant the draft was filtered for but never substituted:
     * the app definition read the whole client runtime depends on ignored drafts
     * entirely.
     */
    protected Mono<D> readDrafted(D stored, boolean draft, String clientCode) {

        if (stored == null)
            return Mono.empty();

        if (!draft || !this.isDraftable() || this.draftService == null)
            return this.readInternal(stored.getId());

        // Only the requesting client's OWN draft. readIfExistsInBase returns the
        // most-derived document in the chain, so when a derived client has no
        // override of its own, `stored` is an ANCESTOR's document. Substituting
        // that ancestor's draft served the base client's unpublished work on every
        // derived client's draft surface, and the base is usually SYSTEM.
        if (!stored.getClientCode().equals(clientCode))
            return this.readInternal(stored.getId());

        return this.draftService
                .find(this.getObjectName().toUpperCase(), stored.getAppCode(), stored.getName(),
                        stored.getClientCode())
                .map(d -> this.objectMapper.convertValue(d.getContent(), this.pojoClass))
                .switchIfEmpty(Mono.defer(() -> this.readInternal(stored.getId())));
    }

    protected Mono<D> readIfExistsInBase(String name, String appCode, String urlClientCode, String clientCode) {
        return this.readIfExistsInBase(name, appCode, urlClientCode, clientCode, false);
    }

    protected Mono<D> readIfExistsInBase(String name, String appCode, String urlClientCode, String clientCode,
            boolean draft) {

        return FlatMapUtil.flatMapMono(() -> this.inheritanceService.order(appCode, urlClientCode, clientCode),

                // Never-published objects are filtered out of the list BEFORE the
                // isEmpty and size()==1 checks below, not inside the reversed walk.
                // The size()==1 branch returns unconditionally, so filtering only in
                // the loop would serve unpublished content whenever a name exists at
                // exactly one client, which is the common case. Dropping them here
                // also means an unpublished override correctly falls back to a
                // published ancestor rather than hiding it.
                //
                // The draft surface keeps them: showing work that has never gone
                // live is the entire point of it.
                clientCodes -> this.repo.findByNameAndAppCodeAndClientCodeIn(name, appCode, clientCodes)
                        .filter(e -> draft || !Boolean.FALSE.equals(e.getPublished()))
                        .collectList(),

                (clientCodes, lst) -> {
                    if (lst.isEmpty())
                        return Mono.empty();
                    if (lst.size() == 1)
                        return Mono.just(lst.getFirst());

                    for (String cc : clientCodes.reversed()) {
                        for (D item : lst) {
                            if (cc.equals(item.getClientCode()))
                                return Mono.just(item);
                        }
                    }

                    return Mono.empty();
                },

                (clientCodes, lst, found) -> Mono.just(this.pojoClass.cast(found)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        ABSTRACT_OVERRIDABLE_SERVICE + this.getObjectName() + "Service).readIfExistsInBase"));
    }

    // These parameters are required for the exteneded classes.
    protected Mono<ObjectWithUniqueID<D>> applyChange(String name, String appCode, String clientCode, D object, // NOSONAR
            String checksumString) { // NOSONAR

        return Mono.just(new ObjectWithUniqueID<>(object, checksumString));
    }

    public String getCacheName(String appCode, String name) {

        return new StringBuilder(this.getObjectName()).append(CACHE_NAME)
                .append("_")
                .append(appCode)
                .append("_")
                .append(name)
                .toString();
    }

    /**
     * Live and draft are separate caches, not separate keys within one cache, so
     * that the evictAll calls already in evictRecursively can clear a whole surface
     * in one go.
     */
    public String getCacheName(String appCode, String name, boolean draft) {
        return draft ? this.getCacheName(appCode, name) + DRAFT_CACHE_SUFFIX : this.getCacheName(appCode, name);
    }

    @SuppressWarnings("unchecked")
    public Mono<D> createForClient(String id, String clientCode) {

        return flatMapMono(

                () -> this.readInternal(id),

                // published is reset rather than inherited: forking an unpublished
                // object must not produce a derived copy that is invisible at runtime
                // with no indication why.
                e -> this.create((D) e.setBaseClientCode(e.getClientCode())
                        .setClientCode(clientCode)
                        .setPublished(null)
                        .setId(null))

        ).contextWrite(Context.of(LogUtil.METHOD_NAME,
                ABSTRACT_OVERRIDABLE_SERVICE + this.getObjectName() + "Service).createForClient"));
    }

    public TransportObject makeTransportObject(Object entity) {
        return new TransportObject(this.getObjectName(),
                this.objectMapper.convertValue(this.pojoClass.cast(entity), TYPE_REFERENCE_MAP));
    }

    public D makeEntity(String objectType, Map<String, Object> transportObject) {

        if (!StringUtil.safeEquals(this.getObjectName(), objectType))
            return null;

        return this.objectMapper.convertValue(transportObject, this.pojoClass);
    }

    public Class<D> getPojoClass() {
        return this.pojoClass;
    }

    /**
     * Names of other objects of <b>this same type</b> that have to be saved
     * before this one, used to order a transport import.
     * <p>
     * Only same type edges belong here. Ordering between types is the job of
     * the order the services are listed in {@code getServieMap()}.
     */
    public Collection<String> getTransportDependencies(D entity) { // NOSONAR
        // Overridden by the types that actually have dependencies.
        return List.of();
    }

    /**
     * A copy of this entity with the fields that create its same type
     * dependencies removed, for the first of a two pass save.
     * <p>
     * Objects that depend on each other in a cycle cannot be saved in any
     * single pass when the save itself resolves those references. Returning a
     * stripped copy here lets a transport get them all in first and then save
     * them again whole. Null means this type has no way to defer, which is the
     * right answer whenever the reference is structural rather than a plain
     * link.
     */
    public D stripTransportDependencies(D entity) { // NOSONAR
        // Overridden by the types that can defer.
        return null;
    }

    public Mono<Boolean> updatedBaseAppCode(String appCode, String newBaseAppCode, String clientCode) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.accessCheck(ca, UPDATE, appCode, clientCode, true),

                (ca, hasAccess) -> this.accessCheck(ca, UPDATE, newBaseAppCode, clientCode, true),

                (ca, hasAccess, hasBaseAppAccess) -> {

                    if (!BooleanUtil.safeValueOf(hasAccess) || !BooleanUtil.safeValueOf(hasBaseAppAccess))
                        return Mono.just(false);

                    Query query = new Query(new Criteria().andOperator(Criteria.where(APP_CODE).is(appCode),
                            Criteria.where(CLIENT_CODE).is(clientCode),
                            Criteria.where("baseAppCode")
                                    .exists(true)));

                    return this.mongoTemplate.updateMulti(
                            query, Update.update("baseAppCode", newBaseAppCode), this.getCollectionName())
                            .map(e -> true);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        ABSTRACT_OVERRIDABLE_SERVICE + this.getObjectName() + "Service).updatedBaseAppCode"));
    }

    public Mono<Boolean> deleteEverything(String appCode, String clientCode) {

        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.inheritanceService.order(appCode, clientCode, clientCode),

                (ca, order) -> this.securityService.hasWriteAccess(appCode, clientCode),

                (ca, order, hasAccess) -> {
                    if (!BooleanUtil.safeValueOf(hasAccess)) {
                        return Mono.empty();
                    }
                    return this.repo.findByAppCodeAndClientCode(appCode, clientCode)
                            .map(AbstractOverridableDTO::getName).collectList();
                },

                (ca, order, hasAccess, names) -> this.repo.deleteByAppCodeAndClientCode(appCode, clientCode),

                (ca, order, hasAccess, names, count) -> this.versionService.deleteBy(appCode, clientCode,
                        this.getObjectName().toUpperCase()),

                // Drafts too, for the same reason as versions: an app wipe that left
                // them behind would leave this app's name permanently claimed in the
                // pending list of a client that no longer has the app. Unconditional
                // for the same reason as delete() above.
                (ca, order, hasAccess, names, count, vCount) -> this.draftService == null
                        ? Mono.just(0L)
                        : this.draftService.deleteBy(appCode, this.getObjectName().toUpperCase(), clientCode),

                (ca, order, hasAccess, names, count, vCount, dCount) -> cacheService
                        .evictAll(this.getCacheName(appCode, this.getObjectName()) + READ_PAGE),

                (ca, order, hasAccess, names, count, vCount, dCount, pageCache) -> Flux.fromIterable(names)
                        .map(n -> cacheService.evictAll(this.getCacheName(appCode, n))).collectList()
                        .<Boolean>map(e -> true))
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        ABSTRACT_OVERRIDABLE_SERVICE + this.getObjectName() + "Service).deleteEverything"));
    }
}
