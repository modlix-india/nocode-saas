package com.fincity.saas.ui.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.ui.document.Page;
import com.fincity.saas.ui.model.ComponentDefinition;
import com.fincity.saas.ui.repository.PageRepository;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class PageService extends AbstractUIOverridableDataService<Page, PageRepository> {

    private ApplicationService appServiceForProps;

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(PageService.class);

    public PageService() {
        super(Page.class);
    }

    public void setApplicationService(ApplicationService appService) {
        this.appServiceForProps = appService;
    }

    @Override
    protected Mono<Page> updatableEntity(Page entity) {

        return flatMapMono(

                () -> this.read(entity.getId()),

                existing -> {
                    if (existing.getVersion() != entity.getVersion())
                        return this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                                AbstractMongoMessageResourceService.VERSION_MISMATCH);

                    existing.setDevice(entity.getDevice())
                            .setTranslations(entity.getTranslations())
                            .setProperties(entity.getProperties())
                            .setEventFunctions(entity.getEventFunctions())
                            .setRootComponent(entity.getRootComponent())
                            .setComponentDefinition(entity.getComponentDefinition());

                    existing.setVersion(existing.getVersion() + 1);

                    // Full page PUT: increment all per-component and per-event versions
                    // so any concurrent component-level PATCHes get a 412 on retry.
                    //
                    // Unconditional on purpose. Skipping it when the incoming components
                    // "look unchanged" was tried and reverted: one side of that
                    // comparison has been through Mongo and the other through Jackson,
                    // and structural equality between those two is wrong in both
                    // directions often enough to matter. Anything that must write a page
                    // WITHOUT disturbing these counters needs its own route rather than
                    // a cleverer PUT — `PATCH /{id}/blueprint` is the first of those.
                    incrementAllComponentVersions(existing);

                    return Mono.just(existing);
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "PageService.updatableEntity"));
    }

    @Override
    public Mono<ObjectWithUniqueID<Page>> read(String name, String appCode, String clientCode) {

        return super.read(name, appCode, clientCode).flatMap(pg -> {

            if (StringUtil.safeIsBlank(pg.getObject().getPermission()))
                return Mono.just(pg);

            return flatMapMono(

                    SecurityContextUtil::getUsersContextAuthentication,

                    ca -> Mono.just(ca.isAuthenticated()),

                    (ContextAuthentication ca, @Nonnull Boolean isAuthenticated) -> {

                        if (isAuthenticated)
                            return Mono.just(pg);

                        return flatMapMono(() -> appServiceForProps.readProperties(appCode, appCode, clientCode),

                                props -> {

                                    if (StringUtil.safeIsBlank(props.get("loginPage")))
                                        return Mono.just(pg);

                                    return super.read(props.get("loginPage")
                                            .toString(), appCode, clientCode);
                                }).contextWrite(
                                        Context.of(LogUtil.METHOD_NAME, "PageService.read [Looking for Login page]"));
                    }).contextWrite(Context.of(LogUtil.METHOD_NAME, "PageService.read"));
        })
                .switchIfEmpty(Mono
                        .defer(() -> flatMapMono(() -> appServiceForProps.readProperties(appCode, appCode, clientCode),

                                props -> {

                                    if (StringUtil.safeIsBlank(props.get("notFoundPage")))
                                        return Mono.empty();

                                    return super.read(props.get("notFoundPage")
                                            .toString(), appCode, clientCode);
                                }).contextWrite(Context.of(LogUtil.METHOD_NAME,
                                        "PageService.read [Looking for Not found page]"))));
    }

    @Override
    protected Mono<ObjectWithUniqueID<Page>> applyChange(String name, String appCode, String clientCode, Page page,
            String checksumString) {

        return flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.appServiceForProps.readProperties(appCode, appCode, clientCode),

                (ca, props) -> {
                    if (!StringUtil.safeIsBlank(page.getPermission())) {
                        logger.info("Permission for page {} are {} and with auth : {}", name, page.getPermission(), ca);
                    }
                    if (ca.isAuthenticated()
                            && !SecurityContextUtil.hasAuthority(page.getPermission(), ca.getAuthorities())) {

                        if (StringUtil.safeIsBlank(props.get("forbiddenPage")))
                            return this.messageResourceService.throwMessage(
                                    msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                    AbstractMongoMessageResourceService.FORBIDDEN_PERMISSION, page.getPermission());

                        String fbName = props.get("forbiddenPage").toString();

                        if (!fbName.equals(name))
                            return super.read(fbName, appCode, clientCode);
                    }

                    return Mono.just(new ObjectWithUniqueID<>(page, checksumString));
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "PageService.applyChange"))
                .defaultIfEmpty(new ObjectWithUniqueID<>(page, checksumString));

    }

    /**
     * Increment all per-component and per-event-function versions.
     * Called during full page PUT to invalidate any concurrent component-level PATCHes.
     */
    private void incrementAllComponentVersions(Page page) {
        // Component versions
        Map<String, Integer> cv = page.getComponentVersions();
        if (cv == null) cv = new HashMap<>();
        Map<String, com.fincity.saas.ui.model.ComponentDefinition> compDef = page.getComponentDefinition();
        if (compDef != null) {
            for (String key : compDef.keySet())
                cv.put(key, cv.getOrDefault(key, 0) + 1);
        }
        page.setComponentVersions(cv);

        // Event function versions
        Map<String, Integer> ev = page.getEventFunctionVersions();
        if (ev == null) ev = new HashMap<>();
        Map<String, Object> eventFns = page.getEventFunctions();
        if (eventFns != null) {
            for (String key : eventFns.keySet())
                ev.put(key, ev.getOrDefault(key, 0) + 1);
        }
        page.setEventFunctionVersions(ev);
    }

    // ── Component-level PATCH ─────────────────────────────────────
    //
    // Enables concurrent edits to different components on the same page.
    // PATCH on 'btnSubmit' does NOT conflict with PATCH on 'headerNav'.
    // Version check is per-component-key, not whole-document.

    /**
     * The shape both component-level patches must use: authorize the STORED
     * document, patch the MERGED one, write back only the delta.
     *
     * Both halves of this were wrong before, and both only bite on derived
     * documents, which is why it survived: nearly all PATCH traffic runs on root
     * documents where a delta and a merge are the same thing. Cross-client editing
     * makes every edited document derived, so both become routine.
     *
     * 1. Authorization ran `read(pageId)` and nothing else. That is the READ check
     * (checkAppWriteAccess = false), and inheritance grants a derived client read
     * on every ancestor's document, so any client in the chain could PATCH a
     * component straight into the base client's live page with no write access
     * anywhere in the picture.
     *
     * 2. `read()` returns the MERGED page and `repo.save(existing)` wrote that back
     * over the stored row. On a derived document that flattens the delta: the row
     * stops being an override, freezes a copy of whatever the base held at that
     * moment, and silently stops inheriting.
     *
     * The merged page is still what gets patched, and must be -- a delta legitimately
     * does not contain the component being edited, so patching the stored row
     * directly would 404 on every inherited component. Extracting the override
     * afterwards is what keeps the row a delta, and is the same
     * getMergedSources/extractOverride pair that update() runs.
     */
    private Mono<Page> patchOverridable(String pageId, java.util.function.Function<Page, Mono<Page>> patch) {

        return FlatMapUtil.<ContextAuthentication, Page, Boolean, Page, Page, Page>flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> StringUtil.safeIsBlank(pageId) ? Mono.empty() : this.repo.findById(pageId),

                (ca, stored) -> this.accessCheck(ca, UPDATE, stored.getAppCode(), stored.getClientCode(), true),

                (ca, stored, hasAccess) -> BooleanUtil.safeValueOf(hasAccess) ? this.read(pageId) : Mono.empty(),

                (ca, stored, hasAccess, merged) -> patch.apply(merged),

                (ca, stored, hasAccess, merged, patched) -> this.getMergedSources(patched)
                        .flatMap(sources -> this.extractOverride(patched, sources))
                        .defaultIfEmpty(patched)
                        .flatMap(this.repo::save))

                .flatMap(this::evictRecursively)
                .switchIfEmpty(this.messageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        AbstractMongoMessageResourceService.FORBIDDEN_PERMISSION, "Page"))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PageService.patchOverridable"));
    }

    /**
     * A component-level change made FOR a client, whoever makes it.
     *
     * The id says which page; the target client says whose copy changes. Before this,
     * the id was also the copy: a change meant for FIN2 on a page FIN2 had never
     * customized landed in the owner's page and reached every client.
     *
     * - No target, or the target owns the page with this id: today's patch.
     * - The target has its own copy of the page: patch that copy.
     * - The target has none: start from the page as the target's base client sees it,
     *   apply the change, and create the target's copy with baseClientCode = that base
     *   (none when standalone). create() stores only the difference, so a one-component
     *   change stores one component.
     *
     * With draft, nothing live changes: the target's own copy gets the change as its
     * draft (saveDraft over the copy's draft view), and a target with no copy gets an
     * unpublished one (createUnpublished), visible only on the draft surface until it
     * is published. Asked for explicitly, never inferred, like every draft write.
     *
     * Returns the target's page as it now resolves (its draft view when draft).
     */
    public Mono<Page> patchForClient(String pageId, String targetClientCode, boolean standalone, boolean draft,
            java.util.function.Function<Page, Mono<Page>> patch) {

        if (StringUtil.safeIsBlank(targetClientCode) && !draft)
            return this.patchOverridable(pageId, patch);

        return FlatMapUtil.<ContextAuthentication, Page, Boolean, Optional<Page>, Page>flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> StringUtil.safeIsBlank(pageId) ? Mono.empty() : this.repo.findById(pageId),

                        // The id only names the page. The caller must at least be able to
                        // READ it, as a plain read(id) requires, before its name and app are
                        // used to find or create anything. The write check comes next, on
                        // the document actually written.
                        (ca, stored) -> this.accessCheck(ca, READ, stored.getAppCode(), stored.getClientCode(), false)
                                .filter(BooleanUtil::safeValueOf),

                        (ca, stored, canRead) -> this.targetOf(stored, targetClientCode).equals(stored.getClientCode())
                                ? Mono.just(Optional.of(stored))
                                : this.repo.findOneByNameAndAppCodeAndClientCode(stored.getName(),
                                                stored.getAppCode(), this.targetOf(stored, targetClientCode))
                                        .map(Optional::of)
                                        .defaultIfEmpty(Optional.empty()),

                        (ca, stored, canRead, own) -> {
                            if (own.isEmpty())
                                return this.createForTarget(stored, this.targetOf(stored, targetClientCode),
                                        standalone, draft, patch);
                            return draft ? this.patchDraft(ca, own.get(), patch)
                                    : this.patchOverridable(own.get().getId(), patch);
                        })
                .switchIfEmpty(this.messageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        AbstractMongoMessageResourceService.FORBIDDEN_PERMISSION, "Page"))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PageService.patchForClient"));
    }

    private String targetOf(Page stored, String targetClientCode) {
        return StringUtil.safeIsBlank(targetClientCode) ? stored.getClientCode() : targetClientCode;
    }

    /**
     * The change applied to the draft view of a copy the target already owns. saveDraft
     * stores the full object; publish later runs it through the ordinary update, which
     * is where the difference is extracted.
     */
    private Mono<Page> patchDraft(ContextAuthentication ca, Page own, java.util.function.Function<Page, Mono<Page>> patch) {

        // UPDATE on the copy itself, checked here and refused here. saveDraft alone would
        // not refuse a caller without direct access: its resolveDraftTarget forks the page
        // into the CALLER's client instead, which is a write nobody asked for.
        return this.accessCheck(ca, UPDATE, own.getAppCode(), own.getClientCode(), true)
                .filter(BooleanUtil::safeValueOf)
                .flatMap(allowed -> this.readDraft(own.getId()))
                .map(view -> this.objectMapper.convertValue(view, Page.class))
                .flatMap(patch)
                .flatMap(this::saveDraft)
                .flatMap(saved -> this.readDraft(own.getId()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PageService.patchDraft"));
    }

    private Mono<Page> createForTarget(Page stored, String targetClientCode, boolean standalone, boolean draft,
            java.util.function.Function<Page, Mono<Page>> patch) {

        String name = stored.getName();
        String appCode = stored.getAppCode();

        return FlatMapUtil.flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> this.accessCheck(ca, CREATE, appCode, targetClientCode, true)
                                .filter(BooleanUtil::safeValueOf),

                        (ca, allowed) -> this.baseClientCodeOf(appCode, targetClientCode)
                                .defaultIfEmpty(stored.getClientCode()),

                        // The page as the target's base sees it, so a client under FIN starts
                        // from FIN's customization when FIN has one. Copied through Jackson:
                        // the read is cached and the patch mutates components in place.
                        (ca, allowed, base) -> this.readInternal(name, appCode, base)
                                .map(ObjectWithUniqueID::getObject)
                                .map(merged -> this.objectMapper.convertValue(merged, Page.class)),

                        (ca, allowed, base, seed) -> patch.apply(seed),

                        (ca, allowed, base, seed, patched) -> {
                            patched.setId(null);
                            patched.setClientCode(targetClientCode);
                            patched.setBaseClientCode(standalone ? null : base);
                            patched.setVersion(1);
                            patched.setPublished(null);
                            return draft ? this.createUnpublished(patched) : this.create(patched);
                        },

                        (ca, allowed, base, seed, patched, created) -> this.cacheService
                                .evictAll(EngineService.CACHE_NAME_PAGE + "-" + appCode)
                                .then(this.readInternal(created.getId())))
                .switchIfEmpty(this.messageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        AbstractMongoMessageResourceService.FORBIDDEN_PERMISSION, "Page"))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PageService.createForTarget"));
    }

    /**
     * Add a component under a parent. A key the page already has is refused rather than
     * overwritten; that is what patchComponent is for.
     */
    public Mono<Page> addComponent(String pageId, String componentKey, Map<String, Object> componentData,
            String parentKey, String message, String targetClientCode, boolean standalone, boolean draft) {

        return this.patchForClient(pageId, targetClientCode, standalone, draft, existing -> {

            Map<String, ComponentDefinition> compDef = existing.getComponentDefinition();
            if (compDef == null) {
                compDef = new HashMap<>();
                existing.setComponentDefinition(compDef);
            }

            if (compDef.containsKey(componentKey))
                return this.messageResourceService.<Page>throwMessage(
                        msg -> new GenericException(HttpStatus.CONFLICT, msg),
                        AbstractMongoMessageResourceService.ALREADY_EXISTS, "Component", componentKey);

            ComponentDefinition parent = StringUtil.safeIsBlank(parentKey) ? null : compDef.get(parentKey);
            if (parent == null)
                return this.messageResourceService.<Page>throwMessage(
                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                        AbstractMongoMessageResourceService.OBJECT_NOT_FOUND, "Component", parentKey);

            ComponentDefinition added = this.objectMapper.convertValue(componentData, ComponentDefinition.class);
            added.setKey(componentKey);
            compDef.put(componentKey, added);

            Map<String, Boolean> children = parent.getChildren() == null ? new HashMap<>()
                    : new HashMap<>(parent.getChildren());
            children.put(componentKey, Boolean.TRUE);
            parent.setChildren(children);

            Map<String, Integer> cv = existing.getComponentVersions() == null ? new HashMap<>()
                    : existing.getComponentVersions();
            cv.put(componentKey, 1);
            cv.put(parentKey, cv.getOrDefault(parentKey, 1) + 1);
            existing.setComponentVersions(cv);

            existing.setVersion(existing.getVersion() + 1);
            existing.setMessage(message);
            return Mono.just(existing);
        }).contextWrite(Context.of(LogUtil.METHOD_NAME, "PageService.addComponent"));
    }

    /**
     * Remove a component and everything under it. On a client's copy the removal is
     * that client's alone: the stored difference records the keys as removed.
     */
    public Mono<Page> removeComponent(String pageId, String componentKey, String message,
            String targetClientCode, boolean standalone, boolean draft) {

        return this.patchForClient(pageId, targetClientCode, standalone, draft, existing -> {

            Map<String, ComponentDefinition> compDef = existing.getComponentDefinition();
            if (compDef == null || !compDef.containsKey(componentKey))
                return this.messageResourceService.<Page>throwMessage(
                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                        AbstractMongoMessageResourceService.OBJECT_NOT_FOUND, "Component", componentKey);

            if (componentKey.equals(existing.getRootComponent()))
                return this.messageResourceService.<Page>throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        AbstractMongoMessageResourceService.OBJECT_NOT_UPDATABLE);

            java.util.Set<String> gone = new java.util.LinkedHashSet<>();
            java.util.Deque<String> todo = new java.util.ArrayDeque<>(List.of(componentKey));
            while (!todo.isEmpty()) {
                String key = todo.pop();
                if (!gone.add(key)) continue;
                ComponentDefinition c = compDef.get(key);
                if (c != null && c.getChildren() != null) todo.addAll(c.getChildren().keySet());
            }

            Map<String, Integer> cv = existing.getComponentVersions() == null ? new HashMap<>()
                    : existing.getComponentVersions();

            for (Map.Entry<String, ComponentDefinition> e : compDef.entrySet()) {
                if (gone.contains(e.getKey()) || e.getValue().getChildren() == null
                        || !e.getValue().getChildren().containsKey(componentKey))
                    continue;
                Map<String, Boolean> children = new HashMap<>(e.getValue().getChildren());
                children.remove(componentKey);
                e.getValue().setChildren(children);
                cv.put(e.getKey(), cv.getOrDefault(e.getKey(), 1) + 1);
            }

            gone.forEach(compDef::remove);
            gone.forEach(cv::remove);
            existing.setComponentVersions(cv);

            existing.setVersion(existing.getVersion() + 1);
            existing.setMessage(message);
            return Mono.just(existing);
        }).contextWrite(Context.of(LogUtil.METHOD_NAME, "PageService.removeComponent"));
    }

    /**
     * Patch a single component within a page with per-component version checking.
     *
     * @param pageId                  Page MongoDB ID
     * @param componentKey            Component key to update
     * @param componentData           The updated ComponentDefinition fields (merged onto existing)
     * @param expectedComponentVersion Expected version of this specific component (0 = skip check)
     * @param message                 Change description for version history
     */
    public Mono<Page> patchComponent(String pageId, String componentKey,
            Map<String, Object> componentData, int expectedComponentVersion, String message) {
        return this.patchComponent(pageId, componentKey, componentData, expectedComponentVersion, message, null,
                false, false);
    }

    /**
     * The same patch, made for a target client: see {@link #patchForClient}.
     */
    public Mono<Page> patchComponent(String pageId, String componentKey,
            Map<String, Object> componentData, int expectedComponentVersion, String message,
            String targetClientCode, boolean standalone, boolean draft) {

        return this.patchForClient(pageId, targetClientCode, standalone, draft,

                existing -> {
                    Map<String, ComponentDefinition> compDef = existing.getComponentDefinition();
                    if (compDef == null || !compDef.containsKey(componentKey))
                        return this.messageResourceService.<Page>throwMessage(
                                msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                AbstractMongoMessageResourceService.OBJECT_NOT_FOUND,
                                "Component", componentKey);

                    // Per-component version check
                    Map<String, Integer> cv = existing.getComponentVersions();
                    if (cv != null && expectedComponentVersion > 0) {
                        int currentCv = cv.getOrDefault(componentKey, 1);
                        if (currentCv != expectedComponentVersion)
                            return this.messageResourceService.<Page>throwMessage(
                                    msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                                    AbstractMongoMessageResourceService.VERSION_MISMATCH);
                    }

                    // Merge component data onto existing component
                    ComponentDefinition comp = compDef.get(componentKey);
                    ComponentDefinition updated = this.objectMapper.convertValue(
                            componentData, ComponentDefinition.class);
                    if (updated.getProperties() != null)
                        comp.setProperties(updated.getProperties());
                    if (updated.getStyleProperties() != null)
                        comp.setStyleProperties(updated.getStyleProperties());
                    if (updated.getChildren() != null)
                        comp.setChildren(updated.getChildren());
                    if (updated.getDisplayOrder() != null)
                        comp.setDisplayOrder(updated.getDisplayOrder());
                    if (updated.getBindingPath() != null)
                        comp.setBindingPath(updated.getBindingPath());
                    if (updated.getBindingPath2() != null)
                        comp.setBindingPath2(updated.getBindingPath2());
                    if (updated.getBindingPath3() != null)
                        comp.setBindingPath3(updated.getBindingPath3());
                    if (updated.getBindingPath4() != null)
                        comp.setBindingPath4(updated.getBindingPath4());
                    if (updated.getBindingPath5() != null)
                        comp.setBindingPath5(updated.getBindingPath5());
                    if (updated.getBindingPath6() != null)
                        comp.setBindingPath6(updated.getBindingPath6());

                    // Increment per-component version
                    if (cv == null) cv = new HashMap<>();
                    cv.put(componentKey, cv.getOrDefault(componentKey, 1) + 1);
                    existing.setComponentVersions(cv);

                    // Increment whole-doc version
                    existing.setVersion(existing.getVersion() + 1);
                    existing.setMessage(message);

                    return Mono.just(existing);
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "PageService.patchComponent"));
    }

    /**
     * Patch a single event function within a page with per-event version checking.
     *
     * @param pageId                  Page MongoDB ID
     * @param eventName               Event function name
     * @param eventDefinition         The full event function definition (replaces existing)
     * @param expectedEventVersion    Expected version of this event function (0 = skip check)
     * @param message                 Change description
     */
    public Mono<Page> patchEventFunction(String pageId, String eventName,
            Map<String, Object> eventDefinition, int expectedEventVersion, String message) {
        return this.patchEventFunction(pageId, eventName, eventDefinition, expectedEventVersion, message, null,
                false, false);
    }

    /**
     * The same patch, made for a target client: see {@link #patchForClient}.
     */
    public Mono<Page> patchEventFunction(String pageId, String eventName,
            Map<String, Object> eventDefinition, int expectedEventVersion, String message,
            String targetClientCode, boolean standalone, boolean draft) {

        return this.patchForClient(pageId, targetClientCode, standalone, draft,

                existing -> {
                    // Per-event version check
                    Map<String, Integer> ev = existing.getEventFunctionVersions();
                    if (ev != null && expectedEventVersion > 0) {
                        int currentEv = ev.getOrDefault(eventName, 1);
                        if (currentEv != expectedEventVersion)
                            return this.messageResourceService.<Page>throwMessage(
                                    msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                                    AbstractMongoMessageResourceService.VERSION_MISMATCH);
                    }

                    // Set event function
                    Map<String, Object> eventFunctions = existing.getEventFunctions();
                    if (eventFunctions == null) eventFunctions = new HashMap<>();
                    eventFunctions.put(eventName, eventDefinition);
                    existing.setEventFunctions(eventFunctions);

                    // Increment per-event version
                    if (ev == null) ev = new HashMap<>();
                    ev.put(eventName, ev.getOrDefault(eventName, 1) + 1);
                    existing.setEventFunctionVersions(ev);

                    // Increment whole-doc version
                    existing.setVersion(existing.getVersion() + 1);
                    existing.setMessage(message);

                    return Mono.just(existing);
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "PageService.patchEventFunction"));
    }

    @Override
    public Mono<Page> update(Page page) {
        return super.update(page)
                .flatMap(this.cacheService.evictAllFunction(EngineService.CACHE_NAME_PAGE + "-" + page.getAppCode()));
    }

    @Override
    public Mono<Boolean> delete(String id) {
        return FlatMapUtil.flatMapMono(
                () -> this.read(id),

                page -> super.delete(id),

                (page, deleted) -> this.cacheService.evictAll(EngineService.CACHE_NAME_PAGE + "-" + page.getAppCode()),

                (page, deleted, cacheEvicted) -> this.ssrCacheEvictionService.evictByAppCode(page.getAppCode()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PageService.delete"));
    }
}
